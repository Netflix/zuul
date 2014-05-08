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



import com.netflix.config.DynamicIntProperty
import com.netflix.config.DynamicPropertyFactory
import com.netflix.zuul.ZuulFilter
import com.netflix.zuul.constants.ZuulConstants
import com.netflix.zuul.context.Debug
import com.netflix.zuul.context.RequestContext
import com.netflix.zuul.util.HTTPRequestUtils
import org.apache.http.Header
import org.apache.http.HttpHost
import org.apache.http.HttpRequest
import org.apache.http.HttpResponse
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
import org.apache.http.params.CoreConnectionPNames
import org.apache.http.params.HttpParams
import org.apache.http.protocol.HttpContext
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import javax.servlet.http.HttpServletRequest
import java.util.concurrent.atomic.AtomicReference
import java.util.zip.GZIPInputStream

class SimpleHostRoutingFilter extends ZuulFilter {

    public static final String CONTENT_ENCODING = "Content-Encoding";

    private static final Logger LOG = LoggerFactory.getLogger(SimpleHostRoutingFilter.class);
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

    public SimpleHostRoutingFilter() {}

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
            HttpResponse zuulResponse = forwardRequest(httpclient, httpHost, httpRequest)
            return zuulResponse
        } finally {
            // When HttpClient instance is no longer needed,
            // shut down the connection manager to ensure
            // immediate deallocation of all system resources
//            httpclient.getConnectionManager().shutdown();
        }

    }

    HttpResponse forwardRequest(HttpClient httpclient, HttpHost httpHost, HttpRequest httpRequest) {
        return httpclient.execute(httpHost, httpRequest);
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
            requestEntity = request.getInputStream();
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

        def headers = new ArrayList()
        Enumeration headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String name = (String) headerNames.nextElement();
            String value = request.getHeader(name);
            if (isValidHeader(name)) headers.add(new BasicHeader(name, value))
        }

        Map zuulRequestHeaders = RequestContext.getCurrentContext().getZuulRequestHeaders();

        zuulRequestHeaders.keySet().each {
            String name = it.toLowerCase()
            BasicHeader h = headers.find { BasicHeader he -> he.name == name }
            if (h != null) {
                headers.remove(h)
            }
            headers.add(new BasicHeader((String) it, (String) zuulRequestHeaders[it]))
        }

        if (RequestContext.currentContext.responseGZipped) {
            headers.add(new BasicHeader("accept-encoding", "deflate, gzip"))
        }
        return headers
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

}


