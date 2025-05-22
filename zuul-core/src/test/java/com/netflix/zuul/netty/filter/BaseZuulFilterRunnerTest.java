/*
 * Copyright 2025 Netflix, Inc.
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import com.netflix.spectator.api.NoopRegistry;
import com.netflix.spectator.api.Registry;
import com.netflix.zuul.Filter;
import com.netflix.zuul.FilterUsageNotifier;
import com.netflix.zuul.context.CommonContextKeys;
import com.netflix.zuul.context.SessionContext;
import com.netflix.zuul.filters.BaseFilter;
import com.netflix.zuul.filters.FilterSyncType;
import com.netflix.zuul.filters.FilterType;
import com.netflix.zuul.message.ZuulMessage;
import com.netflix.zuul.message.util.HttpRequestBuilder;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.DefaultEventLoopGroup;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.local.LocalChannel;
import io.netty.handler.codec.http.HttpContent;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import rx.Observable;

/**
 * @author Justin Guerra
 * @since 5/20/25
 */
@ExtendWith(MockitoExtension.class)
public class BaseZuulFilterRunnerTest {

    @Mock
    private FilterUsageNotifier notifier;

    @Mock
    private FilterRunner<ZuulMessage, ZuulMessage> nextStage;

    private ZuulMessage message;
    private TestBaseZuulFilterRunner runner;
    private EventLoopGroup group;
    private TestResumer resumer;
    private ErrorCapturingHandler errorCapturingHandler;

    @BeforeEach
    public void setup() {
        runner = new TestBaseZuulFilterRunner(FilterType.INBOUND, notifier, nextStage, new NoopRegistry());
        SessionContext sessionContext = new SessionContext();
        message = new HttpRequestBuilder(sessionContext).build();

        group = new DefaultEventLoopGroup(1);
        LocalChannel localChannel = new LocalChannel();
        errorCapturingHandler = new ErrorCapturingHandler();
        localChannel.pipeline().addLast(new ChannelInboundHandlerAdapter());
        localChannel.pipeline().addLast(errorCapturingHandler);
        group.register(localChannel);
        ChannelHandlerContext context = localChannel.pipeline().context(ChannelInboundHandlerAdapter.class);
        sessionContext.set(CommonContextKeys.NETTY_SERVER_CHANNEL_HANDLER_CONTEXT, context);
        resumer = new TestResumer(Function.identity());
    }

    @AfterEach
    public void teardown() {
        group.shutdownGracefully();
    }

    @Test
    public void onCompleteCalledBeforeResume() throws Exception {
        AsyncFilter asyncFilter = new AsyncFilter();
        asyncFilter.output.set(message.clone());

        resumer.validator = m -> {
            assertEquals(
                    0,
                    asyncFilter.getConcurrency(),
                    "concurrency should have been decremented before the filter chain was resumed");
            return m;
        };

        runner.filter(asyncFilter, message);
        ZuulMessage filteredMessage = resumer.future.get(5, TimeUnit.SECONDS);
        // sanity check to verify the message was transformed by AsyncFilter
        assertNotSame(message, filteredMessage);
    }

    @Test
    public void onCompleteCalledWithNullMessage() throws Exception {
        AsyncFilter asyncFilter = new AsyncFilter();
        asyncFilter.output.set(null);

        resumer.validator = m -> {
            assertEquals(
                    0,
                    asyncFilter.getConcurrency(),
                    "concurrency should have been decremented before the filter chain was resumed");
            assertNotNull(m);
            return m;
        };

        runner.filter(asyncFilter, message);
        ZuulMessage filteredMessage = resumer.future.get(5, TimeUnit.SECONDS);
        // sanity check to verify the message was transformed by AsyncFilter
        assertSame(message, filteredMessage);
    }

    @Test
    public void onCompleteThrows() {
        doThrow(new RuntimeException()).when(notifier).notify(any(), any());
        AsyncFilter asyncFilter = new AsyncFilter();
        asyncFilter.output.set(message);

        runner.filter(asyncFilter, message);
        Awaitility.await("request should have failed with an exception")
                .atMost(5, TimeUnit.SECONDS)
                .until(() -> errorCapturingHandler.error.get() != null);
    }

    @Filter(type = FilterType.INBOUND, sync = FilterSyncType.ASYNC, order = 1)
    private static class AsyncFilter extends BaseFilter<ZuulMessage, ZuulMessage> {

        private AtomicReference<ZuulMessage> output = new AtomicReference<>();

        @Override
        public Observable<ZuulMessage> applyAsync(ZuulMessage input) {
            return Observable.just(output.get());
        }

        @Override
        public boolean shouldFilter(ZuulMessage msg) {
            return true;
        }
    }

    private static class TestResumer {

        private final CompletableFuture<ZuulMessage> future;
        private volatile Function<ZuulMessage, ZuulMessage> validator;

        public TestResumer(Function<ZuulMessage, ZuulMessage> validator) {
            this.future = new CompletableFuture<>();
            this.validator = validator;
        }

        public void resume(ZuulMessage zuulMesg) {
            try {
                ZuulMessage zuulMessage = validator.apply(zuulMesg);
                future.complete(zuulMessage);
            } catch (Throwable e) {
                future.completeExceptionally(e);
            }
        }
    }

    private class TestBaseZuulFilterRunner extends BaseZuulFilterRunner<ZuulMessage, ZuulMessage> {

        protected TestBaseZuulFilterRunner(
                FilterType filterType,
                FilterUsageNotifier usageNotifier,
                FilterRunner<ZuulMessage, ?> nextStage,
                Registry registry) {
            super(filterType, usageNotifier, nextStage, registry);
        }

        @Override
        protected void resume(ZuulMessage zuulMesg) {
            resumer.resume(zuulMesg);
        }

        @Override
        public void filter(ZuulMessage zuulMesg) {}

        @Override
        public void filter(ZuulMessage zuulMesg, HttpContent chunk) {}
    }

    private static class ErrorCapturingHandler extends ChannelInboundHandlerAdapter {

        private final AtomicReference<Throwable> error = new AtomicReference<>();

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            error.set(cause);
        }
    }
}
