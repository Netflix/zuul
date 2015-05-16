package com.netflix.zuul.ribbon;

import com.netflix.client.ClientException;
import com.netflix.client.ClientFactory;
import com.netflix.client.http.CaseInsensitiveMultiMap;
import com.netflix.client.http.HttpRequest;
import com.netflix.client.http.HttpResponse;
import com.netflix.niws.client.http.RestClient;
import com.netflix.zuul.context.Headers;
import com.netflix.zuul.context.HttpQueryParams;
import com.netflix.zuul.context.HttpRequestMessage;
import com.netflix.zuul.context.HttpResponseMessage;
import com.netflix.zuul.exception.ZuulException;
import com.netflix.zuul.origins.Origin;
import com.netflix.zuul.stats.Timing;
import org.apache.commons.io.IOUtils;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;
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
    public Observable<HttpResponseMessage> request(HttpRequestMessage requestMsg)
    {
        RestClient client = (RestClient) ClientFactory.getNamedClient(name);
        if (client == null) {
            throw new ZuulException("No RestClient found for name! name=" + String.valueOf(name));
        }

        // Convert to a ribbon request.
        HttpRequest.Verb verb = HttpRequest.Verb.valueOf(requestMsg.getMethod().toUpperCase());
        URI uri = URI.create(requestMsg.getPath());
        Headers headers = requestMsg.getHeaders();
        HttpQueryParams params = requestMsg.getQueryParams();

        HttpRequest.Builder builder = HttpRequest.newBuilder().
                verb(verb).
                uri(uri);

        if (requestMsg.getBody() != null) {
            InputStream requestEntity = new ByteArrayInputStream(requestMsg.getBody());
            builder.entity(requestEntity);
        }

        for (Map.Entry<String, String> entry : headers.entries()) {
            builder.header(entry.getKey(), entry.getValue());
        }

        for (Map.Entry<String, String> entry : params.entries()) {
            builder.queryParams(entry.getKey(), entry.getValue());
        }

        HttpRequest httpClientRequest = builder.build();

        final Timing timing = requestMsg.getContext().getRequestProxyTiming();
        timing.start();

        // Execute the request.
        HttpResponse ribbonResp;
        try {
            ribbonResp = client.executeWithLoadBalancer(httpClientRequest);
        }
        catch (ClientException e) {
            throw new ZuulException(e, "Forwarding error", 500, e.getErrorType().toString());
        }
        finally {
            timing.end();
        }
        HttpResponseMessage respMsg = createHttpResponseMessage(ribbonResp, requestMsg);

        // Return response wrapped in an Observable.
        return Observable.just(respMsg);
    }

    protected HttpResponseMessage createHttpResponseMessage(HttpResponse ribbonResp, HttpRequestMessage request)
    {
        // Convert to a zuul response object.
        HttpResponseMessage respMsg = new HttpResponseMessage(request.getContext(), request, 500);
        respMsg.setStatus(ribbonResp.getStatus());
        for (Map.Entry<String, String> header : ribbonResp.getHttpHeaders().getAllHeaders()) {
            if (isValidHeader(header.getKey())) {
                respMsg.getHeaders().add(header.getKey(), header.getValue());
            }
        }
        byte[] body;
        try {
            body = IOUtils.toByteArray(ribbonResp.getInputStream());
        }
        catch (IOException e) {
            throw new ZuulException(e, "Error reading response body.");
        }
        respMsg.setBody(body);
        return respMsg;
    }

    protected boolean isValidHeader(String headerName)
    {
        switch (headerName.toLowerCase()) {
            case "connection":
            case "content-length":
            case "content-encoding":
            case "server":
            case "transfer-encoding":
                return false;
            default:
                return true;
        }
    }



    @RunWith(MockitoJUnitRunner.class)
    public static class TestUnit
    {
        @Mock
        HttpResponse proxyResp;

        @Mock
        HttpRequestMessage request;

        @Test
        public void testHeaderResponse()
        {
            RibbonOrigin origin = new RibbonOrigin("blah");
            Assert.assertTrue(origin.isValidHeader("test"));
            Assert.assertFalse(origin.isValidHeader("content-length"));
            Assert.assertFalse(origin.isValidHeader("content-encoding"));
        }

        @Test
        public void testSetResponse() throws Exception
        {
            RibbonOrigin origin = new RibbonOrigin("blah");
            origin = Mockito.spy(origin);

            CaseInsensitiveMultiMap headers = new CaseInsensitiveMultiMap();
            headers.addHeader("test", "test");
            headers.addHeader("content-length", "100");

            byte[] body = "test-body".getBytes("UTF-8");
            InputStream inp = new ByteArrayInputStream(body);

            Mockito.when(proxyResp.getStatus()).thenReturn(200);
            Mockito.when(proxyResp.getInputStream()).thenReturn(inp);
            Mockito.when(proxyResp.hasEntity()).thenReturn(true);
            Mockito.when(proxyResp.getHttpHeaders()).thenReturn(headers);

            HttpResponseMessage response = origin.createHttpResponseMessage(proxyResp, request);

            Assert.assertEquals(200, response.getStatus());
            Assert.assertNotNull(response.getBody());
            Assert.assertEquals(body.length, response.getBody().length);
            Assert.assertTrue(response.getHeaders().contains("test", "test"));
        }
    }
}
