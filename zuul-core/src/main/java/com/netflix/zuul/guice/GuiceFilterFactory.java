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
import com.netflix.zuul.FilterFactory;
import com.netflix.zuul.filters.ZuulFilter;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class GuiceFilterFactory implements FilterFactory {

    private final Injector injector;

    @Inject
    public GuiceFilterFactory(Injector injector) {
        this.injector = injector;
    }

    @Override
    public ZuulFilter newInstance(Class clazz) throws Exception {
        return (ZuulFilter) injector.getInstance(clazz);
    }
}
