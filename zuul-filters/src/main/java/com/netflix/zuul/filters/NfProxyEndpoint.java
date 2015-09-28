package com.netflix.zuul.filters;

import com.netflix.zuul.context.Debug;
import com.netflix.zuul.context.SessionContext;
import com.netflix.zuul.exception.ZuulException;
import com.netflix.zuul.message.http.HttpRequestMessage;
import com.netflix.zuul.message.http.HttpResponseMessage;
import com.netflix.zuul.origins.Origin;
import com.netflix.zuul.origins.OriginManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;

/**
 * General-purpose Proxy endpoint implementation with both async and sync/blocking methods.
 * <p>
 * You can probably just subclass this in your project, and use as-is.
 * <p>
 * User: michaels@netflix.com Date: 5/22/15 Time: 1:42 PM
 */
public class NfProxyEndpoint extends Endpoint<HttpRequestMessage, HttpResponseMessage> {

    @Override
    public Observable<HttpResponseMessage> applyAsync(final HttpRequestMessage request) {
        final SessionContext context = request.getContext();

        return Debug.writeDebugRequest(context, request, false)
                    .map(aBoolean -> getOrigin(request))
                    .flatMap(origin -> origin.request(request))
                    .doOnNext(httpResponseMessage -> {
                        context.put("origin_http_status", Integer.toString(httpResponseMessage.getStatus()));
                    })
                    .flatMap(originResp -> Debug.writeDebugResponse(context, originResp, true)
                                                .map(aBoolean -> originResp));
    }

    public HttpResponseMessage apply(HttpRequestMessage request) {
        return applyAsync(request).toBlocking().first();
    }

    protected Origin getOrigin(HttpRequestMessage request) {
        final String name = request.getContext().getRouteVIP();
        OriginManager originManager = request.getContext().getOriginManager();
        Origin origin = originManager.getOrigin(name);
        if (origin == null) {
            throw new ZuulException("No Origin registered for name=" + name + "!", "UNKNOWN_VIP");
        }

        return origin;
    }

    private static final Logger LOG = LoggerFactory.getLogger(NfProxyEndpoint.class);
}
