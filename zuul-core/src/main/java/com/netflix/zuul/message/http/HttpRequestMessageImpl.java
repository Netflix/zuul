/*
 * Copyright 2018 Netflix, Inc.
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
package com.netflix.zuul.message.http;


import com.netflix.config.CachedDynamicBooleanProperty;
import com.netflix.config.CachedDynamicIntProperty;
import com.netflix.config.DynamicStringProperty;
import com.netflix.zuul.context.SessionContext;
import com.netflix.zuul.filters.ZuulFilter;
import com.netflix.zuul.message.Headers;
import com.netflix.zuul.message.ZuulMessage;
import com.netflix.zuul.message.ZuulMessageImpl;
import com.netflix.zuul.util.HttpUtils;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.Cookie;
import io.netty.handler.codec.http.CookieDecoder;
import io.netty.handler.codec.http.HttpContent;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * User: michaels
 * Date: 2/24/15
 * Time: 10:54 AM
 */
public class HttpRequestMessageImpl implements HttpRequestMessage
{
    private static final Logger LOG = LoggerFactory.getLogger(HttpRequestMessageImpl.class);

    private static final CachedDynamicIntProperty MAX_BODY_SIZE_PROP = new CachedDynamicIntProperty(
            "zuul.HttpRequestMessage.body.max.size", 15 * 1000 * 1024
    );
    private static final CachedDynamicBooleanProperty CLEAN_COOKIES = new CachedDynamicBooleanProperty(
            "zuul.HttpRequestMessage.cookies.clean", false
    );

    /** ":::"-delimited list of regexes to strip out of the cookie headers. */
    private static final DynamicStringProperty REGEX_PTNS_TO_STRIP_PROP =
            new DynamicStringProperty("zuul.request.cookie.cleaner.strip", " Secure,");
    private static final List<Pattern> RE_STRIP;
    static {
        RE_STRIP = new ArrayList<>();
        for (String ptn : REGEX_PTNS_TO_STRIP_PROP.get().split(":::")) {
            RE_STRIP.add(Pattern.compile(ptn));
        }
    }

    private static final Pattern PTN_COLON = Pattern.compile(":");
    private static final String URI_SCHEME_SEP = "://";
    private static final String URI_SCHEME_HTTP = "http";
    private static final String URI_SCHEME_HTTPS = "https";

    private final boolean immutable;
    private ZuulMessage message;
    private String protocol;
    private String method;
    private String path;
    private HttpQueryParams queryParams;
    private String clientIp;
    private String scheme;
    private int port;
    private String serverName;

    private HttpRequestInfo inboundRequest = null;
    private Cookies parsedCookies = null;

    // These attributes are populated only if immutable=true.
    private String reconstructedUri = null;
    private String pathAndQuery = null;
    private String infoForLogging = null;


    public HttpRequestMessageImpl(SessionContext context, String protocol, String method, String path,
                                  HttpQueryParams queryParams, Headers headers, String clientIp, String scheme,
                                  int port, String serverName)
    {
        this(context, protocol, method, path, queryParams, headers, clientIp, scheme, port, serverName, false);
    }

    public HttpRequestMessageImpl(SessionContext context, String protocol, String method, String path,
                                  HttpQueryParams queryParams, Headers headers, String clientIp, String scheme,
                                  int port, String serverName,
                                  boolean immutable)
    {
        this.immutable = immutable;
        this.message = new ZuulMessageImpl(context, headers);
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

    private void immutableCheck()
    {
        if (immutable) {
            throw new IllegalStateException("This HttpRequestMessageImpl is immutable. No mutating operations allowed!");
        }
    }

    @Override
    public SessionContext getContext()
    {
        return message.getContext();
    }

    @Override
    public Headers getHeaders()
    {
        return message.getHeaders();
    }

    @Override
    public void setHeaders(Headers newHeaders)
    {
        immutableCheck();
        message.setHeaders(newHeaders);
    }

    @Override
    public void setHasBody(boolean hasBody) {
        message.setHasBody(hasBody);
    }

    @Override
    public boolean hasBody() {
        return message.hasBody();
    }

    @Override
    public void bufferBodyContents(HttpContent chunk) {
        message.bufferBodyContents(chunk);
    }

    @Override
    public void setBodyAsText(String bodyText) {
        message.setBodyAsText(bodyText);
    }

    @Override
    public void setBody(byte[] body) {
        message.setBody(body);
    }

    @Override
    public boolean finishBufferedBodyIfIncomplete() {
        return message.finishBufferedBodyIfIncomplete();
    }

    @Override
    public Iterable<HttpContent> getBodyContents() {
        return message.getBodyContents();
    }

    @Override
    public void runBufferedBodyContentThroughFilter(ZuulFilter filter) {
        message.runBufferedBodyContentThroughFilter(filter);
    }

    @Override
    public String getBodyAsText() {
        return message.getBodyAsText();
    }

    @Override
    public byte[] getBody() {
        return message.getBody();
    }

    @Override
    public int getBodyLength() {
        return message.getBodyLength();
    }

    @Override
    public boolean hasCompleteBody() {
        return message.hasCompleteBody();
    }

    @Override
    public void disposeBufferedBody() {
        message.disposeBufferedBody();
    }

    @Override
    public String getProtocol() {
        return protocol;
    }

    @Override
    public void setProtocol(String protocol)
    {
        immutableCheck();
        this.protocol = protocol;
    }

    @Override
    public String getMethod() {
        return method;
    }
    @Override
    public void setMethod(String method)
    {
        immutableCheck();
        this.method = method;
    }

    @Override
    public String getPath() {
        return path;
    }
    @Override
    public void setPath(String path)
    {
        immutableCheck();
        this.path = path;
    }

    @Override
    public HttpQueryParams getQueryParams() {
        return queryParams;
    }

    @Override
    public String getPathAndQuery()
    {
        // If this instance is immutable, then lazy-cache.
        if (immutable) {
            if (pathAndQuery == null) {
                pathAndQuery = generatePathAndQuery();
            }
            return pathAndQuery;
        }
        else {
            return generatePathAndQuery();
        }
    }

    protected String generatePathAndQuery()
    {
        if (queryParams != null && queryParams.entries().size() > 0) {
            return getPath() + "?" + queryParams.toEncodedString();
        }
        else {
            return getPath();
        }
    }

    @Override
    public String getClientIp() {
        return clientIp;
    }
    @Override
    public void setClientIp(String clientIp)
    {
        immutableCheck();
        this.clientIp = clientIp;
    }

    @Override
    public String getScheme() {
        return scheme;
    }
    @Override
    public void setScheme(String scheme)
    {
        immutableCheck();
        this.scheme = scheme;
    }

    @Override
    public int getPort()
    {
        return port;
    }
    @Override
    public void setPort(int port)
    {
        immutableCheck();
        this.port = port;
    }

    @Override
    public String getServerName()
    {
        return serverName;
    }
    @Override
    public void setServerName(String serverName)
    {
        immutableCheck();
        this.serverName = serverName;
    }

    @Override
    public Cookies parseCookies()
    {
        if (parsedCookies == null) {
            parsedCookies = reParseCookies();
        }
        return parsedCookies;
    }

    @Override
    public Cookies reParseCookies()
    {
        Cookies cookies = new Cookies();
        for (String aCookieHeader : getHeaders().get(HttpHeaderNames.COOKIE))
        {
            try {
                if (CLEAN_COOKIES.get()) {
                    aCookieHeader = cleanCookieHeader(aCookieHeader);
                }

                Set<Cookie> decoded = CookieDecoder.decode(aCookieHeader, false);
                for (Cookie cookie : decoded) {
                    cookies.add(cookie);
                }
            }
            catch (Exception e) {
                LOG.error(String.format("Error parsing request Cookie header. cookie=%s, request-info=%s",
                        aCookieHeader, getInfoForLogging()));
            }

        }
        parsedCookies = cookies;
        return cookies;
    }

    private static String cleanCookieHeader(String cookie)
    {
        for (Pattern stripPtn : RE_STRIP) {
            Matcher matcher = stripPtn.matcher(cookie);
            if (matcher.find()) {
                cookie = matcher.replaceAll("");
            }
        }
        return cookie;
    }

    @Override
    public int getMaxBodySize() {
        return MAX_BODY_SIZE_PROP.get();
    }

    @Override
    public ZuulMessage clone()
    {
        HttpRequestMessageImpl clone = new HttpRequestMessageImpl(message.getContext().clone(),
                protocol, method, path,
                queryParams.clone(), message.getHeaders().clone(), clientIp, scheme,
                port, serverName);
        if (getInboundRequest() != null) {
            clone.inboundRequest = (HttpRequestInfo) getInboundRequest().clone();
        }
        return clone;
    }

    protected HttpRequestInfo copyRequestInfo()
    {
        // Unlike clone(), we create immutable copies of the Headers and HttpQueryParams here.
        HttpRequestMessageImpl req = new HttpRequestMessageImpl(message.getContext(),
                protocol, method, path,
                queryParams.immutableCopy(), message.getHeaders().immutableCopy(), clientIp, scheme,
                port, serverName, true);
        req.setHasBody(hasBody());
        return req;
    }

    @Override
    public void storeInboundRequest()
    {
        inboundRequest = copyRequestInfo();
    }

    @Override
    public HttpRequestInfo getInboundRequest()
    {
        return inboundRequest;
    }

    @Override
    public void setQueryParams(HttpQueryParams queryParams)
    {
        immutableCheck();
        this.queryParams = queryParams;
    }

    @Override
    public String getInfoForLogging()
    {
        // If this instance is immutable, then lazy-cache generating this info.
        if (immutable) {
            if (infoForLogging == null) {
                infoForLogging = generateInfoForLogging();
            }
            return infoForLogging;
        }
        else {
            return generateInfoForLogging();
        }
    }

    protected String generateInfoForLogging()
    {
        HttpRequestInfo req = getInboundRequest() == null ? this : getInboundRequest();
        StringBuilder sb = new StringBuilder()
                .append("uri=").append(req.reconstructURI())
                .append(", method=").append(req.getMethod())
                .append(", clientip=").append(HttpUtils.getClientIP(req))
                ;
        return sb.toString();
    }

    /**
     * The originally request host. This will NOT include port.
     *
     * The Host header may contain port, but in this method we strip it out for consistency - use the
     * getOriginalPort method for that.
     *
     * @return
     */
    @Override
    public String getOriginalHost()
    {
        String host = getHeaders().getFirst(HttpHeaderNames.X_FORWARDED_HOST);
        if (host == null) {
            host = getHeaders().getFirst(HttpHeaderNames.HOST);
            if (host != null) {
                // Host header may have a trailing port. Strip that out if it does.
                host = PTN_COLON.split(host)[0];
            }

            if (host == null) {
                host = getServerName();
            }
        }
        return host;
    }

    @Override
    public String getOriginalScheme()
    {
        String scheme = getHeaders().getFirst(HttpHeaderNames.X_FORWARDED_PROTO);
        if (scheme == null) {
            scheme = getScheme();
        }
        return scheme;
    }

    @Override
    public String getOriginalProtocol()
    {
        String proto = getHeaders().getFirst(HttpHeaderNames.X_FORWARDED_PROTO_VERSION);
        if (proto == null) {
            proto = getProtocol();
        }
        return proto;
    }

    @Override
    public int getOriginalPort()
    {
        int port;
        String portStr = getHeaders().getFirst(HttpHeaderNames.X_FORWARDED_PORT);
        if (portStr == null) {
            // Check if port was specified on a Host header.
            String hostHeader = getHeaders().getFirst(HttpHeaderNames.HOST);
            if (hostHeader != null) {
                String[] hostParts = PTN_COLON.split(hostHeader);
                if (hostParts.length == 2) {
                    port = Integer.parseInt(hostParts[1]);
                }
                else {
                    port = getPort();
                }
            }
            else {
                port = getPort();
            }
        }
        else {
            port = Integer.parseInt(portStr);
        }
        return port;
    }

    /**
     * Attempt to reconstruct the full URI that the client used.
     *
     * @return String
     */
    @Override
    public String reconstructURI()
    {
        // If this instance is immutable, then lazy-cache reconstructing the uri.
        if (immutable) {
            if (reconstructedUri == null) {
                reconstructedUri = _reconstructURI();
            }
            return reconstructedUri;
        }
        else {
            return _reconstructURI();
        }
    }

    protected String _reconstructURI()
    {
        try {
            StringBuilder uri = new StringBuilder(100);

            String scheme = getOriginalScheme().toLowerCase();
            uri.append(scheme);
            uri.append(URI_SCHEME_SEP).append(getOriginalHost());

            int port = getOriginalPort();
            if ((URI_SCHEME_HTTP.equals(scheme) && 80 == port)
                    || (URI_SCHEME_HTTPS.equals(scheme) && 443 == port)) {
                // Don't need to include port.
            } else {
                uri.append(':').append(port);
            }

            uri.append(getPathAndQuery());

            return uri.toString();
        }
        catch (Exception e) {
            LOG.error("Error reconstructing request URI!", e);
            return "";
        }
    }

    @Override
    public String toString() {
        return "HttpRequestMessageImpl{" +
                "immutable=" + immutable +
                ", message=" + message +
                ", protocol='" + protocol + '\'' +
                ", method='" + method + '\'' +
                ", path='" + path + '\'' +
                ", queryParams=" + queryParams +
                ", clientIp='" + clientIp + '\'' +
                ", scheme='" + scheme + '\'' +
                ", port=" + port +
                ", serverName='" + serverName + '\'' +
                ", inboundRequest=" + inboundRequest +
                ", parsedCookies=" + parsedCookies +
                ", reconstructedUri='" + reconstructedUri + '\'' +
                ", pathAndQuery='" + pathAndQuery + '\'' +
                ", infoForLogging='" + infoForLogging + '\'' +
                '}';
    }

    @RunWith(MockitoJUnitRunner.class)
    public static class TestUnit
    {
        HttpRequestMessageImpl request;

        @Test
        public void testOriginalRequestInfo()
        {
            HttpQueryParams queryParams = new HttpQueryParams();
            queryParams.add("flag", "5");
            Headers headers = new Headers();
            headers.add("Host", "blah.netflix.com");
            request = new HttpRequestMessageImpl(new SessionContext(), "HTTP/1.1", "POST", "/some/where", queryParams, headers,
                    "192.168.0.2", "https", 7002, "localhost");

            request.storeInboundRequest();
            HttpRequestInfo originalRequest = request.getInboundRequest();

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
            request = new HttpRequestMessageImpl(new SessionContext(), "HTTP/1.1", "POST", "/some/where", queryParams, headers,
                    "192.168.0.2", "https", 7002, "localhost");
            Assert.assertEquals("https://blah.netflix.com:7002/some/where?flag=5", request.reconstructURI());

            queryParams = new HttpQueryParams();
            headers = new Headers();
            headers.add("X-Forwarded-Host", "place.netflix.com");
            headers.add("X-Forwarded-Port", "80");
            request = new HttpRequestMessageImpl(new SessionContext(), "HTTP/1.1", "POST", "/some/where", queryParams, headers,
                    "192.168.0.2", "http", 7002, "localhost");
            Assert.assertEquals("http://place.netflix.com/some/where", request.reconstructURI());

            queryParams = new HttpQueryParams();
            headers = new Headers();
            headers.add("X-Forwarded-Host", "place.netflix.com");
            headers.add("X-Forwarded-Proto", "https");
            headers.add("X-Forwarded-Port", "443");
            request = new HttpRequestMessageImpl(new SessionContext(), "HTTP/1.1", "POST", "/some/where", queryParams, headers,
                    "192.168.0.2", "http", 7002, "localhost");
            Assert.assertEquals("https://place.netflix.com/some/where", request.reconstructURI());

            queryParams = new HttpQueryParams();
            headers = new Headers();
            request = new HttpRequestMessageImpl(new SessionContext(), "HTTP/1.1", "POST", "/some/where", queryParams, headers,
                    "192.168.0.2", "http", 7002, "localhost");
            Assert.assertEquals("http://localhost:7002/some/where", request.reconstructURI());

            queryParams = new HttpQueryParams();
            queryParams.add("flag", "5");
            queryParams.add("flag B", "9");
            headers = new Headers();
            request = new HttpRequestMessageImpl(new SessionContext(), "HTTP/1.1", "POST", "/some%20where", queryParams, headers,
                    "192.168.0.2", "https", 7002, "localhost");
            Assert.assertEquals("https://localhost:7002/some%20where?flag=5&flag+B=9", request.reconstructURI());
        }

        @Test
        public void testReconstructURI_immutable()
        {
            HttpQueryParams queryParams = new HttpQueryParams();
            queryParams.add("flag", "5");
            Headers headers = new Headers();
            headers.add("Host", "blah.netflix.com");
            request = new HttpRequestMessageImpl(new SessionContext(), "HTTP/1.1", "POST", "/some/where", queryParams, headers,
                    "192.168.0.2", "https", 7002, "localhost", true);

            // Check it's the same value 2nd time.
            Assert.assertEquals("https://blah.netflix.com:7002/some/where?flag=5", request.reconstructURI());
            Assert.assertEquals("https://blah.netflix.com:7002/some/where?flag=5", request.reconstructURI());

            // Check that cached on 1st usage.
            request = new HttpRequestMessageImpl(new SessionContext(), "HTTP/1.1", "POST", "/some/where", queryParams, headers,
                    "192.168.0.2", "https", 7002, "localhost", true);
            request = spy(request);
            when(request._reconstructURI()).thenReturn("http://testhost/blah");
            verify(request, times(1))._reconstructURI();
            Assert.assertEquals("http://testhost/blah", request.reconstructURI());
            Assert.assertEquals("http://testhost/blah", request.reconstructURI());

            // Check that throws exception if we try to mutate it.
            try {
                request.setPath("/new-path");
                fail();
            }
            catch (IllegalStateException e) {
                assertTrue(true);
            }
        }

        @Test
        public void testPathAndQuery()
        {
            HttpQueryParams queryParams = new HttpQueryParams();
            queryParams.add("flag", "5");
            request = new HttpRequestMessageImpl(new SessionContext(), "HTTP/1.1", "POST", "/some/where", queryParams, new Headers(),
                    "192.168.0.2", "https", 7002, "localhost");

            // Check that value changes.
            Assert.assertEquals("/some/where?flag=5", request.getPathAndQuery());
            request.getQueryParams().add("k", "v");
            Assert.assertEquals("/some/where?flag=5&k=v", request.getPathAndQuery());
            request.setPath("/other");
            Assert.assertEquals("/other?flag=5&k=v", request.getPathAndQuery());
        }

        @Test
        public void testPathAndQuery_immutable()
        {
            HttpQueryParams queryParams = new HttpQueryParams();
            queryParams.add("flag", "5");
            request = new HttpRequestMessageImpl(new SessionContext(), "HTTP/1.1", "POST", "/some/where", queryParams, new Headers(),
                    "192.168.0.2", "https", 7002, "localhost", true);

            // Check it's the same value 2nd time.
            Assert.assertEquals("/some/where?flag=5", request.getPathAndQuery());
            Assert.assertEquals("/some/where?flag=5", request.getPathAndQuery());

            // Check that cached on 1st usage.
            request = new HttpRequestMessageImpl(new SessionContext(), "HTTP/1.1", "POST", "/some/where", queryParams, new Headers(),
                    "192.168.0.2", "https", 7002, "localhost", true);
            request = spy(request);
            when(request.generatePathAndQuery()).thenReturn("/blah");
            verify(request, times(1)).generatePathAndQuery();
            Assert.assertEquals("/blah", request.getPathAndQuery());
            Assert.assertEquals("/blah", request.getPathAndQuery());
        }

        @Test
        public void testGetOriginalHost()
        {
            HttpQueryParams queryParams = new HttpQueryParams();
            Headers headers = new Headers();
            headers.add("Host", "blah.netflix.com");
            request = new HttpRequestMessageImpl(new SessionContext(), "HTTP/1.1", "POST", "/some/where", queryParams, headers,
                    "192.168.0.2", "https", 7002, "localhost");
            Assert.assertEquals("blah.netflix.com", request.getOriginalHost());

            headers = new Headers();
            headers.add("Host", "blah.netflix.com");
            headers.add("X-Forwarded-Host", "foo.netflix.com");
            request = new HttpRequestMessageImpl(new SessionContext(), "HTTP/1.1", "POST", "/some/where", queryParams, headers,
                    "192.168.0.2", "https", 7002, "localhost");
            Assert.assertEquals("foo.netflix.com", request.getOriginalHost());

            headers = new Headers();
            headers.add("X-Forwarded-Host", "foo.netflix.com");
            request = new HttpRequestMessageImpl(new SessionContext(), "HTTP/1.1", "POST", "/some/where", queryParams, headers,
                    "192.168.0.2", "https", 7002, "localhost");
            Assert.assertEquals("foo.netflix.com", request.getOriginalHost());

            headers = new Headers();
            headers.add("Host", "blah.netflix.com:8080");
            request = new HttpRequestMessageImpl(new SessionContext(), "HTTP/1.1", "POST", "/some/where", queryParams, headers,
                    "192.168.0.2", "https", 7002, "localhost");
            Assert.assertEquals("blah.netflix.com", request.getOriginalHost());
        }

        @Test
        public void testGetOriginalPort()
        {
            HttpQueryParams queryParams = new HttpQueryParams();
            Headers headers = new Headers();
            request = new HttpRequestMessageImpl(new SessionContext(), "HTTP/1.1", "POST", "/some/where", queryParams, headers,
                    "192.168.0.2", "https", 7002, "localhost");
            Assert.assertEquals(7002, request.getOriginalPort());

            headers = new Headers();
            headers.add("Host", "blah.netflix.com");
            headers.add("X-Forwarded-Port", "443");
            request = new HttpRequestMessageImpl(new SessionContext(), "HTTP/1.1", "POST", "/some/where", queryParams, headers,
                    "192.168.0.2", "https", 7002, "localhost");
            Assert.assertEquals(443, request.getOriginalPort());

            headers = new Headers();
            headers.add("Host", "blah.netflix.com:443");
            request = new HttpRequestMessageImpl(new SessionContext(), "HTTP/1.1", "POST", "/some/where", queryParams, headers,
                    "192.168.0.2", "https", 7002, "localhost");
            Assert.assertEquals(443, request.getOriginalPort());

            headers = new Headers();
            headers.add("Host", "blah.netflix.com:443");
            headers.add("X-Forwarded-Port", "7005");
            request = new HttpRequestMessageImpl(new SessionContext(), "HTTP/1.1", "POST", "/some/where", queryParams, headers,
                    "192.168.0.2", "https", 7002, "localhost");
            Assert.assertEquals(7005, request.getOriginalPort());
        }

        @Test
        public void testCleanCookieHeaders()
        {
            assertEquals("BlahId=12345; something=67890;", HttpRequestMessageImpl.cleanCookieHeader("BlahId=12345; Secure, something=67890;"));
            assertEquals("BlahId=12345; something=67890;", HttpRequestMessageImpl.cleanCookieHeader("BlahId=12345; something=67890;"));
            assertEquals(" BlahId=12345; something=67890;", HttpRequestMessageImpl.cleanCookieHeader(" Secure, BlahId=12345; Secure, something=67890;"));
            assertEquals("", HttpRequestMessageImpl.cleanCookieHeader(""));
        }
    }
}