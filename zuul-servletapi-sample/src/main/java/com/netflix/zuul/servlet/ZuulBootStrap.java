package com.netflix.zuul.servlet;

import com.google.inject.AbstractModule;
import com.netflix.governator.guice.LifecycleInjectorBuilder;
import com.netflix.karyon.server.ServerBootstrap;
import com.netflix.zuul.context.SampleSessionContextDecorator;
import com.netflix.zuul.context.ServletSessionContextFactory;
import com.netflix.zuul.context.SessionContextDecorator;
import com.netflix.zuul.context.SessionContextFactory;
import com.netflix.zuul.init.ZuulFiltersModule;
import com.netflix.zuul.origins.OriginManager;
import com.netflix.zuul.ribbon.RibbonOriginManager;
import com.sun.jersey.guice.JerseyServletModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * User: michaels@netflix.com
 * Date: 5/8/15
 * Time: 11:55 AM
 */
public class ZuulBootStrap extends ServerBootstrap
{
    private static final Logger LOG = LoggerFactory.getLogger(ZuulBootStrap.class);

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
        },

                new AbstractModule() {
                    @Override
                    protected void configure()
                    {
                        bind(OriginManager.class).to(RibbonOriginManager.class);
                        bind(SessionContextFactory.class).to(ServletSessionContextFactory.class);
                        bind(SessionContextDecorator.class).to(SampleSessionContextDecorator.class);
                    }
                },

                new ZuulFiltersModule());
    }
}