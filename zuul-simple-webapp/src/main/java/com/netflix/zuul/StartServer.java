/*
 * Copyright 2013 Netflix, Inc.
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

import com.netflix.zuul.context.RequestContext;
import com.netflix.zuul.filters.FilterRegistry;
import com.netflix.zuul.groovy.GroovyCompiler;
import com.netflix.zuul.groovy.GroovyFileFilter;
import com.netflix.zuul.monitoring.MonitoringHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import java.io.File;

public class StartServer implements ServletContextListener {

    private static final Logger logger = LoggerFactory.getLogger(StartServer.class);

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        logger.info("starting server");

        // mocks monitoring infrastructure as we don't need it for this simple app
        MonitoringHelper.initMocks();

        // initializes groovy filesystem poller
        initGroovyFilterManager();

        // initializes a few java filter examples
        initJavaFilters();
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        logger.info("stopping server");
    }

    private void initGroovyFilterManager() {
        FilterLoader.getInstance().setCompiler(new GroovyCompiler());

        String scriptRoot = System.getProperty("zuul.filter.root", "");
        if (scriptRoot.length() > 0) scriptRoot = scriptRoot + File.separator;
        try {
            FilterFileManager.setFilenameFilter(new GroovyFileFilter());
            FilterFileManager.init(5, scriptRoot + "pre", scriptRoot + "route", scriptRoot + "post");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void initJavaFilters() {
        final FilterRegistry r = FilterRegistry.instance();

        r.put("javaPreFilter", new ZuulFilter() {
            @Override
            public int filterOrder() {
                return 50000;
            }

            @Override
            public String filterType() {
                return "pre";
            }

            @Override
            public boolean shouldFilter() {
                return true;
            }

            @Override
            public Object run() {
                logger.debug("running javaPreFilter");
                RequestContext.getCurrentContext().set("javaPreFilter-ran", true);
                return null;
            }
        });

        r.put("javaPostFilter", new ZuulFilter() {
            @Override
            public int filterOrder() {
                return 50000;
            }

            @Override
            public String filterType() {
                return "post";
            }

            @Override
            public boolean shouldFilter() {
                return true;
            }

            @Override
            public Object run() {
                logger.debug("running javaPostFilter");
                RequestContext.getCurrentContext().set("javaPostFilter-ran", true);
                return null;
            }
        });
    }

}
