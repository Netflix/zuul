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

package com.netflix.zuul.integration.server;

import com.google.inject.Provider;
import com.netflix.zuul.DefaultFilterFactory;
import com.netflix.zuul.FilterLoader;
import com.netflix.zuul.StaticFilterLoader;
import com.netflix.zuul.filters.ZuulFilter;
import com.netflix.zuul.integration.server.filters.InboundRoutesFilter;
import com.netflix.zuul.integration.server.filters.NeedsBodyBufferedInboundFilter;
import com.netflix.zuul.integration.server.filters.NeedsBodyBufferedOutboundFilter;
import com.netflix.zuul.integration.server.filters.RequestHeaderFilter;
import com.netflix.zuul.integration.server.filters.ResponseHeaderFilter;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public class FilterLoaderProvider implements Provider<FilterLoader> {

    private static final Set<? extends Class<? extends ZuulFilter<?, ?>>> FILTER_TYPES;

    static {
        Set<Class<? extends ZuulFilter<?, ?>>> classes = new LinkedHashSet<>();
        classes.add(InboundRoutesFilter.class);
        classes.add(NeedsBodyBufferedInboundFilter.class);
        classes.add(RequestHeaderFilter.class);
        classes.add(ResponseHeaderFilter.class);
        classes.add(NeedsBodyBufferedOutboundFilter.class);

        FILTER_TYPES = Collections.unmodifiableSet(classes);
    }

    @Override
    public FilterLoader get() {
        StaticFilterLoader loader = new StaticFilterLoader(new DefaultFilterFactory(), FILTER_TYPES);
        return loader;
    }
}
