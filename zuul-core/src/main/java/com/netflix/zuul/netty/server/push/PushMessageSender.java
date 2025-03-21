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

import com.google.common.base.Strings;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.ReferenceCountUtil;
import jakarta.inject.Inject;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Serves "/push" URL that is used by the backend to POST push messages to a given Zuul instance. This URL handler
 * MUST BE accessible ONLY from RFC 1918 private internal network space (10.0.0.0 or 172.16.0.0) to guarantee that
 * external applications/agents cannot push messages to your client. In AWS this can typically be achieved using
 * correctly configured security groups.
 *
 * Author: Susheel Aroskar
 * Date: 5/14/18
 */
@ChannelHandler.Sharable
public abstract class PushMessageSender extends SimpleChannelInboundHandler<FullHttpRequest> {

    private final PushConnectionRegistry pushConnectionRegistry;

    public static final String SECURE_TOKEN_HEADER_NAME = "X-Zuul.push.secure.token";
    private static final Logger logger = LoggerFactory.getLogger(PushMessageSender.class);

    @Inject
    public PushMessageSender(PushConnectionRegistry pushConnectionRegistry) {
        this.pushConnectionRegistry = pushConnectionRegistry;
    }

    private void sendHttpResponse(
            ChannelHandlerContext ctx, FullHttpRequest request, HttpResponseStatus status, PushUserAuth userAuth) {
        FullHttpResponse resp = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status);
        resp.headers().add("Content-Length", "0");
        ChannelFuture cf = ctx.channel().writeAndFlush(resp);
        if (!HttpUtil.isKeepAlive(request)) {
            cf.addListener(ChannelFutureListener.CLOSE);
        }
        logPushEvent(request, status, userAuth);
    }

    protected boolean verifySecureToken(FullHttpRequest request, PushConnection conn) {
        String secureToken = request.headers().get(SECURE_TOKEN_HEADER_NAME);
        if (Strings.isNullOrEmpty(secureToken)) {
            // caller is not asking to verify secure token
            return true;
        }
        return secureToken.equals(conn.getSecureToken());
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) throws Exception {
        if (!request.decoderResult().isSuccess()) {
            sendHttpResponse(ctx, request, HttpResponseStatus.BAD_REQUEST, null);
            return;
        }

        String path = request.uri();
        if (path == null) {
            sendHttpResponse(ctx, request, HttpResponseStatus.BAD_REQUEST, null);
            return;
        }

        if (path.endsWith("/push")) {
            logPushAttempt();

            HttpMethod method = request.method();
            if (!Objects.equals(method, HttpMethod.POST) && !Objects.equals(method, HttpMethod.GET)) {
                sendHttpResponse(ctx, request, HttpResponseStatus.METHOD_NOT_ALLOWED, null);
                return;
            }

            PushUserAuth userAuth = getPushUserAuth(request);
            if (!userAuth.isSuccess()) {
                sendHttpResponse(ctx, request, HttpResponseStatus.UNAUTHORIZED, userAuth);
                logNoIdentity();
                return;
            }

            PushConnection pushConn = pushConnectionRegistry.get(userAuth.getClientIdentity());
            if (pushConn == null) {
                sendHttpResponse(ctx, request, HttpResponseStatus.NOT_FOUND, userAuth);
                logClientNotConnected();
                return;
            }

            if (!verifySecureToken(request, pushConn)) {
                sendHttpResponse(ctx, request, HttpResponseStatus.FORBIDDEN, userAuth);
                logSecurityTokenVerificationFail();
                return;
            }

            if (Objects.equals(method, HttpMethod.GET)) {
                // client only checking if particular CID + ESN is connected to this instance
                sendHttpResponse(ctx, request, HttpResponseStatus.OK, userAuth);
                return;
            }

            if (pushConn.isRateLimited()) {
                sendHttpResponse(ctx, request, HttpResponseStatus.SERVICE_UNAVAILABLE, userAuth);
                logRateLimited();
                return;
            }

            ByteBuf body = request.content().retain();
            if (body.readableBytes() <= 0) {
                sendHttpResponse(ctx, request, HttpResponseStatus.NO_CONTENT, userAuth);
                // Because we are not passing the body to the pushConn (who would normally handle destroying),
                // we need to release it here.
                ReferenceCountUtil.release(body);
                return;
            }

            ChannelFuture clientFuture = pushConn.sendPushMessage(body);
            clientFuture.addListener(cf -> {
                HttpResponseStatus status;
                if (cf.isSuccess()) {
                    logPushSuccess();
                    status = HttpResponseStatus.OK;
                } else {
                    logPushError(cf.cause());
                    status = HttpResponseStatus.INTERNAL_SERVER_ERROR;
                }
                sendHttpResponse(ctx, request, status, userAuth);
            });
        } else {
            // Last handler in the chain
            sendHttpResponse(ctx, request, HttpResponseStatus.BAD_REQUEST, null);
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

    protected void logRateLimited() {
        logger.warn("Push message was rejected because of the rate limiting");
    }

    protected void logSecurityTokenVerificationFail() {
        logger.warn("Push secure token verification failed");
    }

    protected void logPushEvent(FullHttpRequest request, HttpResponseStatus status, PushUserAuth userAuth) {
        logger.debug("Push notification status: {}, auth: {}", status.code(), userAuth != null ? userAuth : "-");
    }

    protected abstract PushUserAuth getPushUserAuth(FullHttpRequest request);
}
