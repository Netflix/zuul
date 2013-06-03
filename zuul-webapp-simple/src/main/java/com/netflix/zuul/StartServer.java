package com.netflix.zuul;

import com.netflix.zuul.filters.FilterRegistry;
import com.netflix.zuul.groovy.GroovyFileFilter;
import com.netflix.zuul.monitoring.MonitoringHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

public class StartServer implements ServletContextListener {

    private static final Logger logger = LoggerFactory.getLogger(StartServer.class);

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        logger.info("starting server");

        MonitoringHelper.initMocks();

        // initGroovyFilterManager();

        initJavaFilters();
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        logger.info("stopping server");
    }

    private void initGroovyFilterManager() {
        final String scriptDirPath = System.getProperty("zuul.script.dir");
        try {
            FilterFileManager.setFilenameFilter(new GroovyFileFilter());
            FilterFileManager.init(5,
                    scriptDirPath,
                    scriptDirPath,
                    scriptDirPath);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void initJavaFilters() {
        final FilterRegistry r = FilterRegistry.instance();
        r.put("hi", new ZuulFilter() {
            @Override
            public int filterOrder() {
                return 0;
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
                return null;
            }
        });
    }

}
