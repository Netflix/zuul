package com.netflix.api.proxy.filters

import com.netflix.api.proxy.context.RequestContext
import com.netflix.api.proxy.groovy.ProxyFilter
import java.util.regex.Pattern

/**
 * Created by IntelliJ IDEA.
 * User: mcohen
 * Date: 2/2/12
 * Time: 1:34 PM
 * To change this template use File | Settings | File Templates.
 */
public abstract class StaticResponseFilter extends ProxyFilter {

    abstract def uri()

    abstract String responseBody()

    @Override
    String filterType() {
        return "static"
    }

    @Override
    int filterOrder() {
        return 0
    }

    boolean shouldFilter() {
        String path = RequestContext.currentContext.getRequest().getRequestURI()
        if (checkPath(path)) return true
        if (checkPath("/" + path)) return true
        return false
    }

    boolean checkPath(String path) {
        def uri = uri()
        if(uri instanceof String){
            return uri.equals(path)
        } else if(uri instanceof List){
           return uri.contains(path)
        } else if(uri instanceof Pattern){
            return uri.matcher(path).matches();
        }
        return false;
    }

    @Override
    Object run() {
        RequestContext ctx = RequestContext.getCurrentContext();
        // first StaticResponseFilter instance to match wins, others do not set body and/or status
        if(ctx.getResponseBody() == null) {
            ctx.setResponseBody(responseBody())
            ctx.sendProxyResponse = false;
        }
    }

}
