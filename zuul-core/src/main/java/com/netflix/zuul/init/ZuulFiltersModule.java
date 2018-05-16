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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.reflect.ClassPath;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.netflix.config.ConfigurationManager;
import com.netflix.zuul.BasicFilterUsageNotifier;
import com.netflix.zuul.DynamicCodeCompiler;
import com.netflix.zuul.FilterFactory;
import com.netflix.zuul.FilterFileManager.FilterFileManagerConfig;
import com.netflix.zuul.FilterUsageNotifier;
import com.netflix.zuul.filters.ZuulFilter;
import com.netflix.zuul.groovy.GroovyCompiler;
import com.netflix.zuul.guice.GuiceFilterFactory;
import org.apache.commons.configuration.AbstractConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Arrays;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * User: michaels@netflix.com
 * Date: 5/8/15
 * Time: 6:15 PM
 */
public class ZuulFiltersModule extends AbstractModule {
    private static final Logger LOG = LoggerFactory.getLogger(ZuulFiltersModule.class);

    private static Predicate<String> blank = String::isEmpty;

    @Override
    protected void configure() {
        LOG.info("Starting Groovy Filter file manager");

        bind(DynamicCodeCompiler.class).to(GroovyCompiler.class);
        bind(FilterFactory.class).to(GuiceFilterFactory.class);

        bind(FilterUsageNotifier.class).to(BasicFilterUsageNotifier.class);

        LOG.info("Groovy Filter file manager started");
    }

    @Provides
    FilterFileManagerConfig provideFilterFileManagerConfig() {
        // Get filter directories.
        final AbstractConfiguration config = ConfigurationManager.getConfigInstance();

        String[] filterLocations = findFilterLocations(config);
        String[] filterClassNames = findClassNames(config);

        // Init the FilterStore.
        FilterFileManagerConfig filterConfig = new FilterFileManagerConfig(filterLocations, filterClassNames, 5);
        return filterConfig;
    }

    // Get compiled filter classes to be found on classpath.
    @VisibleForTesting
    String[] findClassNames(AbstractConfiguration config) {

        // Find individually-specified filter classes.
        String[] filterClassNamesStrArray = config.getStringArray("zuul.filters.classes");
        Stream<String> classNameStream = Arrays.stream(filterClassNamesStrArray)
                .map(String::trim)
                .filter(blank.negate());

        // Find filter classes in specified packages.
        String[] packageNamesStrArray = config.getStringArray("zuul.filters.packages");
        ClassPath cp;
        try {
            cp = ClassPath.from(this.getClass().getClassLoader());
        }
        catch (IOException e) {
            throw new RuntimeException("Error attempting to read classpath to find filters!", e);
        }
        Stream<String> packageStream = Arrays.stream(packageNamesStrArray)
                .map(String::trim)
                .filter(blank.negate())
                .flatMap(packageName -> cp.getTopLevelClasses(packageName).stream())
                .map(ClassPath.ClassInfo::load)
                .filter(ZuulFilter.class::isAssignableFrom)
                .map(Class::getCanonicalName);


        String[] filterClassNames = Stream.concat(classNameStream, packageStream).toArray(String[]::new);
        if (filterClassNames.length != 0) {
            LOG.info("Using filter classnames: ");
            for (String location : filterClassNames) {
                LOG.info("  " + location);
            }
        }

        return filterClassNames;
    }

    @VisibleForTesting
    String[] findFilterLocations(AbstractConfiguration config) {
        String[] locations = config.getStringArray("zuul.filters.locations");
        if (locations == null) {
            locations = new String[]{"inbound", "outbound", "endpoint"};
        }
        String[] filterLocations = Arrays.stream(locations)
                .map(String::trim)
                .filter(blank.negate())
                .toArray(String[]::new);

        if (filterLocations.length != 0) {
            LOG.info("Using filter locations: ");
            for (String location : filterLocations) {
                LOG.info("  " + location);
            }
        }
        return filterLocations;
    }
}
