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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import com.netflix.spectator.api.NoopRegistry;
import com.netflix.spectator.api.Registry;
import com.netflix.zuul.Filter;
import com.netflix.zuul.FilterConstraint;
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
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.MultithreadEventLoopGroup;
import io.netty.channel.local.LocalChannel;
import io.netty.channel.local.LocalIoHandler;
import io.netty.handler.codec.http.HttpContent;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import lombok.NonNull;
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
    private MultithreadEventLoopGroup group;
    private TestResumer resumer;
    private ErrorCapturingHandler errorCapturingHandler;
    private TestConstraint constraint;

    @BeforeEach
    public void setup() {
        constraint = new TestConstraint();
        runner = new TestBaseZuulFilterRunner(FilterType.INBOUND, notifier, nextStage, new NoopRegistry());
        SessionContext sessionContext = new SessionContext();
        message = new HttpRequestBuilder(sessionContext).build();

        group = new MultiThreadIoEventLoopGroup(1, LocalIoHandler.newFactory());
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
            assertThat(asyncFilter.getConcurrency())
                    .as("concurrency should have been decremented before the filter chain was resumed")
                    .isEqualTo(0);
            return m;
        };

        runner.executeFilter(asyncFilter, message);
        ZuulMessage filteredMessage = resumer.future.get(5, TimeUnit.SECONDS);
        // sanity check to verify the message was transformed by AsyncFilter
        assertThat(filteredMessage).isNotSameAs(message);
    }

    @Test
    public void onCompleteCalledWithNullMessage() throws Exception {
        AsyncFilter asyncFilter = new AsyncFilter();
        asyncFilter.output.set(null);

        resumer.validator = m -> {
            assertThat(asyncFilter.getConcurrency())
                    .as("concurrency should have been decremented before the filter chain was resumed")
                    .isEqualTo(0);
            assertThat(m).isNotNull();
            return m;
        };

        runner.executeFilter(asyncFilter, message);
        ZuulMessage filteredMessage = resumer.future.get(5, TimeUnit.SECONDS);
        // sanity check to verify the message was transformed by AsyncFilter
        assertThat(filteredMessage).isSameAs(message);
    }

    @Test
    public void onCompleteThrows() {
        doThrow(new RuntimeException()).when(notifier).notify(any(), any());
        AsyncFilter asyncFilter = new AsyncFilter();
        asyncFilter.output.set(message);

        runner.executeFilter(asyncFilter, message);
        Awaitility.await("request should have failed with an exception")
                .atMost(5, TimeUnit.SECONDS)
                .until(() -> errorCapturingHandler.error.get() != null);
    }

    @Test
    public void endpointFilterNeverSkipped() {
        EndpointFilter filter = new EndpointFilter(false);
        message.getContext().stopFilterProcessing();
        message.getContext().cancel();
        assertThat(runner.shouldSkipFilter(message, filter)).isFalse();
    }

    @Test
    public void stopFilterProcessingSkipsFilter() {
        InboundFilter filter = new InboundFilter(true);
        message.getContext().stopFilterProcessing();
        assertThat(runner.shouldSkipFilter(message, filter)).isTrue();
    }

    @Test
    public void stopFilterProcessingDoesNotSkipWhenOverridden() {
        OverrideStopFilter filter = new OverrideStopFilter(true);
        message.getContext().stopFilterProcessing();
        assertThat(runner.shouldSkipFilter(message, filter)).isFalse();
    }

    @Test
    public void cancelledRequestSkipsFilter() {
        InboundFilter filter = new InboundFilter(true);
        message.getContext().cancel();
        assertThat(runner.shouldSkipFilter(message, filter)).isTrue();
    }

    @Test
    public void constrainedFilterSkipped() {
        InboundFilter filter = new InboundFilter(true);
        constraint.constrained = true;
        assertThat(runner.shouldSkipFilter(message, filter)).isTrue();
    }

    @Test
    public void shouldFilterFalseSkipsFilter() {
        InboundFilter filter = new InboundFilter(false);
        assertThat(runner.shouldSkipFilter(message, filter)).isTrue();
    }

    @Test
    public void shouldFilterTrueDoesNotSkip() {
        InboundFilter filter = new InboundFilter(true);
        assertThat(runner.shouldSkipFilter(message, filter)).isFalse();
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
            super(filterType, usageNotifier, nextStage, new FilterConstraints(List.of(constraint)), registry);
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

    @Filter(
            type = FilterType.INBOUND,
            order = 1,
            constraints = {TestConstraint.class})
    private static class InboundFilter extends BaseFilter<ZuulMessage, ZuulMessage> {

        private final boolean shouldFilter;

        InboundFilter(boolean shouldFilter) {
            this.shouldFilter = shouldFilter;
        }

        @Override
        public Observable<ZuulMessage> applyAsync(ZuulMessage input) {
            return Observable.just(input);
        }

        @Override
        public boolean shouldFilter(ZuulMessage msg) {
            return shouldFilter;
        }
    }

    @Filter(type = FilterType.ENDPOINT, order = 1)
    private static class EndpointFilter extends BaseFilter<ZuulMessage, ZuulMessage> {

        private final boolean shouldFilter;

        EndpointFilter(boolean shouldFilter) {
            this.shouldFilter = shouldFilter;
        }

        @Override
        public Observable<ZuulMessage> applyAsync(ZuulMessage input) {
            return Observable.just(input);
        }

        @Override
        public boolean shouldFilter(ZuulMessage msg) {
            return shouldFilter;
        }
    }

    @Filter(type = FilterType.INBOUND, order = 1)
    private static class OverrideStopFilter extends BaseFilter<ZuulMessage, ZuulMessage> {

        private final boolean shouldFilter;

        OverrideStopFilter(boolean shouldFilter) {
            this.shouldFilter = shouldFilter;
        }

        @Override
        public boolean overrideStopFilterProcessing() {
            return true;
        }

        @Override
        public Observable<ZuulMessage> applyAsync(ZuulMessage input) {
            return Observable.just(input);
        }

        @Override
        public boolean shouldFilter(ZuulMessage msg) {
            return shouldFilter;
        }
    }

    private static class TestConstraint implements FilterConstraint {

        boolean constrained;

        @Override
        public boolean isConstrained(@NonNull ZuulMessage msg) {
            return constrained;
        }
    }
}
