package com.netflix.zuul.servlet;

import com.netflix.config.ConfigurationManager;
import com.netflix.governator.guice.LifecycleInjectorBuilder;
import com.netflix.karyon.server.ServerBootstrap;
import com.netflix.zuul.FilterFileManager;
import com.sun.jersey.guice.JerseyServletModule;
import org.apache.commons.configuration.AbstractConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * User: michaels@netflix.com
 * Date: 5/8/15
 * Time: 11:55 AM
 */
public class ZuulBootstrap extends ServerBootstrap
{
    private static final Logger LOG = LoggerFactory.getLogger(ZuulBootstrap.class);

    @Override
    protected void beforeInjectorCreation(LifecycleInjectorBuilder builder)
    {
        builder.withAdditionalModules(new JerseyServletModule()
        {
            @Override
            protected void configureServlets()
            {
                serve("/*").with(ZuulServlet.class);
                bind(ZuulServlet.class).asEagerSingleton();

                initZuulFilters();
            }

            protected void initZuulFilters()
            {
                LOG.info("Starting Groovy Filter file manager");

                // Get filter directories.
                final AbstractConfiguration config = ConfigurationManager.getConfigInstance();
                String[] filterLocations = config.getString("zuul.filters.locations", "pre,post,route").split(",");
                LOG.info("Using filter locations: ");
                for (String location : filterLocations) {
                    LOG.info("  " + location);
                }

                // Init the FilterStore.
                FilterFileManager.FilterFileManagerConfig filterConfig = new FilterFileManager.FilterFileManagerConfig(filterLocations, 5);
                bind(FilterFileManager.FilterFileManagerConfig.class).toInstance(filterConfig);

                LOG.info("Groovy Filter file manager started");
            }
        });
    }
}