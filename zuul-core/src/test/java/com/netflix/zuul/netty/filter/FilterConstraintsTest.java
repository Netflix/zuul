/*
 * Copyright 2026 Netflix, Inc.
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

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.netflix.zuul.FilterConstraint;
import com.netflix.zuul.context.SessionContext;
import com.netflix.zuul.filters.ZuulFilter;
import com.netflix.zuul.message.ZuulMessage;
import com.netflix.zuul.message.http.HttpRequestMessage;
import com.netflix.zuul.message.util.HttpRequestBuilder;
import java.util.List;
import lombok.NonNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * @author Justin Guerra
 * @since 2/13/26
 */
@SuppressWarnings("unchecked")
class FilterConstraintsTest {

    private HttpRequestMessage request;
    private FilterConstraints filterConstraints;

    private boolean constraintAResult;
    private boolean constraintBResult;

    @BeforeEach
    void setUp() {
        request = new HttpRequestBuilder(new SessionContext()).build();
        constraintAResult = false;
        constraintBResult = false;
        filterConstraints = new FilterConstraints(List.of(new ConstraintA(), new ConstraintB()));
    }

    @Test
    void nullConstraintsFromFilter() {
        ZuulFilter<?, ?> filter = mockFilter(null);
        assertThat(filterConstraints.isConstrained(request, filter)).isFalse();
    }

    @Test
    void emptyConstraintsFromFilter() {
        ZuulFilter<?, ?> filter = mockFilter(new Class[0]);
        assertThat(filterConstraints.isConstrained(request, filter)).isFalse();
    }

    @Test
    void singleConstraintMatches() {
        constraintAResult = true;
        ZuulFilter<?, ?> filter = mockFilter(new Class[] {ConstraintA.class});
        assertThat(filterConstraints.isConstrained(request, filter)).isTrue();
    }

    @Test
    void singleConstraintDoesNotMatch() {
        ZuulFilter<?, ?> filter = mockFilter(new Class[] {ConstraintA.class});
        assertThat(filterConstraints.isConstrained(request, filter)).isFalse();
    }

    @Test
    void multipleConstraintsFirstMatches() {
        constraintAResult = true;
        ZuulFilter<?, ?> filter = mockFilter(new Class[] {ConstraintA.class, ConstraintB.class});
        assertThat(filterConstraints.isConstrained(request, filter)).isTrue();
    }

    @Test
    void multipleConstraintsSecondMatches() {
        constraintBResult = true;
        ZuulFilter<?, ?> filter = mockFilter(new Class[] {ConstraintA.class, ConstraintB.class});
        assertThat(filterConstraints.isConstrained(request, filter)).isTrue();
    }

    @Test
    void multipleConstraintsNoneMatch() {
        ZuulFilter<?, ?> filter = mockFilter(new Class[] {ConstraintA.class, ConstraintB.class});
        assertThat(filterConstraints.isConstrained(request, filter)).isFalse();
    }

    @Test
    void constraintNotInLookup() {
        FilterConstraints limited = new FilterConstraints(List.of(new ConstraintA()));
        constraintBResult = true;
        ZuulFilter<?, ?> filter = mockFilter(new Class[] {ConstraintB.class});
        assertThat(limited.isConstrained(request, filter)).isFalse();
    }

    private ZuulFilter<?, ?> mockFilter(Class<? extends FilterConstraint>[] constraints) {
        ZuulFilter<?, ?> filter = mock(ZuulFilter.class);
        when(filter.constraints()).thenReturn(constraints);
        return filter;
    }

    private class ConstraintA implements FilterConstraint {
        @Override
        public boolean isConstrained(@NonNull ZuulMessage msg) {
            return constraintAResult;
        }
    }

    private class ConstraintB implements FilterConstraint {
        @Override
        public boolean isConstrained(@NonNull ZuulMessage msg) {
            return constraintBResult;
        }
    }
}
