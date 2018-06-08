/**
 * Copyright 2018 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.zuul.netty.server.push;

import com.netflix.config.CachedDynamicIntProperty;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.ReferenceCountUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.netflix.zuul.netty.server.push.PushConnectionRegistry.PushConnection;

/**
 * Author: Susheel Aroskar
 * Date: 5/14/18
 */
public abstract class PushRegistrationHandler extends ChannelInboundHandlerAdapter {

    protected final PushConnectionRegistry pushConnectionRegistry;
    protected final PushProtocol pushProtocol;

    /* Identity */
    private volatile PushUserAuth authEvent;

    /* state */
    protected final AtomicBoolean destroyed;
    private ChannelHandlerContext ctx;
    private volatile PushConnection pushConnection;


    public static final CachedDynamicIntProperty PUSH_REGISTRY_TTL = new CachedDynamicIntProperty("zuul.push.registry.ttl.seconds", 30 * 60);
    public static final CachedDynamicIntProperty RECONNECT_DITHER = new CachedDynamicIntProperty("zuul.push.reconnect.dither.seconds", 3 * 60);
    public static final CachedDynamicIntProperty UNAUTHENTICATED_CONN_TTL = new CachedDynamicIntProperty("zuul.push.noauth.ttl.seconds", 8);
    public static final CachedDynamicIntProperty CLIENT_CLOSE_GRACE_PERIOD = new CachedDynamicIntProperty("zuul.push.client.close.grace.period", 4);

    private static Logger logger = LoggerFactory.getLogger(PushRegistrationHandler.class);


    public PushRegistrationHandler(PushConnectionRegistry pushConnectionRegistry, PushProtocol pushProtocol) {
        this.pushConnectionRegistry = pushConnectionRegistry;
        this.pushProtocol = pushProtocol;
        this.destroyed = new AtomicBoolean();
    }

    protected final boolean isAuthenticated() {
        return (authEvent != null && authEvent.isSuccess());
    }

    protected final PushUserAuth getAuthEvent() {
        return authEvent;
    }

    @Override
    public final void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        this.ctx = ctx;
        try {
            handleRead(ctx, msg);
        }
        finally {
            ReferenceCountUtil.release(msg);
        }
    }


    private void tearDown()  {
        if (! destroyed.get()) {
            destroyed.set(true);
            if (authEvent != null) {
                pushConnectionRegistry.remove(authEvent.getClientIdentity());
                logger.debug("Closing connection for {}", authEvent);
            }
        }
    }

    @Override
    public final void channelInactive(ChannelHandlerContext ctx) throws Exception {
        tearDown();
        super.channelInactive(ctx);
    }

    @Override
    public final void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.error("Exception caught, closing push channel for " + authEvent, cause);
        ctx.close();
        super.exceptionCaught(ctx, cause);
    }

    protected final void sendErrorAndClose(int statusCode, String reasonText) {
        ctx.writeAndFlush(serverClosingConnectionMessage(statusCode, reasonText)).addListener(ChannelFutureListener.CLOSE);
    }

    protected final void forceCloseConnectionFromServerSide() {
        if (! destroyed.get()) {
            sendErrorAndClose(1000, "server closed connection");
            logger.debug("server forcing close connection");
        }
    }

    private void closeIfNotAuthenticated() {
        if (! isAuthenticated()) {
            logger.error("Closing connection because it is still unauthenticated after {} seconds.", UNAUTHENTICATED_CONN_TTL.get());
            forceCloseConnectionFromServerSide();
        }
    }

    private void requestClientToCloseConnection() {
        if (ctx.channel().isActive()) {
            // Application level protocol for asking client to close connection
            ctx.writeAndFlush(goAwayMessage());
            // Force close connection if client doesn't close in reasonable time after we made request
            ctx.executor().schedule(() -> forceCloseConnectionFromServerSide(), CLIENT_CLOSE_GRACE_PERIOD.get(), TimeUnit.SECONDS);
        } else {
            forceCloseConnectionFromServerSide();
        }
    }

    private int ditheredReconnectDeadline() {
        final int dither = ThreadLocalRandom.current().nextInt(RECONNECT_DITHER.get());
        return PUSH_REGISTRY_TTL.get() - dither - CLIENT_CLOSE_GRACE_PERIOD.get();
    }

    @Override
    public final void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        this.ctx = ctx;
        if (! destroyed.get()) {
            if (evt == pushProtocol.getHandshakeCompleteEvent())  {
                pushConnection = new PushConnection(pushProtocol, ctx);
                // Unauthenticated connection, wait for small amount of time for a client to send auth token in
                // a first web socket frame, otherwise close connection
                ctx.executor().schedule(this::closeIfNotAuthenticated, UNAUTHENTICATED_CONN_TTL.get(), TimeUnit.SECONDS);
                logger.debug("WebSocket handshake complete.");
            }
            else if (evt instanceof PushUserAuth) {
                authEvent = (PushUserAuth) evt;
                if (authEvent.isSuccess()) {
                    logger.debug("registering client {}", authEvent);
                    ctx.pipeline().remove(PushAuthHandler.NAME);
                    registerClient(ctx, authEvent, pushConnection, pushConnectionRegistry);
                    logger.debug("Authentication complete {}", authEvent);
                } else {
                    sendErrorAndClose(1008, "Auth Failed");
                }
            }
        }
        super.userEventTriggered(ctx, evt);
    }

    /**
     * Register authenticated client  - represented by PushAuthEvent - with PushConnectionRegistry of this instance.
     *
     * For all but really simplistic case - basically anything other than a single node push cluster, You'd most likely
     * need some sort of off-box, partitioned, global registration registry that keeps track of which client is connected
     * to which push server instance. You should override this default implementation for such cases and register your
     * client with your global registry in addition to local push connection registry that is limited to this JVM instance
     * Make sure such a registration is done in strictly non-blocking fashion lest you will block Netty event loop
     * decimating your throughput.
     *
     * A typical arrangement is to use something like Memcached or redis cluster sharded by client connection key and
     * to use blocking Memcached/redis driver in a background thread-pool to do the actual registration so that Netty
     * event loop doesn't block
     */
    protected void registerClient(ChannelHandlerContext ctx, PushUserAuth authEvent,
                                 PushConnection conn, PushConnectionRegistry registry) {
        registry.put(authEvent.getClientIdentity(), conn);
        //Make client reconnect after ttl seconds by closing this connection to limit stickiness of the client
        ctx.executor().schedule(this::requestClientToCloseConnection, ditheredReconnectDeadline(), TimeUnit.SECONDS);
    }



    /**
     * Application level protocol for asking client to close connection
     * @return WebSocketFrame which when sent to client will cause it to close the WebSocket
     */
    protected abstract Object goAwayMessage();

    /**
     * Message server sends to the client just before it force closes connection from its side
     * @return
     */
    protected abstract Object serverClosingConnectionMessage(int statusCode, String reasonText);

    /**
     * Implement this method to parse application level protocol if your push connection is duplex, i.e.
     * Client can send messages to server as well as server pushing messages to the client.
     */
    protected abstract void handleRead(ChannelHandlerContext ctx, Object msg);

}
