/*
 * Copyright 2020 Netflix, Inc.
 *
 *      Licensed under the Apache License, Version 2.0 (the "License");
 *      you may not use this file except in compliance with the License.
 *      You may obtain a copy of the License at
 *
 *          http://www.apache.org/licenses/LICENSE-2.0
 *
 *      Unless required by applicable law or agreed to in writing, software
 *      distributed under the License is distributed on an "AS IS" BASIS,
 *      WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *      See the License for the specific language governing permissions and
 *      limitations under the License.
 */

package com.netflix.netty.common.proxyprotocol;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Sets;
import com.netflix.config.DynamicStringListProperty;
import com.netflix.netty.common.ssl.SslHandshakeInfo;
import com.netflix.zuul.netty.server.ssl.SslHandshakeInfoHandler;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.ssl.ClientAuth;
import io.netty.util.AsciiString;

import java.util.Collection;
import java.util.List;

/**
 * Strip out any X-Forwarded-* headers from inbound http requests if connection is not trusted.
 */
@ChannelHandler.Sharable
public class StripUntrustedProxyHeadersHandler extends ChannelInboundHandlerAdapter
{
    private static final DynamicStringListProperty XFF_BLACKLIST =
            new DynamicStringListProperty("zuul.proxy.headers.host.blacklist", "");

    public enum AllowWhen {
        ALWAYS,
        MUTUAL_SSL_AUTH,
        NEVER
    }

    private static final Collection<AsciiString> HEADERS_TO_STRIP = Sets.newHashSet(
            new AsciiString("x-forwarded-for"),
            new AsciiString("x-forwarded-port"),
            new AsciiString("x-forwarded-proto"),
            new AsciiString("x-forwarded-proto-version"),
            new AsciiString("x-real-ip")
    );

    private final AllowWhen allowWhen;

    public StripUntrustedProxyHeadersHandler(AllowWhen allowWhen)
    {
        this.allowWhen = allowWhen;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception
    {
        if (msg instanceof HttpRequest) {
            HttpRequest req = (HttpRequest) msg;

            switch (allowWhen) {
                case NEVER:
                    stripXFFHeaders(req);
                    break;
                case MUTUAL_SSL_AUTH:
                    if (! connectionIsUsingMutualSSLWithAuthEnforced(ctx.channel())) {
                        stripXFFHeaders(req);
                    } else {
                        checkBlacklist(req, XFF_BLACKLIST.get());
                    }
                    break;
                case ALWAYS:
                    checkBlacklist(req, XFF_BLACKLIST.get());
                    break;
                default:
                    // default to not allow.
                    stripXFFHeaders(req);
            }
        }

        super.channelRead(ctx, msg);
    }

    @VisibleForTesting
    boolean connectionIsUsingMutualSSLWithAuthEnforced(Channel ch)
    {
        boolean is = false;
        SslHandshakeInfo sslHandshakeInfo = ch.attr(SslHandshakeInfoHandler.ATTR_SSL_INFO).get();
        if (sslHandshakeInfo != null) {
            if (sslHandshakeInfo.getClientAuthRequirement() == ClientAuth.REQUIRE) {
                is = true;
            }
        }
        return is;
    }

    @VisibleForTesting
    void stripXFFHeaders(HttpRequest req)
    {
        HttpHeaders headers = req.headers();
        for (AsciiString headerName : HEADERS_TO_STRIP) {
            headers.remove(headerName);
        }
    }

    @VisibleForTesting
    void checkBlacklist(HttpRequest req, List<String> blacklist) {
        // blacklist headers from
        if (blacklist.stream().anyMatch(h -> h.equalsIgnoreCase(req.headers().get(HttpHeaderNames.HOST)))) {
            stripXFFHeaders(req);
        }
    }
}
