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

package com.netflix.zuul.init;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

import com.netflix.zuul.init2.TestZuulFilter2;
import org.apache.commons.configuration.AbstractConfiguration;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class ZuulFiltersModuleTest {

    @Mock
    AbstractConfiguration configuration;

    private final ZuulFiltersModule module = new ZuulFiltersModule();

    @Test
    public void testDefaultFilterLocations() {
        Mockito.when(configuration.getStringArray("zuul.filters.locations"))
                .thenReturn("inbound,outbound,endpoint".split(","));

        String[] filterLocations = module.findFilterLocations(configuration);

        assertThat(filterLocations.length, equalTo(3));
        assertThat(filterLocations[1], equalTo("outbound"));
    }

    @Test
    public void testEmptyFilterLocations() {
        Mockito.when(configuration.getStringArray("zuul.filters.locations")).thenReturn(new String[0]);

        String[] filterLocations = module.findFilterLocations(configuration);

        assertThat(filterLocations.length, equalTo(0));
    }

    @Test
    public void testEmptyClassNames() {
        Mockito.when(configuration.getStringArray("zuul.filters.classes")).thenReturn(new String[]{});
        Mockito.when(configuration.getStringArray("zuul.filters.packages")).thenReturn(new String[]{});

        String[] classNames = module.findClassNames(configuration);

        assertThat(classNames.length, equalTo(0));
    }

    @Test
    public void testClassNamesOnly() {

        Class<?> expectedClass = TestZuulFilter.class;

        Mockito.when(configuration.getStringArray("zuul.filters.classes"))
                .thenReturn(new String[]{"com.netflix.zuul.init.TestZuulFilter"});
        Mockito.when(configuration.getStringArray("zuul.filters.packages")).thenReturn(new String[]{});

        String[] classNames = module.findClassNames(configuration);

        assertThat(classNames.length, equalTo(1));
        assertThat(classNames[0], equalTo(expectedClass.getCanonicalName()));

    }

    @Test
    public void testClassNamesPackagesOnly() {

        Class<?> expectedClass = TestZuulFilter.class;

        Mockito.when(configuration.getStringArray("zuul.filters.classes")).thenReturn(new String[]{});
        Mockito.when(configuration.getStringArray("zuul.filters.packages"))
                .thenReturn(new String[]{"com.netflix.zuul.init"});

        String[] classNames = module.findClassNames(configuration);

        assertThat(classNames.length, equalTo(1));
        assertThat(classNames[0], equalTo(expectedClass.getCanonicalName()));

    }

    @Test
    public void testMultiClasses() {
        Class<?> expectedClass1 = TestZuulFilter.class;
        Class<?> expectedClass2 = TestZuulFilter2.class;

        Mockito.when(configuration.getStringArray("zuul.filters.classes"))
                .thenReturn(new String[] {
                        "com.netflix.zuul.init.TestZuulFilter", "com.netflix.zuul.init2.TestZuulFilter2"});
        Mockito.when(configuration.getStringArray("zuul.filters.packages")).thenReturn(new String[0]);

        String[] classNames = module.findClassNames(configuration);

        assertThat(classNames.length, equalTo(2));
        assertThat(classNames[0], equalTo(expectedClass1.getCanonicalName()));
        assertThat(classNames[1], equalTo(expectedClass2.getCanonicalName()));
    }

    @Test
    public void testMultiPackages() {
        Class<?> expectedClass1 = TestZuulFilter.class;
        Class<?> expectedClass2 = TestZuulFilter2.class;

        Mockito.when(configuration.getStringArray("zuul.filters.classes")).thenReturn(new String[0]);
        Mockito.when(configuration.getStringArray("zuul.filters.packages"))
                .thenReturn(new String[]{"com.netflix.zuul.init", "com.netflix.zuul.init2"});

        String[] classNames = module.findClassNames(configuration);

        assertThat(classNames.length, equalTo(2));
        assertThat(classNames[0], equalTo(expectedClass1.getCanonicalName()));
        assertThat(classNames[1], equalTo(expectedClass2.getCanonicalName()));
    }
}