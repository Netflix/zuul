/*
 * Copyright 2021 Netflix, Inc.
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

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.codec.http2.DefaultHttp2ResetFrame;
import io.netty.handler.codec.http2.Http2Error;
import java.util.List;

/**
 * This class is only suitable for use on HTTP/2 child channels.
 */
public final class Http2ContentLengthEnforcingHandler extends ChannelInboundHandlerAdapter {
    private static final long UNSET_CONTENT_LENGTH = -1;

    private long expectedContentLength = UNSET_CONTENT_LENGTH;

    private long seenContentLength;

    /**
     * This checks that the content length does what it says, preventing a client from causing Zuul to misinterpret the
     * request.  Because this class is meant to work in an HTTP/2 setting, the content length and transfer encoding
     * checks are more semantics.  In particular, this checks:
     * <ul>
     *     <li>No duplicate Content length</li>
     *     <li>Content Length (if present) must always be greater than or equal to how much content has been seen</li>
     *     <li>Content Length (if present) must always be equal to how much content has been seen by the end</li>
     *     <li>Content Length cannot be present along with chunked transfer encoding.</li>
     * </ul>
     */
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof HttpRequest) {
            HttpRequest req = (HttpRequest) msg;
            List<String> lengthHeaders = req.headers().getAll(HttpHeaderNames.CONTENT_LENGTH);
            if (lengthHeaders.size() > 1) {
                ctx.writeAndFlush(new DefaultHttp2ResetFrame(Http2Error.PROTOCOL_ERROR));
                return;
            } else if (lengthHeaders.size() == 1) {
                expectedContentLength = Long.parseLong(lengthHeaders.get(0));
                if (expectedContentLength < 0) {
                    // TODO(carl-mastrangelo): this is not right, but meh.  Fix this to return a proper 400.
                    ctx.writeAndFlush(new DefaultHttp2ResetFrame(Http2Error.PROTOCOL_ERROR));
                    return;
                }
            }
            if (hasContentLength() && HttpUtil.isTransferEncodingChunked(req)) {
                // TODO(carl-mastrangelo): this is not right, but meh.  Fix this to return a proper 400.
                ctx.writeAndFlush(new DefaultHttp2ResetFrame(Http2Error.PROTOCOL_ERROR));
                return;
            }
        }
        if (msg instanceof HttpContent) {
            ByteBuf content = ((HttpContent) msg).content();
            incrementSeenContent(content.readableBytes());
            if (hasContentLength() && seenContentLength > expectedContentLength) {
                // TODO(carl-mastrangelo): this is not right, but meh.  Fix this to return a proper 400.
                ctx.writeAndFlush(new DefaultHttp2ResetFrame(Http2Error.PROTOCOL_ERROR));
                return;
            }
        }
        if (msg instanceof LastHttpContent) {
            if (hasContentLength() && seenContentLength != expectedContentLength) {
                // TODO(carl-mastrangelo): this is not right, but meh.  Fix this to return a proper 400.
                ctx.writeAndFlush(new DefaultHttp2ResetFrame(Http2Error.PROTOCOL_ERROR));
                return;
            }
        }
        super.channelRead(ctx, msg);
    }

    private boolean hasContentLength() {
        return expectedContentLength != UNSET_CONTENT_LENGTH;
    }

    private void incrementSeenContent(int length) {
        seenContentLength = Math.addExact(seenContentLength, length);
    }
}
