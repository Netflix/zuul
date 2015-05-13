package com.netflix.zuul.servlet;

import com.netflix.governator.guice.LifecycleInjectorBuilder;
import com.netflix.karyon.server.ServerBootstrap;
import com.netflix.zuul.init.ZuulFiltersModule;
import com.sun.jersey.guice.JerseyServletModule;
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
            }
        }, new ZuulOriginsModule(), new ZuulFiltersModule());
    }
}