package com.netflix.zuul.lifecycle;

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
    private String method;
    private String uri;
    private HttpQueryParams queryParams;
    private String clientIp;

    public HttpRequestMessage(String method, String uri, HttpQueryParams queryParams, Headers headers, String clientIp)
    {
        super(headers);

        this.method = method;
        this.uri = uri;
        this.queryParams = queryParams;
        this.clientIp = clientIp;
    }

    public String getMethod() {
        return method;
    }
    public void setMethod(String method) {
        this.method = method;
    }

    public String getUri() {
        return uri;
    }
    public void setUri(String uri) {
        this.uri = uri;
    }

    public HttpQueryParams getQueryParams() {
        return queryParams;
    }

    public String getClientIp() {
        return clientIp;
    }
    public void setClientIp(String clientIp) {
        this.clientIp = clientIp;
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
}
