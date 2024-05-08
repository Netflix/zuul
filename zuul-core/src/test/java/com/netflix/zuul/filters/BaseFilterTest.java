/*
 * Copyright 2019 Netflix, Inc.
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
package com.netflix.zuul.filters;

import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import com.google.common.truth.Truth;
import com.netflix.config.ConfigurationManager;
import com.netflix.zuul.context.SessionContext;
import com.netflix.zuul.message.Headers;
import com.netflix.zuul.message.ZuulMessage;
import com.netflix.zuul.message.ZuulMessageImpl;
import org.apache.commons.configuration.AbstractConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import rx.Observable;

/**
 * Tests for {@link BaseFilter}
 */
@RunWith(MockitoJUnitRunner.class)
class BaseFilterTest {

    @Mock
    private ZuulMessage req;
    private final AbstractConfiguration config = ConfigurationManager.getConfigInstance();

    @BeforeEach
    public void tearDown() {
        config.clear();
    }

    @Test
    void testShouldFilter() {
        class TestZuulFilter extends BaseSyncFilter {

            @Override
            public int filterOrder() {
                return 0;
            }

            @Override
            public FilterType filterType() {
                return FilterType.INBOUND;
            }

            @Override
            public boolean shouldFilter(ZuulMessage req) {
                return false;
            }

            @Override
            public ZuulMessage apply(ZuulMessage req) {
                return null;
            }
        }

        TestZuulFilter tf1 = spy(new TestZuulFilter());
        TestZuulFilter tf2 = spy(new TestZuulFilter());

        when(tf1.shouldFilter(req)).thenReturn(true);
        when(tf2.shouldFilter(req)).thenReturn(false);
    }

    @Test
    void validateDefaultConcurrencyLimit() {
        final int[] limit = {0};
        class ConcInboundFilter extends BaseFilter {

            @Override
            public Observable applyAsync(ZuulMessage input) {
                limit[0] = calculateConcurency();
                return Observable.just("Done");
            }

            @Override
            public FilterType filterType() {
                return FilterType.INBOUND;
            }

            @Override
            public boolean shouldFilter(ZuulMessage msg) {
                return true;
            }
        }
        new ConcInboundFilter().applyAsync(new ZuulMessageImpl(new SessionContext(), new Headers()));
        Truth.assertThat(limit[0]).isEqualTo(4000);
    }

    @Test
    void validateFilterGlobalConcurrencyLimitOverride() {
        config.setProperty("zuul.filter.concurrency.limit.default", 7000);
        config.setProperty("zuul.ConcInboundFilter.in.concurrency.limit", 4000);
        final int[] limit = {0};

        class ConcInboundFilter extends BaseFilter {

            @Override
            public Observable applyAsync(ZuulMessage input) {
                limit[0] = calculateConcurency();
                return Observable.just("Done");
            }

            @Override
            public FilterType filterType() {
                return FilterType.INBOUND;
            }

            @Override
            public boolean shouldFilter(ZuulMessage msg) {
                return true;
            }
        }
        new ConcInboundFilter().applyAsync(new ZuulMessageImpl(new SessionContext(), new Headers()));
        Truth.assertThat(limit[0]).isEqualTo(7000);
    }

    @Test
    void validateFilterSpecificConcurrencyLimitOverride() {
        config.setProperty("zuul.filter.concurrency.limit.default", 7000);
        config.setProperty("zuul.ConcInboundFilter.in.concurrency.limit", 4300);
        final int[] limit = {0};

        class ConcInboundFilter extends BaseFilter {

            @Override
            public Observable applyAsync(ZuulMessage input) {
                limit[0] = calculateConcurency();
                return Observable.just("Done");
            }

            @Override
            public FilterType filterType() {
                return FilterType.INBOUND;
            }

            @Override
            public boolean shouldFilter(ZuulMessage msg) {
                return true;
            }
        }
        new ConcInboundFilter().applyAsync(new ZuulMessageImpl(new SessionContext(), new Headers()));
        Truth.assertThat(limit[0]).isEqualTo(4300);
    }
}
