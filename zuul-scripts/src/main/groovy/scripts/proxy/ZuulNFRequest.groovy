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
package scripts.proxy


import java.util.zip.GZIPInputStream

import com.netflix.zuul.context.NFRequestContext
import com.netflix.zuul.dependency.NIWSCommand
import org.apache.http.Header
import org.apache.http.message.BasicHeader
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.runners.MockitoJUnitRunner

import javax.servlet.ServletInputStream
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import com.netflix.zuul.groovy.ZuulFilter
import com.netflix.niws.client.http.RestClient
import com.netflix.zuul.context.RequestContext
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import com.netflix.zuul.context.Debug
import com.netflix.zuul.exception.ZuulException
import com.netflix.niws.client.http.HttpClientResponse
import javax.ws.rs.core.MultivaluedMap
import com.netflix.hystrix.exception.HystrixRuntimeException
import com.netflix.client.ClientException
import com.netflix.zuul.util.HTTPRequestUtils
import com.sun.jersey.core.util.MultivaluedMapImpl
import com.netflix.util.Pair

import static com.netflix.niws.client.http.HttpClientRequest.*
import com.netflix.client.ClientFactory
import com.netflix.client.IClient
import com.netflix.zuul.exception.ZuulException

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
        return NFRequestContext.currentContext.getProxyHost() == null && RequestContext.currentContext.sendProxyResponse()
    }

    Object run() {
        NFRequestContext context = NFRequestContext.currentContext
        HttpServletRequest request = context.getRequest();

        MultivaluedMap<String, String> headers = buildProxyRequestHeaders(request)
        MultivaluedMap<String, String> params = buildProxyRequestQueryParams(request)
        Verb verb = getVerb(request);
        Object requestEntity = getRequestBody(request)
        IClient restClient = ClientFactory.getNamedClient(context.getProxyVIP());

        String uri = request.getRequestURI()
        if (context.requestURI != null) {
            uri = context.requestURI
        }
        //remove double slashes
        uri = uri.replace("//", "/")
       
        HttpClientResponse response = proxy(restClient, verb, uri, headers, params, requestEntity)
        setResponse(response)
        return response
    }

	
	
    void debug(RestClient restClient, Verb verb, uri, MultivaluedMap<String, String> headers, MultivaluedMap<String, String> params, InputStream requestEntity) {

        if (Debug.debugRequest()) {

            headers.each {
                Debug.addRequestDebug("PROXY:: > ${it.key}  ${it.value[0]}")
            }
            String query = ""
            params.each {
                it.value.each { v ->
                    query += it.key + "=" + v + "&"
                }
            }

            Debug.addRequestDebug("PROXY:: > ${verb.verb()}  ${uri}?${query} HTTP/1.1")
            RequestContext ctx = RequestContext.getCurrentContext()
            if(!ctx.isChunkedRequestBody()) {
                if (requestEntity != null) {
                    debugRequestEntity(ctx.request.getInputStream())
                }
            }
        }
    }

    void debugRequestEntity(InputStream inputStream) {
        if (!Debug.debugRequestHeadersOnly()) {
			String entity = inputStream.getText()
			Debug.addRequestDebug("PROXY:: > ${entity}")
        }
    }



    def HttpClientResponse proxy(RestClient restClient, Verb verb, uri, MultivaluedMap<String, String> headers, MultivaluedMap<String, String> params, InputStream requestEntity) {
        debug(restClient, verb, uri, headers, params, requestEntity)

//        restClient.apacheHttpClient.params.setVirtualHost(headers.getFirst("host"))

        String route = NFRequestContext.getCurrentContext().route
        if(route == null){
            String path = RequestContext.currentContext.requestURI
            if (path == null) {
                path = RequestContext.currentContext.getRequest() getRequestURI()
            }
            route = "route" //todo get better name
        }
        route = route.replace("/", "_")


        NIWSCommand command = new NIWSCommand(restClient, verb, uri, headers, params, requestEntity);
        try {
            HttpClientResponse response = command.execute();
            return response
        } catch (HystrixRuntimeException e) {
            if (e?.fallbackException?.cause instanceof ClientException) {
                ClientException ex = e.fallbackException.cause as ClientException
                throw new ZuulException(ex, "Proxying error", 500, ex.getErrorType().toString())
            }
            throw new ZuulException(e, "Proxying error", 500, e.failureType.toString())
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



    def MultivaluedMap<String, String> buildProxyRequestQueryParams(HttpServletRequest request) {

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


    def MultivaluedMap<String, String> buildProxyRequestHeaders(HttpServletRequest request) {

        NFRequestContext context = NFRequestContext.currentContext

        MultivaluedMap<String, String> headers = new MultivaluedMapImpl<String, String>();
        Enumeration headerNames = request.getHeaderNames();
        while (headerNames?.hasMoreElements()) {
            String name = (String) headerNames.nextElement();
            String value = request.getHeader(name);
            if (!name.toLowerCase().contains("content-length")) headers.putSingle(name, value);
        }
        Map proxyRequestHeaders = context.getProxyRequestHeaders();

        proxyRequestHeaders.keySet().each {
            headers.putSingle((String) it, (String) proxyRequestHeaders[it])
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
        if (sMethod == null) return RestClient.Verb.GET;
        sMethod = sMethod.toLowerCase();
        if (sMethod.equals("post")) return RestClient.Verb.POST;
        if (sMethod.equals("put")) return RestClient.Verb.PUT;
        if (sMethod.equals("delete")) return RestClient.Verb.DELETE;
        if (sMethod.equals("options")) return RestClient.Verb.OPTIONS;
        if (sMethod.equals("head")) return RestClient.Verb.HEAD;
        return Verb.GET;
    }

    void setResponse(HttpClientResponse resp) {
        RequestContext context = RequestContext.getCurrentContext()

        context.setResponseStatusCode(resp.getStatus());
        if (resp.hasEntity()) {
            context.proxyResponseDataStream = resp.getRawEntity();
        }

        String contentEncoding = resp.getHeaders().get(CONTENT_ENCODING);
		
		if (contentEncoding != null && HTTPRequestUtils.getInstance().isGzipped(contentEncoding)) {
            context.setProxyResponseGZipped(true);
        } else {
            context.setProxyResponseGZipped(false);
        }

        if (Debug.debugRequest()) {
            resp.getHeaders().keySet().each { key ->
                boolean isValidHeader = isValidHeader(key)

                java.util.List<java.lang.String> list = resp.getHeaders().get(key, String.class)
                list.each { header ->
                    context.addOriginResponseHeader(key, header)

                    if(key.equalsIgnoreCase("content-length"))
                        context.setOriginContentLength(header);

                    if (isValidHeader) {
                        context.addProxyResponseHeader(key, header);
                        Debug.addRequestDebug("PROXY_RESPONSE:: < ${key}  ${header}")
                    }
                }
            }

            if (context.proxyResponseDataStream) {
                byte[] origBytes = context.getProxyResponseDataStream().bytes
                InputStream inStream = new ByteArrayInputStream(origBytes);
                if (context.getProxyResponseGZipped())
                    inStream = new GZIPInputStream(inStream);
                String responseEntity = inStream.getText()
                Debug.addRequestDebug("PROXY_RESPONSE:: < ${responseEntity}")
                context.setProxyResponseDataStream(new ByteArrayInputStream(origBytes))
            }

        } else {
            resp.getHeaders().keySet().each { key ->
                boolean isValidHeader = isValidHeader(key)
                java.util.List<java.lang.String> list = resp.getHeaders().get(key, String.class)
                list.each { header ->
                    context.addOriginResponseHeader(key, header)

                    if(key.equalsIgnoreCase("content-length"))
                        context.setOriginContentLength(header);

                    if (isValidHeader) {
                        context.addProxyResponseHeader(key, header);
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

            ZuulNFRequest proxyHostRequest = new ZuulNFRequest()

            Mockito.when(request.getInputStream()).thenReturn(inn)

            InputStream inp = proxyHostRequest.getRequestBody(request)

            Assert.assertEquals(inp, inn)

            Mockito.when(request.getInputStream()).thenReturn(null)

            inp = proxyHostRequest.getRequestBody(request)
            Assert.assertNull(inp)


            Mockito.when(request.getInputStream()).thenReturn(inn)
            ServletInputStream inn2 = Mockito.mock(ServletInputStream.class)
            NFRequestContext.currentContext.requestEntity = inn2

            inp = proxyHostRequest.getRequestBody(request)
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
        public void testbuildProxyRequestHeaders() {

            NFRequestContext.getCurrentContext().unset()

            request = Mockito.mock(HttpServletRequest.class)
            response = Mockito.mock(HttpServletResponse.class)
            RequestContext.getCurrentContext().request = request
            RequestContext.getCurrentContext().response = response
            ZuulNFRequest request = new ZuulNFRequest()
            request = Mockito.spy(request)


            StringTokenizer st = new StringTokenizer("HEADER1,HEADER2", ",")

            Mockito.when(this.request.getHeaderNames()).thenReturn(st)

            MultivaluedMap<String, String> headers = request.buildProxyRequestHeaders(getRequest())
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

            MultivaluedMap<String, String> map = request.buildProxyRequestQueryParams(this.request);

            Assert.assertEquals(map['test'], ['string'])
            Assert.assertEquals(map['ik'], ['moo'])

        }

        @Test
        public void testSetResponse() {
            request = Mockito.mock(HttpServletRequest.class)
            response = Mockito.mock(HttpServletResponse.class)
            HttpClientResponse proxyResponse = Mockito.mock(HttpClientResponse.class)
            RequestContext.getCurrentContext().request = request
            RequestContext.getCurrentContext().response = response
            ZuulNFRequest request = new ZuulNFRequest()
            request = Mockito.spy(request)


            MultivaluedMap<String, String> headers = new MultivaluedMapImpl<String, String>()
            headers.putSingle("test", "test")
            headers.putSingle("content-length", "100")

            InputStream inp = Mockito.mock(InputStream.class)

            Mockito.when(proxyResponse.getStatus()).thenReturn(200)
            Mockito.when(proxyResponse.getRawEntity()).thenReturn(inp)
            Mockito.when(proxyResponse.hasEntity()).thenReturn(true)

            Mockito.when(proxyResponse.headers).thenReturn(headers)
            request.setResponse(proxyResponse)

            Assert.assertEquals(RequestContext.getCurrentContext().getResponseStatusCode(), 200)
            Assert.assertNotNull(RequestContext.getCurrentContext().proxyResponseDataStream)
            Assert.assertTrue(RequestContext.getCurrentContext().proxyResponseHeaders.contains(new Pair('test', "test")))
        }

        @Test
        public void testShouldFilter() {

            RequestContext.currentContext.setProxyHost(new URL("http://www.moldfarm.com"))
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

