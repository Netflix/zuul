/*
 * Copyright 2023 Netflix, Inc.
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

package com.netflix.zuul.spring;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.netflix.appinfo.ApplicationInfoManager;
import com.netflix.spectator.api.Registry;
import com.netflix.zuul.Filter;
import com.netflix.zuul.filters.BaseFilter;
import com.netflix.zuul.filters.FilterType;
import com.netflix.zuul.filters.ZuulFilter;
import com.netflix.zuul.message.ZuulMessage;
import com.netflix.zuul.message.http.HttpRequestMessage;
import com.netflix.zuul.message.http.HttpResponseMessage;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import rx.Observable;

/**
 * @author Justin Guerra
 * @since 4/14/23
 */
@SpringBootTest(classes = ZuulSpringAutoConfiguration.class)
class SpringFilterFactoryTest {

    @MockBean
    private Registry registry;

    @MockBean
    private ApplicationInfoManager manager;

    @Autowired
    private SpringFilterFactory factory;

    @Test
    void createFilter() {
        ZuulFilter<?, ?> zuulFilter1 = factory.newInstance(TestFilter.class);
        ZuulFilter<?, ?> zuulFilter2 = factory.newInstance(TestFilter.class);

        assertSame(zuulFilter1, zuulFilter2);

        assertTrue(zuulFilter1.shouldFilter(null));
        ZuulMessage first = zuulFilter1.applyAsync(null).toBlocking().first();
        assertNotNull(first);
    }

    @Test
    void createFilterWrongType() {
        Assertions.assertThrows(NullPointerException.class, () -> factory.newInstance(null));
        Assertions.assertThrows(IllegalArgumentException.class, () -> factory.newInstance(String.class));
    }

    @Filter(order = 1, type = FilterType.INBOUND)
    public static class TestFilter extends BaseFilter<HttpRequestMessage, HttpResponseMessage> {

        @Override
        public boolean shouldFilter(HttpRequestMessage msg) {
            return true;
        }

        @Override
        public Observable<HttpResponseMessage> applyAsync(HttpRequestMessage input) {
            return Observable.just(Mockito.mock(HttpResponseMessage.class));
        }
    }
}