package com.netflix.zuul.context;

/**
 * User: michaels@netflix.com
 * Date: 6/9/15
 * Time: 11:11 AM
 */
public class HttpRequestInfo
{
    private final String protocol;
    private final String method;
    private final String path;
    private final HttpQueryParams queryParams;
    private final String clientIp;
    private final String scheme;
    private final int port;
    private final Headers headers;

    public HttpRequestInfo(String protocol, String method, String path, HttpQueryParams queryParams, Headers headers, String clientIp, String scheme, int port)
    {
        this.protocol = protocol;
        this.method = method;
        this.path = path;
        // Don't allow this to be null.
        this.queryParams = queryParams == null ? new HttpQueryParams() : queryParams;
        this.headers = headers == null ? new Headers() : headers;
        this.clientIp = clientIp;
        this.scheme = scheme;
        this.port = port;
    }

    public String getProtocol() {
        return protocol;
    }

    public String getMethod() {
        return method;
    }

    public String getPath() {
        return path;
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

    public Headers getHeaders()
    {
        return headers;
    }

    public String getClientIp() {
        return clientIp;
    }

    public String getScheme() {
        return scheme;
    }

    public int getPort()
    {
        return port;
    }
}
