package com.netflix.zuul.context;

/**
 * User: michaels@netflix.com
 * Date: 2/25/15
 * Time: 4:09 PM
 */
public interface RequestContextDecorator {
    public RequestContext decorate(RequestContext ctx);
}
