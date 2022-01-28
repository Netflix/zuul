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

import static com.netflix.zuul.context.CommonContextKeys.NETTY_SERVER_CHANNEL_HANDLER_CONTEXT;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.netflix.spectator.api.Registry;
import com.netflix.zuul.ExecutionStatus;
import com.netflix.zuul.FilterUsageNotifier;
import com.netflix.zuul.context.SessionContext;
import com.netflix.zuul.filters.FilterType;
import com.netflix.zuul.filters.ZuulFilter;
import com.netflix.zuul.filters.http.HttpInboundFilter;
import com.netflix.zuul.filters.http.HttpOutboundFilter;
import com.netflix.zuul.message.Headers;
import com.netflix.zuul.message.http.HttpQueryParams;
import com.netflix.zuul.message.http.HttpRequestMessage;
import com.netflix.zuul.message.http.HttpRequestMessageImpl;
import com.netflix.zuul.message.http.HttpResponseMessage;
import com.netflix.zuul.message.http.HttpResponseMessageImpl;

import io.netty.channel.ChannelHandlerContext;
import io.netty.util.concurrent.ImmediateEventExecutor;
import rx.Observable;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;


@RunWith(JUnit4.class)
public class ZuulFilterChainRunnerTest {
    private HttpRequestMessage request;
    private HttpResponseMessage response;

    @Before
    public void before() {
        SessionContext context = new SessionContext();
        Headers headers = new Headers();
        ChannelHandlerContext chc = mock(ChannelHandlerContext.class);
        when(chc.executor()).thenReturn(ImmediateEventExecutor.INSTANCE);
        context.put(NETTY_SERVER_CHANNEL_HANDLER_CONTEXT, chc);
        request = new HttpRequestMessageImpl(context, "http", "GET", "/foo/bar", new HttpQueryParams(), headers, "127.0.0.1", "http", 8080, "server123");
        request.storeInboundRequest();
        response = new HttpResponseMessageImpl(context, request, 200);
    }

    @Test
    public void testInboundFilterChain() {
        final SimpleInboundFilter inbound1 = spy(new SimpleInboundFilter(true));
        final SimpleInboundFilter inbound2 = spy(new SimpleInboundFilter(false));

        final ZuulFilter[] filters = new ZuulFilter[] { inbound1, inbound2 };

        final FilterUsageNotifier notifier = mock(FilterUsageNotifier.class);
        final Registry registry = mock(Registry.class);

        final ZuulFilterChainRunner runner = new ZuulFilterChainRunner(
                filters,
                notifier,
                registry);

        runner.filter(request);

        verify(inbound1, times(1)).applyAsync(eq(request));
        verify(inbound2, never()).applyAsync(eq(request));

        verify(notifier).notify(eq(inbound1), eq(ExecutionStatus.SUCCESS));
        verify(notifier).notify(eq(inbound2), eq(ExecutionStatus.SKIPPED));
        verifyNoMoreInteractions(notifier);
    }

    @Test
    public void testOutboundFilterChain() {
        final SimpleOutboundFilter outbound1 = spy(new SimpleOutboundFilter(true));
        final SimpleOutboundFilter outbound2 = spy(new SimpleOutboundFilter(false));

        final ZuulFilter[] filters = new ZuulFilter[] { outbound1, outbound2 };

        final FilterUsageNotifier notifier = mock(FilterUsageNotifier.class);
        final Registry registry = mock(Registry.class);

        final ZuulFilterChainRunner runner = new ZuulFilterChainRunner(
                filters,
                notifier,
                registry);

        runner.filter(response);

        verify(outbound1, times(1)).applyAsync(any());
        verify(outbound2, never()).applyAsync(any());

        verify(notifier).notify(eq(outbound1), eq(ExecutionStatus.SUCCESS));
        verify(notifier).notify(eq(outbound2), eq(ExecutionStatus.SKIPPED));
        verifyNoMoreInteractions(notifier);
    }

    class SimpleInboundFilter extends HttpInboundFilter {
        private final boolean shouldFilter;

        public SimpleInboundFilter(final boolean shouldFilter) {
            this.shouldFilter = shouldFilter;
        }

        @Override
        public int filterOrder() {
            return 0;
        }

        @Override
        public FilterType filterType() {
            return FilterType.INBOUND;
        }

        @Override
        public Observable<HttpRequestMessage> applyAsync(HttpRequestMessage input) {
            return Observable.just(input);
        }

        @Override
        public boolean shouldFilter(HttpRequestMessage msg) {
            return this.shouldFilter;
        }
    }

    class SimpleOutboundFilter extends HttpOutboundFilter {
        private final boolean shouldFilter;

        public SimpleOutboundFilter(final boolean shouldFilter) {
            this.shouldFilter = shouldFilter;
        }

        @Override
        public int filterOrder() {
            return 0;
        }

        @Override
        public FilterType filterType() {
            return FilterType.OUTBOUND;
        }

        @Override
        public Observable<HttpResponseMessage> applyAsync(HttpResponseMessage input) {
            return Observable.just(input);
        }

        @Override
        public boolean shouldFilter(HttpResponseMessage msg) {
            return this.shouldFilter;
        }
    }
    
}

