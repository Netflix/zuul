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
import com.netflix.zuul.ZuulFilter
import com.netflix.zuul.constants.ZuulConstants
import com.netflix.zuul.context.Debug
import com.netflix.zuul.context.RequestContext
import com.netflix.zuul.util.HTTPRequestUtils
import org.apache.http.*
import org.apache.http.client.HttpClient
import org.apache.http.client.RedirectStrategy
import org.apache.http.client.methods.HttpPost
import org.apache.http.client.methods.HttpPut
import org.apache.http.client.methods.HttpUriRequest
import org.apache.http.config.Registry
import org.apache.http.config.RegistryBuilder
import org.apache.http.conn.HttpClientConnectionManager
import org.apache.http.conn.socket.ConnectionSocketFactory
import org.apache.http.conn.socket.PlainConnectionSocketFactory
import org.apache.http.conn.ssl.SSLConnectionSocketFactory
import org.apache.http.conn.ssl.SSLContexts
import org.apache.http.entity.InputStreamEntity
import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.impl.client.DefaultHttpRequestRetryHandler
import org.apache.http.impl.client.HttpClientBuilder
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager
import org.apache.http.message.BasicHeader
import org.apache.http.message.BasicHttpRequest
import org.apache.http.protocol.HttpContext
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import javax.net.ssl.SSLContext
import javax.servlet.http.HttpServletRequest
import java.util.concurrent.atomic.AtomicReference

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

    private static final AtomicReference<CloseableHttpClient> CLIENT = new AtomicReference<CloseableHttpClient>(newClient());

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

    public SimpleHostRoutingFilter() {
        super();
    }

    private static final HttpClientConnectionManager newConnectionManager() {
        SSLContext sslContext = SSLContexts.createSystemDefault();

        Registry<ConnectionSocketFactory> socketFactoryRegistry = RegistryBuilder.<ConnectionSocketFactory>create()
                .register("http", PlainConnectionSocketFactory.INSTANCE)
                .register("https", new SSLConnectionSocketFactory(sslContext))
                .build();

        HttpClientConnectionManager cm = new PoolingHttpClientConnectionManager(socketFactoryRegistry);
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
        return RequestContext.getCurrentContext().getRouteHost() != null && RequestContext.getCurrentContext().sendZuulResponse()
    }

    private static final void loadClient() {
        final CloseableHttpClient oldClient = CLIENT.get();
        CLIENT.set(newClient())
        if (oldClient != null) {
            CONNECTION_MANAGER_TIMER.schedule(new TimerTask() {
                @Override
                void run() {
                    try {
                        oldClient.close()
                    } catch (Throwable t) {
                        LOG.error("error shutting down old connection manager", t);
                    }
                }
            }, 30000);
        }

    }

    private static final CloseableHttpClient newClient() {
        HttpClientBuilder builder = HttpClientBuilder.create()
        builder.setConnectionManager(newConnectionManager())
        builder.setRetryHandler(new DefaultHttpRequestRetryHandler(0, false))
        builder.setRedirectStrategy(new RedirectStrategy() {
            @Override
            boolean isRedirected(HttpRequest httpRequest, HttpResponse httpResponse, HttpContext httpContext) throws ProtocolException {
                return false
            }

            @Override
            HttpUriRequest getRedirect(HttpRequest httpRequest, HttpResponse httpResponse, HttpContext httpContext) throws ProtocolException {
                return null
            }
        })

        return builder.build()
    }

    Object run() {
        HttpServletRequest request = RequestContext.getCurrentContext().getRequest();
        Header[] headers = buildZuulRequestHeaders(request)
        String verb = getVerb(request);
        InputStream requestEntity = request.getInputStream();
        CloseableHttpClient httpclient = CLIENT.get()

        String uri = request.getRequestURI()
        if (RequestContext.getCurrentContext().requestURI != null) {
            uri = RequestContext.getCurrentContext().requestURI
        }

        try {
            HttpResponse response = forward(httpclient, verb, uri, request, headers, requestEntity)
            Debug.addRequestDebug("ZUUL :: ${uri}")
            Debug.addRequestDebug("ZUUL :: Response statusLine > ${response.getStatusLine()}")
            Debug.addRequestDebug("ZUUL :: Response code > ${response.getStatusLine().statusCode}")
            setResponse(response)
        } catch (Exception e) {
            throw e;
        }
        return null
    }

    InputStream debug(String verb, String uri, HttpServletRequest request, Header[] headers, InputStream requestEntity) {

        if (Debug.debugRequest()) {
            Debug.addRequestDebug("ZUUL:: host=${RequestContext.getCurrentContext().getRouteHost()}")
            headers.each {
                Debug.addRequestDebug("ZUUL:: Header > ${it.name}  ${it.value}")
            }
            String query = request.queryString != null ? "?" + request.queryString : ""

            Debug.addRequestDebug("ZUUL:: > ${verb}  ${uri}${query} HTTP/1.1")
            if (requestEntity != null) {
                requestEntity = debugRequestEntity(requestEntity)
            }

        }
        return requestEntity
    }

    InputStream debugRequestEntity(InputStream inputStream) {
        if (Debug.debugRequestHeadersOnly()) {
            return inputStream
        }

        if (inputStream == null) {
            return null
        }

        String entity = inputStream.getText()
        Debug.addRequestDebug("ZUUL:: Entity > ${entity}")
        return new ByteArrayInputStream(entity.bytes)
    }

    HttpResponse forward(CloseableHttpClient httpclient, String verb, String uri, HttpServletRequest request, Header[] headers, InputStream requestEntity) {

        requestEntity = debug(verb, uri, request, headers, requestEntity)
        HttpHost httpHost = getHttpHost()
        HttpRequest httpRequest;

        switch (verb) {
            case 'POST':
                httpRequest = new HttpPost(uri + getQueryString())
                InputStreamEntity entity = new InputStreamEntity(requestEntity)
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
            return forwardRequest(httpclient, httpHost, httpRequest)
        } finally {
            //httpclient.close();
        }
    }

    HttpResponse forwardRequest(HttpClient httpclient, HttpHost httpHost, HttpRequest httpRequest) {
        return httpclient.execute(httpHost, httpRequest);
    }

    String getQueryString() {
        String encoding = "UTF-8"
        HttpServletRequest request = RequestContext.getCurrentContext().getRequest();
        String currentQueryString = request.getQueryString()
        if (currentQueryString == null || currentQueryString.equals("")) {
            return ""
        }

        String rebuiltQueryString = ""
        for (String keyPair : currentQueryString.split("&")) {
            if (rebuiltQueryString.length() > 0) {
                rebuiltQueryString = rebuiltQueryString + "&"
            }

            if (keyPair.contains("=")) {
                def (name,value) = keyPair.split("=", 2)
                value = URLDecoder.decode(value, encoding)
                value = new URI(null, null, null, value, null).toString().substring(1)
                value = value.replaceAll('&', '%26')
                rebuiltQueryString = rebuiltQueryString + name + "=" + value
            } else {
                def value = URLDecoder.decode(keyPair, encoding)
                value = new URI(null, null, null, value, null).toString().substring(1)
                rebuiltQueryString = rebuiltQueryString + value
            }
        }
        return "?" + rebuiltQueryString
    }

    HttpHost getHttpHost() {
        HttpHost httpHost
        URL host = RequestContext.getCurrentContext().getRouteHost()

        httpHost = new HttpHost(host.getHost(), host.getPort(), host.getProtocol())

        return httpHost
    }


    InputStream getRequestBody(HttpServletRequest request) {
        try {
            return request.getInputStream();
        } catch (IOException e) {
            LOG.warn(e.getMessage())
            return null
        }
    }

    boolean isValidHeader(String name) {
        if (name.toLowerCase().contains("content-length")) {
            return false
        }

        if (name.toLowerCase().equals("host")) {
            return false
        }

        if (!RequestContext.getCurrentContext().responseGZipped) {
            if (name.toLowerCase().contains("accept-encoding")) {
                return false
            }
        }
        return true
    }

    Header[] buildZuulRequestHeaders(HttpServletRequest request) {

        ArrayList<BasicHeader> headers = new ArrayList()
        Enumeration headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String name = ((String) headerNames.nextElement()).toLowerCase();
            String value = request.getHeader(name);
            if (isValidHeader(name)) headers.add(new BasicHeader(name, value))
        }

        Map<String, String> zuulRequestHeaders = RequestContext.getCurrentContext().getZuulRequestHeaders();

        zuulRequestHeaders.keySet().each { String it ->
            String name = it.toLowerCase()
            BasicHeader h = headers.find { BasicHeader he -> he.name == name }
            if (h != null) {
                headers.remove(h)
            }
            headers.add(new BasicHeader(it, zuulRequestHeaders[it]))
        }

        if (RequestContext.getCurrentContext().responseGZipped) {
            headers.add(new BasicHeader("accept-encoding", "deflate, gzip"))
        }

        return headers
    }


     String getVerb(HttpServletRequest request) {
        return getVerb(request.getMethod().toUpperCase());
    }

    String getVerb(String sMethod) {
        if (sMethod == null) {
            return "GET"
        }

        sMethod = sMethod.toLowerCase();

        if (sMethod.equalsIgnoreCase("post")) {
            return "POST"
        }
        if (sMethod.equalsIgnoreCase("put")) {
            return "PUT"
        }
        if (sMethod.equalsIgnoreCase("delete")) {
            return "DELETE"
        }
        if (sMethod.equalsIgnoreCase("options")) {
            return "OPTIONS"
        }
        if (sMethod.equalsIgnoreCase("head")) {
            return "HEAD"
        }
        return "GET"
    }

    void setResponse(HttpResponse response) {
        RequestContext context = RequestContext.getCurrentContext()

        RequestContext.getCurrentContext().set("hostZuulResponse", response)
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


