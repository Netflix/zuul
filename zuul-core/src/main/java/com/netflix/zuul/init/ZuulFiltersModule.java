/**
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.zuul.init;

import com.google.inject.AbstractModule;
import com.netflix.config.ConfigurationManager;
import com.netflix.zuul.FilterFileManager;
import com.netflix.zuul.FilterProcessor;
import com.netflix.zuul.FilterUsageNotifier;
import org.apache.commons.configuration.AbstractConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * User: michaels@netflix.com
 * Date: 5/8/15
 * Time: 6:15 PM
 */
public class ZuulFiltersModule extends AbstractModule
{
    private static final Logger LOG = LoggerFactory.getLogger(ZuulFiltersModule.class);

    @Override
    protected void configure() {
        LOG.info("Starting Groovy Filter file manager");

        // Get filter directories.
        final AbstractConfiguration config = ConfigurationManager.getConfigInstance();
        String[] filterLocations = config.getStringArray("zuul.filters.locations");
        if (filterLocations == null || filterLocations.length == 0) {
            // Default to these locations.
            filterLocations = "inbound,outbound,endpoint".split(",");
        }
        LOG.info("Using filter locations: ");
        for (String location : filterLocations) {
            LOG.info("  " + location);
        }

        // Init the FilterStore.
        FilterFileManager.FilterFileManagerConfig filterConfig = new FilterFileManager.FilterFileManagerConfig(filterLocations, 5);
        bind(FilterFileManager.FilterFileManagerConfig.class).toInstance(filterConfig);
        bind(FilterUsageNotifier.class).to(FilterProcessor.BasicFilterUsageNotifier.class);

        LOG.info("Groovy Filter file manager started");
    }
}
