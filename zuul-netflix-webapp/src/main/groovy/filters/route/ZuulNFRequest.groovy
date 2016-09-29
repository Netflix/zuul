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
import com.netflix.util.Pair
import com.netflix.zuul.ZuulFilter
import com.netflix.zuul.context.Debug
import com.netflix.zuul.context.NFRequestContext
import com.netflix.zuul.context.RequestContext
import com.netflix.zuul.dependency.ribbon.hystrix.RibbonCommand
import com.netflix.zuul.exception.ZuulException
import com.netflix.zuul.util.HTTPRequestUtils
import com.sun.jersey.core.util.MultivaluedMapImpl
import org.apache.http.Header
import org.apache.http.message.BasicHeader
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.runners.MockitoJUnitRunner
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import javax.servlet.ServletInputStream
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import javax.ws.rs.core.MultivaluedMap
import java.util.zip.GZIPInputStream

import static HttpRequest.Verb

class ZuulNFRequest extends ZuulFilter {

    private static final Logger LOG = LoggerFactory.getLogger(ZuulNFRequest.class);

    public static final String CONTENT_ENCODING = "Content-Encoding";

    @Override
    String filterType() {
        return 'route'
    }

    @Override
    int filterOrder() {
        return 10
    }

    boolean shouldFilter() {
        return NFRequestContext.currentContext.getRouteHost() == null && RequestContext.currentContext.sendZuulResponse()
    }

    Object run() {
        NFRequestContext context = NFRequestContext.currentContext
        HttpServletRequest request = context.getRequest();

        MultivaluedMap<String, String> headers = buildZuulRequestHeaders(request)
        MultivaluedMap<String, String> params = buildZuulRequestQueryParams(request)
        Verb verb = getVerb(request);
        Object requestEntity = getRequestBody(request)
        IClient restClient = ClientFactory.getNamedClient(context.getRouteVIP());

        String uri = request.getRequestURI()
        if (context.requestURI != null) {
            uri = context.requestURI
        }
        //remove double slashes
        uri = uri.replace("//", "/")

        HttpResponse response = forward(restClient, verb, uri, headers, params, requestEntity)
        setResponse(response)
        return response
    }



    void debug(RestClient restClient, Verb verb, uri, MultivaluedMap<String, String> headers, MultivaluedMap<String, String> params, InputStream requestEntity) {

        if (Debug.debugRequest()) {

            headers.each {
                Debug.addRequestDebug("ZUUL:: > ${it.key}  ${it.value[0]}")
            }
            String query = ""
            params.each {
                it.value.each { v ->
                    query += it.key + "=" + v + "&"
                }
            }

            Debug.addRequestDebug("ZUUL:: > ${verb.verb()}  ${uri}?${query} HTTP/1.1")
            RequestContext ctx = RequestContext.getCurrentContext()
            if (!ctx.isChunkedRequestBody()) {
                if (requestEntity != null) {
                    debugRequestEntity(ctx.request.getInputStream())
                }
            }
        }
    }

    void debugRequestEntity(InputStream inputStream) {
        if (!Debug.debugRequestHeadersOnly()) {
            String entity = inputStream.getText()
            Debug.addRequestDebug("ZUUL:: > ${entity}")
        }
    }



    def HttpResponse forward(RestClient restClient, Verb verb, uri, MultivaluedMap<String, String> headers, MultivaluedMap<String, String> params, InputStream requestEntity) {
        debug(restClient, verb, uri, headers, params, requestEntity)

//        restClient.apacheHttpClient.params.setVirtualHost(headers.getFirst("host"))

        String route = NFRequestContext.getCurrentContext().route
        if (route == null) {
            String path = RequestContext.currentContext.requestURI
            if (path == null) {
                path = RequestContext.currentContext.getRequest() getRequestURI()
            }
            route = "route" //todo get better name
        }
        route = route.replace("/", "_")


        RibbonCommand command = new RibbonCommand(restClient, verb, uri, headers, params, requestEntity);
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


    def getRequestBody(HttpServletRequest request) {
        Object requestEntity = null;
        try {
            requestEntity = NFRequestContext.currentContext.requestEntity
            if (requestEntity == null) {
                requestEntity = request.getInputStream();
            }
        } catch (IOException e) {
            LOG.error(e);
        }

        return requestEntity
    }



    def MultivaluedMap<String, String> buildZuulRequestQueryParams(HttpServletRequest request) {

        Map<String, List<String>> map = HTTPRequestUtils.getInstance().getQueryParams()

        MultivaluedMap<String, String> params = new MultivaluedMapImpl<String, String>();
        if (map == null) return params;
        map.entrySet().each {
            it.value.each { v ->
                params.add(it.key, v)
            }

        }
        return params
    }


    def MultivaluedMap<String, String> buildZuulRequestHeaders(HttpServletRequest request) {

        NFRequestContext context = NFRequestContext.currentContext

        MultivaluedMap<String, String> headers = new MultivaluedMapImpl<String, String>();
        Enumeration headerNames = request.getHeaderNames();
        while (headerNames?.hasMoreElements()) {
            String name = (String) headerNames.nextElement();
            String value = request.getHeader(name);
            if (!name.toLowerCase().contains("content-length")) headers.putSingle(name, value);
        }
        Map zuulRequestHeaders = context.getZuulRequestHeaders();

        zuulRequestHeaders.keySet().each {
            headers.putSingle((String) it, (String) zuulRequestHeaders[it])
        }

        headers.putSingle("accept-encoding", "deflate, gzip")

        if (headers.containsKey("transfer-encoding"))
            headers.remove("transfer-encoding")

        return headers
    }



    Verb getVerb(HttpServletRequest request) {
        String sMethod = request.getMethod();
        return getVerb(sMethod);
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

    void setResponse(HttpResponse resp) {
        RequestContext context = RequestContext.getCurrentContext()

        context.setResponseStatusCode(resp.getStatus());
        if (resp.hasEntity()) {
            context.responseDataStream = resp.inputStream;
        }

        String contentEncoding = resp.getHeaders().get(CONTENT_ENCODING)?.first();

        if (contentEncoding != null && HTTPRequestUtils.getInstance().isGzipped(contentEncoding)) {
            context.setResponseGZipped(true);
        } else {
            context.setResponseGZipped(false);
        }

        if (Debug.debugRequest()) {
            resp.getHeaders().keySet().each { key ->
                boolean isValidHeader = isValidHeader(key)

                Collection<String> list = resp.getHeaders().get(key)
                list.each { header ->
                    context.addOriginResponseHeader(key, header)

                    if (key.equalsIgnoreCase("content-length"))
                        context.setOriginContentLength(header);

                    if (isValidHeader) {
                        context.addZuulResponseHeader(key, header);
                        Debug.addRequestDebug("ORIGIN_RESPONSE:: < ${key}  ${header}")
                    }
                }
            }

            if (context.responseDataStream) {
                byte[] origBytes = context.getResponseDataStream().bytes
                InputStream inStream = new ByteArrayInputStream(origBytes);
                if (context.getResponseGZipped())
                    inStream = new GZIPInputStream(inStream);
                String responseEntity = inStream.getText()
                Debug.addRequestDebug("ORIGIN_RESPONSE:: < ${responseEntity}")
                context.setResponseDataStream(new ByteArrayInputStream(origBytes))
            }

        } else {
            resp.getHeaders().keySet().each { key ->
                boolean isValidHeader = isValidHeader(key)
                Collection<java.lang.String> list = resp.getHeaders().get(key)
                list.each { header ->
                    context.addOriginResponseHeader(key, header)

                    if (key.equalsIgnoreCase("content-length"))
                        context.setOriginContentLength(header);

                    if (isValidHeader) {
                        context.addZuulResponseHeader(key, header);
                    }
                }
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
        HttpServletResponse response
        @Mock
        HttpServletRequest request

        @Before
        public void before() {
            RequestContext.setContextClass(NFRequestContext.class);
        }

        @Test
        public void testGetRequestBody() {
            this.request = Mockito.mock(HttpServletRequest.class)
            ServletInputStream inn = Mockito.mock(ServletInputStream.class)
            RequestContext.currentContext.request = this.request

            ZuulNFRequest routeHostRequest = new ZuulNFRequest()

            Mockito.when(request.getInputStream()).thenReturn(inn)

            InputStream inp = routeHostRequest.getRequestBody(request)

            Assert.assertEquals(inp, inn)

            Mockito.when(request.getInputStream()).thenReturn(null)

            inp = routeHostRequest.getRequestBody(request)
            Assert.assertNull(inp)


            Mockito.when(request.getInputStream()).thenReturn(inn)
            ServletInputStream inn2 = Mockito.mock(ServletInputStream.class)
            NFRequestContext.currentContext.requestEntity = inn2

            inp = routeHostRequest.getRequestBody(request)
            Assert.assertNotNull(inp)
            Assert.assertEquals(inp, inn2)


        }



        @Test
        public void testHeaderResponse() {

            ZuulNFRequest request = new ZuulNFRequest()
            Header header = new BasicHeader("test", "test")
            Header header1 = new BasicHeader("content-length", "100")
            Header header2 = new BasicHeader("content-encoding", "test")

            Assert.assertTrue(request.isValidHeader(header.name))
            Assert.assertFalse(request.isValidHeader(header1.name))
            Assert.assertFalse(request.isValidHeader(header2.name))


        }

        @Test
        public void testbuildZuulRequestHeaders() {

            NFRequestContext.getCurrentContext().unset()

            request = Mockito.mock(HttpServletRequest.class)
            response = Mockito.mock(HttpServletResponse.class)
            RequestContext.getCurrentContext().request = request
            RequestContext.getCurrentContext().response = response
            ZuulNFRequest request = new ZuulNFRequest()
            request = Mockito.spy(request)


            StringTokenizer st = new StringTokenizer("HEADER1,HEADER2", ",")

            Mockito.when(this.request.getHeaderNames()).thenReturn(st)

            MultivaluedMap<String, String> headers = request.buildZuulRequestHeaders(getRequest())
            Assert.assertEquals(headers.size(), 3)
            Assert.assertEquals(headers.getFirst("accept-encoding"), "deflate, gzip")


        }

        @Test
        public void testParseQueryParams() {
            request = Mockito.mock(HttpServletRequest.class)
            response = Mockito.mock(HttpServletResponse.class)
            RequestContext.getCurrentContext().request = request
            RequestContext.getCurrentContext().response = response
            ZuulNFRequest request = new ZuulNFRequest()


            Mockito.when(this.request.getQueryString()).thenReturn("test=string&ik=moo")

            MultivaluedMap<String, String> map = request.buildZuulRequestQueryParams(this.request);

            Assert.assertEquals(map['test'], ['string'])
            Assert.assertEquals(map['ik'], ['moo'])

        }

        @Test
        public void testSetResponse() {
            request = Mockito.mock(HttpServletRequest.class)
            response = Mockito.mock(HttpServletResponse.class)
            HttpResponse zuulResponse = Mockito.mock(HttpResponse.class)
            RequestContext.getCurrentContext().request = request
            RequestContext.getCurrentContext().response = response
            ZuulNFRequest request = new ZuulNFRequest()
            request = Mockito.spy(request)



            Map<String, Collection<String>> headers = new HashMap<>();
            headers.put("test", ["test"])
            headers.put("content-length", ["100"])

            InputStream inp = Mockito.mock(InputStream.class)

            Mockito.when(zuulResponse.getStatus()).thenReturn(200)
            Mockito.when(zuulResponse.getInputStream()).thenReturn(inp)
            Mockito.when(zuulResponse.hasEntity()).thenReturn(true)

            Mockito.when(zuulResponse.headers).thenReturn(headers)
            request.setResponse(zuulResponse)

            Assert.assertEquals(RequestContext.getCurrentContext().getResponseStatusCode(), 200)
            Assert.assertNotNull(RequestContext.getCurrentContext().responseDataStream)
            Assert.assertTrue(RequestContext.getCurrentContext().zuulResponseHeaders.contains(new Pair('test', "test")))
        }

        @Test
        public void testShouldFilter() {

            RequestContext.currentContext.setRouteHost(new URL("http://www.moldfarm.com"))
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



