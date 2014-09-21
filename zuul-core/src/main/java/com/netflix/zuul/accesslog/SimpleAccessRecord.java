package com.netflix.zuul.accesslog;

import java.net.SocketAddress;

/**
 * @author Nitesh Kant
 */
public class SimpleAccessRecord implements AccessRecord {

    private final int statusCode;
    private final String httpMethod;
    private final String uri;
    private final SocketAddress remoteAddress;

    public SimpleAccessRecord(int statusCode, String httpMethod, String uri, SocketAddress remoteAddress) {
        this.statusCode = statusCode;
        this.httpMethod = httpMethod;
        this.uri = uri;
        this.remoteAddress = remoteAddress;
    }

    @Override
    public String toLogLine() {
        return statusCode + " " + httpMethod + " " + uri + " " + remoteAddress.toString();
    }
}
