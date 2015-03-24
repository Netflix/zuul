package com.netflix.zuul.lifecycle;

import com.netflix.zuul.context.RequestContext;
import com.netflix.zuul.metrics.OriginStats;
import com.netflix.zuul.metrics.Timing;
import io.netty.buffer.ByteBuf;
import io.reactivex.netty.protocol.http.client.HttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * User: michaels@netflix.com
 * Date: 2/20/15
 * Time: 2:19 PM
 */
public class Origin
{
    private static final Logger LOG = LoggerFactory.getLogger(Origin.class);

    private final String name;
    private final String vip;
    private final LoadBalancer loadBalancer;
    private final OriginStats stats;

    public Origin(String originName, String vip, LoadBalancer loadBalancer, OriginStats stats)
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
    }

    public String getVip() {
        return vip;
    }

    public LoadBalancer getLoadBalancer() {
        return loadBalancer;
    }

    public Observable<IngressResponse<ByteBuf>> request(EgressRequest<RequestContext> egressReq)
    {
        RequestContext ctx = egressReq.get();

        HttpClient<ByteBuf, ByteBuf> client = getLoadBalancer().getNextServer();

        final AtomicBoolean isSuccess = new AtomicBoolean(true);
        final Timing timing = ctx.getRequestProxyTiming();

        // TODO - Ideally want to move this into a "onStart" handler... but how?
        timing.start();
        stats.started();

        // Construct.
        return client.submit(egressReq.getHttpClientRequest())
                .map(resp -> {
                            // Copy the response headers and info into the RequestContext for use by post filters.
                            HttpResponseMessage zuulResp = (HttpResponseMessage) egressReq.get().getResponse();
                            if (resp.getStatus() != null) {
                                zuulResp.setStatus(resp.getStatus().code());
                            }
                            Headers zuulRespHeaders = zuulResp.getHeaders();
                            for (Map.Entry<String, String> entry : resp.getHeaders().entries()) {
                                zuulRespHeaders.add(entry.getKey(), entry.getValue());
                            }

                            ctx.getAttributes().put("origin_http_status", Integer.toString(resp.getStatus().code()));

                            // Convert the RxNetty response into a Zuul IngressResponse.
                            return (IngressResponse<ByteBuf>) IngressResponse.from(resp, ctx);
                        }
                )
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
}
