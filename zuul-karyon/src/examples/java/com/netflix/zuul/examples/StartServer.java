package com.netflix.zuul.examples;

import com.google.inject.*;
import com.google.inject.spi.Message;
import com.google.inject.util.Providers;
import com.netflix.appinfo.ApplicationInfoManager;
import com.netflix.config.ConfigurationManager;
import com.netflix.config.DeploymentContext;
import com.netflix.config.DynamicPropertyFactory;
import com.netflix.config.DynamicStringProperty;
import com.netflix.governator.guice.BootstrapBinder;
import com.netflix.governator.guice.BootstrapModule;
import com.netflix.governator.guice.LifecycleInjector;
import com.netflix.governator.lifecycle.LifecycleManager;
import com.netflix.zuul.RequestCompleteHandler;
import com.netflix.zuul.ZuulRequestHandler;
import com.netflix.zuul.accesslog.AccessLogPublisher;
import com.netflix.zuul.context.FilterStateFactory;
import com.netflix.zuul.context.RequestContext;
import com.netflix.zuul.context.RequestContextDecorator;
import com.netflix.zuul.context.RequestContextFactory;
import com.netflix.zuul.filterstore.ClassPathFilterStore;
import com.netflix.zuul.filterstore.FilterStore;
import com.netflix.zuul.lifecycle.FilterProcessor;
import com.netflix.zuul.metrics.ZuulMetricsPublisherFactory;
import com.netflix.zuul.metrics.ZuulPlugins;
import com.netflix.zuul.servopublisher.ZuulServoMetricsPublisher;
import io.reactivex.netty.contexts.RxContexts;
import io.reactivex.netty.metrics.MetricEventsListenerFactory;
import io.reactivex.netty.protocol.http.server.HttpServer;
import io.reactivex.netty.protocol.http.server.HttpServerBuilder;
import io.reactivex.netty.protocol.http.server.RequestHandler;
import io.reactivex.netty.servo.ServoEventsListenerFactory;
import netflix.adminresources.resources.KaryonWebAdminModule;
import netflix.karyon.Karyon;
import netflix.karyon.ShutdownModule;
import netflix.karyon.eureka.KaryonEurekaModule;
import netflix.karyon.health.AlwaysHealthyHealthCheck;
import netflix.karyon.health.HealthCheckHandler;
import netflix.karyon.health.HealthCheckInvocationStrategy;
import netflix.karyon.health.SyncHealthCheckInvocationStrategy;
import netflix.karyon.servo.KaryonServoModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * User: Mike Smith
 * Date: 3/15/15
 * Time: 5:22 PM
 */
public class StartServer
{
    private final Logger log = LoggerFactory.getLogger(this.getClass());
    private final static String DEFAULT_APP_NAME = "zuul";


    public void init() throws Exception
    {
        System.out.println("Starting up Zuul...");

        int port = Integer.parseInt(System.getProperty("zuul.port.http", "7001"));
        int coreCount = Runtime.getRuntime().availableProcessors();
        int requestThreadCount = coreCount;
        System.out.printf("Using server port=%s, request-threads=%s\n", port, requestThreadCount);

        loadProperties();

        Injector injector = LifecycleInjector.bootstrap(RxNettyServerBackedServer.class,
                new ZuulBootstrapModule(),
                Karyon.toBootstrapModule(KaryonWebAdminModule.class)
        );

        RxNettyServerBackedServer server = injector.getInstance(RxNettyServerBackedServer.class);
        server.init(port, requestThreadCount);
        server.startAndWaitTillShutdown();
    }

    public void loadProperties()
    {
        DeploymentContext deploymentContext = ConfigurationManager.getDeploymentContext();
        if (deploymentContext.getApplicationId() == null) {
            deploymentContext.setApplicationId(DEFAULT_APP_NAME);
        }

        String infoStr = String.format("env=%s, region=%s, appId=%s, stack=%s",
                deploymentContext.getDeploymentEnvironment(), deploymentContext.getDeploymentRegion(),
                deploymentContext.getApplicationId(), deploymentContext.getDeploymentStack());

        System.out.printf("Using deployment context: %s\n", infoStr);

        try {
            ConfigurationManager.loadCascadedPropertiesFromResources(deploymentContext.getApplicationId());
        } catch (Exception e) {
            log.error(String.format("Failed to load properties for application id: %s and environment: %s.", infoStr), e);
            throw new RuntimeException(e);
        }

    }

    public static void main(String[] args)
    {
        try {
            new StartServer().init();
        }
        catch (CreationException e) {
            System.err.println("Injection error while starting StartServer. Messages follow:");
            for (Message msg : e.getErrorMessages()) {
                System.err.printf("ErrorMessage: %s, Causes: %s\n", msg.getMessage(), getErrorCauseMessages(e.getCause(), 4));
                if (msg.getCause() != null) {
                    msg.getCause().printStackTrace();
                }
            }
            e.printStackTrace();
            System.exit(-1);
        } catch (Exception e) {
            System.err.println("Error while starting StartServer. msg=" + e.getMessage());
            e.printStackTrace();
            System.exit(-1);
        }

        // In case we have non-daemon threads running
        System.exit(0);
    }

    private static String getErrorCauseMessages(Throwable error, int depth) {
        String fullMessage = "";
        Throwable cause = error;
        for (int i=0; i<depth; i++) {
            if (cause == null) {
                break;
            }
            fullMessage = fullMessage + String.format("cause%s=\"%s\", ", i, cause.getMessage());
            cause = cause.getCause();
        }
        return fullMessage;
    }


    static class ZuulModule extends AbstractModule
    {
        @Override
        protected void configure()
        {
            // Bootstrap from system properties.
            String filterPath = "com.netflix.zuul.filter.example";
            String[] filterLocations = System.getProperty("zuul.filters.locations",
                    filterPath + ".error," + filterPath + ".post," + filterPath + ".pre," + filterPath + ".route")
                    .split(",");

            System.out.println("Using filter locations: ");
            for (String location : filterLocations) {
                System.out.println("  " + location);
            }


            bind(ApplicationInfoManager.class).asEagerSingleton();

//            bind(AtlasPluginManager.class).asEagerSingleton();

            // Karyon deps.
            //bind(MetricEventsListenerFactory.class).to(ZuulServoEventsListenerFactory.class);
            bind(HealthCheckInvocationStrategy.class).to(SyncHealthCheckInvocationStrategy.class);
            bind(HealthCheckHandler.class).to(AlwaysHealthyHealthCheck.class);

            // Init the FilterStore.
            FilterStore<RequestContext> filterStore = new ClassPathFilterStore<>(filterLocations);
            filterStore.init();
            bind(new TypeLiteral<FilterStore<RequestContext>>()
            {
            }).toInstance(filterStore);

            // Configure the temp/hack load balancer i'm using for now.
//            bind(LoadBalancerFactory.class).to(NiwsLoadBalancer.Factory.class);
//            bind(OriginManager.class).asEagerSingleton();

            // Configure the FilterStateFactory that will create and initialise each requests' EdgeRequestContext.
            bind(new TypeLiteral<FilterStateFactory<RequestContext>>()
            {
            }).to(RequestContextFactory.class);
            bind(new TypeLiteral<FilterProcessor<RequestContext>>()
            {
            }).asEagerSingleton();

            bind(RequestContextDecorator.class).toProvider(Providers.of(null));
            bind(RequestCompleteHandler.class).toProvider(Providers.of(null));

            // Setup zuul metrics publishing.
            ZuulPlugins.getInstance().registerMetricsPublisher(ZuulServoMetricsPublisher.getInstance());
            ZuulMetricsPublisherFactory.createOrRetrieveGlobalPublisher();
            bind(MetricEventsListenerFactory.class).to(ServoEventsListenerFactory.class);

            // Inject access log and request-metrics implementations.
            bind(AccessLogPublisher.class).asEagerSingleton();
//            bind(RequestMetricsPublisher.class).to(EdgeRequestMetricsPublisher.class);
//            bind(RequestCompleteHandler.class).to(EdgeResponseCompleteHandler.class);


            // Specify to use Zuul's RequestHandler.
            bind(RequestHandler.class).to(ZuulRequestHandler.class);
        }
    }

    static class ZuulBootstrapModule implements BootstrapModule
    {
        @Override
        public void configure(BootstrapBinder binder)
        {
            binder.include(ShutdownModule.class,
                    KaryonServoModule.class,
                    KaryonEurekaModule.class,
                    ZuulModule.class);
        }
    }

    static class RxNettyServerBackedServer
    {
        private final static DynamicStringProperty NETTY_WIRE_LOGLEVEL = DynamicPropertyFactory.getInstance()
                .getStringProperty("zuul.netty.wire.loglevel", "ERROR");

        private final Logger log = LoggerFactory.getLogger(this.getClass());

        private LifecycleManager lifecycleManager;
        private RequestHandler requestHandler;
        private HttpServer rxNettyServer;
        private MetricEventsListenerFactory metricEventsListenerFactory;

        // TEMP
        //private AtlasPluginManager atlasPluginManager;

        @Inject
        public RxNettyServerBackedServer(RequestHandler requestHandler, LifecycleManager lifecycleManager,
                                         MetricEventsListenerFactory metricEventsListenerFactory)
        {
            this.lifecycleManager = lifecycleManager;
            this.requestHandler = requestHandler;
            this.metricEventsListenerFactory = metricEventsListenerFactory;

//            // TODO - this is just temp to force/verify that AtlasPluginManager has been constructed AND initialized because the @PostConstruct init method
//            // does not seem to get called.
//            this.atlasPluginManager = atlasPluginManager;
//            this.atlasPluginManager.init();
        }

        public void init(int port, int requestThreads)
        {
            //LogLevel wireLogLevel = NETTY_WIRE_LOGLEVEL.get() == null ? null : LogLevel.valueOf(NETTY_WIRE_LOGLEVEL.get());

            HttpServerBuilder serverBuilder = RxContexts.newHttpServerBuilder(port, requestHandler, RxContexts.DEFAULT_CORRELATOR);

            // TODO - Review config we're using here. This is just a first pass.
            rxNettyServer = (HttpServer) serverBuilder
                    .withRequestProcessingThreads(requestThreads)
                    .withMetricEventsListenerFactory(metricEventsListenerFactory)
                            //.enableWireLogging(wireLogLevel)
                    .build();
        }

        public final void start()
        {
            this.startLifecycleManager();
            rxNettyServer.start();
        }

        public void startAndWaitTillShutdown()
        {
            this.start();
            this.waitTillShutdown();
        }

        protected void startLifecycleManager()
        {
            try {
                this.lifecycleManager.start();
            } catch (Exception var2) {
                throw new RuntimeException(var2);
            }
        }

        public void shutdown()
        {
            if(this.lifecycleManager != null) {
                this.lifecycleManager.close();
            }

            try {
                this.rxNettyServer.shutdown();
            } catch (InterruptedException var2) {
                log.error("Interrupted while shutdown.", var2);
                Thread.interrupted();
                throw new RuntimeException(var2);
            }
        }

        public void waitTillShutdown() {
            try {
                this.rxNettyServer.waitTillShutdown();
            } catch (InterruptedException var2) {
                log.error("Interrupted while waiting for shutdown.", var2);
                Thread.interrupted();
                throw new RuntimeException(var2);
            }
        }
    }
}
