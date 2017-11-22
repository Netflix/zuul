/*
 * Copyright 2018 Netflix, Inc.
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

package com.netflix.zuul.netty.server.http2;

import com.netflix.zuul.netty.server.ssl.SslHandshakeInfoHandler;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http2.Http2ServerDowngrader;
import io.netty.util.AttributeKey;
import com.netflix.netty.common.SourceAddressChannelHandler;
import com.netflix.netty.common.proxyprotocol.ElbProxyProtocolChannelHandler;

import java.util.function.Consumer;

import static com.netflix.zuul.netty.server.http2.Http2OrHttpHandler.PROTOCOL_NAME;

/**
 * TODO - can this be done when we create the Http2StreamChannelBootstrap instead now?
 */
@ChannelHandler.Sharable
public class Http2StreamInitializer extends ChannelInboundHandlerAdapter
{
    private static final Http2StreamHeaderCleaner http2StreamHeaderCleaner = new Http2StreamHeaderCleaner();
    private static final Http2ResetFrameHandler http2ResetFrameHandler = new Http2ResetFrameHandler();

    private final Channel parent;
    private final Consumer<ChannelPipeline> addHttpHandlerFn;

    public Http2StreamInitializer(Channel parent, Consumer<ChannelPipeline> addHttpHandlerFn)
    {
        this.parent = parent;
        this.addHttpHandlerFn = addHttpHandlerFn;
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception
    {
        copyAttrsFromParentChannel(this.parent, ctx.channel());

        addHttp2StreamSpecificHandlers(ctx.pipeline());
        addHttpHandlerFn.accept(ctx.pipeline());

        ctx.pipeline().remove(this);
    }

    protected void addHttp2StreamSpecificHandlers(ChannelPipeline pipeline)
    {
        pipeline.addLast(http2ResetFrameHandler);
        pipeline.addLast("h2_downgrader", new Http2ServerDowngrader(false));
        pipeline.addLast(http2StreamHeaderCleaner);
    }

    protected void copyAttrsFromParentChannel(Channel parent, Channel child)
    {
        AttributeKey[] attributesToCopy = {
                SourceAddressChannelHandler.ATTR_LOCAL_ADDRESS,
                SourceAddressChannelHandler.ATTR_LOCAL_INET_ADDR,
                SourceAddressChannelHandler.ATTR_LOCAL_PORT,
                SourceAddressChannelHandler.ATTR_SOURCE_ADDRESS,
                SourceAddressChannelHandler.ATTR_SOURCE_INET_ADDR,
                SourceAddressChannelHandler.ATTR_SOURCE_PORT,

                PROTOCOL_NAME,
                SslHandshakeInfoHandler.ATTR_SSL_INFO,
                ElbProxyProtocolChannelHandler.ATTR_HAPROXY_MESSAGE,
        };

        for (AttributeKey key : attributesToCopy) {
            copyAttrFromParentChannel(parent, child, key);
        }
    }

    protected void copyAttrFromParentChannel(Channel parent, Channel child, AttributeKey key)
    {
        child.attr(key).set(parent.attr(key).get());
    }
}
