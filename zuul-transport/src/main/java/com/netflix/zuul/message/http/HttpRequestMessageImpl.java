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
package com.netflix.zuul.message.http;


import com.netflix.config.DynamicBooleanProperty;
import com.netflix.config.DynamicIntProperty;
import com.netflix.config.DynamicPropertyFactory;
import com.netflix.config.DynamicStringProperty;
import com.netflix.zuul.context.SessionContext;
import com.netflix.zuul.message.Headers;
import com.netflix.zuul.message.ZuulMessage;
import com.netflix.zuul.message.ZuulMessageImpl;
import com.netflix.zuul.stats.Timing;
import com.netflix.zuul.util.HttpUtils;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.Cookie;
import io.netty.handler.codec.http.CookieDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * User: michaels
 * Date: 2/24/15
 * Time: 10:54 AM
 */
public class HttpRequestMessageImpl implements HttpRequestMessage
{
    private static final Logger LOG = LoggerFactory.getLogger(HttpRequestMessageImpl.class);

    private static final DynamicIntProperty MAX_BODY_SIZE_PROP = DynamicPropertyFactory.getInstance().getIntProperty(
            "zuul.HttpRequestMessage.body.max.size", 15 * 1000 * 1024
    );
    private static final DynamicBooleanProperty CLEAN_COOKIES = DynamicPropertyFactory.getInstance().getBooleanProperty(
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


    public HttpRequestMessageImpl(SessionContext context, String protocol, String method, String path,
                                  HttpQueryParams queryParams, Headers headers, String clientIp, String scheme,
                                  int port, String serverName)
    {
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
        message.setHeaders(newHeaders);
    }

    @Override
    public byte[] getBody()
    {
        return message.getBody();
    }

    @Override
    public void setBody(byte[] body)
    {
        message.setBody(body);
    }

    @Override
    public boolean hasBody()
    {
        return message.hasBody();
    }

    @Override
    public void setBodyAsText(String bodyText, Charset cs)
    {
        message.setBodyAsText(bodyText, cs);
    }

    @Override
    public void setBodyAsText(String bodyText)
    {
        message.setBodyAsText(bodyText);
    }

    @Override
    public boolean isBodyBuffered()
    {
        return message.isBodyBuffered();
    }

    @Override
    public Observable<byte[]> bufferBody()
    {
        // Wrap the buffering of request body in a timer.
        Timing timing = getContext().getTimings().getRequestBodyRead();
        timing.start();
        return message.bufferBody()
                .finallyDo(() -> {
                    timing.end();
                });
    }

    @Override
    public Observable<ByteBuf> getBodyStream()
    {
        return message.getBodyStream();
    }

    @Override
    public void setBodyStream(Observable<ByteBuf> bodyStream)
    {
        message.setBodyStream(bodyStream);
    }

    @Override
    public String getProtocol() {
        return protocol;
    }

    @Override
    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    @Override
    public String getMethod() {
        return method;
    }
    @Override
    public void setMethod(String method) {
        this.method = method;
    }

    @Override
    public String getPath() {
        return path;
    }
    @Override
    public void setPath(String path) {
        this.path = path;
    }

    @Override
    public HttpQueryParams getQueryParams() {
        return queryParams;
    }

    @Override
    public String getPathAndQuery()
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
    public void setClientIp(String clientIp) {
        this.clientIp = clientIp;
    }

    @Override
    public String getScheme() {
        return scheme;
    }
    @Override
    public void setScheme(String scheme) {
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

    static String cleanCookieHeader(String cookie)
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
        return new HttpRequestMessageImpl(message.getContext().clone(),
                protocol, method, path,
                queryParams.immutableCopy(), message.getHeaders().immutableCopy(), clientIp, scheme,
                port, serverName);
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
    public String getInfoForLogging()
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
}