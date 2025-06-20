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
import org.mockito.junit.MockitoJUnitRunner;
import rx.Observable;

/**
 * Tests for {@link BaseFilter}
 */
@RunWith(MockitoJUnitRunner.class)
class BaseFilterTest {

    private final AbstractConfiguration config = ConfigurationManager.getConfigInstance();

    @BeforeEach
    public void setUpTest() {
        config.clear();
    }

    @Test
    void validateDefaultConcurrencyLimit() throws InterruptedException {
        int[] limit = {0};
        class ConcInboundFilter extends BaseFilter<ZuulMessage, ZuulMessage> {
            @Override
            public Observable<ZuulMessage> applyAsync(ZuulMessage input) {
                limit[0] = calculateConcurency();
                return Observable.just(new ZuulMessageImpl(new SessionContext()));
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
        new ConcInboundFilter()
                .applyAsync(new ZuulMessageImpl(new SessionContext(), new Headers()))
                .toBlocking()
                .single();
        Truth.assertThat(limit[0]).isEqualTo(4000);
    }

    @Test
    void validateFilterGlobalConcurrencyLimitOverride() throws InterruptedException {
        config.setProperty("zuul.filter.concurrency.limit.default", 7000);
        config.setProperty("zuul.ConcInboundFilter.in.concurrency.limit", 4000);
        int[] limit = {0};

        class ConcInboundFilter extends BaseFilter<ZuulMessage, ZuulMessage> {
            @Override
            public Observable<ZuulMessage> applyAsync(ZuulMessage input) {
                limit[0] = calculateConcurency();
                return Observable.just(new ZuulMessageImpl(new SessionContext()));
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
        new ConcInboundFilter()
                .applyAsync(new ZuulMessageImpl(new SessionContext(), new Headers()))
                .toBlocking()
                .single();
        Truth.assertThat(limit[0]).isEqualTo(7000);
    }

    @Test
    void validateFilterSpecificConcurrencyLimitOverride() throws InterruptedException {
        config.setProperty("zuul.filter.concurrency.limit.default", 7000);
        config.setProperty("zuul.ConcInboundFilter.in.concurrency.limit", 4300);
        int[] limit = {0};

        class ConcInboundFilter extends BaseFilter<ZuulMessage, ZuulMessage> {
            @Override
            public Observable<ZuulMessage> applyAsync(ZuulMessage input) {
                limit[0] = calculateConcurency();
                return Observable.just(new ZuulMessageImpl(new SessionContext()));
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
        new ConcInboundFilter()
                .applyAsync(new ZuulMessageImpl(new SessionContext(), new Headers()))
                .toBlocking()
                .single();
        Truth.assertThat(limit[0]).isEqualTo(4300);
    }
}
