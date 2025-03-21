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
import com.netflix.zuul.message.http.Cookies;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.cookie.Cookie;
import io.netty.handler.codec.http.cookie.ServerCookieDecoder;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Author: Susheel Aroskar
 * Date: 5/11/18
 */
@ChannelHandler.Sharable
public abstract class PushAuthHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

    private final String pushConnectionPath;
    private final String originDomain;

    public static final String NAME = "push_auth_handler";
    private static final Logger logger = LoggerFactory.getLogger(PushAuthHandler.class);

    public PushAuthHandler(String pushConnectionPath, String originDomain) {
        this.pushConnectionPath = pushConnectionPath;
        this.originDomain = originDomain;
    }

    public final void sendHttpResponse(HttpRequest req, ChannelHandlerContext ctx, HttpResponseStatus status) {
        FullHttpResponse resp = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status);
        resp.headers().add("Content-Length", "0");
        boolean closeConn = (!Objects.equals(status, HttpResponseStatus.OK) || !HttpUtil.isKeepAlive(req));
        if (closeConn) {
            resp.headers().add(HttpHeaderNames.CONNECTION, "Close");
        }
        ChannelFuture cf = ctx.channel().writeAndFlush(resp);
        if (closeConn) {
            cf.addListener(ChannelFutureListener.CLOSE);
        }
    }

    @Override
    protected final void channelRead0(ChannelHandlerContext ctx, FullHttpRequest req) {
        if (!Objects.equals(req.method(), HttpMethod.GET)) {
            sendHttpResponse(req, ctx, HttpResponseStatus.METHOD_NOT_ALLOWED);
            return;
        }

        String path = req.uri();
        if (path.equals("/healthcheck")) {
            sendHttpResponse(req, ctx, HttpResponseStatus.OK);
        } else if (pushConnectionPath.equals(path)) {
            // CSRF protection
            if (isInvalidOrigin(req)) {
                sendHttpResponse(req, ctx, HttpResponseStatus.BAD_REQUEST);
            } else if (isDelayedAuth(req, ctx)) {
                // client auth will happen later, continue with WebSocket upgrade handshake
                ctx.fireChannelRead(req.retain());
            } else {
                PushUserAuth authEvent = doAuth(req, ctx);
                if (authEvent.isSuccess()) {
                    ctx.fireChannelRead(req.retain()); // continue with WebSocket upgrade handshake
                    ctx.fireUserEventTriggered(authEvent);
                } else {
                    logger.warn("Auth failed: {}", authEvent.statusCode());
                    sendHttpResponse(req, ctx, HttpResponseStatus.valueOf(authEvent.statusCode()));
                }
            }
        } else {
            sendHttpResponse(req, ctx, HttpResponseStatus.NOT_FOUND);
        }
    }

    protected boolean isInvalidOrigin(FullHttpRequest req) {
        String origin = req.headers().get(HttpHeaderNames.ORIGIN);
        if (origin == null || !origin.toLowerCase(Locale.ROOT).endsWith(originDomain)) {
            logger.error("Invalid Origin header {} in WebSocket upgrade request", origin);
            return true;
        }
        return false;
    }

    protected final Cookies parseCookies(FullHttpRequest req) {
        Cookies cookies = new Cookies();
        String cookieStr = req.headers().get(HttpHeaderNames.COOKIE);
        if (!Strings.isNullOrEmpty(cookieStr)) {
            List<Cookie> decoded = ServerCookieDecoder.LAX.decodeAll(cookieStr);
            decoded.forEach(cookies::add);
        }
        return cookies;
    }

    /**
     * @return true if Auth credentials will be provided later, for example in first WebSocket frame sent
     */
    protected abstract boolean isDelayedAuth(FullHttpRequest req, ChannelHandlerContext ctx);

    protected abstract PushUserAuth doAuth(FullHttpRequest req, ChannelHandlerContext ctx);
}
