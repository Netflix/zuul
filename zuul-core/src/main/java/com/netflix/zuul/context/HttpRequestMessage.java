package com.netflix.zuul.context;


import io.netty.handler.codec.http.Cookie;
import io.netty.handler.codec.http.CookieDecoder;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * User: michaels
 * Date: 2/24/15
 * Time: 10:54 AM
 */
public class HttpRequestMessage extends ZuulMessage
{
    private String protocol;
    private String method;
    private String path;
    private HttpQueryParams queryParams;
    private String clientIp;
    private String scheme;

    public HttpRequestMessage(String protocol, String method, String path, HttpQueryParams queryParams, Headers headers, String clientIp, String scheme)
    {
        super(headers);

        this.protocol = protocol;
        this.method = method;
        this.path = path;
        // Don't allow this to be null.
        this.queryParams = queryParams == null ? new HttpQueryParams() : queryParams;
        this.clientIp = clientIp;
        this.scheme = scheme;
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

    public Map<String, Set<Cookie>> parseCookies()
    {
        Map<String, Set<Cookie>> cookies = new HashMap<String, Set<Cookie>>();
        for (String aCookieHeader : getHeaders().get("cookie")) {
            Set<Cookie> decode = CookieDecoder.decode(aCookieHeader);
            for (Cookie cookie : decode) {
                Set<Cookie> existingCookiesOfName = cookies.get(cookie.getName());
                if (null == existingCookiesOfName) {
                    existingCookiesOfName = new HashSet<Cookie>();
                    cookies.put(cookie.getName(), existingCookiesOfName);
                }
                existingCookiesOfName.add(cookie);
            }
        }
        return cookies;
    }

    @Override
    public Object clone()
    {
        return super.clone();
    }
}