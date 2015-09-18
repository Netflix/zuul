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

import com.netflix.config.DynamicIntProperty
import com.netflix.config.DynamicPropertyFactory
import com.netflix.util.Pair
import com.netflix.zuul.ZuulFilter
import com.netflix.zuul.constants.ZuulConstants
import com.netflix.zuul.context.Debug
import com.netflix.zuul.context.NFRequestContext
import com.netflix.zuul.context.RequestContext
import com.netflix.zuul.dependency.httpclient.hystrix.HostCommand
import com.netflix.zuul.util.HTTPRequestUtils
import org.apache.http.*
import org.apache.http.client.HttpClient
import org.apache.http.client.methods.HttpPost
import org.apache.http.client.methods.HttpPut
import org.apache.http.client.params.ClientPNames
import org.apache.http.conn.ClientConnectionManager
import org.apache.http.conn.scheme.PlainSocketFactory
import org.apache.http.conn.scheme.Scheme
import org.apache.http.conn.scheme.SchemeRegistry
import org.apache.http.entity.InputStreamEntity
import org.apache.http.impl.client.DefaultHttpClient
import org.apache.http.impl.client.DefaultHttpRequestRetryHandler
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager
import org.apache.http.message.BasicHeader
import org.apache.http.message.BasicHttpRequest
import org.apache.http.message.BasicStatusLine
import org.apache.http.params.CoreConnectionPNames
import org.apache.http.params.HttpParams
import org.apache.http.protocol.HttpContext
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
import java.util.concurrent.atomic.AtomicReference
import java.util.zip.GZIPInputStream

class ZuulHostRequest extends ZuulFilter {

    public static final String CONTENT_ENCODING = "Content-Encoding";

    private static final Logger LOG = LoggerFactory.getLogger(ZuulHostRequest.class);
    private static final Runnable CLIENTLOADER = new Runnable() {
        @Override
        void run() {
            loadClient();
        }
    }

    private static final DynamicIntProperty SOCKET_TIMEOUT =
        DynamicPropertyFactory.getInstance().getIntProperty(ZuulConstants.ZUUL_HOST_SOCKET_TIMEOUT_MILLIS, 10000)

    private static final DynamicIntProperty CONNECTION_TIMEOUT =
        DynamicPropertyFactory.getInstance().getIntProperty(ZuulConstants.ZUUL_HOST_CONNECT_TIMEOUT_MILLIS, 2000)



    private static final AtomicReference<HttpClient> CLIENT = new AtomicReference<HttpClient>(newClient());

    private static final Timer CONNECTION_MANAGER_TIMER = new Timer(true);

    // cleans expired connections at an interval
    static {
        SOCKET_TIMEOUT.addCallback(CLIENTLOADER)
        CONNECTION_TIMEOUT.addCallback(CLIENTLOADER)
        CONNECTION_MANAGER_TIMER.schedule(new TimerTask() {
            @Override
            void run() {
                try {
                    final HttpClient hc = CLIENT.get();
                    if (hc == null) return;
                    hc.getConnectionManager().closeExpiredConnections();
                } catch (Throwable t) {
                    LOG.error("error closing expired connections", t);
                }
            }
        }, 30000, 5000)
    }

    public ZuulHostRequest() {
        super();
    }

    private static final ClientConnectionManager newConnectionManager() {
        SchemeRegistry schemeRegistry = new SchemeRegistry();
        schemeRegistry.register(
                new Scheme("http", 80, PlainSocketFactory.getSocketFactory()));

        ClientConnectionManager cm = new ThreadSafeClientConnManager(schemeRegistry);
        cm.setMaxTotal(Integer.parseInt(System.getProperty("zuul.max.host.connections", "200")));
        cm.setDefaultMaxPerRoute(Integer.parseInt(System.getProperty("zuul.max.host.connections", "20")));
        return cm;
    }

    @Override
    String filterType() {
        return 'route'
    }

    @Override
    int filterOrder() {
        return 100
    }

    boolean shouldFilter() {
        return RequestContext.currentContext.getRouteHost() != null && RequestContext.currentContext.sendZuulResponse()
    }

    private static final void loadClient() {
        final HttpClient oldClient = CLIENT.get();
        CLIENT.set(newClient())
        if (oldClient != null) {
            CONNECTION_MANAGER_TIMER.schedule(new TimerTask() {
                @Override
                void run() {
                    try {
                        oldClient.getConnectionManager().shutdown();
                    } catch (Throwable t) {
                        LOG.error("error shutting down old connection manager", t);
                    }
                }
            }, 30000);
        }

    }

    private static final HttpClient newClient() {
        // I could statically cache the connection manager but we will probably want to make some of its properties
        // dynamic in the near future also
        HttpClient httpclient = new DefaultHttpClient(newConnectionManager());
        HttpParams httpParams = httpclient.getParams();
        httpParams.setIntParameter(CoreConnectionPNames.SO_TIMEOUT, SOCKET_TIMEOUT.get())
        httpParams.setIntParameter(CoreConnectionPNames.CONNECTION_TIMEOUT, CONNECTION_TIMEOUT.get())
        httpclient.setHttpRequestRetryHandler(new DefaultHttpRequestRetryHandler(0, false))
        httpParams.setParameter(ClientPNames.COOKIE_POLICY, org.apache.http.client.params.CookiePolicy.IGNORE_COOKIES);
        httpclient.setRedirectStrategy(new org.apache.http.client.RedirectStrategy() {
            @Override
            boolean isRedirected(HttpRequest httpRequest, HttpResponse httpResponse, HttpContext httpContext) {
                return false
            }

            @Override
            org.apache.http.client.methods.HttpUriRequest getRedirect(HttpRequest httpRequest, HttpResponse httpResponse, HttpContext httpContext) {
                return null
            }
        })
        return httpclient
    }

    Object run() {
        HttpServletRequest request = RequestContext.currentContext.getRequest();
        Header[] headers = buildZuulRequestHeaders(request)
        String verb = getVerb(request);
        InputStream requestEntity = getRequestBody(request)
        HttpClient httpclient = CLIENT.get()

        String uri = request.getRequestURI()
        if (RequestContext.currentContext.requestURI != null) {
            uri = RequestContext.currentContext.requestURI
        }

        try {
            HttpResponse response = forward(httpclient, verb, uri, request, headers, requestEntity)
            setResponse(response)
        } catch (Exception e) {
            throw e;
        }
        return null
    }

    def fallback() {
        final NFRequestContext ctx = NFRequestContext.getCurrentContext();

        // disables overrides to ensure that same override logic is not applied and matched
        // this would only be necessary if we were re-running the route filter, but I am leaving it here
        ctx.setDisableOverrides(true);

        // ideally I could re-run the route, but that is not working out for me
        //        new Routing().run();

        // for now I will hack this to default to the previously set VIP, with gzip
        ctx.removeRouteHost();
        ctx.setResponseGZipped(true)
        GroovyProcessor.instance.runFilters("route")
    }

    def InputStream debug(HttpClient httpclient, String verb, String uri, HttpServletRequest request, Header[] headers, InputStream requestEntity) {

        if (Debug.debugRequest()) {

            Debug.addRequestDebug("ZUUL:: host=${RequestContext.currentContext.getRouteHost()}")

            headers.each {
                Debug.addRequestDebug("ZUUL::> ${it.name}  ${it.value}")
            }
            String query = request.queryString

            Debug.addRequestDebug("ZUUL:: > ${verb}  ${uri}?${query} HTTP/1.1")
            if (requestEntity != null) {
                requestEntity = debugRequestEntity(requestEntity)
            }

        }
        return requestEntity
    }

    InputStream debugRequestEntity(InputStream inputStream) {
        if (Debug.debugRequestHeadersOnly()) return inputStream
        if (inputStream == null) return null
        String entity = inputStream.getText()
        Debug.addRequestDebug("ZUUL::> ${entity}")
        return new ByteArrayInputStream(entity.bytes)
    }

    def HttpResponse forward(HttpClient httpclient, String verb, String uri, HttpServletRequest request, Header[] headers, InputStream requestEntity) {

        requestEntity = debug(httpclient, verb, uri, request, headers, requestEntity)

        org.apache.http.HttpHost httpHost

        httpHost = getHttpHost()

        org.apache.http.HttpRequest httpRequest;

        switch (verb) {
            case 'POST':
                httpRequest = new HttpPost(uri + getQueryString())
                InputStreamEntity entity = new InputStreamEntity(requestEntity, request.getContentLength())
                httpRequest.setEntity(entity)
                break
            case 'PUT':
                httpRequest = new HttpPut(uri + getQueryString())
                InputStreamEntity entity = new InputStreamEntity(requestEntity, request.getContentLength())
                httpRequest.setEntity(entity)
                break;
            default:
                httpRequest = new BasicHttpRequest(verb, uri + getQueryString())
        }

        try {
            httpRequest.setHeaders(headers)
            HttpResponse zuulResponse = executeHttpRequest(httpclient, httpHost, httpRequest)
            return zuulResponse


        } finally {
            // When HttpClient instance is no longer needed,
            // shut down the connection manager to ensure
            // immediate deallocation of all system resources
//            httpclient.getConnectionManager().shutdown();
        }

    }

    HttpResponse executeHttpRequest(HttpClient httpclient, HttpHost httpHost, HttpRequest httpRequest) {
        HostCommand command = new HostCommand(httpclient, httpHost, httpRequest)
        command.execute();
    }

    String getQueryString() {
        HttpServletRequest request = RequestContext.currentContext.getRequest();
        String query = request.getQueryString()
        return (query != null) ? "?${query}" : "";
    }

    HttpHost getHttpHost() {
        HttpHost httpHost
        URL host = RequestContext.currentContext.getRouteHost()

        httpHost = new HttpHost(host.getHost(), host.getPort(), host.getProtocol())

        return httpHost
    }


    def getRequestBody(HttpServletRequest request) {
        Object requestEntity = null;
        try {
            requestEntity = NFRequestContext.currentContext.requestEntity
            if (requestEntity == null) {
                requestEntity = request.getInputStream();
            }
        } catch (IOException e) {
            //no requestBody is ok.
        }
        return requestEntity
    }

    boolean isValidHeader(String name) {
        if (name.toLowerCase().contains("content-length")) return false;
        if (!RequestContext.currentContext.responseGZipped) {
            if (name.toLowerCase().contains("accept-encoding")) return false;
        }
        return true;
    }


    def Header[] buildZuulRequestHeaders(HttpServletRequest request) {
        Map headers = new HashMap()
        Enumeration headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String name = (String) headerNames.nextElement();

            final StringBuilder valBuilder = new StringBuilder();
            for (final Enumeration vals = request.getHeaders(name); vals.hasMoreElements();) {
                valBuilder.append(vals.nextElement());
                valBuilder.append(',')
            }
            valBuilder.setLength(valBuilder.length()-1)

            if (isValidHeader(name)) {
                headers.put(name.toLowerCase(),new BasicHeader(name, valBuilder.toString()))
            }
        }

        Map zuulRequestHeaders = RequestContext.getCurrentContext().getZuulRequestHeaders();

        zuulRequestHeaders.keySet().each {
            headers.put(it.toLowerCase(),new BasicHeader((String) it, (String) zuulRequestHeaders[it]))
        }

        if (RequestContext.currentContext.responseGZipped) {
            headers.put("accept-encoding",new BasicHeader("accept-encoding", "deflate, gzip"))
        }
        return headers.values().toArray()
    }

    String getVerb(HttpServletRequest request) {
        String sMethod = request.getMethod();
        return sMethod.toUpperCase();
    }

    String getVerb(String sMethod) {
        if (sMethod == null) return "GET";
        sMethod = sMethod.toLowerCase();
        if (sMethod.equalsIgnoreCase("post")) return "POST"
        if (sMethod.equalsIgnoreCase("put")) return "PUT"
        if (sMethod.equalsIgnoreCase("delete")) return "DELETE"
        if (sMethod.equalsIgnoreCase("options")) return "OPTIONS"
        if (sMethod.equalsIgnoreCase("head")) return "HEAD"
        return "GET"
    }

    void setResponse(HttpResponse response) {
        RequestContext context = RequestContext.getCurrentContext()

        RequestContext.currentContext.set("hostZuulResponse", response)
        RequestContext.getCurrentContext().setResponseStatusCode(response.getStatusLine().statusCode)
        RequestContext.getCurrentContext().responseDataStream = response?.entity?.content

        boolean isOriginResponseGzipped = false

        for (Header h : response.getHeaders(CONTENT_ENCODING)) {
            if (HTTPRequestUtils.getInstance().isGzipped(h.value)) {
                isOriginResponseGzipped = true;
                break;
            }
        }
        context.setResponseGZipped(isOriginResponseGzipped);


        if (Debug.debugRequest()) {
            response.getAllHeaders()?.each { Header header ->
                if (isValidHeader(header)) {
                    RequestContext.getCurrentContext().addZuulResponseHeader(header.name, header.value);
                    Debug.addRequestDebug("ORIGIN_RESPONSE:: < ${header.name}, ${header.value}")
                }
            }

            if (context.responseDataStream) {
                byte[] origBytes = context.getResponseDataStream().bytes
                ByteArrayInputStream byteStream = new ByteArrayInputStream(origBytes)
                InputStream inputStream = byteStream
                if (RequestContext.currentContext.responseGZipped) {
                    inputStream = new GZIPInputStream(byteStream);
                }


                context.setResponseDataStream(new ByteArrayInputStream(origBytes))
            }

        } else {
            response.getAllHeaders()?.each { Header header ->
                RequestContext ctx = RequestContext.getCurrentContext()
                ctx.addOriginResponseHeader(header.name, header.value)

                if (header.name.equalsIgnoreCase("content-length"))
                    ctx.setOriginContentLength(header.value)

                if (isValidHeader(header)) {
                    ctx.addZuulResponseHeader(header.name, header.value);
                }
            }
        }

    }

    boolean isValidHeader(Header header) {
        switch (header.name.toLowerCase()) {
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
        public void testHeaderResponse() {

            ZuulHostRequest request = new ZuulHostRequest()
            Header header = new BasicHeader("test", "test")
            Header header1 = new BasicHeader("content-length", "100")
            Header header2 = new BasicHeader("content-encoding", "test")

            Assert.assertTrue(request.isValidHeader(header))
            Assert.assertFalse(request.isValidHeader(header1))
            Assert.assertFalse(request.isValidHeader(header2))


        }

        @Test
        public void testBuildZuulRequestHeaders() {

            request = Mockito.mock(HttpServletRequest.class)
            response = Mockito.mock(HttpServletResponse.class)
            RequestContext.getCurrentContext().request = request
            RequestContext.getCurrentContext().response = response
            RequestContext.getCurrentContext().setResponseGZipped(true);
            ZuulHostRequest request = new ZuulHostRequest()
            request = Mockito.spy(request)


            StringTokenizer st = new StringTokenizer("HEADER1,HEADER2", ",")

            Mockito.when(this.request.getHeaderNames()).thenReturn(st)

            Header[] headers = request.buildZuulRequestHeaders(getRequest())
            Assert.assertTrue(headers.any {
                return (it.name == "accept-encoding" &&
                        it.value == "deflate, gzip")
            })

        }

        @Test
        public void testSetResponse() {
            request = Mockito.mock(HttpServletRequest.class)
            response = Mockito.mock(HttpServletResponse.class)
            Debug.setDebugRouting(false)
            Debug.setDebugRequest(false)
            RequestContext.getCurrentContext().request = request
            RequestContext.getCurrentContext().response = response
            ZuulHostRequest request = new ZuulHostRequest()
            request = Mockito.spy(request)
            Header[] headers = new Header[2]
            headers[0] = new BasicHeader("test", "test")
            headers[1] = new BasicHeader("content-length", "100")


            HttpResponse httpResponse = Mockito.mock(HttpResponse.class)
            BasicStatusLine status = Mockito.mock(BasicStatusLine.class)
            Mockito.when(httpResponse.getStatusLine()).thenReturn(status)
            Mockito.when(httpResponse.getStatusLine().statusCode).thenReturn(200)
            HttpEntity entity = Mockito.mock(HttpEntity.class)
            InputStream inp = new ByteArrayInputStream("test".bytes)
            Mockito.when(entity.content).thenReturn(inp)
            Mockito.when(httpResponse.entity).thenReturn(entity)
            Mockito.when(httpResponse.getAllHeaders()).thenReturn(headers)
            request.setResponse(httpResponse)

            Assert.assertEquals(RequestContext.getCurrentContext().getResponseStatusCode(), 200)
            Assert.assertEquals(RequestContext.getCurrentContext().responseDataStream, inp)
            Assert.assertTrue(RequestContext.getCurrentContext().zuulResponseHeaders.contains(new Pair('test', "test")))
//            assertNull(RequestContext.getCurrentContext().zuulResponseHeaders['content-length'])

        }

        @Test
        public void testShouldFilter() {
            RequestContext.currentContext.unset()
            RequestContext.currentContext.setRouteHost(new URL("http://www.moldfarm.com"))
            ZuulHostRequest filter = new ZuulHostRequest()
            Assert.assertTrue(filter.shouldFilter())
        }

        @Test
        public void testGetRequestBody() {
            this.request = Mockito.mock(HttpServletRequest.class)
            ServletInputStream inn = Mockito.mock(ServletInputStream.class)
            RequestContext.currentContext.request = this.request

            ZuulHostRequest routeHostRequest = new ZuulHostRequest()

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
        public void testGetVerbRequest() {
            this.request = Mockito.mock(HttpServletRequest.class)
            RequestContext.currentContext.request = this.request

            ZuulHostRequest routeHostRequest = new ZuulHostRequest()

            Mockito.when(request.getMethod()).thenReturn("GET")
            String verb = routeHostRequest.getVerb(this.request)
            Assert.assertEquals(verb, 'GET')

            Mockito.when(request.getMethod()).thenReturn("get")
            verb = routeHostRequest.getVerb(this.request)
            Assert.assertEquals(verb, 'GET')

            Mockito.when(request.getMethod()).thenReturn("POST")
            verb = routeHostRequest.getVerb(this.request)
            Assert.assertEquals(verb, 'POST')

            Mockito.when(request.getMethod()).thenReturn("PUT")
            verb = routeHostRequest.getVerb(this.request)
            Assert.assertEquals(verb, 'PUT')

            Mockito.when(request.getMethod()).thenReturn("DELETE")
            verb = routeHostRequest.getVerb(this.request)
            Assert.assertEquals(verb, 'DELETE')

        }


        @Test
        public void testGetVerb() {

            ZuulHostRequest request = new ZuulHostRequest()
            String verb = request.getVerb("get")
            Assert.assertEquals('GET', verb)
            verb = request.getVerb("Get")
            Assert.assertEquals('GET', verb)

            verb = request.getVerb("post")
            Assert.assertEquals('POST', verb)
            verb = request.getVerb("POST")
            Assert.assertEquals('POST', verb)

            verb = request.getVerb("PUT")
            Assert.assertEquals('PUT', verb)
            verb = request.getVerb("put")
            Assert.assertEquals('PUT', verb)

            verb = request.getVerb("OPTIONS")
            Assert.assertEquals('OPTIONS', verb)
            verb = request.getVerb("options")
            Assert.assertEquals('OPTIONS', verb)

            verb = request.getVerb("delete")
            Assert.assertEquals('DELETE', verb)
            verb = request.getVerb("Delete")
            Assert.assertEquals('DELETE', verb)

            verb = request.getVerb("head")
            Assert.assertEquals('HEAD', verb)
            verb = request.getVerb("HEAD")
            Assert.assertEquals('HEAD', verb)
        }


        @Test
        public void testGetHost() {

            ZuulHostRequest request = new ZuulHostRequest()

            URL url = new URL("http://www.moldfarm.com")
            RequestContext.currentContext.routeHost = url
            HttpHost host = request.getHttpHost()
            Assert.assertNotNull(host)
            Assert.assertEquals(host.hostName, "www.moldfarm.com")
            Assert.assertEquals(host.port, -1)
            Assert.assertEquals(host.schemeName, "http")

            url = new URL("https://www.moldfarm.com:8000")
            RequestContext.currentContext.routeHost = url
            host = request.getHttpHost()
            Assert.assertNotNull(host)
            Assert.assertEquals(host.hostName, "www.moldfarm.com")
            Assert.assertEquals(host.port, 8000)
            Assert.assertEquals(host.schemeName, "https")


        }

    }
}
