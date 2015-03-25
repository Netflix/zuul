package com.netflix.zuul.context;

import com.google.inject.Inject;
import com.netflix.zuul.lifecycle.Headers;
import com.netflix.zuul.lifecycle.HttpQueryParams;
import com.netflix.zuul.lifecycle.HttpRequestMessage;
import com.netflix.zuul.lifecycle.HttpResponseMessage;
import io.netty.channel.Channel;
import io.reactivex.netty.protocol.http.server.HttpServerRequest;

import javax.annotation.Nullable;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.List;
import java.util.Map;

/**
 * User: michaels@netflix.com
 * Date: 2/25/15
 * Time: 4:03 PM
 */
public class RequestContextFactory implements FilterStateFactory<RequestContext>
{
    private RequestContextDecorator decorator;

    @Inject
    public RequestContextFactory(@Nullable RequestContextDecorator decorator) {
        this.decorator = decorator;
    }

    @Override
    public RequestContext create(HttpServerRequest httpServerRequest)
    {
        // Get the client IP (ignore XFF headers at this point, as that can be app specific).
        String clientIp = getIpAddress(httpServerRequest.getNettyChannel());

        // Setup the req/resp message objects.
        HttpRequestMessage httpReqMsg = new HttpRequestMessage(
                httpServerRequest.getHttpMethod().name().toLowerCase(),
                httpServerRequest.getUri(),
                copyQueryParams(httpServerRequest),
                copyHeaders(httpServerRequest),
                clientIp,
                httpServerRequest.getHttpVersion().toString()
        );
        HttpResponseMessage httpRespMsg = new HttpResponseMessage(httpReqMsg, 500);

        // Create the new context object.
        RequestContext ctx = new RequestContext(httpReqMsg, httpRespMsg);
        ctx.internal_setHttpServerRequest(httpServerRequest);

        // Optionally decorate it.
        if (decorator != null) {
            ctx = decorator.decorate(ctx);
        }

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
