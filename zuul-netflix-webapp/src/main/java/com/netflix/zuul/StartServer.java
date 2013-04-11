package com.netflix.zuul;

/**
 * Created by IntelliJ IDEA.
 * User: mcohen
 * Date: 10/18/11
 * Time: 11:14 AM
 * To change this template use File | Settings | File Templates.
 */


import com.google.common.base.Throwables;
import com.google.inject.Injector;
import com.google.inject.servlet.GuiceServletContextListener;
import com.netflix.appinfo.ApplicationInfoManager;
import com.netflix.appinfo.CloudInstanceConfig;
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
import com.netflix.discovery.DefaultEurekaClientConfig;
import com.netflix.discovery.DiscoveryManager;
import com.netflix.karyon.server.KaryonServer;
import com.netflix.karyon.spi.Application;
import com.netflix.zuul.context.NFRequestContext;
import com.netflix.zuul.context.RequestContext;
import com.netflix.zuul.groovy.GroovyFilterFileManager;
import com.netflix.zuul.monitoring.CounterFactory;
import com.netflix.zuul.monitoring.TracerFactory;
import com.netflix.zuul.plugins.Counter;
import com.netflix.zuul.plugins.Tracer;
import com.netflix.zuul.scriptManager.ProxyFilterCheck;
import com.netflix.zuul.scriptManager.ZuulFilterDAO;
import com.netflix.zuul.scriptManager.ZuulFilterDAOCassandra;
import com.netflix.zuul.stats.AmazonInfoHolder;
import com.netflix.zuul.threads.CheckThreads;
import org.apache.commons.configuration.AbstractConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletContextEvent;
import java.io.IOException;
import java.util.Properties;

@edu.umd.cs.findbugs.annotations.SuppressWarnings(value = "Dm",
        justification = "Needs to shutdown in case of startup exception")

@Application
public class StartServer extends GuiceServletContextListener {
    private static final String SERVER_INIT_TRACER_TEMPLATE = "ZUUL::Initialization::StartServer";
    private static final String LIBRARY_INIT_TRACER_TEMPLATE = "ZUUL::Initialization::Library-%s";
    private static final String LIBRARY_INIT_COUNTER_TEMPLATE = "ZUUL::LibraryInitializationFailed-%s";
    private static final String MBEAN_REGISTER_TRACER_TEMPLATE = "ZUUL::Initialization::RegisterMBean-%s";

    private static final DynamicBooleanProperty checkThreadsEnabled = DynamicPropertyFactory.getInstance().getBooleanProperty("zuul.threadcheck.enabled", false);

    private static final DynamicBooleanProperty cassandraEnabled = DynamicPropertyFactory.getInstance().getBooleanProperty("zuul.cassandra.enabled", true);

    private static final DynamicBooleanProperty geoEnabled = DynamicPropertyFactory.getInstance().getBooleanProperty("zuul.geolocation.support.enabled", true);

    private static Logger LOG = LoggerFactory.getLogger(StartServer.class);


    private static GroovyFilterFileManager fileManager;


    public static String CONFIG_NAME = System.getenv("NETFLIX_APP");

    static {
        if (CONFIG_NAME == null) {
            CONFIG_NAME = "nfzuul";
        }
    }

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



        long realtime = System.currentTimeMillis();

        String stackName = System.getenv("NETFLIX_STACK");

        String sVipAddress = CONFIG_NAME + "-" + ((stackName != null) ? stackName : "none") + ".netflix.net";

        System.setProperty("netflix.appinfo.name", CONFIG_NAME);
        System.setProperty("netflix.appinfo.sid", "1");
        System.setProperty("netflix.appinfo.port", "7001");
        System.setProperty("netflix.appinfo.statusPageUrlPath", "/Status");
        System.setProperty("netflix.appinfo.version", "v1.0");


        System.setProperty("netflix.appinfo.vipAddress", sVipAddress + ":7001");
        System.setProperty("netflix.appinfo.statusPageUrlPath", "/Status");
        System.setProperty("netflix.appinfo.homePageUrlPath", "/admin/filterLoader.jsp");
        try {
            server.start();
        } catch (Exception e) {
            logger.error("Error while starting karyon.", e);
            throw Throwables.propagate(e);
        }
        try {
            initialize();
        } catch (Exception e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
        super.contextInitialized(sce);

    }


    @Override
    public void contextDestroyed(ServletContextEvent servletContextEvent) {
        try {
            server.close();
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
        initProxy();


        // now that properties are loaded let's start the JMX adapter before the system initialization stuff
        LOG.info("Initializing JMX Adapter");


        initCassandra();


        // loads and caches Amazon instance metadata
        AmazonInfoHolder.getInfo();

        initNIWS();

        if (checkThreadsEnabled.get()) CheckThreads.run();



        ApplicationInfoManager.getInstance().setInstanceStatus(
                InstanceInfo.InstanceStatus.UP);
    }

    private void initNIWS() throws ClientException {
        String stack = ConfigurationManager.getDeploymentContext().getDeploymentStack();

        if (stack != null && !stack.trim().isEmpty() && NIWSConfig.isAutodetectingBackendVips()) {
            NIWSConfig.setupDefaultNIWSConfig();
            ZuulApplicationInfo.setApplicationName(NIWSConfig.getApplicationName());
        } else {
            DynamicStringProperty DEFAULT_CLIENT =
                    DynamicPropertyFactory.getInstance().getStringProperty("zuul.niws.defaultClient", null);
            if (DEFAULT_CLIENT.get() != null) {
                ZuulApplicationInfo.setApplicationName(DEFAULT_CLIENT.get());
            } else {
                ZuulApplicationInfo.setApplicationName(stack);
            }
        }
        String clientPropertyList = DynamicPropertyFactory.getInstance().getStringProperty("zuul.niws.clientlist", "").get();
        String[] aClientList = clientPropertyList.split("\\|");
        for (String client : aClientList) {
            DefaultClientConfigImpl clientConfig = DefaultClientConfigImpl.getClientConfigWithDefaultValues(client);
            ClientFactory.registerClientFromProperties(client, clientConfig);
        }


    }


    //todo add core proxy path, abstract this out to base statrup class to pass in other directories.
    void initProxy() throws IOException, IllegalAccessException, InstantiationException {
        RequestContext.setContextClass(NFRequestContext.class);


        CounterFactory.initialize(new Counter());
        TracerFactory.initialize(new Tracer());

        LOG.info("starting groovy script file manager");

        final AbstractConfiguration config = ConfigurationManager.getConfigInstance();

        final String preProcessPath = config.getString("zuul.script.preprocess.path");
        final String postProcessPath = config.getString("zuul.script.postprocess.path");
        final String proxyPath = config.getString("zuul.script.proxy.path");
        final String customPath = config.getString("zuul.script.custom.path");


        if (customPath == null) {
            fileManager = new GroovyFilterFileManager(5, preProcessPath, postProcessPath, proxyPath);
        } else {
            fileManager = new GroovyFilterFileManager(5, preProcessPath, postProcessPath, proxyPath, customPath);
        }


        LOG.info("groovy script file manager started");
        ProxyFilterCheck.getInstance(); // start check thread
    }

    void initCassandra() throws Exception {
        if (cassandraEnabled.get()) {
            final AbstractConfiguration cassandraProperties = ConfigurationManager.getConfigInstance();

            /* defaults */
            cassandraProperties.setProperty("default.nfastyanax.readConsistency", DynamicPropertyFactory.getInstance().getStringProperty("zuul.cassandra.default.nfastyanax.readConsistency", "CL_ONE").get());
            cassandraProperties.setProperty("default.nfastyanax.writeConsistency", DynamicPropertyFactory.getInstance().getStringProperty("zuul.cassandra.default.nfastyanax.writeConsistency", "CL_ONE").get());
            cassandraProperties.setProperty("default.nfastyanax.socketTimeout", DynamicPropertyFactory.getInstance().getStringProperty("zuul.cassandra.default.nfastyanax.socketTimeout", "2000").get());
            cassandraProperties.setProperty("default.nfastyanax.maxConnsPerHost", DynamicPropertyFactory.getInstance().getStringProperty("zuul.cassandra.default.nfastyanax.maxConnsPerHost", "3").get());
            cassandraProperties.setProperty("default.nfastyanax.maxTimeoutWhenExhausted", DynamicPropertyFactory.getInstance().getStringProperty("zuul.cassandra.default.nfastyanax.maxTimeoutWhenExhausted", "2000").get());
            cassandraProperties.setProperty("default.nfastyanax.maxFailoverCount", DynamicPropertyFactory.getInstance().getStringProperty("zuul.cassandra.default.nfastyanax.maxFailoverCount", "1").get());
            cassandraProperties.setProperty("default.nfastyanax.failoverWaitTime", DynamicPropertyFactory.getInstance().getStringProperty("zuul.cassandra.default.nfastyanax.failoverWaitTime", "0").get());

            AstyanaxContext context = getZuulCassContext();
            ZuulFilterDAO dao = new ZuulFilterDAOCassandra(context);
            ProxyFilterCheck.start(dao);


        }
    }

    private static AstyanaxContext  zuulCassContext;

    public static AstyanaxContext getZuulCassContext() throws Exception{

        if(zuulCassContext != null) return zuulCassContext;
            try {
                AstyanaxContext<Keyspace> context = new AstyanaxContext.Builder()
                        .forKeyspace(DynamicPropertyFactory.getInstance().getStringProperty("zuul.cassandra.keyspace", "zuul_scripts").get())
                        .withAstyanaxConfiguration(new AstyanaxConfigurationImpl()
                                .setDiscoveryType(NodeDiscoveryType.RING_DESCRIBE)
                        )
                        .withConnectionPoolConfiguration(new ConnectionPoolConfigurationImpl("cass_connection_pool")
                                .setPort(DynamicPropertyFactory.getInstance().getIntProperty("zuul.cassandra.port", 7102).get())
                                .setMaxConnsPerHost(DynamicPropertyFactory.getInstance().getIntProperty("zuul.cassandra.maxConnectionsPerHost", 1).get())
                                .setSeeds(DynamicPropertyFactory.getInstance().getStringProperty("zuul.cassandra.host", "").get() + ":" +
                                        DynamicPropertyFactory.getInstance().getIntProperty("zuul.cassandra.port", 7102).get()
                                )
                        )
                        .withConnectionPoolMonitor(new CountingConnectionPoolMonitor())
                        .buildKeyspace(ThriftFamilyFactory.getInstance());

                context.start();
                zuulCassContext = context;
                return  context;
            } catch (Exception e) {
                logger.error("Exception occurred when initializing Cassandra keyspace: " + e);
                throw e;
            }

    }

}
