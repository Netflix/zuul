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
import com.netflix.astyanax.AstyanaxContext;
import com.netflix.astyanax.Keyspace;
import com.netflix.astyanax.connectionpool.NodeDiscoveryType;
import com.netflix.astyanax.connectionpool.impl.ConnectionPoolConfigurationImpl;
import com.netflix.astyanax.connectionpool.impl.CountingConnectionPoolMonitor;
import com.netflix.astyanax.impl.AstyanaxConfigurationImpl;
import com.netflix.astyanax.thrift.ThriftFamilyFactory;
import com.netflix.client.ClientException;
import com.netflix.client.ClientFactory;
import com.netflix.client.config.DefaultClientConfigImpl;
import com.netflix.config.ConfigurationManager;
import com.netflix.config.DynamicBooleanProperty;
import com.netflix.config.DynamicPropertyFactory;
import com.netflix.config.DynamicStringProperty;
import com.netflix.karyon.server.KaryonServer;
import com.netflix.karyon.spi.Application;
import com.netflix.servo.util.ThreadCpuStats;
import com.netflix.zuul.context.NFRequestContext;
import com.netflix.zuul.context.RequestContext;
import com.netflix.zuul.dependency.ribbon.RibbonConfig;
import com.netflix.zuul.groovy.GroovyCompiler;
import com.netflix.zuul.groovy.GroovyFileFilter;
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
import org.apache.commons.configuration.AbstractConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletContextEvent;
import java.io.IOException;

import static com.netflix.zuul.constants.ZuulConstants.*;

@edu.umd.cs.findbugs.annotations.SuppressWarnings(value = "Dm",
        justification = "Needs to shutdown in case of startup exception")

@Application
public class StartServer extends GuiceServletContextListener {

    private static final DynamicBooleanProperty cassandraEnabled = DynamicPropertyFactory.getInstance().getBooleanProperty(ZUUL_CASSANDRA_ENABLED, true);
    private static Logger LOG = LoggerFactory.getLogger(StartServer.class);
    private static Keyspace zuulCassKeyspace;
    protected static final Logger logger = LoggerFactory.getLogger(StartServer.class);
    private final KaryonServer server;


    public StartServer() {

        System.setProperty(DynamicPropertyFactory.ENABLE_JMX, "true");
        server = new KaryonServer();
        server.initialize();

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
            logger.error("Error while starting karyon.", e);
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
            FilterFileManager.shutdown();
        } catch (IOException e) {
            logger.error("Error while stopping karyon.", e);
            throw Throwables.propagate(e);
        }
    }

    @Override
    protected Injector getInjector() {
        return server.initialize();
    }

    protected void initialize() throws Exception {

        initPlugins();

        initZuul();

        initCassandra();

        // loads and caches Amazon instance metadata
        AmazonInfoHolder.getInfo();

        initNIWS();

        ApplicationInfoManager.getInstance().setInstanceStatus(
                InstanceInfo.InstanceStatus.UP);
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

    private void initNIWS() throws ClientException {
        String stack = ConfigurationManager.getDeploymentContext().getDeploymentStack();

        if (stack != null && !stack.trim().isEmpty() && RibbonConfig.isAutodetectingBackendVips()) {
            RibbonConfig.setupDefaultRibbonConfig();
            ZuulApplicationInfo.setApplicationName(RibbonConfig.getApplicationName());
        } else {
            DynamicStringProperty DEFAULT_CLIENT =
                    DynamicPropertyFactory.getInstance().getStringProperty(ZUUL_NIWS_DEFAULTCLIENT, null);
            if (DEFAULT_CLIENT.get() != null) {
                ZuulApplicationInfo.setApplicationName(DEFAULT_CLIENT.get());
            } else {
                ZuulApplicationInfo.setApplicationName(stack);
            }
        }
        String clientPropertyList = DynamicPropertyFactory.getInstance().getStringProperty(ZUUL_NIWS_CLIENTLIST, "").get();
        String[] aClientList = clientPropertyList.split("\\|");
        String namespace = DynamicPropertyFactory.getInstance().getStringProperty(ZUUL_RIBBON_NAMESPACE, "ribbon").get();
        for (String client : aClientList) {
            DefaultClientConfigImpl clientConfig = DefaultClientConfigImpl.getClientConfigWithDefaultValues(client, namespace);
            ClientFactory.registerClientFromProperties(client, clientConfig);
        }


    }


    void initZuul() throws Exception, IllegalAccessException, InstantiationException {

        RequestContext.setContextClass(NFRequestContext.class);


        CounterFactory.initialize(new Counter());
        TracerFactory.initialize(new Tracer());

        LOG.info("Starting Groovy Filter file manager");

        final AbstractConfiguration config = ConfigurationManager.getConfigInstance();

        final String preFiltersPath = config.getString(ZUUL_FILTER_PRE_PATH);
        final String postFiltersPath = config.getString(ZUUL_FILTER_POST_PATH);
        final String routingFiltersPath = config.getString(ZUUL_FILTER_ROUTING_PATH);
        final String customPath = config.getString(ZUUL_FILTER_CUSTOM_PATH);

        FilterLoader.getInstance().setCompiler(new GroovyCompiler());
        FilterFileManager.setFilenameFilter(new GroovyFileFilter());
        if (customPath == null) {
            FilterFileManager.init(5, preFiltersPath, postFiltersPath, routingFiltersPath);
        } else {
            FilterFileManager.init(5, preFiltersPath, postFiltersPath, routingFiltersPath, customPath);
        }

        LOG.info("Groovy Filter file manager started");

    }

    void initCassandra() throws Exception {
        if (cassandraEnabled.get()) {
            final AbstractConfiguration cassandraProperties = ConfigurationManager.getConfigInstance();

            /* defaults */
            cassandraProperties.setProperty(DEFAULT_NFASTYANAX_READCONSISTENCY, DynamicPropertyFactory.getInstance().getStringProperty(DEFAULT_NFASTYANAX_READCONSISTENCY, "CL_ONE").get());
            cassandraProperties.setProperty(DEFAULT_NFASTYANAX_WRITECONSISTENCY, DynamicPropertyFactory.getInstance().getStringProperty("zuul.cassandra.default.nfastyanax.writeConsistency", "CL_ONE").get());
            cassandraProperties.setProperty(DEFAULT_NFASTYANAX_SOCKETTIMEOUT, DynamicPropertyFactory.getInstance().getStringProperty("zuul.cassandra.default.nfastyanax.socketTimeout", "2000").get());
            cassandraProperties.setProperty(DEFAULT_NFASTYANAX_MAXCONNSPERHOST, DynamicPropertyFactory.getInstance().getStringProperty("zuul.cassandra.default.nfastyanax.maxConnsPerHost", "3").get());
            cassandraProperties.setProperty(DEFAULT_NFASTYANAX_MAXTIMEOUTWHENEXHAUSTED, DynamicPropertyFactory.getInstance().getStringProperty("zuul.cassandra.default.nfastyanax.maxTimeoutWhenExhausted", "2000").get());
            cassandraProperties.setProperty(DEFAULT_NFASTYANAX_MAXFAILOVERCOUNT, DynamicPropertyFactory.getInstance().getStringProperty("zuul.cassandra.default.nfastyanax.maxFailoverCount", "1").get());
            cassandraProperties.setProperty(DEFAULT_NFASTYANAX_FAILOVERWAITTIME, DynamicPropertyFactory.getInstance().getStringProperty("zuul.cassandra.default.nfastyanax.failoverWaitTime", "0").get());

            LOG.info("Getting AstyanaxContext");
            Keyspace keyspace = getZuulCassKeyspace();
            LOG.info("Initializing Cassandra ZuulFilterDAO");
            ZuulFilterDAO dao = new ZuulFilterDAOCassandra(keyspace);
            LOG.info("Starting ZuulFilter Poller");
            ZuulFilterPoller.start(dao);


        }
    }

    /**
     * @return the Keyspace for the Zuul Cassandra cluster which stores filters
     * @throws Exception
     */
    public static Keyspace getZuulCassKeyspace() throws Exception {

        if (zuulCassKeyspace != null) return zuulCassKeyspace;
        try {
            AstyanaxContext<Keyspace> context = new AstyanaxContext.Builder()
                    .forKeyspace(DynamicPropertyFactory.getInstance().getStringProperty(ZUUL_CASSANDRA_KEYSPACE, "zuul_scripts").get())
                    .withAstyanaxConfiguration(new AstyanaxConfigurationImpl()
                            .setDiscoveryType(NodeDiscoveryType.RING_DESCRIBE)
                    )
                    .withConnectionPoolConfiguration(new ConnectionPoolConfigurationImpl("cass_connection_pool")
                            .setPort(DynamicPropertyFactory.getInstance().getIntProperty(ZUUL_CASSANDRA_PORT, 7102).get())
                            .setMaxConnsPerHost(DynamicPropertyFactory.getInstance().getIntProperty(ZUUL_CASSANDRA_MAXCONNECTIONSPERHOST, 1).get())
                            .setSeeds(DynamicPropertyFactory.getInstance().getStringProperty(ZUUL_CASSANDRA_HOST, "").get() + ":" +
                                    DynamicPropertyFactory.getInstance().getIntProperty(ZUUL_CASSANDRA_PORT, 7102).get()
                            )
                    )
                    .withConnectionPoolMonitor(new CountingConnectionPoolMonitor())
                    .buildKeyspace(ThriftFamilyFactory.getInstance());

            context.start();
            zuulCassKeyspace = context.getEntity();
            return zuulCassKeyspace;
        } catch (Exception e) {
            logger.error("Exception occurred when initializing Cassandra keyspace: " + e);
            throw e;
        }

    }

}
