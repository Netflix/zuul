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

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.codec.http2.DefaultHttp2ResetFrame;
import io.netty.handler.codec.http2.Http2Error;
import io.netty.util.ReferenceCountUtil;
import java.util.List;

/**
 * Validates that HTTP/2 request content-length headers are consistent with the actual body.
 * This class is only suitable for use on HTTP/2 child channels.
 */
public final class Http2ContentLengthEnforcingHandler extends ChannelInboundHandlerAdapter {
    private static final long UNSET_CONTENT_LENGTH = -1;

    private long expectedContentLength = UNSET_CONTENT_LENGTH;

    private long seenContentLength;

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof HttpRequest req && !validateRequest(req)) {
            rejectAndRelease(ctx, msg);
            return;
        }

        if (msg instanceof HttpContent httpContent && !validateContent(httpContent)) {
            rejectAndRelease(ctx, msg);
            return;
        }

        if (msg instanceof LastHttpContent && !validateEndOfStream()) {
            rejectAndRelease(ctx, msg);
            return;
        }

        super.channelRead(ctx, msg);
    }

    private boolean validateRequest(HttpRequest req) {
        List<String> lengthHeaders = req.headers().getAll(HttpHeaderNames.CONTENT_LENGTH);
        if (lengthHeaders.size() > 1) {
            return false;
        }

        if (lengthHeaders.size() == 1) {
            try {
                expectedContentLength = Long.parseLong(lengthHeaders.getFirst());
            } catch (NumberFormatException e) {
                return false;
            }
            if (expectedContentLength < 0) {
                return false;
            }
        }

        return isContentLengthUnset() || !HttpUtil.isTransferEncodingChunked(req);
    }

    private boolean validateContent(HttpContent httpContent) {
        incrementSeenContent(httpContent.content().readableBytes());
        return isContentLengthUnset() || seenContentLength <= expectedContentLength;
    }

    private boolean validateEndOfStream() {
        return isContentLengthUnset() || seenContentLength == expectedContentLength;
    }

    private void rejectAndRelease(ChannelHandlerContext ctx, Object msg) {
        // TODO(carl-mastrangelo): this is not right, but meh.  Fix this to return a proper 400.
        ctx.writeAndFlush(new DefaultHttp2ResetFrame(Http2Error.PROTOCOL_ERROR));
        ReferenceCountUtil.safeRelease(msg);
    }

    private boolean isContentLengthUnset() {
        return expectedContentLength == UNSET_CONTENT_LENGTH;
    }

    private void incrementSeenContent(int length) {
        seenContentLength = Math.addExact(seenContentLength, length);
    }
}
