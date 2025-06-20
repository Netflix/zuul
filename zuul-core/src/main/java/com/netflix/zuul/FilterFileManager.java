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
package com.netflix.zuul;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class manages the loading of filters from a set of known packages.
 *
 * @author Mikey Cohen
 *         Date: 12/7/11
 *         Time: 12:09 PM
 */
@Singleton
public class FilterFileManager {

    private static final Logger LOG = LoggerFactory.getLogger(FilterFileManager.class);

    private final FilterFileManagerConfig config;
    private final FilterLoader filterLoader;

    @Inject
    public FilterFileManager(FilterFileManagerConfig config, FilterLoader filterLoader) {
        this.config = config;
        this.filterLoader = filterLoader;
    }

    @Inject
    public void init() throws Exception {
        if (!config.enabled) {
            return;
        }

        long startTime = System.currentTimeMillis();
        filterLoader.putFiltersForClasses(config.getClassNames());
        LOG.warn("Finished loading all zuul filters. Duration = {} ms.", (System.currentTimeMillis() - startTime));
    }

    public static class FilterFileManagerConfig {
        private final String[] classNames;
        boolean enabled;

        public FilterFileManagerConfig(String[] classNames) {
            this(classNames, true);
        }

        public FilterFileManagerConfig(String[] classNames, boolean enabled) {
            this.classNames = classNames;
            this.enabled = enabled;
        }

        public String[] getClassNames() {
            return classNames;
        }
    }
}
