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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.netflix.spectator.api.Registry;
import com.netflix.zuul.ExecutionStatus;
import com.netflix.zuul.Filter;
import com.netflix.zuul.FilterUsageNotifier;
import com.netflix.zuul.context.CommonContextKeys;
import com.netflix.zuul.context.SessionContext;
import com.netflix.zuul.filters.FilterType;
import com.netflix.zuul.filters.ZuulFilter;
import com.netflix.zuul.filters.http.HttpInboundFilter;
import com.netflix.zuul.filters.http.HttpOutboundFilter;
import com.netflix.zuul.message.Headers;
import com.netflix.zuul.message.ZuulMessage;
import com.netflix.zuul.message.http.HttpQueryParams;
import com.netflix.zuul.message.http.HttpRequestMessage;
import com.netflix.zuul.message.http.HttpRequestMessageImpl;
import com.netflix.zuul.message.http.HttpResponseMessage;
import com.netflix.zuul.message.http.HttpResponseMessageImpl;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.HttpContent;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import rx.Observable;

class ZuulFilterChainRunnerTest {
    private HttpRequestMessage request;
    private HttpResponseMessage response;
    private EmbeddedChannel channel;

    @BeforeEach
    void before() {
        SessionContext context = new SessionContext();
        Headers headers = new Headers();

        channel = new EmbeddedChannel(new ChannelInboundHandlerAdapter());
        ChannelHandlerContext ctx = channel.pipeline().context(ChannelInboundHandlerAdapter.class);
        context.put(CommonContextKeys.NETTY_SERVER_CHANNEL_HANDLER_CONTEXT, ctx);
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
        response = new HttpResponseMessageImpl(context, request, 200);
    }

    @Test
    void testInboundFilterChain() {
        SimpleInboundFilter inbound1 = spy(new SimpleInboundFilter(true));
        SimpleInboundFilter inbound2 = spy(new SimpleInboundFilter(false));

        ZuulFilter[] filters = new ZuulFilter[] {inbound1, inbound2};

        FilterUsageNotifier notifier = mock(FilterUsageNotifier.class);
        Registry registry = mock(Registry.class);

        ZuulFilterChainRunner runner =
                new ZuulFilterChainRunner(filters, notifier, new FilterConstraints(List.of()), registry);

        runner.filter(request);

        verify(inbound1, times(1)).applyAsync(eq(request));
        verify(inbound2, never()).applyAsync(eq(request));

        verify(notifier).notify(eq(inbound1), eq(ExecutionStatus.SUCCESS));
        verify(notifier).notify(eq(inbound2), eq(ExecutionStatus.SKIPPED));
        verifyNoMoreInteractions(notifier);
    }

    @Test
    void testOutboundFilterChain() {
        SimpleOutboundFilter outbound1 = spy(new SimpleOutboundFilter(true));
        SimpleOutboundFilter outbound2 = spy(new SimpleOutboundFilter(false));

        ZuulFilter[] filters = new ZuulFilter[] {outbound1, outbound2};

        FilterUsageNotifier notifier = mock(FilterUsageNotifier.class);
        Registry registry = mock(Registry.class);

        ZuulFilterChainRunner runner =
                new ZuulFilterChainRunner(filters, notifier, new FilterConstraints(List.of()), registry);

        runner.filter(response);

        verify(outbound1, times(1)).applyAsync(any());
        verify(outbound2, never()).applyAsync(any());

        verify(notifier).notify(eq(outbound1), eq(ExecutionStatus.SUCCESS));
        verify(notifier).notify(eq(outbound2), eq(ExecutionStatus.SKIPPED));
        verifyNoMoreInteractions(notifier);
    }

    @Test
    void chunkPathSkipsNoOpFiltersButStillForwardsChunk() {
        SimpleOutboundFilter outbound1 = spy(new SimpleOutboundFilter(true));
        SimpleOutboundFilter outbound2 = spy(new SimpleOutboundFilter(true));

        ZuulFilter[] filters = new ZuulFilter[] {outbound1, outbound2};
        ZuulFilterChainRunner runner = new ZuulFilterChainRunner(
                filters, mock(FilterUsageNotifier.class), new FilterConstraints(List.of()), mock(Registry.class));

        runner.filter(response);
        channel.readInbound();
        clearInvocations(outbound1, outbound2);

        HttpContent chunk = new DefaultHttpContent(Unpooled.copiedBuffer("data".getBytes(UTF_8)));
        runner.filter(response, chunk);

        verify(outbound1, never()).shouldFilter(any());
        verify(outbound1, never()).isDisabled();
        verify(outbound1, never()).processContentChunk(any(), any());
        verify(outbound2, never()).shouldFilter(any());
        assertThat((HttpContent) channel.readInbound()).isSameAs(chunk);
    }

    @Test
    void chunkPathStillRunsChunkTransformingFilters() {
        SimpleOutboundFilter noOp = spy(new SimpleOutboundFilter(true));
        ChunkTransformingOutboundFilter transformer = spy(new ChunkTransformingOutboundFilter());

        ZuulFilter[] filters = new ZuulFilter[] {noOp, transformer};
        ZuulFilterChainRunner runner = new ZuulFilterChainRunner(
                filters, mock(FilterUsageNotifier.class), new FilterConstraints(List.of()), mock(Registry.class));

        runner.filter(response);
        channel.readInbound();
        clearInvocations(noOp, transformer);

        HttpContent chunk = new DefaultHttpContent(Unpooled.copiedBuffer("data".getBytes(UTF_8)));
        runner.filter(response, chunk);

        verify(noOp, never()).processContentChunk(any(), any());
        verify(transformer, times(1)).processContentChunk(eq(response), eq(chunk));
        assertThat((HttpContent) channel.readInbound()).isSameAs(transformer.replacement);
    }

    @Filter(order = 1)
    class SimpleInboundFilter extends HttpInboundFilter {
        private final boolean shouldFilter;

        public SimpleInboundFilter(boolean shouldFilter) {
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

    @Filter(order = 1)
    class SimpleOutboundFilter extends HttpOutboundFilter {
        private final boolean shouldFilter;

        public SimpleOutboundFilter(boolean shouldFilter) {
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

    @Filter(order = 1)
    class ChunkTransformingOutboundFilter extends HttpOutboundFilter {
        final HttpContent replacement = new DefaultHttpContent(Unpooled.copiedBuffer("gz".getBytes(UTF_8)));

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
            return true;
        }

        @Override
        public HttpContent processContentChunk(ZuulMessage zuulMessage, HttpContent chunk) {
            return replacement;
        }
    }
}
