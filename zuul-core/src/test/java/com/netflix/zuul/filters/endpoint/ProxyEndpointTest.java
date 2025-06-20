/*
 * Copyright 2022 Netflix, Inc.
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

package com.netflix.zuul.filters.endpoint;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.spectator.api.Spectator;
import com.netflix.zuul.context.CommonContextKeys;
import com.netflix.zuul.context.SessionContext;
import com.netflix.zuul.discovery.DiscoveryResult;
import com.netflix.zuul.exception.OutboundErrorType;
import com.netflix.zuul.message.http.HttpRequestMessage;
import com.netflix.zuul.message.http.HttpRequestMessageImpl;
import com.netflix.zuul.message.http.HttpResponseMessage;
import com.netflix.zuul.netty.NettyRequestAttemptFactory;
import com.netflix.zuul.netty.connectionpool.DefaultOriginChannelInitializer;
import com.netflix.zuul.netty.connectionpool.PooledConnection;
import com.netflix.zuul.netty.server.MethodBinding;
import com.netflix.zuul.netty.timeouts.OriginTimeoutManager;
import com.netflix.zuul.niws.RequestAttempt;
import com.netflix.zuul.niws.RequestAttempts;
import com.netflix.zuul.origins.BasicNettyOriginManager;
import com.netflix.zuul.origins.NettyOrigin;
import com.netflix.zuul.passport.CurrentPassport;
import com.netflix.zuul.passport.PassportItem;
import com.netflix.zuul.passport.PassportState;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.local.LocalAddress;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.DefaultLastHttpContent;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProxyEndpointTest {

    @Mock
    private ChannelHandlerContext chc;

    @Mock
    private NettyOrigin nettyOrigin;

    @Mock
    private OriginTimeoutManager timeoutManager;

    @Mock
    private NettyRequestAttemptFactory attemptFactory;

    private ProxyEndpoint proxyEndpoint;
    private SessionContext context;
    private HttpRequestMessage request;
    private HttpResponse response;
    private CurrentPassport passport;
    private EmbeddedChannel channel;

    @BeforeEach
    void setup() {
        channel = new EmbeddedChannel();
        doReturn(channel).when(chc).channel();

        context = new SessionContext();
        request = new HttpRequestMessageImpl(
                context,
                "HTTP/1.1",
                "POST",
                "/some/where",
                null,
                null,
                "192.168.0.2",
                "https",
                7002,
                "localhost",
                new LocalAddress("777"),
                false);
        request.storeInboundRequest();

        request.setBody("Hello There".getBytes(UTF_8));
        BasicNettyOriginManager originManager = new BasicNettyOriginManager(Spectator.globalRegistry());

        context.set(CommonContextKeys.ORIGIN_MANAGER, originManager);
        context.setRouteVIP("some-vip");
        passport = CurrentPassport.create();
        context.put(CommonContextKeys.PASSPORT, passport);
        context.put(CommonContextKeys.REQUEST_ATTEMPTS, new RequestAttempts());

        Promise<PooledConnection> promise = channel.eventLoop().newPromise();
        doReturn(promise).when(nettyOrigin).connectToOrigin(any(), any(), anyInt(), any(), any(), any());

        proxyEndpoint = spy(new ProxyEndpoint(request, chc, null, MethodBinding.NO_OP_BINDING, attemptFactory) {
            @Override
            public NettyOrigin getOrigin(HttpRequestMessage request) {
                return nettyOrigin;
            }

            @Override
            protected OriginTimeoutManager getTimeoutManager(NettyOrigin origin) {
                return timeoutManager;
            }
        });

        doNothing().when(proxyEndpoint).operationComplete(any());
        doNothing().when(proxyEndpoint).invokeNext((HttpResponseMessage) any());
    }

    @Test
    void testRecordProxyRequestEndIsCalledOnce() {
        proxyEndpoint.apply(request);
        proxyEndpoint.finish(false);

        verify(nettyOrigin, times(1)).recordProxyRequestEnd();
    }

    @Test
    void testRetryWillResetBodyReader() {

        assertEquals("Hello There", new String(request.getBody(), UTF_8));

        // move the body readerIndex to the end to mimic nettys behavior after writing to the origin channel
        request.getBodyContents()
                .forEach((b) -> b.content().readerIndex(b.content().capacity()));

        createResponse(HttpResponseStatus.SERVICE_UNAVAILABLE);

        DiscoveryResult discoveryResult = createDiscoveryResult();

        // when retrying a response, the request body reader should have it's indexes reset
        proxyEndpoint.handleOriginNonSuccessResponse(response, discoveryResult);
        assertEquals("Hello There", new String(request.getBody(), UTF_8));
    }

    @Test
    void retryWhenNoAdjustment() {
        createResponse(HttpResponseStatus.SERVICE_UNAVAILABLE);

        proxyEndpoint.handleOriginNonSuccessResponse(response, createDiscoveryResult());
        verify(nettyOrigin).adjustRetryPolicyIfNeeded(eq(request));
        verify(nettyOrigin).originRetryPolicyAdjustmentIfNeeded(request, response);
        verify(nettyOrigin).connectToOrigin(any(), any(), anyInt(), any(), any(), any());
    }

    @Test
    void testRetryAdjustsLimit() {
        createResponse(HttpResponseStatus.SERVICE_UNAVAILABLE);
        disableRetriesOnAdjustment();

        proxyEndpoint.handleOriginNonSuccessResponse(response, createDiscoveryResult());
        validateNoRetry();
    }

    @Test
    void noRetryAdjustmentOnNonRetriableStatusCode() {
        createResponse(HttpResponseStatus.BAD_REQUEST);
        proxyEndpoint.handleOriginNonSuccessResponse(response, createDiscoveryResult());
        verify(nettyOrigin, never()).adjustRetryPolicyIfNeeded(request);
        verify(nettyOrigin, never()).originRetryPolicyAdjustmentIfNeeded(request, response);
        validateNoRetry();
    }

    @Test
    public void onErrorFromOriginNoRetryAdjustment() {
        doReturn(OutboundErrorType.RESET_CONNECTION).when(attemptFactory).mapNettyToOutboundErrorType(any());
        proxyEndpoint.errorFromOrigin(new RuntimeException());

        verify(nettyOrigin).adjustRetryPolicyIfNeeded(request);
        verify(nettyOrigin).connectToOrigin(any(), any(), anyInt(), any(), any(), any());
    }

    @Test
    void onErrorFromOriginWithRetryAdjustment() {
        doReturn(OutboundErrorType.RESET_CONNECTION).when(attemptFactory).mapNettyToOutboundErrorType(any());
        disableRetriesOnAdjustment();

        proxyEndpoint.errorFromOrigin(new RuntimeException());
        validateNoRetry();
    }

    @Test
    public void onErrorFromOriginNoRetryOnNonRetriableError() {
        doReturn(OutboundErrorType.OTHER).when(attemptFactory).mapNettyToOutboundErrorType(any());
        disableRetriesOnAdjustment();

        proxyEndpoint.errorFromOrigin(new RuntimeException());
        verify(nettyOrigin, never()).adjustRetryPolicyIfNeeded(request);
        verify(nettyOrigin, never()).originRetryPolicyAdjustmentIfNeeded(request, response);
        validateNoRetry();
    }

    @Test
    public void lastContentAfterProxyStartedIsConsideredReplayable() {
        Promise<PooledConnection> promise = channel.eventLoop().newPromise();

        PooledConnection pooledConnection = Mockito.mock(PooledConnection.class);
        promise.setSuccess(pooledConnection);

        doReturn(channel).when(pooledConnection).getChannel();
        doReturn(promise).when(nettyOrigin).connectToOrigin(any(), any(), anyInt(), any(), any(), any());

        doReturn(Mockito.mock(RequestAttempt.class)).when(nettyOrigin).newRequestAttempt(any(), any(), any(), anyInt());

        request = new HttpRequestMessageImpl(
                context,
                "HTTP/1.1",
                "POST",
                "/some/where",
                null,
                null,
                "192.168.0.2",
                "https",
                7002,
                "localhost",
                new LocalAddress("777"),
                false);
        request.storeInboundRequest();

        proxyEndpoint = spy(new ProxyEndpoint(request, chc, null, MethodBinding.NO_OP_BINDING, attemptFactory) {
            @Override
            public NettyOrigin getOrigin(HttpRequestMessage request) {
                return nettyOrigin;
            }

            @Override
            protected OriginTimeoutManager getTimeoutManager(NettyOrigin origin) {
                return timeoutManager;
            }
        });

        channel.pipeline()
                .addLast(DefaultOriginChannelInitializer.CONNECTION_POOL_HANDLER, new ChannelInboundHandlerAdapter());

        proxyEndpoint.apply(request);
        LastHttpContent lastContent = new DefaultLastHttpContent();
        assertFalse(proxyEndpoint.isRequestReplayable());
        proxyEndpoint.processContentChunk(request, lastContent);
        assertTrue(proxyEndpoint.isRequestReplayable());

        channel.releaseOutbound();
        assertEquals(1, lastContent.refCnt(), "ref count should be 1 in case a retry is needed");
        ReferenceCountUtil.safeRelease(lastContent);
    }

    private void validateNoRetry() {
        verify(nettyOrigin, never()).connectToOrigin(any(), any(), anyInt(), any(), any(), any());
        passport.getHistory().stream()
                .map(PassportItem::getState)
                .filter(s -> s == PassportState.ORIGIN_RETRY_START)
                .findAny()
                .ifPresent(s -> fail());
    }

    private void disableRetriesOnAdjustment() {
        doAnswer(invocation -> {
                    doReturn(-1).when(nettyOrigin).getMaxRetriesForRequest(context);
                    return null;
                })
                .when(nettyOrigin)
                .adjustRetryPolicyIfNeeded(request);
    }

    private static DiscoveryResult createDiscoveryResult() {
        InstanceInfo instanceInfo = InstanceInfo.Builder.newBuilder()
                .setAppName("app")
                .setHostName("localhost")
                .setPort(443)
                .build();
        return DiscoveryResult.from(instanceInfo, true);
    }

    private void createResponse(HttpResponseStatus status) {
        response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, status);
    }
}
