package com.netflix.zuul.lifecycle;

import io.netty.channel.Channel;
import io.reactivex.netty.protocol.http.server.HttpServerRequest;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.List;
import java.util.Map;

/**
 * A FilterStateFactory implementation that creates new EdgeRequestContext instances for each request,
 * and adds various manager and helper instances into it.
 *
 * This is mainly useful as not able to use DI on the groovy filters, so we have DI inject the instances on this class
 * and then pass them to filters in the EdgeRequestContext.getHelpers().
 *
 * User: michaels@netflix.com
 * Date: 2/23/15
 * Time: 1:41 PM
 */
public class EdgeZuulFilterStateFactory implements FilterStateFactory<EdgeRequestContext>
{
    //@Inject
    private OriginManager originManager;

    public EdgeZuulFilterStateFactory(OriginManager originManager) {
        this.originManager = originManager;
    }

    @Override
    public EdgeRequestContext create(HttpServerRequest httpServerRequest)
    {
        // Get the client IP (ignore XFF headers at this point, as that can be app specific).
        String clientIp = getIpAddress(httpServerRequest.getNettyChannel());

        // Setup the req/resp message objects.
        HttpRequestMessage httpReqMsg = new HttpRequestMessage(
                httpServerRequest.getHttpMethod().name().toLowerCase(),
                httpServerRequest.getUri(),
                copyQueryParams(httpServerRequest),
                copyHeaders(httpServerRequest),
                clientIp
        );
        HttpResponseMessage httpRespMsg = new HttpResponseMessage(httpReqMsg, 500);

        EdgeRequestContext ctx = new EdgeRequestContext(httpReqMsg, httpRespMsg);
        ctx.internal_setHttpServerRequest(httpServerRequest);

        // Inject any helper objects into the context for use of filters.
        ctx.getHelpers().put("origin_manager", originManager);

        return ctx;
    }

    private Headers copyHeaders(HttpServerRequest httpServerRequest)
    {
        Headers headers = new Headers();
        for (Map.Entry<String, String> entry : httpServerRequest.getHeaders().entries()) {
            headers.add(entry.getKey(), entry.getValue());
        }
        return headers;
    }

    private HttpQueryParams copyQueryParams(HttpServerRequest httpServerRequest)
    {
        HttpQueryParams queryParams = new HttpQueryParams();
        Map<String, List<String>> serverQueryParams = httpServerRequest.getQueryParameters();
        for (String key : serverQueryParams.keySet()) {
            for (String value : serverQueryParams.get(key)) {
                queryParams.add(key, value);
            }
        }
        return queryParams;
    }

    private static String getIpAddress(Channel channel) {
        if (null == channel) {
            return "";
        }

        SocketAddress localSocketAddress = channel.localAddress();
        if (null != localSocketAddress && InetSocketAddress.class.isAssignableFrom(localSocketAddress.getClass())) {
            InetSocketAddress inetSocketAddress = (InetSocketAddress) localSocketAddress;
            if (inetSocketAddress.getAddress() != null) {
                return inetSocketAddress.getAddress().getHostAddress();
            }
        }

        SocketAddress remoteSocketAddr = channel.remoteAddress();
        if (null != remoteSocketAddr && InetSocketAddress.class.isAssignableFrom(remoteSocketAddr.getClass())) {
            InetSocketAddress inetSocketAddress = (InetSocketAddress) remoteSocketAddr;
            if (inetSocketAddress.getAddress() != null) {
                return inetSocketAddress.getAddress().getHostAddress();
            }
        }

        return null;
    }
}
