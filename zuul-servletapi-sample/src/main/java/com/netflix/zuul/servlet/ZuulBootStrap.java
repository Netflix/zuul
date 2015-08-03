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
package com.netflix.zuul.servlet;

import com.google.inject.AbstractModule;
import com.netflix.governator.guice.LifecycleInjectorBuilder;
import com.netflix.karyon.server.ServerBootstrap;
import com.netflix.zuul.BasicRequestCompleteHandler;
import com.netflix.zuul.RequestCompleteHandler;
import com.netflix.zuul.context.*;
import com.netflix.zuul.init.ZuulFiltersModule;
import com.netflix.zuul.origins.OriginManager;
import com.netflix.zuul.ribbon.RibbonOriginManager;
import com.netflix.zuul.ribbon.RibbonSessionCleaner;
import com.netflix.zuul.stats.BasicRequestMetricsPublisher;
import com.netflix.zuul.stats.RequestMetricsPublisher;
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
                        bind(SessionCleaner.class).to(RibbonSessionCleaner.class);
                        bind(RequestCompleteHandler.class).to(BasicRequestCompleteHandler.class);
                        bind(RequestMetricsPublisher.class).to(BasicRequestMetricsPublisher.class);
                    }
                },

                new ZuulFiltersModule());
    }
}