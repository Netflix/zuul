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
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http2.Http2Exception;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Logs and tracks connection errors. The actual
 * sending of the go-away and closing the connection is handled by netty in {@link io.netty.handler.codec.http2.Http2ConnectionHandler}
 * onConnectionError
 *
 * See also, {@link com.netflix.netty.common.channel.config.CommonChannelConfigKeys#http2CatchConnectionErrors}
 * @author Justin Guerra
 * @since 11/14/23
 */
public class Http2ConnectionErrorHandler extends ChannelInboundHandlerAdapter {

    private static final Logger LOG = LoggerFactory.getLogger(Http2ConnectionErrorHandler.class);

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        if (cause instanceof Http2Exception http2Exception) {
            LOG.debug("Received Http/2 connection error", cause);
            SpectatorUtils.newCounter(
                            "server.connection.http2.connection.exception",
                            http2Exception.error().name())
                    .increment();
        } else {
            ctx.fireExceptionCaught(cause);
        }
    }
}
