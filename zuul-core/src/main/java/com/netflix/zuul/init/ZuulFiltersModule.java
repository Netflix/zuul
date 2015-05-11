package com.netflix.zuul.init;

import com.google.inject.AbstractModule;
import com.netflix.config.ConfigurationManager;
import com.netflix.zuul.FilterFileManager;
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
            filterLocations = "pre,post,route".split(",");
        }
        LOG.info("Using filter locations: ");
        for (String location : filterLocations) {
            LOG.info("  " + location);
        }

        // Init the FilterStore.
        FilterFileManager.FilterFileManagerConfig filterConfig = new FilterFileManager.FilterFileManagerConfig(filterLocations, 5);
        bind(FilterFileManager.FilterFileManagerConfig.class).toInstance(filterConfig);

        LOG.info("Groovy Filter file manager started");
    }
}
