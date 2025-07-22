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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import com.netflix.zuul.context.SessionContext;
import com.netflix.zuul.exception.OutboundErrorType;
import com.netflix.zuul.exception.OutboundException;
import com.netflix.zuul.netty.connectionpool.OriginConnectException;
import com.netflix.zuul.niws.RequestAttempts;
import com.netflix.zuul.origins.OriginConcurrencyExceededException;
import com.netflix.zuul.origins.OriginName;
import io.netty.handler.codec.http2.Http2Error;
import io.netty.handler.codec.http2.Http2Exception;
import io.netty.handler.timeout.ReadTimeoutException;
import java.nio.channels.ClosedChannelException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class NettyRequestAttemptFactoryTest {
    private NettyRequestAttemptFactory factory;

    @BeforeEach
    public void setup() {
        factory = new NettyRequestAttemptFactory();
    }

    @Test
    public void mapNettyToOutboundExceptionMapsToOutboundException() {
        Exception e = new OutboundException(OutboundErrorType.OTHER,
                new RequestAttempts());
        OutboundException mapException = factory.mapNettyToOutboundException(e, new SessionContext());

        assertEquals(e, mapException);
        // check that the type is OutboundException
        assertNotNull(mapException);
    }

    @Test
    public void mapNettyToOutboundExceptionMapsToReadTimeoutException() {
        OutboundException mapException = factory.mapNettyToOutboundException(new ReadTimeoutException(), new SessionContext());

        // check that the type is OutboundException
        assertNotNull(mapException);
        assertEquals(OutboundErrorType.READ_TIMEOUT, mapException.getOutboundErrorType());
    }

    @Test
    public void mapNettyToOutboundExceptionMapsToOriginConcurrencyExceededException() {
        OutboundException mapException = factory.mapNettyToOutboundException(new OriginConcurrencyExceededException(OriginName.fromVipAndApp("vip", "app")), new SessionContext());

        // check that the type is OutboundException
        assertNotNull(mapException);
        assertEquals(OutboundErrorType.ORIGIN_CONCURRENCY_EXCEEDED, mapException.getOutboundErrorType());
    }

    @Test
    public void mapNettyToOutboundExceptionMapsToOriginConnectException() {
        OutboundException mapException = factory.mapNettyToOutboundException(new OriginConnectException("error",
                OutboundErrorType.OTHER), new SessionContext());

        // check that the type is OutboundException
        assertNotNull(mapException);
        assertEquals(OutboundErrorType.OTHER, mapException.getOutboundErrorType());
    }

    @Test
    public void mapNettyToOutboundExceptionMapsToClosedChannelException() {
        OutboundException mapException = factory.mapNettyToOutboundException(new ClosedChannelException(), new SessionContext());

        // check that the type is OutboundException
        assertNotNull(mapException);
        assertEquals(OutboundErrorType.RESET_CONNECTION, mapException.getOutboundErrorType());
    }

    @Test
    public void mapNettyToOutboundExceptionMapsToHeaderListSizeException() {
        OutboundException mapException = factory.mapNettyToOutboundException(Http2Exception.headerListSizeError(1, Http2Error.CONNECT_ERROR, false, ""), new SessionContext());

        // check that the type is OutboundException
        assertNotNull(mapException);
        assertEquals(OutboundErrorType.HEADER_FIELDS_TOO_LARGE, mapException.getOutboundErrorType());
    }

    @Test
    public void mapNettyToOutboundExceptionMapsToIllegalStateException() {
        OutboundException mapException = factory.mapNettyToOutboundException(new Exception(new IllegalStateException(new Throwable("No available server"))), new SessionContext());

        // check that the type is OutboundException
        assertNotNull(mapException);
        assertEquals(OutboundErrorType.NO_AVAILABLE_SERVERS, mapException.getOutboundErrorType());
    }

    @Test
    public void mapNettyToOutboundExceptionMapsToOtherExceptionType() {
        OutboundException mapException = factory.mapNettyToOutboundException(new Exception(), new SessionContext());

        // check that the type is OutboundException
        assertNotNull(mapException);
        assertEquals(OutboundErrorType.OTHER, mapException.getOutboundErrorType());
    }
}
