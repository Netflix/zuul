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

package com.netflix.zuul.netty;

import com.netflix.zuul.context.SessionContext;
import com.netflix.zuul.exception.ErrorType;
import com.netflix.zuul.exception.OutboundException;
import com.netflix.zuul.netty.connectionpool.OriginConnectException;
import com.netflix.zuul.niws.RequestAttempts;
import com.netflix.zuul.origins.OriginConcurrencyExceededException;
import io.netty.channel.unix.Errors;
import io.netty.handler.timeout.ReadTimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.channels.ClosedChannelException;

import static com.netflix.zuul.exception.OutboundErrorType.NO_AVAILABLE_SERVERS;
import static com.netflix.zuul.exception.OutboundErrorType.ORIGIN_CONCURRENCY_EXCEEDED;
import static com.netflix.zuul.exception.OutboundErrorType.OTHER;
import static com.netflix.zuul.exception.OutboundErrorType.READ_TIMEOUT;
import static com.netflix.zuul.exception.OutboundErrorType.RESET_CONNECTION;


public class NettyRequestAttemptFactory {

    private final static Logger LOG = LoggerFactory.getLogger(NettyRequestAttemptFactory.class);

    public ErrorType mapNettyToOutboundErrorType(final Throwable t) {
        if (t instanceof ReadTimeoutException) {
            return READ_TIMEOUT;
        }

        if (t instanceof OriginConcurrencyExceededException) {
            return ORIGIN_CONCURRENCY_EXCEEDED;
        }

        if (t instanceof OriginConnectException) {
            return ((OriginConnectException) t).getErrorType();
        }

        if (t instanceof OutboundException) {
            return ((OutboundException) t).getOutboundErrorType();
        }

        if (t instanceof Errors.NativeIoException && Errors.ERRNO_ECONNRESET_NEGATIVE == ((Errors.NativeIoException) t).expectedErr()) {
            // This is a "Connection reset by peer" which we see fairly often happening when Origin servers are overloaded.
            LOG.warn("ERRNO_ECONNRESET_NEGATIVE mapped to RESET_CONNECTION", t);
            return RESET_CONNECTION;
        }

        if (t instanceof ClosedChannelException) {
            return RESET_CONNECTION;
        }

        final Throwable cause = t.getCause();
        if (cause instanceof IllegalStateException && cause.getMessage().contains("server")) {
            LOG.warn("IllegalStateException mapped to NO_AVAILABLE_SERVERS", cause);
            return NO_AVAILABLE_SERVERS;
        }

        return OTHER;
    }

    public OutboundException mapNettyToOutboundException(final Throwable t, final SessionContext context) {
        if (t instanceof OutboundException) {
            return (OutboundException) t;
        }

        // Map this throwable to zuul's OutboundException.
        final ErrorType errorType = mapNettyToOutboundErrorType(t);
        final RequestAttempts attempts = RequestAttempts.getFromSessionContext(context);
        if (errorType == OTHER) {
            return new OutboundException(errorType, attempts, t);
        }
        return new OutboundException(errorType, attempts);
    }
}
