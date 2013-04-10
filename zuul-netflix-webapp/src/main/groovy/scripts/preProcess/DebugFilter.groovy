package scripts.preProcess

import com.netflix.zuul.context.NFRequestContext
import com.netflix.config.DynamicBooleanProperty
import com.netflix.config.DynamicPropertyFactory
import com.netflix.zuul.groovy.ProxyFilter
import com.netflix.zuul.context.RequestContext

class DebugFilter extends ProxyFilter {
    static final DynamicBooleanProperty proxyDebug = DynamicPropertyFactory.getInstance().getBooleanProperty("zuul.debug.request", false)

    @Override
    String filterType() {
        return 'pre'
    }

    @Override
    int filterOrder() {
        return 1
    }

    boolean shouldFilter() {
        if("true".equals(NFRequestContext.currentContext.getRequest().getParameter("debugRequest"))) return true;
        return proxyDebug.get();

    }

    Object run() {
        RequestContext.getCurrentContext().setDebugRequest(true)
        RequestContext.getCurrentContext().setDebugProxy(true)
        return null;

    }


}



