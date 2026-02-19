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

package com.netflix.zuul.netty.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.netflix.spectator.api.NoopRegistry;
import com.netflix.spectator.api.Registry;
import com.netflix.zuul.Filter;
import com.netflix.zuul.FilterCategory;
import com.netflix.zuul.FilterLoader;
import com.netflix.zuul.FilterUsageNotifier;
import com.netflix.zuul.context.CommonContextKeys;
import com.netflix.zuul.context.SessionContext;
import com.netflix.zuul.filters.Endpoint;
import com.netflix.zuul.filters.FilterType;
import com.netflix.zuul.filters.ZuulFilter;
import com.netflix.zuul.message.Headers;
import com.netflix.zuul.message.ZuulMessage;
import com.netflix.zuul.message.http.HttpQueryParams;
import com.netflix.zuul.message.http.HttpRequestMessage;
import com.netflix.zuul.message.http.HttpRequestMessageImpl;
import com.netflix.zuul.message.http.HttpResponseMessage;
import com.netflix.zuul.message.http.HttpResponseMessageImpl;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.concurrent.ImmediateEventExecutor;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ZuulEndPointRunnerTest {
    private static final String BASIC_ENDPOINT = "basicEndpoint";
    private ZuulEndPointRunner endpointRunner;
    private FilterUsageNotifier usageNotifier;
    private FilterLoader filterLoader;
    private FilterRunner filterRunner;
    private Registry registry;
    private HttpRequestMessageImpl request;

    @BeforeEach
    void beforeEachTest() {
        usageNotifier = mock(FilterUsageNotifier.class);

        filterLoader = mock(FilterLoader.class);
        when(filterLoader.getFilterByNameAndType(ZuulEndPointRunner.DEFAULT_ERROR_ENDPOINT.get(), FilterType.ENDPOINT))
                .thenReturn(new ErrorEndpoint());
        when(filterLoader.getFilterByNameAndType(BASIC_ENDPOINT, FilterType.ENDPOINT))
                .thenReturn(new BasicEndpoint());

        filterRunner = mock(FilterRunner.class);
        registry = new NoopRegistry();
        endpointRunner = new ZuulEndPointRunner(
                usageNotifier, filterLoader, filterRunner, new FilterConstraints(List.of()), registry);

        SessionContext context = new SessionContext();
        Headers headers = new Headers();
        ChannelHandlerContext chc = mock(ChannelHandlerContext.class);
        when(chc.executor()).thenReturn(ImmediateEventExecutor.INSTANCE);
        context.put(CommonContextKeys.NETTY_SERVER_CHANNEL_HANDLER_CONTEXT, chc);
        request = new HttpRequestMessageImpl(
                context,
                "http",
                "GET",
                "/foo/bar",
                new HttpQueryParams(),
                headers,
                "127.0.0.1",
                "http",
                8080,
                "server123");
        request.storeInboundRequest();
    }

    @Test
    void nonErrorEndpoint() {
        request.getContext().setShouldSendErrorResponse(false);
        request.getContext().setEndpoint(BASIC_ENDPOINT);
        assertThat(request.getContext().get(CommonContextKeys.ZUUL_ENDPOINT)).isNull();
        endpointRunner.filter(request);
        ZuulFilter<HttpRequestMessage, HttpResponseMessage> filter =
                request.getContext().get(CommonContextKeys.ZUUL_ENDPOINT);
        assertThat(filter instanceof BasicEndpoint).isTrue();

        ArgumentCaptor<HttpResponseMessage> captor = ArgumentCaptor.forClass(HttpResponseMessage.class);
        verify(filterRunner, times(1)).filter(captor.capture());
        HttpResponseMessage capturedResponseMessage = captor.getValue();
        assertThat(request.getInboundRequest()).isEqualTo(capturedResponseMessage.getInboundRequest());
        assertThat(capturedResponseMessage.getContext().getEndpoint()).isEqualTo("basicEndpoint");
        assertThat(capturedResponseMessage.getContext().errorResponseSent()).isFalse();
    }

    @Test
    void errorEndpoint() {
        request.getContext().setShouldSendErrorResponse(true);
        assertThat(request.getContext().get(CommonContextKeys.ZUUL_ENDPOINT)).isNull();
        endpointRunner.filter(request);
        ZuulFilter filter = request.getContext().get(CommonContextKeys.ZUUL_ENDPOINT);
        assertThat(filter instanceof ErrorEndpoint).isTrue();

        ArgumentCaptor<HttpResponseMessage> captor = ArgumentCaptor.forClass(HttpResponseMessage.class);
        verify(filterRunner, times(1)).filter(captor.capture());
        HttpResponseMessage capturedResponseMessage = captor.getValue();
        assertThat(request.getInboundRequest()).isEqualTo(capturedResponseMessage.getInboundRequest());
        assertThat(capturedResponseMessage.getContext().getEndpoint()).isNull();
        assertThat(capturedResponseMessage.getContext().errorResponseSent()).isTrue();
    }

    @Filter(order = 10, type = FilterType.ENDPOINT)
    static class ErrorEndpoint extends Endpoint {
        @Override
        public FilterCategory category() {
            return super.category();
        }

        @Override
        public CompletableFuture applyAsync(ZuulMessage input) {
            return CompletableFuture.completedFuture(buildHttpResponseMessage(input));
        }
    }

    @Filter(order = 20, type = FilterType.ENDPOINT)
    static class BasicEndpoint extends Endpoint {

        @Override
        public FilterCategory category() {
            return super.category();
        }

        @Override
        public CompletableFuture applyAsync(ZuulMessage input) {
            return CompletableFuture.completedFuture(buildHttpResponseMessage(input));
        }
    }

    private static HttpResponseMessage buildHttpResponseMessage(ZuulMessage request) {
        return new HttpResponseMessageImpl(request.getContext(), (HttpRequestMessage) request, 200);
    }
}
