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
package com.netflix.zuul.context;


import com.netflix.config.DynamicIntProperty;
import com.netflix.config.DynamicPropertyFactory;
import com.netflix.zuul.stats.Timing;
import io.netty.handler.codec.http.Cookie;
import io.netty.handler.codec.http.CookieDecoder;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;
import rx.Observable;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Set;

/**
 * User: michaels
 * Date: 2/24/15
 * Time: 10:54 AM
 */
public class HttpRequestMessage extends ZuulMessage
{
    private static final DynamicIntProperty MAX_BODY_SIZE_PROP = DynamicPropertyFactory.getInstance().getIntProperty(
            "zuul.HttpRequestMessage.body.max.size", 15 * 1000 * 1024);

    private String protocol;
    private String method;
    private String path;
    private HttpQueryParams queryParams;
    private String clientIp;
    private String scheme;
    private int port;
    private String serverName;

    private HttpRequestInfo originalRequestInfo = null;


    public HttpRequestMessage(SessionContext context, String protocol, String method, String path,
                              HttpQueryParams queryParams, Headers headers, String clientIp, String scheme,
                              int port, String serverName)
    {
        super(context, headers);

        this.protocol = protocol;
        this.method = method;
        this.path = path;
        // Don't allow this to be null.
        this.queryParams = queryParams == null ? new HttpQueryParams() : queryParams;
        this.clientIp = clientIp;
        this.scheme = scheme;
        this.port = port;
        this.serverName = serverName;
    }

    public String getProtocol() {
        return protocol;
    }

    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    public String getMethod() {
        return method;
    }
    public void setMethod(String method) {
        this.method = method;
    }

    public String getPath() {
        return path;
    }
    public void setPath(String path) {
        this.path = path;
    }

    public HttpQueryParams getQueryParams() {
        return queryParams;
    }

    public String getPathAndQuery()
    {
        if (queryParams != null && queryParams.entries().size() > 0) {
            return getPath() + "?" + queryParams.toEncodedString();
        }
        else {
            return getPath();
        }
    }

    public String getClientIp() {
        return clientIp;
    }
    public void setClientIp(String clientIp) {
        this.clientIp = clientIp;
    }

    public String getScheme() {
        return scheme;
    }
    public void setScheme(String scheme) {
        this.scheme = scheme;
    }

    public int getPort()
    {
        return port;
    }
    public void setPort(int port)
    {
        this.port = port;
    }

    public String getServerName()
    {
        return serverName;
    }
    public void setServerName(String serverName)
    {
        this.serverName = serverName;
    }

    public Cookies parseCookies()
    {
        Cookies cookies = new Cookies();
        for (String aCookieHeader : getHeaders().get("cookie")) {
            Set<Cookie> decode = CookieDecoder.decode(aCookieHeader);
            for (Cookie cookie : decode) {
                cookies.add(cookie);
            }
        }
        return cookies;
    }

    @Override
    public int getMaxBodySize() {
        return MAX_BODY_SIZE_PROP.get();
    }

    @Override
    public Observable<byte[]> bufferBody()
    {
        // Wrap the buffering of request body in a timer.
        Timing timing = getContext().getTimings().getRequestBodyRead();
        timing.start();
        return super.bufferBody()
                .finallyDo(() -> {
                    timing.end();
                });
    }

    @Override
    public ZuulMessage clone()
    {
        return super.clone();
    }

    @Override
    public String getInfoForLogging()
    {
        StringBuilder sb = new StringBuilder()
                .append("url=").append(getPathAndQuery())
                .append(",host=").append(String.valueOf(getHeaders().getFirst("Host")))
                ;
        return sb.toString();
    }

    public HttpRequestInfo copyRequestInfo()
    {
        return new HttpRequestInfo(protocol, method, path, queryParams.clone(), headers.clone(), clientIp, scheme, port);
    }

    public void storeOriginalRequestInfo()
    {
        originalRequestInfo = copyRequestInfo();
    }

    public HttpRequestInfo getOriginalRequestInfo()
    {
        return originalRequestInfo;
    }


    public String getOriginalHost()
    {
        String host = headers.getFirst("X-Forwarded-Host");
        if (host == null) {
            host = headers.getFirst("Host");
            if (host == null) {
                host = getServerName();
            }
        }
        return host;
    }

    public String getOriginalScheme()
    {
        String scheme = headers.getFirst("X-Forwarded-Proto");
        if (scheme == null) {
            scheme = getScheme();
        }
        return scheme;
    }

    public int getOriginalPort()
    {
        int port;
        String portStr = headers.getFirst("X-Forwarded-Port");
        if (portStr == null) {
            port = getPort();
        }
        else {
            port = Integer.parseInt(portStr);
        }
        return port;
    }

    /**
     * Attempt to reconstruct the full URI that the client used.
     *
     * @return
     */
    public URI reconstructURI()
    {
        String scheme = getOriginalScheme();
        String host = getOriginalHost();

        int port = getOriginalPort();
        if (("http".equalsIgnoreCase(scheme) && 80 == port)
                || ("https".equalsIgnoreCase(scheme) && 443 == port)) {
            // Don't need to include port.
            port = -1;
        }

        String queryStr = null;
        if (queryParams != null && queryParams.entries().size() > 0) {
            queryStr = queryParams.toEncodedString();
        }

        URI uri;
        try {
            uri = new URI(scheme, null, host, port, path, queryStr, null);
        }
        catch (URISyntaxException e) {
            throw new RuntimeException("Error reconstructing request URI!", e);
        }

        return uri;
    }

    @RunWith(MockitoJUnitRunner.class)
    public static class TestUnit
    {
        HttpRequestMessage request;

        @Test
        public void testOriginalRequestInfo()
        {
            HttpQueryParams queryParams = new HttpQueryParams();
            queryParams.add("flag", "5");
            Headers headers = new Headers();
            headers.add("Host", "blah.netflix.com");
            request = new HttpRequestMessage(new SessionContext(), "HTTP/1.1", "POST", "/some/where", queryParams, headers,
                    "192.168.0.2", "https", 7002, "localhost");

            request.storeOriginalRequestInfo();
            HttpRequestInfo originalRequest = request.getOriginalRequestInfo();

            Assert.assertEquals(request.getPort(), originalRequest.getPort());
            Assert.assertEquals(request.getPath(), originalRequest.getPath());
            Assert.assertEquals(request.getQueryParams().getFirst("flag"), originalRequest.getQueryParams().getFirst("flag"));
            Assert.assertEquals(request.getHeaders().getFirst("Host"), originalRequest.getHeaders().getFirst("Host"));

            request.setPort(8080);
            request.setPath("/another/place");
            request.getQueryParams().set("flag", "20");
            request.getHeaders().set("Host", "wah.netflix.com");

            Assert.assertEquals(7002, originalRequest.getPort());
            Assert.assertEquals("/some/where", originalRequest.getPath());
            Assert.assertEquals("5", originalRequest.getQueryParams().getFirst("flag"));
            Assert.assertEquals("blah.netflix.com", originalRequest.getHeaders().getFirst("Host"));
        }

        @Test
        public void testReconstructURI()
        {
            HttpQueryParams queryParams = new HttpQueryParams();
            queryParams.add("flag", "5");
            Headers headers = new Headers();
            headers.add("Host", "blah.netflix.com");
            request = new HttpRequestMessage(new SessionContext(), "HTTP/1.1", "POST", "/some/where", queryParams, headers,
                    "192.168.0.2", "https", 7002, "localhost");
            Assert.assertEquals("https://blah.netflix.com:7002/some/where?flag=5", request.reconstructURI().toString());

            queryParams = new HttpQueryParams();
            headers = new Headers();
            headers.add("X-Forwarded-Host", "place.netflix.com");
            headers.add("X-Forwarded-Port", "80");
            request = new HttpRequestMessage(new SessionContext(), "HTTP/1.1", "POST", "/some/where", queryParams, headers,
                    "192.168.0.2", "http", 7002, "localhost");
            Assert.assertEquals("http://place.netflix.com/some/where", request.reconstructURI().toString());

            queryParams = new HttpQueryParams();
            headers = new Headers();
            headers.add("X-Forwarded-Host", "place.netflix.com");
            headers.add("X-Forwarded-Proto", "https");
            headers.add("X-Forwarded-Port", "443");
            request = new HttpRequestMessage(new SessionContext(), "HTTP/1.1", "POST", "/some/where", queryParams, headers,
                    "192.168.0.2", "http", 7002, "localhost");
            Assert.assertEquals("https://place.netflix.com/some/where", request.reconstructURI().toString());

            queryParams = new HttpQueryParams();
            headers = new Headers();
            request = new HttpRequestMessage(new SessionContext(), "HTTP/1.1", "POST", "/some/where", queryParams, headers,
                    "192.168.0.2", "http", 7002, "localhost");
            Assert.assertEquals("http://localhost:7002/some/where", request.reconstructURI().toString());

            queryParams = new HttpQueryParams();
            queryParams.add("flag", "5");
            queryParams.add("flag B", "9");
            headers = new Headers();
            request = new HttpRequestMessage(new SessionContext(), "HTTP/1.1", "POST", "/some where", queryParams, headers,
                    "192.168.0.2", "https", 7002, "localhost");
            Assert.assertEquals("https://localhost:7002/some%20where?flag=5&flag+B=9", request.reconstructURI().toString());
        }
    }
}