/*
 * Copyright 2013 Netflix, Inc.
 *
 *      Licensed under the Apache License, Version 2.0 (the "License");
 *      you may not use this file except in compliance with the License.
 *      You may obtain a copy of the License at
 *
 *          http://www.apache.org/licenses/LICENSE-2.0
 *
 *      Unless required by applicable law or agreed to in writing, software
 *      distributed under the License is distributed on an "AS IS" BASIS,
 *      WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *      See the License for the specific language governing permissions and
 *      limitations under the License.
 */
package filters.route

import com.netflix.client.ClientException
import com.netflix.client.ClientFactory
import com.netflix.client.IClient
import com.netflix.client.http.HttpRequest
import com.netflix.client.http.HttpResponse
import com.netflix.hystrix.exception.HystrixRuntimeException
import com.netflix.niws.client.http.RestClient
import com.netflix.zuul.ZuulFilter
import com.netflix.zuul.context.*
import com.netflix.zuul.dependency.ribbon.hystrix.RibbonCommand
import com.netflix.zuul.exception.ZuulException
import com.netflix.zuul.util.HttpUtils
import org.apache.commons.io.IOUtils
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.runners.MockitoJUnitRunner
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.util.zip.GZIPInputStream

import static HttpRequest.Verb

class ZuulNFRequest extends ZuulFilter {

    private static final Logger LOG = LoggerFactory.getLogger(ZuulNFRequest.class);


    @Override
    String filterType() {
        return 'route'
    }

    @Override
    int filterOrder() {
        return 10
    }

    @Override
    boolean shouldFilter(SessionContext ctx) {
        return RequestContext.currentContext.getRouteHost() == null && RequestContext.currentContext.sendZuulResponse()
    }

    @Override
    SessionContext apply(SessionContext context)
    {
        HttpRequestMessage request = context.getRequest()
        Attributes attrs = context.getAttributes()

        debug(context, request)

        Verb verb = getVerb(request.getMethod())
        InputStream requestEntity = new ByteArrayInputStream(request.getBody())
        IClient restClient = ClientFactory.getNamedClient(attrs.getRouteVIP())

        String path = request.getPath()

        //remove double slashes
        path = path.replace("//", "/")

        HttpResponse response = forward(restClient, verb, path, request.getHeaders(), request.getQueryParams(), requestEntity)
        setResponse(context, response)
        return response
    }



    void debug(SessionContext context, HttpRequestMessage request) {

        if (Debug.debugRequest()) {

            request.getHeaders().entries().each {
                Debug.addRequestDebug(context, "ZUUL:: > ${it.key}  ${it.value}")
            }
            String query = ""
            request.getQueryParams().getEntries().each {
                query += it.key + "=" + it.value + "&"
            }

            Debug.addRequestDebug(context, "ZUUL:: > ${request.getMethod()}  ${request.getPath()}?${query} ${request.getProtocol()}")

            if (request.getBody() != null) {
                if (!Debug.debugRequestHeadersOnly()) {
                    String entity = new ByteArrayInputStream(request.getBody()).getText()
                    Debug.addRequestDebug(context, "ZUUL:: > ${entity}")
                }
            }
        }
    }


    def HttpResponse forward(RestClient restClient, Verb verb, String path, Headers headers, HttpQueryParams params, InputStream requestEntity) {

//        restClient.apacheHttpClient.params.setVirtualHost(headers.getFirst("host"))

        RibbonCommand command = new RibbonCommand(restClient, verb, path, headers, params, requestEntity);
        try {
            HttpResponse response = command.execute();
            return response
        } catch (HystrixRuntimeException e) {
            if (e?.fallbackException?.cause instanceof ClientException) {
                ClientException ex = e.fallbackException.cause as ClientException
                throw new ZuulException(ex, "Forwarding error", 500, ex.getErrorType().toString())
            }
            throw new ZuulException(e, "Forwarding error", 500, e.failureType.toString())
        }

    }


    Verb getVerb(String sMethod) {
        if (sMethod == null) return Verb.GET;
        sMethod = sMethod.toLowerCase();
        if (sMethod.equals("post")) return Verb.POST;
        if (sMethod.equals("put")) return Verb.PUT;
        if (sMethod.equals("delete")) return Verb.DELETE;
        if (sMethod.equals("options")) return Verb.OPTIONS;
        if (sMethod.equals("head")) return Verb.HEAD;
        return Verb.GET;
    }

    void setResponse(SessionContext ctx, HttpResponse proxyResp) {

        HttpResponseMessage resp = ctx.getResponse()

        resp.setStatus(proxyResp.getStatus().intValue());

        // Collect the response headers.
        proxyResp.getHeaders().keySet().each { key ->
            boolean isValidHeader = isValidHeader(key)
            Collection<java.lang.String> list = proxyResp.getHeaders().get(key)
            list.each { header ->
                if (isValidHeader) {
                    resp.getHeaders().add(key, header)
                }
            }
        }

        // Body.
        byte[] bodyBytes
        if (proxyResp.hasEntity()) {
            // Read the request body inputstream into a byte array.
            try {
                bodyBytes = IOUtils.toByteArray(proxyResp.getInputStream())
                resp.setBody(bodyBytes)
            }
            catch (SocketTimeoutException e) {
                // This can happen if the request body is smaller than the size specified in the
                // Content-Length header, and using tomcat APR connector.
                LOG.error("SocketTimeoutException reading request body from inputstream. error=" + String.valueOf(e.getMessage()));
            }
        }

        // DEBUG
        if (Debug.debugRequest()) {
            proxyResp.getHeaders().keySet().each { key ->
                Collection<String> list = proxyResp.getHeaders().get(key)
                list.each { header ->
                    Debug.addRequestDebug(ctx, "ORIGIN_RESPONSE:: < ${key}  ${header}")
                }
            }

            if (bodyBytes) {
                InputStream inStream = new ByteArrayInputStream(bodyBytes);
                if (HttpUtils.isGzipped(resp.getHeaders()))
                    inStream = new GZIPInputStream(inStream);
                String responseEntity = inStream.getText()
                Debug.addRequestDebug(ctx, "ORIGIN_RESPONSE:: < ${responseEntity}")
            }
        }
    }

    boolean isValidHeader(String headerName) {
        switch (headerName.toLowerCase()) {
            case "connection":
            case "content-length":
            case "content-encoding":
            case "server":
            case "transfer-encoding":
                return false
            default:
                return true
        }
    }


    @RunWith(MockitoJUnitRunner.class)
    public static class TestUnit {

        @Mock
        HttpResponse proxyResp
        @Mock
        HttpResponseMessage response
        @Mock
        HttpRequestMessage request

        SessionContext ctx

        @Before
        public void setup()
        {
            ctx = new SessionContext(request, response)
        }

        @Test
        public void testHeaderResponse() {

            ZuulNFRequest filter = new ZuulNFRequest()
            Assert.assertTrue(filter.isValidHeader("test"))
            Assert.assertFalse(filter.isValidHeader("content-length"))
            Assert.assertFalse(filter.isValidHeader("content-encoding"))
        }

        @Test
        public void testSetResponse() {

            ZuulNFRequest filter = new ZuulNFRequest()
            filter = Mockito.spy(filter)

            Headers headers = new Headers()
            headers.set("test", "test")
            headers.set("content-length", "100")

            byte[] body = "test-body".getBytes("UTF-8")
            InputStream inp = new ByteArrayInputStream(body)

            Mockito.when(proxyResp.getStatus()).thenReturn(200)
            Mockito.when(proxyResp.getInputStream()).thenReturn(inp)
            Mockito.when(proxyResp.hasEntity()).thenReturn(true)
            Mockito.when(proxyResp.getHeaders()).thenReturn(headers)

            filter.setResponse(ctx, proxyResp)

            Assert.assertEquals(200, response.getStatus())
            Assert.assertNotNull(response.getBody())
            Assert.assertEquals(body.length, response.getBody().length)
            Assert.assertTrue(response.getHeaders().contains("test", "test"))
        }

        @Test
        public void testShouldFilter() {

            Attributes attrs = new Attributes()
            attrs.setRouteHost(new URL("http://www.moldfarm.com"))
            Mockito.when(ctx.getAttributes()).thenReturn(attrs)

            ZuulNFRequest filter = new ZuulNFRequest()
            Assert.assertFalse(filter.shouldFilter())
        }

        @Test
        public void testGetVerb() {

            ZuulNFRequest request = new ZuulNFRequest()
            Verb verb = request.getVerb("get")
            Assert.assertEquals(Verb.GET, verb)
            verb = request.getVerb("Get")
            Assert.assertEquals(Verb.GET, verb)

            verb = request.getVerb("post")
            Assert.assertEquals(Verb.POST, verb)
            verb = request.getVerb("POST")
            Assert.assertEquals(Verb.POST, verb)

            verb = request.getVerb("PUT")
            Assert.assertEquals(Verb.PUT, verb)
            verb = request.getVerb("put")
            Assert.assertEquals(Verb.PUT, verb)

            verb = request.getVerb("OPTIONS")
            Assert.assertEquals(Verb.OPTIONS, verb)
            verb = request.getVerb("options")
            Assert.assertEquals(Verb.OPTIONS, verb)

            verb = request.getVerb("delete")
            Assert.assertEquals(Verb.DELETE, verb)
            verb = request.getVerb("Delete")
            Assert.assertEquals(Verb.DELETE, verb)

            verb = request.getVerb("head")
            Assert.assertEquals(Verb.HEAD, verb)
            verb = request.getVerb("HEAD")
            Assert.assertEquals(Verb.HEAD, verb)
        }
    }

}



