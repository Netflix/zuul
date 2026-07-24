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
package com.netflix.zuul.util;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.netflix.zuul.message.Headers;
import com.netflix.zuul.message.ZuulMessage;
import com.netflix.zuul.message.http.HttpHeaderNames;
import com.netflix.zuul.message.http.HttpRequestInfo;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http2.Http2StreamChannel;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Objects;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * User: Mike Smith
 * Date: 4/28/15
 * Time: 11:05 PM
 */
@NullMarked
public class HttpUtils {
    private static final Logger LOG = LoggerFactory.getLogger(HttpUtils.class);
    private static final char[] MALICIOUS_HEADER_CHARS = {'\r', '\n'};

    /**
     * Get the IP address of client making the request.
     *
     * Uses the "x-forwarded-for" HTTP header if available, otherwise uses the remote
     * IP of requester.
     *
     * @param request <code>HttpRequestMessage</code>
     * @return <code>String</code> IP address
     */
    @Nullable
    public static String getClientIP(HttpRequestInfo request) {
        String xForwardedFor = request.getHeaders().getFirst(HttpHeaderNames.X_FORWARDED_FOR);
        String clientIP;
        if (xForwardedFor == null) {
            clientIP = request.getClientIp();
        } else {
            clientIP = extractClientIpFromXForwardedFor(xForwardedFor);
        }
        return clientIP;
    }

    /**
     * Extract the client IP address from an x-forwarded-for header. Returns null if there is no x-forwarded-for header
     *
     * @param xForwardedFor a <code>String</code> value
     * @return a <code>String</code> value
     */
    @Nullable
    public static String extractClientIpFromXForwardedFor(@Nullable String xForwardedFor) {
        if (xForwardedFor == null) {
            return null;
        }
        xForwardedFor = xForwardedFor.trim();
        String[] tokenized = xForwardedFor.split(",", -1);
        if (tokenized.length == 0) {
            return null;
        } else {
            return tokenized[0].trim();
        }
    }

    @VisibleForTesting
    static boolean isCompressed(String contentEncoding) {
        return contentEncoding.contains(HttpHeaderValues.GZIP.toString())
                || contentEncoding.contains(HttpHeaderValues.DEFLATE.toString())
                || contentEncoding.contains(HttpHeaderValues.BR.toString())
                || contentEncoding.contains(HttpHeaderValues.COMPRESS.toString());
    }

    public static boolean isCompressed(Headers headers) {
        String ce = headers.getFirst(HttpHeaderNames.CONTENT_ENCODING);
        return ce != null && isCompressed(ce);
    }

    public static boolean acceptsGzip(Headers headers) {
        String ae = headers.getFirst(HttpHeaderNames.ACCEPT_ENCODING);
        return ae != null && ae.contains(HttpHeaderValues.GZIP.toString());
    }

    /**
     * Ensure decoded new lines are not propagated in headers, in order to prevent XSS
     *
     * @param input - decoded header string
     * @return - clean header string
     */
    @Nullable
    public static String stripMaliciousHeaderChars(@Nullable String input) {
        if (input == null) {
            return null;
        }
        // TODO(carl-mastrangelo): implement this more efficiently.
        for (char c : MALICIOUS_HEADER_CHARS) {
            if (input.indexOf(c) != -1) {
                input = input.replace(Character.toString(c), "");
            }
        }
        return input;
    }

    public static boolean hasNonZeroContentLengthHeader(ZuulMessage msg) {
        Integer contentLengthVal = getContentLengthIfPresent(msg);
        return (contentLengthVal != null) && (contentLengthVal > 0);
    }

    @Nullable
    public static Integer getContentLengthIfPresent(ZuulMessage msg) {
        String contentLengthValue =
                msg.getHeaders().getFirst(com.netflix.zuul.message.http.HttpHeaderNames.CONTENT_LENGTH);
        if (!Strings.isNullOrEmpty(contentLengthValue)) {
            try {
                return Integer.valueOf(contentLengthValue);
            } catch (NumberFormatException e) {
                LOG.info("Invalid Content-Length header value on request. " + "value = {}", contentLengthValue, e);
            }
        }
        return null;
    }

    @Nullable
    public static Integer getBodySizeIfKnown(ZuulMessage msg) {
        Integer bodySize = getContentLengthIfPresent(msg);
        if (bodySize != null) {
            return bodySize;
        }
        if (msg.hasCompleteBody()) {
            return msg.getBodyLength();
        }
        return null;
    }

    public static boolean hasChunkedTransferEncodingHeader(ZuulMessage msg) {
        boolean isChunked = false;
        String teValue = msg.getHeaders().getFirst(com.netflix.zuul.message.http.HttpHeaderNames.TRANSFER_ENCODING);
        if (!Strings.isNullOrEmpty(teValue)) {
            isChunked = teValue.toLowerCase(Locale.ROOT).equals("chunked");
        }
        return isChunked;
    }

    /**
     * If http/1 then will always want to just use ChannelHandlerContext.channel(), but for http/2
     * will want the parent channel (as the child channel is different for each h2 stream).
     */
    public static Channel getMainChannel(ChannelHandlerContext ctx) {
        return getMainChannel(ctx.channel());
    }

    public static Channel getMainChannel(Channel channel) {
        if (channel instanceof Http2StreamChannel) {
            return channel.parent();
        }
        return channel;
    }

    /**
     * Normalizes an origin-form request-target into a routable path, collapsing {@code .} and
     * {@code ..} segments (and their {@code %2e} encodings) and clamping traversal back to root.
     * Encoded slashes ({@code %2f}) are left intact, so the result is not fully decoded. Throws
     * {@link URISyntaxException} for non-origin-form or malformed targets.
     */
    public static String parsePath(String uri) throws URISyntaxException {
        Objects.requireNonNull(uri);
        if (!uri.startsWith("/")) {
            throw new URISyntaxException(uri, "path does not start with leading slash");
        }

        int queryIndex = uri.indexOf('?');
        if (queryIndex > -1) {
            uri = uri.substring(0, queryIndex);
        }

        // Decode %2e before parsing so URI.normalize() can collapse encoded ".."/"." segments.
        String prepared = uri.replace("%2e", ".").replace("%2E", ".");
        String normalized = new URI(prepared).normalize().getRawPath();
        while (normalized.equals("/..") || normalized.startsWith("/../")) {
            normalized = normalized.substring(3);
        }
        return normalized.isEmpty() ? "/" : normalized;
    }
}
