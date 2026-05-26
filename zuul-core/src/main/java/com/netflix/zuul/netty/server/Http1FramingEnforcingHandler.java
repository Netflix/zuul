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

import com.google.common.base.Splitter;
import com.netflix.netty.common.throttle.RejectionUtils;
import com.netflix.zuul.stats.status.ZuulStatusCategory;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.ReferenceCountUtil;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;

/**
 * Enforces HTTP/1.1 message framing rules from RFC 9112 section 6.3, rejecting requests with ambiguous framing that
 * enable request smuggling. Drops the connection with no response when Transfer-Encoding and Content-Length are both
 * present, when Content-Length appears multiple times or is not a non-negative integer, when Transfer-Encoding is set
 * without chunked as the final coding, or when Transfer-Encoding is present on a non-HTTP/1.1 request (per RFC 9112
 * section 6.1, HTTP/1.0 requests with Transfer-Encoding must be treated as faulty framing).
 * <br/>
 * Force-closing (rather than writing a 400) is deliberate: once framing is ambiguous, we cannot trust where the
 * message body ends, and any bytes left in the decoder buffer would otherwise be parsed as the next request.
 */
@NullMarked
@Slf4j
public final class Http1FramingEnforcingHandler extends ChannelInboundHandlerAdapter {

    private static final Splitter TRANSFER_ENCODING_SPLITTER =
            Splitter.on(',').trimResults().omitEmptyStrings();

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (!(msg instanceof HttpRequest req)) {
            super.channelRead(ctx, msg);
            return;
        }

        List<String> contentLengthHeaders = req.headers().getAll(HttpHeaderNames.CONTENT_LENGTH);
        List<String> transferEncodingHeaders = req.headers().getAll(HttpHeaderNames.TRANSFER_ENCODING);

        if (contentLengthHeaders.size() > 1) {
            rejectAndClose(ctx, req, msg, "multiple content-length headers");
            return;
        }

        if (!contentLengthHeaders.isEmpty() && !transferEncodingHeaders.isEmpty()) {
            rejectAndClose(ctx, req, msg, "both transfer-encoding and content-length present");
            return;
        }

        if (!transferEncodingHeaders.isEmpty() && !HttpVersion.HTTP_1_1.equals(req.protocolVersion())) {
            rejectAndClose(ctx, req, msg, "transfer-encoding on non-HTTP/1.1 request");
            return;
        }

        if (!contentLengthHeaders.isEmpty() && !isValidContentLength(contentLengthHeaders.getFirst())) {
            rejectAndClose(ctx, req, msg, "invalid content-length");
            return;
        }

        if (!transferEncodingHeaders.isEmpty() && !isChunkedFinalCoding(transferEncodingHeaders)) {
            rejectAndClose(ctx, req, msg, "transfer-encoding without chunked as final coding");
            return;
        }

        super.channelRead(ctx, msg);
    }

    private static boolean isValidContentLength(String value) {
        try {
            return Long.parseLong(value) >= 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * Returns true if the final transfer coding is `chunked`. Per RFC 9112 section 6.1, when any transfer coding
     * other than chunked is applied, chunked MUST be applied as the final coding, otherwise the message body has no
     * reliable end marker.
     */
    private static boolean isChunkedFinalCoding(List<String> transferEncodingHeaders) {
        if (transferEncodingHeaders.isEmpty()) {
            return false;
        }

        // headers are in order, so fetch the last Transfer-Encoding one and find the last listed encoding
        List<String> encodings = TRANSFER_ENCODING_SPLITTER.splitToList(transferEncodingHeaders.getLast());
        if (encodings.isEmpty()) {
            return false;
        }

        return HttpHeaderValues.CHUNKED.contentEqualsIgnoreCase(stripTransferParameters(encodings.getLast()));
    }

    private static String stripTransferParameters(String coding) {
        int paramStart = coding.indexOf(';');
        return paramStart == -1 ? coding : coding.substring(0, paramStart).trim();
    }

    private static void rejectAndClose(ChannelHandlerContext ctx, HttpRequest req, Object msg, String detail) {
        log.debug("Rejecting HTTP/1.1 request with ambiguous framing: {}, uri={}", detail, req.uri());

        RejectionUtils.rejectByClosingConnection(
                ctx, ZuulStatusCategory.FAILURE_CLIENT_BAD_REQUEST, "http1_framing_violation", req, null);
        ReferenceCountUtil.safeRelease(msg);
    }
}
