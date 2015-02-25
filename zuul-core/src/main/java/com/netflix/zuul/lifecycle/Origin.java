package com.netflix.zuul.lifecycle;

import io.netty.buffer.ByteBuf;
import io.reactivex.netty.protocol.http.client.HttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;

/**
 * User: michaels@netflix.com
 * Date: 2/20/15
 * Time: 2:19 PM
 */
public class Origin
{
    private static final Logger LOG = LoggerFactory.getLogger(Origin.class);

    private final String vip;
    private final LoadBalancer loadBalancer;

    public Origin(String vip, LoadBalancer loadBalancer)
    {
        if (vip == null) {
            throw new IllegalArgumentException("Requires a non-null VIP.");
        }
        this.vip = vip;
        this.loadBalancer = loadBalancer;
    }

    public String getVip() {
        return vip;
    }

    public LoadBalancer getLoadBalancer() {
        return loadBalancer;
    }

    public Observable<IngressResponse<ByteBuf>> request(EgressRequest<EdgeRequestContext> egressReq)
    {
        EdgeRequestContext ctx = egressReq.get();

        HttpClient<ByteBuf, ByteBuf> client = getLoadBalancer().getNextServer();

        return client.submit(egressReq.getHttpClientRequest())
                .map(resp ->
                                (IngressResponse<ByteBuf>) IngressResponse.from(resp, ctx)
                )
                .doOnError(t ->
                                // TODO - Integrate ocelli for retry logic here.

                                // TODO - Check what ProxyFilter does for failures like this.

                                LOG.error("Error making http request.", t)
                );
    }
}
