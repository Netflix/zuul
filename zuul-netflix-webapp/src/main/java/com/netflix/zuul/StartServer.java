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

/**
 * @author Mikey Cohen
 * Date: 10/18/11
 * Time: 11:14 AM
 */

import com.google.common.base.Throwables;
import com.google.inject.Injector;
import com.google.inject.servlet.GuiceServletContextListener;
import com.netflix.appinfo.ApplicationInfoManager;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.astyanax.Keyspace;
import com.netflix.config.DynamicBooleanProperty;
import com.netflix.config.DynamicPropertyFactory;
import com.netflix.karyon.server.KaryonServer;
import com.netflix.karyon.spi.Application;
import com.netflix.servo.util.ThreadCpuStats;
import com.netflix.zuul.dependency.cassandra.CassandraHelper;
import com.netflix.zuul.dependency.ribbon.RibbonConfig;
import com.netflix.zuul.monitoring.CounterFactory;
import com.netflix.zuul.monitoring.TracerFactory;
import com.netflix.zuul.plugins.Counter;
import com.netflix.zuul.plugins.MetricPoller;
import com.netflix.zuul.plugins.ServoMonitor;
import com.netflix.zuul.plugins.Tracer;
import com.netflix.zuul.scriptManager.ZuulFilterDAO;
import com.netflix.zuul.scriptManager.ZuulFilterDAOCassandra;
import com.netflix.zuul.scriptManager.ZuulFilterPoller;
import com.netflix.zuul.stats.AmazonInfoHolder;
import com.netflix.zuul.stats.monitoring.MonitorRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletContextEvent;
import java.io.IOException;

import static com.netflix.zuul.constants.ZuulConstants.ZUUL_CASSANDRA_ENABLED;

@Application
public class StartServer extends GuiceServletContextListener {

    private static final DynamicBooleanProperty cassandraEnabled = DynamicPropertyFactory.getInstance().getBooleanProperty(ZUUL_CASSANDRA_ENABLED, true);
    private static Logger LOG = LoggerFactory.getLogger(StartServer.class);
    private final KaryonServer server;

    public StartServer()
    {
        try {
            System.setProperty(DynamicPropertyFactory.ENABLE_JMX, "true");
            server = new KaryonServer();
            server.initialize();
        }
        catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Error initialising StartServer.", e);
        }
    }

    /**
     * Overridden solely so we can tell how much time is being spent in overall initialization. Without
     * overriding we can't tell how much time was spent in BaseServer doing its own initialization.
     *
     * @param sce
     */
    @Override
    public void contextInitialized(ServletContextEvent sce) {
        try {
            server.start();
        } catch (Exception e) {
            LOG.error("Error while starting karyon.", e);
            throw Throwables.propagate(e);
        }
        try {
            initialize();
        } catch (Exception e) {
            e.printStackTrace();
        }
        super.contextInitialized(sce);
    }


    @Override
    public void contextDestroyed(ServletContextEvent servletContextEvent) {
        try {
            server.close();
        } catch (IOException e) {
            LOG.error("Error while stopping karyon.", e);
            throw Throwables.propagate(e);
        }
    }

    @Override
    protected Injector getInjector() {
        return server.initialize();
    }

    protected void initialize() throws Exception {

        CounterFactory.initialize(new Counter());
        TracerFactory.initialize(new Tracer());

        AmazonInfoHolder.getInfo();
        initPlugins();
        initCassandra();

        ApplicationInfoManager.getInstance().setInstanceStatus(InstanceInfo.InstanceStatus.UP);
    }

    private void initPlugins() {
        LOG.info("Registering Servo Monitor");
        MonitorRegistry.getInstance().setPublisher(new ServoMonitor());

        LOG.info("Starting Poller");
        MetricPoller.startPoller();


        LOG.info("Registering Servo Tracer");
        TracerFactory.initialize(new Tracer());

        LOG.info("Registering Servo Counter");
        CounterFactory.initialize(new Counter());

        LOG.info("Starting CPU stats");
        final ThreadCpuStats stats = ThreadCpuStats.getInstance();
        stats.start();
    }

    void initCassandra() throws Exception {
        if (cassandraEnabled.get()) {
            LOG.info("Getting AstyanaxContext");
            Keyspace keyspace = CassandraHelper.getInstance().getZuulCassKeyspace();
            LOG.info("Initializing Cassandra ZuulFilterDAO");
            ZuulFilterDAO dao = new ZuulFilterDAOCassandra(keyspace);
            LOG.info("Starting ZuulFilter Poller");
            ZuulFilterPoller.start(dao);
        }
    }
}
