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

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.handler.codec.http.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.netflix.zuul.netty.server.push.PushConnectionRegistry.PushConnection;
import static io.netty.handler.codec.http.HttpResponseStatus.*;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

/**
 * Serves "/push" URL that is used by the backend to POST push messages to a given Zuul instance. This URL handler
 * MUST BE accessible ONLY from RFC 1918 private internal network space (10.0.0.0 or 172.16.0.0) to guarantee that
 * external applications/agents cannot push messages to your client. In AWS this can typically be achieved using
 * correctly configured security groups.
 *
 * Author: Susheel Aroskar
 * Date: 5/14/18
 */
@Singleton
@ChannelHandler.Sharable
public abstract class PushMessageSender  extends SimpleChannelInboundHandler<FullHttpRequest> {

    private final PushConnectionRegistry pushConnectionRegistry;

    private static final Logger logger = LoggerFactory.getLogger(PushMessageSender.class);


    @Inject
    public PushMessageSender(PushConnectionRegistry pushConnectionRegistry) {
        this.pushConnectionRegistry = pushConnectionRegistry;
    }


    private void sendHttpResponse(ChannelHandlerContext ctx, FullHttpRequest request, HttpResponseStatus status,
                                  PushUserAuth userAuth) {
        final FullHttpResponse resp = new DefaultFullHttpResponse(HTTP_1_1, status);
        resp.headers().add("Content-Length", "0");
        final ChannelFuture cf = ctx.channel().writeAndFlush(resp);
        if (!HttpUtil.isKeepAlive(request)) {
            cf.addListener(ChannelFutureListener.CLOSE);
        }
        logPushEvent(request, status, userAuth);
    }


    @Override
    protected void channelRead0(final ChannelHandlerContext ctx, final FullHttpRequest request) throws Exception {
        if (!request.decoderResult().isSuccess()) {
            sendHttpResponse(ctx, request, BAD_REQUEST, null);
            return;
        }

        final String path = request.uri();
        if (path == null) {
            sendHttpResponse(ctx, request, BAD_REQUEST, null);
            return;
        }

        if (path.endsWith("/push")) {
            logPushAttempt();

            final HttpMethod method = request.method();
            if ((method != HttpMethod.POST) && (method != HttpMethod.GET)) {
                sendHttpResponse(ctx, request, METHOD_NOT_ALLOWED, null);
                return;
            }


            final PushUserAuth userAuth = getPushUserAuth(request);
            if (!userAuth.isSuccess()) {
                sendHttpResponse(ctx, request, UNAUTHORIZED, userAuth);
                logNoIdentity();
                return;
            }

            final PushConnection pushConn = pushConnectionRegistry.get(userAuth.getClientIdentity());
            if (pushConn == null) {
                sendHttpResponse(ctx, request, NOT_FOUND, userAuth);
                logClientNotConnected();
                return;
            }

            if (method == HttpMethod.GET) {
                //client only checking if particular CID + ESN is connected to this instance
                sendHttpResponse(ctx, request, OK, userAuth);
                return;
            }

            final ByteBuf body = request.content().retain();
            if (body.readableBytes() <= 0) {
                sendHttpResponse(ctx, request, NO_CONTENT, userAuth);
                return;
            }

            final ChannelFuture clientFuture = pushConn.sendPushMessage(body);
            clientFuture.addListener(cf -> {
                HttpResponseStatus status;
                if (cf.isSuccess()) {
                    logPushSuccess();
                    status = OK;
                } else {
                    logPushError(cf.cause());
                    status = INTERNAL_SERVER_ERROR;
                }
                sendHttpResponse(ctx, request, status, userAuth);
            });
        }
        else {
            //Last handler in the chain
            sendHttpResponse(ctx, request, BAD_REQUEST, null);
        }
    }

    protected void logPushAttempt() {
        logger.debug("pushing notification");
    }

    protected void logNoIdentity() {
        logger.debug("push notification missing identity");
    }

    protected void logClientNotConnected() {
        logger.debug("push notification, client not connected");
    }

    protected void logPushSuccess() {
        logger.debug("push notification success");
    }

    protected void logPushError(Throwable t) {
        logger.debug("pushing notification error", t);
    }

    protected void logPushEvent(FullHttpRequest request, HttpResponseStatus status, PushUserAuth userAuth) {
        logger.debug("Push notification status: {}, auth: {}", status.code(), userAuth != null ? userAuth : "-");
    }

    protected abstract PushUserAuth getPushUserAuth(FullHttpRequest request);



}
