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

import com.google.common.truth.Truth;
import com.netflix.zuul.StaticFilterLoader;
import com.netflix.zuul.filters.ZuulFilter;
import com.netflix.zuul.filters.processor.override.SubpackageFilter;
import com.netflix.zuul.filters.processor.subpackage.OverrideFilter;
import java.util.Collection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for {@link FilterProcessor}.
 */
@RunWith(JUnit4.class)
public class FilterProcessorTest {

    @Test
    public void allFilterClassedRecorded() throws Exception {
        Collection<Class<ZuulFilter<?, ?>>> filters =
                StaticFilterLoader.loadFilterTypesFromResources(getClass().getClassLoader());

        Truth.assertThat(filters).containsExactly(
                OuterClassFilter.class,
                TopLevelFilter.class,
                TopLevelFilter.StaticSubclassFilter.class,
                TopLevelFilter.SubclassFilter.class,
                OverrideFilter.class,
                SubpackageFilter.class);
    }
}
