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

package com.netflix.zuul.filters.processor;

import static org.junit.Assert.assertEquals;

import com.google.common.truth.Truth;
import com.netflix.zuul.filters.ZuulFilter;
import com.netflix.zuul.filters.processor.subpackage.ProcessorSubpackageFilters;
import com.netflix.zuul.filters.processor.subpackage.SubpackageFilter;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for {@link FilterProcessor}.
 */
@RunWith(JUnit4.class)
public class FilterProcessorTest {

    @Test
    public void allFilterClassedRecorded() {
        List<? extends Class<? extends ZuulFilter<?, ?>>> filters = FiltersProcessorFilters.getFilters();
        List<? extends Class<? extends ZuulFilter<?, ?>>> subpackage = ProcessorSubpackageFilters.getFilters();

        Truth.assertThat(filters).containsExactly(
                OuterClassFilter.class,
                TopLevelFilter.class,
                TopLevelFilter.StaticSubclassFilter.class,
                TopLevelFilter.SubclassFilter.class);
        Truth.assertThat(subpackage).containsExactly(SubpackageFilter.class);
    }

    @Test
    public void deriveGeneratedClassName_emptyPackage() {
        String className = FilterProcessor.deriveGeneratedClassName("");

        assertEquals("Filters", className);
    }

    @Test
    public void deriveGeneratedClassName_shortPackage() {
        String className = FilterProcessor.deriveGeneratedClassName("somepackage");

        assertEquals("SomepackageFilters", className);
    }

    @Test
    public void deriveGeneratedClassName_twoPartPackage() {
        String className = FilterProcessor.deriveGeneratedClassName("two.part");

        assertEquals("TwoPartFilters", className);
    }

    @Test
    public void deriveGeneratedClassName_threePartPackage() {
        String className = FilterProcessor.deriveGeneratedClassName("packed.three.part");

        assertEquals("ThreePartFilters", className);
    }

    @Test
    public void deriveGeneratedClassName_underscorePackage() {
        String className = FilterProcessor.deriveGeneratedClassName("packed.three_under.part");

        assertEquals("ThreeUnderPartFilters", className);
    }
}
