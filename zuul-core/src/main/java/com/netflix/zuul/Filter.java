/*
 * Copyright 2020 Netflix, Inc.
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

package com.netflix.zuul;

import static java.lang.annotation.ElementType.PACKAGE;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.CLASS;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import com.netflix.zuul.filters.FilterSyncType;
import com.netflix.zuul.filters.FilterType;
import com.netflix.zuul.filters.ZuulFilter;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Identifies a {@link ZuulFilter}.
 */
@Target({TYPE})
@Retention(RUNTIME)
@Documented
public @interface Filter {

    /**
     * The order in which to run.   See {@link ZuulFilter#filterOrder()}.
     */
    int order();

    /**
     * Indicates the type of this filter.
     */
    FilterType type() default FilterType.INBOUND;

    /**
     * Indicates if this is a synchronous filter.
     */
    FilterSyncType sync() default FilterSyncType.SYNC;

    @Target({PACKAGE})
    @Retention(CLASS)
    @Documented
    @interface FilterPackageName {
        String value();
    }

    /**
     * Indicates that the annotated filter should run after another filter in the chain, if the other filter is present.
     * In the case of inbound filters, this implies that the annotated filter should have an order greater than the
     * filters listed.  For outbound filters, the order of this filter should be less than the ones listed.  Usage of
     * this annotation should be used on homogeneous filter types.  Additionally, this should not be applied to endpoint
     * filters.
     */
    @Target({TYPE})
    @Retention(RUNTIME)
    @Documented
    @interface ApplyAfter {
        Class<? extends ZuulFilter<?, ?>>[] value();
    }

    /**
     * Indicates that the annotated filter should run before another filter in the chain, if the other filter is present.
     * In the case of inbound filters, this implies that the annotated filter should have an order less than the
     * filters listed.  For outbound filters, the order of this filter should be greater than the ones listed.  Usage of
     * this annotation should be used on homogeneous filter types.  Additionally, this should not be applied to endpoint
     * filters.
     *
     * <p>Prefer to use this {@link ApplyAfter} instead.  This annotation is meant in case where it may be infeasible
     * to use {@linkplain ApplyAfter}.  (such as due to dependency cycles)
     *
     * @see ApplyAfter
     */
    @Target({TYPE})
    @Retention(RUNTIME)
    @Documented
    @interface ApplyBefore {
        Class<? extends ZuulFilter<?, ?>>[] value();
    }
}
