/**
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.zuul.ribbon;

import com.netflix.client.ClientException;
import com.netflix.client.ClientFactory;
import com.netflix.client.http.CaseInsensitiveMultiMap;
import com.netflix.client.http.HttpRequest;
import com.netflix.client.http.HttpResponse;
import com.netflix.config.DynamicIntProperty;
import com.netflix.config.DynamicPropertyFactory;
import com.netflix.niws.client.http.RestClient;
import com.netflix.zuul.bytebuf.ByteBufUtils;
import com.netflix.zuul.constants.ZuulConstants;
import com.netflix.zuul.context.*;
import com.netflix.zuul.exception.ZuulException;
import com.netflix.zuul.message.Header;
import com.netflix.zuul.message.Headers;
import com.netflix.zuul.message.http.HttpQueryParams;
import com.netflix.zuul.message.http.HttpRequestMessage;
import com.netflix.zuul.message.http.HttpResponseMessage;
import com.netflix.zuul.message.http.HttpResponseMessageImpl;
import com.netflix.zuul.origins.Origin;
import com.netflix.zuul.stats.Timing;
import com.netflix.zuul.util.ProxyUtils;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;

import java.io.ByteArrayInputStream;
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
    private static final Logger LOG = LoggerFactory.getLogger(RibbonOrigin.class);

    private static final DynamicIntProperty MAX_BODY_SIZE_PROP = DynamicPropertyFactory.getInstance().getIntProperty(
            ZuulConstants.ZUUL_REQUEST_BODY_MAX_SIZE, 25 * 1000 * 1024);

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
        SessionContext context = requestMsg.getContext();
        RestClient client = (RestClient) ClientFactory.getNamedClient(name);
        if (client == null) {
            throw proxyError(requestMsg, new IllegalArgumentException("No RestClient found for name! name=" + String.valueOf(name)), null);
        }

        // Convert to a ribbon request.
        HttpRequest.Verb verb = HttpRequest.Verb.valueOf(requestMsg.getMethod().toUpperCase());
        URI uri = URI.create(requestMsg.getPath());
        Headers headers = requestMsg.getHeaders();
        HttpQueryParams params = requestMsg.getQueryParams();

        HttpRequest.Builder builder = HttpRequest.newBuilder().
                verb(verb).
                uri(uri);

        // Request headers.
        for (Header entry : headers.entries()) {
            if (ProxyUtils.isValidRequestHeader(entry.getKey())) {
                builder.header(entry.getKey(), entry.getValue());
            }
        }

        // Request query params.
        for (Map.Entry<String, String> entry : params.entries()) {
            builder.queryParams(entry.getKey(), entry.getValue());
        }

        // Request body.
        Observable<HttpRequest> requestBuiltObs;
        if (requestMsg.getBodyStream() != null) {
            // TODO - find a way to avoid having to buffer the whole request body here. Need a way to convert Observable<ByteBuf> into a single
            // InputStream without first reading each of the ByteBufs (equivalent to what we do in the opposite direction using StringObservable.from().
            requestBuiltObs = ByteBufUtils.aggregate(requestMsg.getBodyStream(), MAX_BODY_SIZE_PROP.get())
                    .map(bb -> new ByteBufInputStream(bb))
                    .single()
                    .map(input -> {
                        builder.entity(input);
                        return builder.build();
                    });
        }
        else {
            requestBuiltObs = Observable.just(builder.build());
        }

        // Wrap the buffering of request body in a timer.
        // NOTE - this is also done in HttpRequestMessage.bufferBody() if that is called.
        // TODO - how to start the timer as a callback on the Observable, instead of here?
        Timing readTiming = context.getTimings().getRequestBodyRead();
        readTiming.start();
        requestBuiltObs.finallyDo(() -> readTiming.end() );


        // Execute the request.
        final Timing timing = context.getTimings().getRequestProxy();
        timing.start();
        Observable<HttpResponseMessage> responseObs = requestBuiltObs.map(httpClientRequest -> {
            HttpResponse ribbonResp;
            try {
                ribbonResp = client.executeWithLoadBalancer(httpClientRequest);
            }
            catch (ClientException e) {
                throw proxyError(requestMsg, e, e.getErrorType().toString());
            }
            catch(Exception e) {
                throw proxyError(requestMsg, e, null);
            }
            finally {
                timing.end();
            }
            HttpResponseMessage respMsg = createHttpResponseMessage(ribbonResp, requestMsg);
            return respMsg;
        });

        return responseObs;
    }

    protected ZuulException proxyError(HttpRequestMessage zuulReq, Throwable t, String errorCauseMsg)
    {
        // Flag this as a proxy failure in the RequestContext. Error filter will then use this flag.
        zuulReq.getContext().setShouldSendErrorResponse(true);

        LOG.error(String.format("Error making http request to Origin. restClientName=%s, url=%s",
                this.name, zuulReq.getPathAndQuery()), t);

        if (errorCauseMsg == null) {
            if (t.getCause() != null) {
                errorCauseMsg = t.getCause().getMessage();
            }
        }
        if (errorCauseMsg == null)
            errorCauseMsg = "unknown";

        return new ZuulException("Proxying error", t, errorCauseMsg);
    }

    protected HttpResponseMessage createHttpResponseMessage(HttpResponse ribbonResp, HttpRequestMessage request)
    {
        // Convert to a zuul response object.
        HttpResponseMessage respMsg = new HttpResponseMessageImpl(request.getContext(), request, 500);
        respMsg.setStatus(ribbonResp.getStatus());
        for (Map.Entry<String, String> header : ribbonResp.getHttpHeaders().getAllHeaders()) {
            if (ProxyUtils.isValidResponseHeader(header.getKey())) {
                respMsg.getHeaders().add(header.getKey(), header.getValue());
            }
        }

        // Store this original response info for future reference (ie. for metrics and access logging purposes).
        respMsg.storeInboundResponse();

        // Body.
        Observable<ByteBuf> responseBodyObs = ByteBufUtils.fromInputStream(ribbonResp.getInputStream());
        respMsg.setBodyStream(responseBodyObs);

        return respMsg;
    }


    @RunWith(MockitoJUnitRunner.class)
    public static class TestUnit
    {
        @Mock
        HttpResponse proxyResp;

        @Mock
        HttpRequestMessage request;

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

            byte[] respBodyBytes = ByteBufUtils.toBytes(response.getBodyStream().toBlocking().single());
            Assert.assertNotNull(respBodyBytes);
            Assert.assertEquals(body.length, respBodyBytes.length);

            Assert.assertTrue(response.getHeaders().contains("test", "test"));
        }
    }
}
