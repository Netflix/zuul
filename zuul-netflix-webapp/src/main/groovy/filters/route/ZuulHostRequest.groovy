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
import com.netflix.zuul.context.Attributes
import com.netflix.zuul.context.Debug
import com.netflix.zuul.context.Headers
import com.netflix.zuul.context.HttpQueryParams
import com.netflix.zuul.context.HttpRequestMessage
import com.netflix.zuul.context.HttpResponseMessage
import com.netflix.zuul.context.RequestContext
import com.netflix.zuul.context.SessionContext
import com.netflix.zuul.dependency.httpclient.hystrix.HostCommand
import com.netflix.zuul.util.HttpUtils
import org.apache.commons.io.IOUtils
import org.apache.http.*
import org.apache.http.client.HttpClient
import org.apache.http.client.methods.HttpPost
import org.apache.http.client.methods.HttpPut
import org.apache.http.client.params.ClientPNames
import org.apache.http.conn.ClientConnectionManager
import org.apache.http.conn.scheme.PlainSocketFactory
import org.apache.http.conn.scheme.Scheme
import org.apache.http.conn.scheme.SchemeRegistry
import org.apache.http.entity.ByteArrayEntity
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

    @Override
    boolean shouldFilter(SessionContext ctx) {
        return ctx.getAttributes().getRouteHost() != null && ctx.getAttributes().sendZuulResponse()
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

    @Override
    SessionContext apply(SessionContext context)
    {
        HttpRequestMessage request = context.getRequest()

        debug(context, request)

        URL routeHost = context.getAttributes().getRouteHost()
        String verb = getVerb(request.getMethod());
        String path = request.getPath()
        HttpClient httpclient = CLIENT.get()

        try {
            com.netflix.client.http.HttpResponse response = forward(httpclient, routeHost, verb, path,
                    request.getHeaders(), request.getQueryParams(), request.getBody())

            setResponse(context, response)
        }
        catch (Exception e) {
            throw e;
        }
        return null
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

    /*
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
     */

    def HttpResponse forward(HttpClient httpclient, URL routeHost, String verb, String path, Headers headers, HttpQueryParams params, byte[] requestBody) {

        org.apache.http.HttpHost httpHost = getHttpHost(routeHost)
        URI uri
        if (params != null && params.getEntries().size() > 0) {
            String queryString = params.getEntries().each{ URLEncoder.encode(it, "UTF-8") }.join("&")
            uri = new URI(null, null, path, queryString, null)
        } else {
            uri = URI.create(path)
        }

        org.apache.http.HttpRequest httpRequest;

        switch (verb) {
            case 'POST':
                httpRequest = new HttpPost(uri)
                ByteArrayEntity entity = new ByteArrayEntity(requestBody)
                httpRequest.setEntity(entity)
                break
            case 'PUT':
                httpRequest = new HttpPut(uri)
                ByteArrayEntity entity = new ByteArrayEntity(requestBody)
                httpRequest.setEntity(entity)
                break;
            default:
                httpRequest = new BasicHttpRequest(verb, uri)
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

    HttpHost getHttpHost(URL routeHost) {
        HttpHost httpHost

        httpHost = new HttpHost(routeHost.getHost(), routeHost.getPort(), routeHost.getProtocol())

        return httpHost
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

    void setResponse(SessionContext context, HttpResponse proxyResp) {

        HttpResponseMessage resp = context.getResponse()

        resp.setStatus(proxyResp.getStatusLine().getStatusCode());

        // Collect the response headers.
        proxyResp.getAllHeaders()?.each { Header header ->
            if (isValidHeader(header)) {
                resp.getHeaders().add(header.name, header.value)
                Debug.addRequestDebug(context, "ORIGIN_RESPONSE:: < ${header.name}, ${header.value}")
            }
        }

        // Body.
        byte[] bodyBytes
        if (proxyResp.getEntity()) {
            // Read the request body inputstream into a byte array.
            try {
                bodyBytes = IOUtils.toByteArray(proxyResp.getEntity().getContent())
                resp.setBody(bodyBytes)
            }
            catch (SocketTimeoutException e) {
                // This can happen if the request body is smaller than the size specified in the
                // Content-Length header, and using tomcat APR connector.
                LOG.error("SocketTimeoutException reading request body from inputstream. error=" + String.valueOf(e.getMessage()));
            }
        }

        // Debug
        if (Debug.debugRequest(context)) {

            proxyResp.getAllHeaders()?.each { Header header ->
                Debug.addRequestDebug(context, "ORIGIN_RESPONSE:: < ${header.name}, ${header.value}")
            }

            if (bodyBytes) {
                InputStream inStream = new ByteArrayInputStream(bodyBytes);
                if (HttpUtils.isGzipped(resp.getHeaders()))
                    inStream = new GZIPInputStream(inStream);
                String responseEntity = inStream.getText()
                Debug.addRequestDebug(context, "ORIGIN_RESPONSE:: < ${responseEntity}")
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
        com.netflix.client.http.HttpResponse proxyResp

        @Mock
        HttpRequestMessage request

        SessionContext ctx
        HttpResponseMessage response

        @Before
        public void setup()
        {
            response = new HttpResponseMessage(200)
            ctx = new SessionContext(request, response)
        }

        @Test
        public void testSetResponse() {

            Debug.setDebugRouting(ctx, false)
            Debug.setDebugRequest(ctx, false)

            ZuulHostRequest filter = new ZuulHostRequest()
            filter = Mockito.spy(filter)

            Header[] headers = new Header[2]
            headers[0] = new BasicHeader("test", "test")
            headers[1] = new BasicHeader("content-length", "100")



            HttpResponse httpResponse = Mockito.mock(HttpResponse.class)
            BasicStatusLine status = Mockito.mock(BasicStatusLine.class)
            Mockito.when(httpResponse.getStatusLine()).thenReturn(status)
            Mockito.when(httpResponse.getStatusLine().statusCode).thenReturn(200)
            HttpEntity entity = Mockito.mock(HttpEntity.class)
            byte[] body = "test".bytes
            ByteArrayInputStream inp = new ByteArrayInputStream(body)
            Mockito.when(entity.content).thenReturn(inp)
            Mockito.when(httpResponse.entity).thenReturn(entity)
            Mockito.when(httpResponse.getAllHeaders()).thenReturn(headers)
            filter.setResponse(ctx, httpResponse)

            Assert.assertEquals(response.getStatus(), 200)
            Assert.assertEquals(response.getBody().length, body.length)
            Assert.assertTrue(response.getHeaders().contains('test', "test"))
        }

        @Test
        public void testShouldFilter() {
            ctx.getAttributes().setRouteHost(new URL("http://www.moldfarm.com"))
            ZuulHostRequest filter = new ZuulHostRequest()
            Assert.assertTrue(filter.shouldFilter(ctx))
        }


        @Test
        public void testGetHost() {

            ZuulHostRequest filter = new ZuulHostRequest()

            URL url = new URL("http://www.moldfarm.com")
            HttpHost host = filter.getHttpHost(url)
            Assert.assertNotNull(host)
            Assert.assertEquals(host.hostName, "www.moldfarm.com")
            Assert.assertEquals(host.port, -1)
            Assert.assertEquals(host.schemeName, "http")

            url = new URL("https://www.moldfarm.com:8000")
            host = filter.getHttpHost(url)
            Assert.assertNotNull(host)
            Assert.assertEquals(host.hostName, "www.moldfarm.com")
            Assert.assertEquals(host.port, 8000)
            Assert.assertEquals(host.schemeName, "https")
        }

    }
}
