package com.netflix.zuul.lifecycle;

import com.netflix.zuul.context.RequestContext;
import io.netty.buffer.ByteBuf;
import io.reactivex.netty.protocol.http.client.HttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;

import java.util.Map;

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

    public Observable<IngressResponse<ByteBuf>> request(EgressRequest<RequestContext> egressReq)
    {
        RequestContext ctx = egressReq.get();

        HttpClient<ByteBuf, ByteBuf> client = getLoadBalancer().getNextServer();

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

                            // Convert the RxNetty response into a Zuul IngressResponse.
                            return (IngressResponse<ByteBuf>) IngressResponse.from(resp, ctx);
                        }
                )
                .doOnError(t ->
                                // TODO - Integrate ocelli for retry logic here.

                                // TODO - Check what ProxyFilter does for failures like this.

                                LOG.error("Error making http request.", t)
                );
    }
}
