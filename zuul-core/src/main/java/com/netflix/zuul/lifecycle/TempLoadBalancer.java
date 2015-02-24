package com.netflix.zuul.lifecycle;

import com.netflix.appinfo.InstanceInfo;
import com.netflix.config.FastProperty;
import com.netflix.discovery.DiscoveryManager;
import io.netty.buffer.ByteBuf;
import io.reactivex.netty.RxNetty;
import io.reactivex.netty.protocol.http.client.HttpClient;
import io.reactivex.netty.protocol.http.client.HttpClientPipelineConfigurator;
import io.reactivex.netty.servo.ServoEventsListenerFactory;
import io.reactivex.netty.servo.http.HttpClientListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * User: michaels@netflix.com
 * Date: 2/19/15
 * Time: 6:15 PM
 */
public class TempLoadBalancer
{
    private final Logger LOG = LoggerFactory.getLogger(this.getClass());

    /* Set this to false if running locally (ie. outside of AWS) */
    private final FastProperty.BooleanProperty NIWS_USE_IP = new FastProperty.BooleanProperty("niws.client.EnableUseIPInURI", true);

    private final FastProperty.IntProperty ORIGIN_MAX_CONNS_PER_HOST = new FastProperty.IntProperty("origin.max_conns_per_host", 250);
    private final FastProperty.IntProperty ORIGIN_READ_TIMEOUT = new FastProperty.IntProperty("origin.read_timeout", 15000);
    private final FastProperty.IntProperty EUREKA_POLL_DELAY = new FastProperty.IntProperty("eureka.poll.delay", 30);


    private String vip;

    private AtomicReference<List<HttpClient<ByteBuf, ByteBuf>>> clientsToUse = new AtomicReference<>();
    private int clientSize;
    private AtomicInteger currentServerIndexPointer;
    private HttpClientListener listener = null;
    private ScheduledExecutorService service = Executors.newScheduledThreadPool(1);

    public TempLoadBalancer(String vip) {
        this.vip = vip;
    }

    public HttpClient<ByteBuf, ByteBuf> getNextServer()
    {
        int currentServerIndex = Math.abs(currentServerIndexPointer.incrementAndGet() % clientSize);
        return clientsToUse.get().get(currentServerIndex);
    }

    public HttpClientListener getListener() {
        return listener;
    }

    public void init()
    {
        // Update the server list now first.
        Runnable initializer = new Initializer();
        initializer.run();

        // And then setup a background thread for updating it periodically.
        int delay = EUREKA_POLL_DELAY.get();
        service.scheduleWithFixedDelay(initializer, delay, delay, TimeUnit.SECONDS);
    }

    public void shutdown()
    {
        service.shutdown();
    }

    class Initializer implements Runnable
    {
        @Override
        public void run() {
            LOG.warn("refreshing the backend client list.");
            try {
                ServoEventsListenerFactory factory = new ServoEventsListenerFactory("api-backend-client", "server-factory-should-not-be-invoked");
                List<HttpClient<ByteBuf, ByteBuf>> clients = new ArrayList<>();

                List<InstanceInfo> instances = DiscoveryManager.getInstance().getDiscoveryClient().getInstancesByVipAddress(vip, false);

                for (InstanceInfo instance : instances) {
                    if (instance.getStatus() != InstanceInfo.InstanceStatus.UP) {
                        continue;
                    }

                    String hostname = NIWS_USE_IP.get() ? instance.getIPAddr() : instance.getHostName();

                    HttpClient<ByteBuf, ByteBuf> client = RxNetty.<ByteBuf, ByteBuf>newHttpClientBuilder(
                            hostname,
                            instance.getPort())
                            .pipelineConfigurator(new HttpClientPipelineConfigurator<>())
                            .withMaxConnections(ORIGIN_MAX_CONNS_PER_HOST.get())
                            .config(
                                    new HttpClient.HttpClientConfig.Builder()
                                            .setFollowRedirect(false)
                                            .readTimeout(ORIGIN_READ_TIMEOUT.get(), TimeUnit.MILLISECONDS)
                                            .build())
                            .build();
                    if (null == listener) {
                        listener = factory.forHttpClient(client);
                    }
                    client.subscribe(listener);
                    clients.add(client);
                }
                clientSize = clients.size();
                currentServerIndexPointer = new AtomicInteger();
                clientsToUse.set(clients);
            }
            catch (Exception e) {
                LOG.error("Error polling Eureka for server list. vip=" + String.valueOf(vip), e);
            }
        }
    }
}
