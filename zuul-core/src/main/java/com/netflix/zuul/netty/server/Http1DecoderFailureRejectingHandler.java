/*
 * Copyright 2026 Netflix, Inc.
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

package com.netflix.zuul.netty.server;

import com.netflix.netty.common.throttle.RejectionUtils;
import com.netflix.zuul.stats.status.ZuulStatusCategory;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.util.ReferenceCountUtil;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;

/**
 * Rejects HTTP/1.1 requests that the HttpServerCodec marked as a decoder failure - for example a malformed
 * header name surfaced when {@code server.http.request.headers.validation.enabled=true}. Force-closes the
 * connection with no response.
 */
@NullMarked
@Slf4j
public final class Http1DecoderFailureRejectingHandler extends ChannelInboundHandlerAdapter {

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof HttpRequest req && req.decoderResult().isFailure()) {
            log.debug(
                    "Rejecting HTTP/1.1 request with decoder failure: {}, uri={}",
                    req.decoderResult().cause(),
                    req.uri());
            RejectionUtils.rejectByClosingConnection(
                    ctx, ZuulStatusCategory.FAILURE_CLIENT_BAD_REQUEST, "http1_decoder_failure", req, null);
            ReferenceCountUtil.safeRelease(msg);
            return;
        }
        super.channelRead(ctx, msg);
    }
}
