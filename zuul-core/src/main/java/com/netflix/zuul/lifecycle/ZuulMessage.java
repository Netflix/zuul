package com.netflix.zuul.lifecycle;

import rx.Observable;

/**
 * User: michaels@netflix.com
 * Date: 2/20/15
 * Time: 3:10 PM
 */
public class ZuulMessage
{
    private final Headers headers;

    public ZuulMessage() {
        this(new Headers());
    }

    public ZuulMessage(Headers headers) {
        this.headers = headers;
    }

    public Headers getHeaders() {
        return headers;
    }

    public Observable getBody() {
        // TODO
        throw new UnsupportedOperationException();
    }
}
