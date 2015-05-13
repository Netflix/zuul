package com.netflix.zuul.ribbon;

import com.netflix.client.ClientException;
import com.netflix.client.ClientFactory;
import com.netflix.client.IClient;
import com.netflix.client.http.HttpRequest;
import com.netflix.client.http.HttpResponse;
import com.netflix.niws.client.http.RestClient;
import com.netflix.zuul.context.*;
import com.netflix.zuul.exception.ZuulException;
import com.netflix.zuul.origins.Origin;
import com.netflix.zuul.stats.Timing;
import org.apache.commons.io.IOUtils;
import rx.Observable;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Map;

/**
 * User: Mike Smith
 * Date: 5/12/15
 * Time: 11:25 PM
 */
public class RibbonOrigin implements Origin
{
    private final String name;

    public RibbonOrigin(String name)
    {
        this.name = name;
    }

    @Override
    public String getName()
    {
        return name;
    }

    @Override
    public Observable<SessionContext> request(SessionContext ctx)
    {
        final Timing timing = ctx.getRequestProxyTiming();
        timing.start();
        return request(ctx.getHttpRequest())
                .map(originResp -> {
                    // Copy the response from Origin onto the SessionContext.
                    HttpResponseMessage zuulResp = ctx.getHttpResponse();
                    zuulResp.setStatus(originResp.getStatus());
                    zuulResp.getHeaders().putAll(originResp.getHeaders());
                    zuulResp.setBody(originResp.getBody());
                    return ctx;
                })
                .finallyDo(() -> {
                    timing.end();
                });
    }

    @Override
    public Observable<HttpResponseMessage> request(HttpRequestMessage requestMsg)
    {
        RestClient client = (RestClient) ClientFactory.getNamedClient(name);
        if (client == null) {
            throw new ZuulException("No RestClient found for name! name=" + String.valueOf(name));
        }

        // Convert to a ribbon request.
        HttpRequest.Verb verb = HttpRequest.Verb.valueOf(requestMsg.getMethod());
        URI uri = URI.create(requestMsg.getPath());
        Headers headers = requestMsg.getHeaders();
        HttpQueryParams params = requestMsg.getQueryParams();
        InputStream requestEntity = new ByteArrayInputStream(requestMsg.getBody());

        HttpRequest.Builder builder = HttpRequest.newBuilder().
                verb(verb).
                uri(uri).
                entity(requestEntity);

        for (Map.Entry<String, String> entry : headers.entries()) {
            builder.header(entry.getKey(), entry.getValue());
        }

        for (Map.Entry<String, String> entry : params.entries()) {
            builder.queryParams(entry.getKey(), entry.getValue());
        }

        HttpRequest httpClientRequest = builder.build();

        // Execute the request.
        HttpResponse ribbonResp;
        try {
            ribbonResp = client.executeWithLoadBalancer(httpClientRequest);
        }
        catch (ClientException e) {
            throw new ZuulException(e, "Forwarding error", 500, e.getErrorType().toString());
        }

        // Convert to a zuul response object.
        HttpResponseMessage respMsg = new HttpResponseMessage(500);
        respMsg.setStatus(ribbonResp.getStatus());
        for (Map.Entry<String, String> header : ribbonResp.getHttpHeaders().getAllHeaders()) {
            respMsg.getHeaders().add(header.getKey(), header.getValue());
        }
        byte[] body;
        try {
            body = IOUtils.toByteArray(ribbonResp.getInputStream());
        }
        catch (IOException e) {
            throw new ZuulException(e, "Error reading response body.");
        }
        respMsg.setBody(body);

        // Return response wrapped in an Observable.
        return Observable.just(respMsg);
    }
}
