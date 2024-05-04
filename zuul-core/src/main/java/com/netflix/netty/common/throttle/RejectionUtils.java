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

package com.netflix.netty.common.throttle;

import com.netflix.netty.common.ConnectionCloseChannelAttributes;
import com.netflix.netty.common.proxyprotocol.HAProxyMessageChannelHandler;
import com.netflix.zuul.passport.CurrentPassport;
import com.netflix.zuul.passport.PassportState;
import com.netflix.zuul.stats.status.StatusCategory;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.haproxy.HAProxyProtocolVersion;
import io.netty.handler.codec.http.*;
import io.netty.util.ReferenceCountUtil;

import javax.annotation.Nullable;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * A collection of rejection related utilities useful for failing requests. These are tightly coupled with the channel
 * pipeline, but can be called from different handlers.
 */
public final class RejectionUtils {

    // TODO(carl-mastrangelo): add tests for this.

    public static final HttpResponseStatus REJECT_CLOSING_STATUS = new HttpResponseStatus(999, "Closing(Rejection)");

    /**
     * Closes the connection without sending a response, and fires a {@link RequestRejectedEvent} back up the pipeline.
     *
     * @param throttlingContextData The ThrottlingContextData object
     */
    public static void rejectByClosingConnection(
            ThrottlingContextData throttlingContextData) {

        // Notify other handlers before closing the conn.
        notifyHandlers(throttlingContextData.getCtx(), throttlingContextData.getNfStatus(), REJECT_CLOSING_STATUS, throttlingContextData.getReason(), throttlingContextData.getRequest());

        if (throttlingContextData.getInjectedLatencyMillis() != null && throttlingContextData.getInjectedLatencyMillis() > 0) {
            // Delay closing the connection for configured time.
            throttlingContextData.getCtx().executor()
                    .schedule(
                            () -> {
                                CurrentPassport.fromChannel(throttlingContextData.getCtx().channel()).add(PassportState.SERVER_CH_REJECTING);
                                throttlingContextData.getCtx().close();
                            },
                            throttlingContextData.getInjectedLatencyMillis(),
                            TimeUnit.MILLISECONDS);
        } else {
            // Close the connection immediately.
            CurrentPassport.fromChannel(throttlingContextData.getCtx().channel()).add(PassportState.SERVER_CH_REJECTING);
            throttlingContextData.getCtx().close();
        }
    }

    /**
     * Sends a rejection response back to the client, and fires a {@link RequestRejectedEvent} back up the pipeline.
     *
     * @param rejectedCode          the HTTP code to send back to the client.
     * @param rejectedBody          the HTTP body to be sent back.  It is assumed to be of type text/plain.
     * @param rejectionHeaders      additional HTTP headers to add to the rejection response
     * @param throttlingContextData The ThrottlingContextData object
     */
    public static void sendRejectionResponse(
            HttpResponseStatus rejectedCode,
            String rejectedBody,
            Map<String, String> rejectionHeaders, ThrottlingContextData throttlingContextData) {
        boolean shouldClose = closeConnectionAfterReject(throttlingContextData.getCtx().channel());
        // Write out a rejection response message.
        FullHttpResponse response = createRejectionResponse(rejectedCode, rejectedBody, shouldClose, rejectionHeaders);

        if (throttlingContextData.getInjectedLatencyMillis() != null && throttlingContextData.getInjectedLatencyMillis() > 0) {
            // Delay writing the response for configured time.
            throttlingContextData.getCtx().executor()
                    .schedule(
                            () -> {
                                CurrentPassport.fromChannel(throttlingContextData.getCtx().channel()).add(PassportState.IN_REQ_REJECTED);
                                throttlingContextData.getCtx().writeAndFlush(response);
                            },
                            throttlingContextData.getInjectedLatencyMillis(),
                            TimeUnit.MILLISECONDS);
        } else {
            // Write the response immediately.
            CurrentPassport.fromChannel(throttlingContextData.getCtx().channel()).add(PassportState.IN_REQ_REJECTED);
            throttlingContextData.getCtx().writeAndFlush(response);
        }

        // Notify other handlers that we've rejected this request.
        notifyHandlers(throttlingContextData.getCtx(), throttlingContextData.getNfStatus(), rejectedCode, throttlingContextData.getReason(), throttlingContextData.getRequest());
    }

    /**
     * Marks the given channel for being closed after the next response.
     *
     * @param ctx the channel handler processing the request
     */
    public static void allowThenClose(ChannelHandlerContext ctx) {
        // Just flag this channel to be closed after response complete.
        ctx.channel()
                .attr(ConnectionCloseChannelAttributes.CLOSE_AFTER_RESPONSE)
                .set(ctx.newPromise());

        // And allow this request through without rejecting.
    }

    /**
     * Throttle either by sending rejection response message, or by closing the connection now, or just drop the
     * message. Only call this if ThrottleResult.shouldThrottle() returned {@code true}.
     *
     * @param ctx                   the channel handler processing the request
     * @param msg                   the request that is being rejected.
     * @param rejectionType         the type of rejection
     * @param nfStatus              the status to use for metric reporting
     * @param reason                the reason for rejecting the request.  This is not sent back to the client.
     * @param injectedLatencyMillis optional parameter to delay sending a response. The reject notification is still
     *                              sent up the pipeline.
     * @param rejectedCode          the HTTP code to send back to the client.
     * @param rejectedBody          the HTTP body to be sent back.  It is assumed to be of type text/plain.
     * @param rejectionHeaders      additional HTTP headers to add to the rejection response
     */
    public static void handleRejection(
            ChannelHandlerContext ctx,
            Object msg,
            RejectionType rejectionType,
            StatusCategory nfStatus,
            String reason,
            @Nullable Integer injectedLatencyMillis,
            HttpResponseStatus rejectedCode,
            String rejectedBody,
            Map<String, String> rejectionHeaders)
            throws Exception {

        boolean shouldDropMessage = false;
        if (rejectionType == RejectionType.REJECT || rejectionType == RejectionType.CLOSE) {
            shouldDropMessage = true;
        }

        boolean shouldRejectNow = false;
        if (rejectionType == RejectionType.REJECT && msg instanceof LastHttpContent) {
            shouldRejectNow = true;
        } else if (rejectionType == RejectionType.CLOSE && msg instanceof HttpRequest) {
            shouldRejectNow = true;
        } else if (rejectionType == RejectionType.ALLOW_THEN_CLOSE && msg instanceof HttpRequest) {
            shouldRejectNow = true;
        }

        if (shouldRejectNow) {
            // Send a rejection response.
            HttpRequest request = msg instanceof HttpRequest ? (HttpRequest) msg : null;
            reject(
                    rejectionType,
                    rejectedCode,
                    rejectedBody,
                    rejectionHeaders, new ThrottlingContextData(
                            ctx,
                            nfStatus,
                            reason,
                            request,
                            injectedLatencyMillis));
        }

        if (shouldDropMessage) {
            ReferenceCountUtil.safeRelease(msg);
        } else {
            ctx.fireChannelRead(msg);
        }
    }

    /**
     * Switches on the rejection type to decide how to reject the request and or close the conn.
     *
     * @param rejectionType         the type of rejection
     * @param rejectedCode          the HTTP code to send back to the client.
     * @param rejectedBody          the HTTP body to be sent back.  It is assumed to be of type text/plain.
     * @param throttlingContextData The ThrottlingContextData object
     */
    public static void reject(
            RejectionType rejectionType,
            HttpResponseStatus rejectedCode,
            String rejectedBody, ThrottlingContextData throttlingContextData) {
        reject(
                rejectionType,
                rejectedCode,
                rejectedBody,
                Collections.emptyMap(), new ThrottlingContextData(
                        throttlingContextData.getCtx(),
                        throttlingContextData.getNfStatus(),
                        throttlingContextData.getReason(),
                        throttlingContextData.getRequest(),
                        throttlingContextData.getInjectedLatencyMillis()));
    }

    /**
     * Switches on the rejection type to decide how to reject the request and or close the conn.
     *
     * @param rejectionType         the type of rejection
     * @param rejectedCode          the HTTP code to send back to the client.
     * @param rejectedBody          the HTTP body to be sent back.  It is assumed to be of type text/plain.
     * @param rejectionHeaders      additional HTTP headers to add to the rejection response
     * @param throttlingContextData The ThrottlingContextData object
     */
    public static void reject(
            RejectionType rejectionType,
            HttpResponseStatus rejectedCode,
            String rejectedBody,
            Map<String, String> rejectionHeaders, ThrottlingContextData throttlingContextData) {
        switch (rejectionType) {
            case REJECT:
                sendRejectionResponse(
                        rejectedCode,
                        rejectedBody,
                        rejectionHeaders, new ThrottlingContextData(
                                throttlingContextData.getCtx(),
                                throttlingContextData.getNfStatus(),
                                throttlingContextData.getReason(),
                                throttlingContextData.getRequest(),
                                throttlingContextData.getInjectedLatencyMillis()));
                return;
            case CLOSE:
                rejectByClosingConnection(new ThrottlingContextData(throttlingContextData.getCtx(), throttlingContextData.getNfStatus(), throttlingContextData.getReason(), throttlingContextData.getRequest(), throttlingContextData.getInjectedLatencyMillis()));
                return;
            case ALLOW_THEN_CLOSE:
                allowThenClose(throttlingContextData.getCtx());
                return;
        }
        throw new AssertionError("Bad rejection type: " + rejectionType);
    }

    private static void notifyHandlers(
            ChannelHandlerContext ctx,
            StatusCategory nfStatus,
            HttpResponseStatus status,
            String reason,
            HttpRequest request) {
        RequestRejectedEvent event = new RequestRejectedEvent(request, nfStatus, status, reason);
        // Send this from the beginning of the pipeline, as it may be sent from the ClientRequestReceiver.
        ctx.pipeline().fireUserEventTriggered(event);
    }

    private static boolean closeConnectionAfterReject(Channel channel) {
        if (channel.hasAttr(HAProxyMessageChannelHandler.ATTR_HAPROXY_VERSION)) {
            return HAProxyProtocolVersion.V2
                    == channel.attr(HAProxyMessageChannelHandler.ATTR_HAPROXY_VERSION)
                    .get();
        } else {
            return false;
        }
    }

    private static FullHttpResponse createRejectionResponse(
            HttpResponseStatus status,
            String plaintextMessage,
            boolean closeConnection,
            Map<String, String> rejectionHeaders) {
        ByteBuf body = Unpooled.wrappedBuffer(plaintextMessage.getBytes(StandardCharsets.UTF_8));
        int length = body.readableBytes();
        DefaultHttpHeaders headers = new DefaultHttpHeaders();
        headers.set(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=utf-8");
        headers.set(HttpHeaderNames.CONTENT_LENGTH, length);
        if (closeConnection) {
            headers.set(HttpHeaderNames.CONNECTION, "close");
        }
        rejectionHeaders.forEach(headers::add);

        return new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status, body, headers, EmptyHttpHeaders.INSTANCE);
    }

    private RejectionUtils() {
    }
}
