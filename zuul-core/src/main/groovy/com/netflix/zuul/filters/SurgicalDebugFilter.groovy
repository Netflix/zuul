package com.netflix.zuul.filters

import com.netflix.zuul.context.RequestContext
import com.netflix.zuul.groovy.ProxyFilter
import com.netflix.zuul.util.HTTPRequestUtils
import com.netflix.config.FastProperty


/**
 * Created with IntelliJ IDEA.
 * User: mcohen
 * Date: 6/27/12
 * Time: 12:54 PM
 * To change this template use File | Settings | File Templates.
 */
public abstract class SurgicalDebugFilter extends ProxyFilter {

    abstract def boolean patternMatches()

    abstract def String filterDisablePropertyName()


    @Override
    String filterType() {
        return "pre"
    }

    @Override
    int filterOrder() {
        return 99
    }

    boolean shouldFilter() {
        FastProperty.BooleanProperty debugFilterShutoff = new FastProperty.BooleanProperty("api.zuul.debugFilters.disabed", false)
        if (debugFilterShutoff.get()) return false;

        debugFilterShutoff = new FastProperty.BooleanProperty(filterDisablePropertyName(), false)
        if (debugFilterShutoff.get()) return false;

        String isSurgicalFilterRequest = RequestContext.currentContext.getRequest().getHeader("X-Netflix-Surgical-Filter");
        if ("true".equals(isSurgicalFilterRequest)) return false; // dont' apply filter if it was already applied
        return patternMatches();
    }


    @Override
    Object run() {
        RequestContext.currentContext.proxyVIP = "apiproxy-debug"
        RequestContext.currentContext.addProxyRequestHeader("X-Netflix-Surgical-Filter", "true");
        if (HTTPRequestUtils.getInstance().getQueryParams() == null) {
            RequestContext.getCurrentContext().setRequestQueryParams(new HashMap<String, List<String>>());
        }
        HTTPRequestUtils.getInstance().getQueryParams().put("debugRequest", ["true"])
        RequestContext.currentContext.setDebugRequest(true)
        RequestContext.getCurrentContext().proxyToProxy = true
    }
}
