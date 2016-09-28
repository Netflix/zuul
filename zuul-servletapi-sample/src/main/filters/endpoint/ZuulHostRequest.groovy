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
package endpoint

import com.netflix.config.DynamicIntProperty
import com.netflix.config.DynamicPropertyFactory
import com.netflix.zuul.bytebuf.ByteBufUtils
import com.netflix.zuul.constants.ZuulConstants
import com.netflix.zuul.context.Debug
import com.netflix.zuul.context.SessionContext
import com.netflix.zuul.dependency.httpclient.hystrix.HostCommand
import com.netflix.zuul.filters.BaseFilterTest
import com.netflix.zuul.filters.http.HttpSyncEndpoint
import com.netflix.zuul.message.HeaderName
import com.netflix.zuul.message.Headers
import com.netflix.zuul.message.http.*
import com.netflix.zuul.util.HttpUtils
import com.netflix.zuul.util.ProxyUtils
import io.netty.buffer.ByteBuf
import io.netty.buffer.ByteBufInputStream
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
import org.apache.http.params.CoreConnectionPNames
import org.apache.http.params.HttpParams
import org.apache.http.protocol.HttpContext
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.mockito.runners.MockitoJUnitRunner
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import rx.Observable

import java.util.concurrent.atomic.AtomicReference
import java.util.zip.GZIPInputStream

class ZuulHostRequest extends HttpSyncEndpoint
{
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

    private static final DynamicIntProperty MAX_BODY_SIZE_PROP = DynamicPropertyFactory.getInstance().getIntProperty(
            ZuulConstants.ZUUL_REQUEST_BODY_MAX_SIZE, 25 * 1000 * 1024);



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
    HttpResponseMessage apply(HttpRequestMessage request)
    {
        debug(request.getContext(), request)

        URL routeHost = request.getContext().getRouteHost()
        String verb = getVerb(request.getMethod());
        String path = request.getPath()
        HttpClient httpclient = CLIENT.get()

        InputStream requestBodyInput = null;
        if (request.getBodyStream() != null) {
            requestBodyInput = ByteBufUtils.aggregate(request.getBodyStream(), MAX_BODY_SIZE_PROP.get())
                    .map({ bb ->
                        return new ByteBufInputStream(bb)
                    })
                    .toBlocking().last()
        }

        HttpResponse ribbonResponse = forward(httpclient, routeHost, verb, path,
                request.getHeaders(), request.getQueryParams(), requestBodyInput)

        HttpResponseMessage response = createHttpResponseMessage(ribbonResponse, request)
        debugResponse(response)

        return response
    }

    void debug(SessionContext context, HttpRequestMessage request) {

        if (Debug.debugRequest(context)) {

            request.getHeaders().entries().each {
                Debug.addRequestDebug(context, "REQUEST_INBOUND:: > ${it.key}  ${it.value}")
            }
            String query = ""
            request.getQueryParams().entries().each {
                query += it.key + "=" + it.value + "&"
            }

            Debug.addRequestDebug(context, "REQUEST_INBOUND:: > ${request.getMethod()}  ${request.getPath()}?${query} ${request.getProtocol()}")

            if (request.getBody() != null) {
                if (!Debug.debugRequestHeadersOnly()) {
                    String entity = new ByteArrayInputStream(request.getBody()).getText()
                    Debug.addRequestDebug(context, "REQUEST_INBOUND:: > ${entity}")
                }
            }
        }
    }

    def HttpResponse forward(HttpClient httpclient, URL routeHost, String verb, String path, Headers headers, HttpQueryParams params, InputStream requestBodyInput) {

        org.apache.http.HttpHost httpHost = getHttpHost(routeHost)
        URI uri
        if (params != null && params.entries().size() > 0) {
            uri = URI.create(path + "?" + params.toEncodedString())
        } else {
            uri = URI.create(path)
        }

        org.apache.http.HttpRequest httpRequest;

        switch (verb) {
            case 'POST':
                httpRequest = new HttpPost(uri)
                if (requestBodyInput != null) {
                    InputStreamEntity entity = new InputStreamEntity(requestBodyInput)
                    httpRequest.setEntity(entity)
                }
                break
            case 'PUT':
                httpRequest = new HttpPut(uri)
                if (requestBodyInput != null) {
                    InputStreamEntity entity = new InputStreamEntity(requestBodyInput)
                    httpRequest.setEntity(entity)
                }
                break;
            default:
                httpRequest = new BasicHttpRequest(verb, uri.toASCIIString())
        }

        try {
            // Copy the request headers.
            headers.entries().each {
                if (ProxyUtils.isValidRequestHeader(it.getName())) {
                    httpRequest.addHeader(it.getKey(), it.getValue())
                }
            }

            // Make the request.
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

    void debugResponse(HttpResponseMessage resp) {

        SessionContext context = resp.getContext()

        // Debug
        if (Debug.debugRequest(context)) {

            resp.getHeaders().entries().each { header ->
                Debug.addRequestDebug(context, "RESPONSE_INBOUND:: < ${header.getKey()}, ${header.getValue()}")
            }

            if (resp.getBody()) {
                InputStream inStream = new ByteArrayInputStream(resp.getBody());
                if (HttpUtils.isGzipped(resp.getHeaders()))
                    inStream = new GZIPInputStream(inStream);
                String responseEntity = inStream.getText()
                Debug.addRequestDebug(context, "RESPONSE_INBOUND:: < ${responseEntity}")
            }
        }
    }

    protected HttpResponseMessage createHttpResponseMessage(HttpResponse ribbonResp, HttpRequestMessage request)
    {
        // Convert to a zuul response object.
        HttpResponseMessage respMsg = new HttpResponseMessageImpl(request.getContext(), request, 500);
        respMsg.setStatus(ribbonResp.getStatusLine().getStatusCode());

        // Headers.
        for (Header header : ribbonResp.getAllHeaders()) {
            HeaderName hn = HttpHeaderNames.get(header.getName())
            if (ProxyUtils.isValidResponseHeader(hn)) {
                respMsg.getHeaders().add(hn, header.getValue());
            }
        }

        // Body.
        if (ribbonResp.getEntity()) {
            Observable<ByteBuf> respBodyObs = ByteBufUtils.fromInputStream(ribbonResp.getEntity().getContent())
            respMsg.setBodyStream(respBodyObs)
        }

        return respMsg
    }

    @RunWith(MockitoJUnitRunner.class)
    public static class TestUnit extends BaseFilterTest
    {
        @Test
        public void testSetResponse() {

            Debug.setDebugRouting(context, false)
            Debug.setDebugRequest(context, false)

            ZuulHostRequest filter = new ZuulHostRequest()
            filter = Mockito.spy(filter)

            Header[] headers = [
                new BasicHeader("test", "test"),
                new BasicHeader("content-length", "100")
            ]

            HttpResponse httpResponse = Mockito.mock(HttpResponse.class)
            StatusLine status = Mockito.mock(StatusLine.class)
            Mockito.when(status.getStatusCode()).thenReturn(200)
            Mockito.when(httpResponse.getStatusLine()).thenReturn(status)

            byte[] body = "test".bytes
            HttpEntity entity = new ByteArrayEntity(body)
            Mockito.when(httpResponse.getEntity()).thenReturn(entity)
            Mockito.when(httpResponse.getAllHeaders()).thenReturn(headers)
            response = filter.createHttpResponseMessage(httpResponse, request)

            Assert.assertEquals(response.getStatus(), 200)
            byte[] respBodyBytes = ByteBufUtils.toBytes(response.getBodyStream().toBlocking().single())
            Assert.assertEquals(body.length, respBodyBytes.length)
            Assert.assertTrue(response.getHeaders().contains('test', "test"))
        }

        @Test
        public void testShouldFilter() {
            context.setRouteHost(new URL("http://www.moldfarm.com"))
            ZuulHostRequest filter = new ZuulHostRequest()
            Assert.assertTrue(filter.shouldFilter(request))
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
