package com.netflix.zuul.lifecycle;

import com.netflix.config.DynamicIntProperty;
import com.netflix.config.DynamicPropertyFactory;
import com.netflix.zuul.context.RequestContext;
import com.netflix.zuul.metrics.OriginStats;
import com.netflix.zuul.metrics.Timing;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.reactivex.netty.client.RxClient;
import io.reactivex.netty.metrics.MetricEventsListener;
import io.reactivex.netty.metrics.MetricEventsListenerFactory;
import io.reactivex.netty.protocol.http.client.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * User: michaels@netflix.com
 * Date: 2/20/15
 * Time: 2:19 PM
 */
public class Origin
{
    private static final Logger LOG = LoggerFactory.getLogger(Origin.class);

    private final DynamicIntProperty ORIGIN_MAX_CONNS_PER_HOST = DynamicPropertyFactory.getInstance().getIntProperty("origin.max_conns_per_host", 250);
    private final DynamicIntProperty ORIGIN_READ_TIMEOUT = DynamicPropertyFactory.getInstance().getIntProperty("origin.read_timeout", 15000);


    private final String name;
    private final String vip;
    private final LoadBalancer loadBalancer;
    private final OriginStats stats;
    private CompositeHttpClient<ByteBuf, ByteBuf> client;

    public Origin(String originName, String vip, LoadBalancer loadBalancer, OriginStats stats, MetricEventsListenerFactory metricEventsListenerFactory)
    {
        if (originName == null) {
            throw new IllegalArgumentException("Requires a non-null originName.");
        }
        if (vip == null) {
            throw new IllegalArgumentException("Requires a non-null VIP.");
        }

        this.name = originName;
        this.vip = vip;
        this.loadBalancer = loadBalancer;
        this.stats = stats;
        this.client = createCompositeHttpClient();

        // Configure rxnetty httpclient metrics for this client.
        MetricEventsListener httpClientListener = metricEventsListenerFactory.forHttpClient(client);
        this.client.subscribe(httpClientListener);
    }

    public String getVip() {
        return vip;
    }

    public LoadBalancer getLoadBalancer() {
        return loadBalancer;
    }

    public Observable<RequestContext> request(RequestContext ctx)
    {
        RxClient.ServerInfo serverInfo = getLoadBalancer().getNextServer();

        final AtomicBoolean isSuccess = new AtomicBoolean(true);
        final Timing timing = ctx.getRequestProxyTiming();

        // TODO - Ideally want to move this into a "onStart" handler... but how?
        timing.start();
        if (stats != null)
            stats.started();

        HttpClientRequest<ByteBuf> clientRequest = RxNettyUtils.createHttpClientRequest(ctx);

        // Construct.
        Observable<HttpClientResponse<ByteBuf>> respObs = client.submit(serverInfo, clientRequest);

        return RxNettyUtils.bufferHttpClientResponse(respObs, ctx)
                .doOnError(t -> {
                            isSuccess.set(false);

                            // TODO - Integrate ocelli for retry logic here.

                            // TODO - Add NIWS Attempts data to context.

                            // Flag this as a proxy failure in the RequestContext. Error filter will then use this flag.
                            ctx.getAttributes().put("error", t);

                            LOG.error("Error making http request.", t);
                    }
            ).finallyDo(() -> {
                timing.end();
                if (stats != null)
                    stats.completed(isSuccess.get(), timing.getDuration());
            });
    }

    private CompositeHttpClient<ByteBuf, ByteBuf> createCompositeHttpClient()
    {
        // Override the max HTTP header size.
        int maxHeaderSize = 20000;
        HttpClientPipelineConfigurator<ByteBuf, ByteBuf> clientPipelineConfigurator = new HttpClientPipelineConfigurator<>(
                HttpClientPipelineConfigurator.MAX_INITIAL_LINE_LENGTH_DEFAULT,
                maxHeaderSize,
                HttpClientPipelineConfigurator.MAX_CHUNK_SIZE_DEFAULT,
                // don't validate headers.
                false);

        CompositeHttpClient<ByteBuf, ByteBuf> client = new CompositeHttpClientBuilder<ByteBuf, ByteBuf>(new Bootstrap())
                .withMaxConnections(ORIGIN_MAX_CONNS_PER_HOST.get())
                .pipelineConfigurator(clientPipelineConfigurator)
                .config(new HttpClient.HttpClientConfig.Builder()
                                .setFollowRedirect(false)
                                .readTimeout(ORIGIN_READ_TIMEOUT.get(), TimeUnit.MILLISECONDS)
                                .build()
                )
                .build();

        return client;
    }
}
