/**
 * Copyright 2023 Netflix, Inc.
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
package com.netflix.zuul.netty.server.http2;

import com.netflix.zuul.netty.SpectatorUtils;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http2.DefaultHttp2GoAwayFrame;
import io.netty.handler.codec.http2.Http2Exception;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles exceptions due to malformed http/2 requests by sending a go-away and closing the connection
 *
 * See {@link com.netflix.netty.common.channel.config.CommonChannelConfigKeys#http2CloseOnProtocolErrors}
 * @author Justin Guerra
 * @since 11/14/23
 */
public class Http2ProtocolErrorHandler extends ChannelInboundHandlerAdapter {

    private static final Logger LOG = LoggerFactory.getLogger(Http2ProtocolErrorHandler.class);

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        if(cause instanceof Http2Exception http2Exception && http2Exception.shutdownHint() == Http2Exception.ShutdownHint.HARD_SHUTDOWN) {
            LOG.debug("Http/2 protocol error. Closing connection", cause);
            SpectatorUtils.newCounter("server.connection.http2.protocol.exception", http2Exception.getClass().getSimpleName()).increment();
            ctx.writeAndFlush(new DefaultHttp2GoAwayFrame(http2Exception.error()))
                    .addListener(ChannelFutureListener.CLOSE);
        } else {
            ctx.fireExceptionCaught(cause);
        }
    }
}
