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
import io.netty.channel.*;
import io.netty.handler.codec.http.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

import static io.netty.handler.codec.http.HttpResponseStatus.*;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

/**
 * Author: Susheel Aroskar
 * Date: 5/11/18
 */
@ChannelHandler.Sharable

public abstract class PushAuthHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

    private final String pushConnectionPath;
    private final String originDomain;

    public static final String NAME = "push_auth_handler";
    private static Logger logger = LoggerFactory.getLogger(PushAuthHandler.class);


    public PushAuthHandler(String pushConnectionPath, String originDomain) {
        this.pushConnectionPath = pushConnectionPath;
        this.originDomain = originDomain;
    }

    public final void sendHttpResponse(HttpRequest req, ChannelHandlerContext ctx, HttpResponseStatus status) {
        FullHttpResponse resp = new DefaultFullHttpResponse(HTTP_1_1, status);
        resp.headers().add("Content-Length", "0");
        final boolean closeConn = ((status != OK) || (! HttpUtil.isKeepAlive(req)));
        if (closeConn)  {
            resp.headers().add(HttpHeaderNames.CONNECTION, "Close");
        }
        final ChannelFuture cf = ctx.channel().writeAndFlush(resp);
        if (closeConn) {
            cf.addListener(ChannelFutureListener.CLOSE);
        }
    }

    @Override
    protected final void channelRead0(ChannelHandlerContext ctx, FullHttpRequest req) throws Exception {
        if (req.method() != HttpMethod.GET) {
            sendHttpResponse(req, ctx, METHOD_NOT_ALLOWED);
            return;
        }

        final String path = req.uri();
        if ("/healthcheck".equals(path)) {
            sendHttpResponse(req, ctx, OK);
        }
        else if (pushConnectionPath.equals(path)) {
            // CSRF protection
            final String origin = req.headers().get(HttpHeaderNames.ORIGIN);
            if (((PushProtocol.WEBSOCKET.getPath().equals(pushConnectionPath))) &&
                ((origin == null) || (!origin.toLowerCase().endsWith(originDomain)))) {
                logger.error("Invalid Origin header {} in WebSocket upgrade request", origin);
                sendHttpResponse(req, ctx, BAD_REQUEST);
            }
            else if (isDelayedAuth(req, ctx)) {
                // client auth will happen later, continue with WebSocket upgrade handshake
                ctx.fireChannelRead(req.retain());
            } else {
                final PushUserAuth authEvent = doAuth(req);
                if (authEvent.isSuccess()) {
                    ctx.fireChannelRead(req.retain()); // continue with WebSocket upgrade handshake
                    ctx.fireUserEventTriggered(authEvent);
                } else {
                    logger.warn("Auth failed: {}", authEvent.statusCode());
                    sendHttpResponse(req, ctx, HttpResponseStatus.valueOf(authEvent.statusCode()));
                }
            }
        }
        else {
            sendHttpResponse(req, ctx, NOT_FOUND);
        }
    }

    protected final Cookies parseCookies(FullHttpRequest req) {
        final Cookies cookies = new Cookies();
        final String cookieStr = req.headers().get(HttpHeaderNames.COOKIE);
        if (!Strings.isNullOrEmpty(cookieStr)) {
            final Set<Cookie> decoded = CookieDecoder.decode(cookieStr, false);
            decoded.forEach(cookie -> cookies.add(cookie));
        }
        return cookies;
    }

    /**
     * @return true if Auth credentials will be provided later, for example in first WebSocket frame sent
     */
    protected abstract boolean isDelayedAuth(FullHttpRequest req, ChannelHandlerContext ctx);

    protected abstract PushUserAuth doAuth(FullHttpRequest req);

}
