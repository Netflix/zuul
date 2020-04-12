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

package com.netflix.zuul.guice;

import com.google.inject.Injector;
import com.netflix.zuul.filters.BaseSyncFilter;
import com.netflix.zuul.filters.FilterType;
import com.netflix.zuul.message.ZuulMessage;
import javax.inject.Inject;

public class TestGuiceConstructorFilter extends BaseSyncFilter {

    final Injector injector;

    @Inject
    public TestGuiceConstructorFilter(Injector injector) {
        this.injector = injector;
    }

    @Override
    public FilterType filterType() {
        return FilterType.INBOUND;
    }

    @Override
    public int filterOrder() {
        return 0;
    }

    @Override
    public boolean shouldFilter(ZuulMessage msg) {
        return false;
    }

    @Override
    public ZuulMessage apply(ZuulMessage msg) {
        return null;
    }
}