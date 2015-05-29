package com.netflix.zuul.rxnetty;

import com.netflix.config.DynamicIntProperty;
import com.netflix.config.DynamicPropertyFactory;
import com.netflix.zuul.context.HttpRequestMessage;
import com.netflix.zuul.context.HttpResponseMessage;
import com.netflix.zuul.context.SessionContext;
import com.netflix.zuul.metrics.OriginStats;
import com.netflix.zuul.origins.LoadBalancer;
import com.netflix.zuul.origins.Origin;
import com.netflix.zuul.origins.ServerInfo;
import com.netflix.zuul.stats.Timing;
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
public class RxNettyOrigin implements Origin {
    private static final Logger LOG = LoggerFactory.getLogger(RxNettyOrigin.class);

    private final DynamicIntProperty ORIGIN_MAX_CONNS_PER_HOST = DynamicPropertyFactory.getInstance().getIntProperty("origin.max_conns_per_host", 250);
    private final DynamicIntProperty ORIGIN_READ_TIMEOUT = DynamicPropertyFactory.getInstance().getIntProperty("origin.read_timeout", 15000);


    private final String name;
    private final String vip;
    private final LoadBalancer loadBalancer;
    private final OriginStats stats;
    private CompositeHttpClient<ByteBuf, ByteBuf> client;

    public RxNettyOrigin(String originName, String vip, LoadBalancer loadBalancer, OriginStats stats, MetricEventsListenerFactory metricEventsListenerFactory)
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

    @Override
    public String getName() {
        return name;
    }

    public String getVip() {
        return vip;
    }

    public LoadBalancer getLoadBalancer() {
        return loadBalancer;
    }


    @Override
    public Observable<HttpResponseMessage> request(HttpRequestMessage requestMsg)
    {
        ServerInfo serverInfo = getLoadBalancer().getNextServer();

        HttpClientRequest<ByteBuf> clientRequest = RxNettyUtils.createHttpClientRequest(requestMsg);

        // Convert to rxnetty ServerInfo impl.
        RxClient.ServerInfo rxNettyServerInfo = new RxClient.ServerInfo(serverInfo.getHost(), serverInfo.getPort());

        // Start timing.
        SessionContext ctx = requestMsg.getContext();
        final Timing timing = ctx.getRequestProxyTiming();
        timing.start();
        if (stats != null)
            stats.started();

        final AtomicBoolean isSuccess = new AtomicBoolean(true);

        // Construct.
        Observable<HttpClientResponse<ByteBuf>> respObs = client.submit(rxNettyServerInfo, clientRequest)
        .doOnError(t -> {
                    isSuccess.set(false);

                    // Flag this as a proxy failure in the RequestContext. Error filter will then use this flag.
                    ctx.getAttributes().setShouldSendErrorResponse(true);

                    LOG.error(String.format("Error making http request to Origin. vip=%s, url=%s, target-host=%s",
                            this.vip, requestMsg.getPathAndQuery(), serverInfo.getHost()), t);
                }
        )
        .finallyDo(() -> {
            timing.end();
            if (stats != null)
                stats.completed(isSuccess.get(), timing.getDuration());
        });

        return bufferHttpClientResponse(requestMsg, respObs);
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

    protected Observable<HttpResponseMessage> bufferHttpClientResponse(HttpRequestMessage zuulReq,
                                                                           Observable<HttpClientResponse<ByteBuf>> clientResp)
    {
        return clientResp.map(resp -> {

            HttpResponseMessage zuulResp = RxNettyUtils.clientResponseToZuulResponse(zuulReq, resp);

            //PublishSubject<ByteBuf> cachedContent = PublishSubject.create();
            UnicastDisposableCachingSubject<ByteBuf> cachedContent = UnicastDisposableCachingSubject.create();

            // Only apply the filters once the request body has been fully read and buffered.
            Observable<ByteBuf> content = resp.getContent();

            // Subscribe to the response-content observable (retaining the ByteBufS first).
            content.map(ByteBuf::retain).subscribe(cachedContent);

            // Store this content ref on the zuul response object.
            zuulResp.setBodyStream(cachedContent);

            return zuulResp;
        });
    }
}
