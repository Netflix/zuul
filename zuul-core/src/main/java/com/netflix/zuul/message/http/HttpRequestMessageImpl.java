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


import com.google.common.annotations.VisibleForTesting;
import com.netflix.config.CachedDynamicBooleanProperty;
import com.netflix.config.CachedDynamicIntProperty;
import com.netflix.config.DynamicStringProperty;
import com.netflix.zuul.context.CommonContextKeys;
import com.netflix.zuul.context.SessionContext;
import com.netflix.zuul.filters.ZuulFilter;
import com.netflix.zuul.message.Headers;
import com.netflix.zuul.message.ZuulMessage;
import com.netflix.zuul.message.ZuulMessageImpl;
import com.netflix.zuul.util.HttpUtils;
import io.netty.handler.codec.http.Cookie;
import io.netty.handler.codec.http.CookieDecoder;
import io.netty.handler.codec.http.DefaultCookie;
import io.netty.handler.codec.http.HttpContent;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import io.netty.handler.codec.http.cookie.ServerCookieDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    private String decodedPath;
    private HttpQueryParams queryParams;
    private String clientIp;
    private String scheme;
    private int port;
    private String serverName;
    private SocketAddress clientRemoteAddress;

    private HttpRequestInfo inboundRequest = null;
    private Cookies parsedCookies = null;

    // These attributes are populated only if immutable=true.
    private String reconstructedUri = null;
    private String pathAndQuery = null;
    private String infoForLogging = null;

    private static final SocketAddress UNDEFINED_CLIENT_DEST_ADDRESS = new SocketAddress() {
        @Override
        public String toString() {
            return "Undefined destination address.";
        }
    };

    public HttpRequestMessageImpl(SessionContext context, String protocol, String method, String path,
                                  HttpQueryParams queryParams, Headers headers, String clientIp, String scheme,
                                  int port, String serverName)
    {
        this(context, protocol, method, path, queryParams, headers, clientIp, scheme, port, serverName,
                UNDEFINED_CLIENT_DEST_ADDRESS, false);
    }

    public HttpRequestMessageImpl(SessionContext context, String protocol, String method, String path,
                                  HttpQueryParams queryParams, Headers headers, String clientIp, String scheme,
                                  int port, String serverName, SocketAddress clientRemoteAddress,
                                  boolean immutable)
    {
        this.immutable = immutable;
        this.message = new ZuulMessageImpl(context, headers);
        this.protocol = protocol;
        this.method = method;
        this.path = path;
        try {
            this.decodedPath = URLDecoder.decode(path, "UTF-8");
        } catch (Exception e) {
            // fail to decode URI
            // just set decodedPath to original path
            this.decodedPath = path;
        }
        // Don't allow this to be null.
        this.queryParams = queryParams == null ? new HttpQueryParams() : queryParams;
        this.clientIp = clientIp;
        this.scheme = scheme;
        this.port = port;
        this.serverName = serverName;
        this.clientRemoteAddress = clientRemoteAddress;
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
        if (message.getContext().containsKey(CommonContextKeys.ZUUL_USE_DECODED_URI)) {
            return decodedPath;
        }
        return path;
    }
    @Override
    public void setPath(String path)
    {
        immutableCheck();
        this.path = path;
        this.decodedPath = path;
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

    @Deprecated
    @VisibleForTesting
    void setClientIp(String clientIp)
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

    @Deprecated
    @VisibleForTesting
    void setPort(int port)
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
                List<io.netty.handler.codec.http.cookie.Cookie> decodedCookies = ServerCookieDecoder.LAX.decodeAll(aCookieHeader);
                // Temporarily map to the deprecated objects until Zuul moves to the new interfaces.
                Set<Cookie> mappedCookies = decodedCookies.stream()
                        .map(cookie -> new DefaultCookie(cookie.name(), cookie.value()))
                        .collect(Collectors.toSet());
                for (Cookie cookie : mappedCookies) {
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

    @VisibleForTesting
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
                port, serverName, clientRemoteAddress, immutable);
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
                port, serverName, clientRemoteAddress, true);
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

    @Override
    public Optional<Integer> getClientDestinationPort() {
        if (clientRemoteAddress instanceof InetSocketAddress) {
            InetSocketAddress inetSocketAddress = (InetSocketAddress) this.clientRemoteAddress;
            return Optional.of(inetSocketAddress.getPort());
        } else {
            return Optional.empty();
        }
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
}