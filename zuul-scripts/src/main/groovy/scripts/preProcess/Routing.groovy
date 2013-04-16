package scripts.preProcess



import com.netflix.zuul.ZuulApplicationInfo
import com.netflix.zuul.context.NFRequestContext
import com.netflix.zuul.groovy.ProxyFilter
import com.netflix.zuul.groovy.GroovyProcessor
import com.netflix.zuul.context.RequestContext
import com.netflix.config.DynamicStringProperty
import com.netflix.config.DynamicPropertyFactory
import com.netflix.zuul.exception.ProxyException

/**
 * Created with IntelliJ IDEA.
 * User: mcohen
 * Date: 1/23/13
 * Time: 2:03 PM
 * To change this template use File | Settings | File Templates.
 */
class Routing extends ProxyFilter {
    DynamicStringProperty defaultClient = DynamicPropertyFactory.getInstance().getStringProperty("zuul.niws.defaultClient", ZuulApplicationInfo.applicationName);
    DynamicStringProperty defaultHost = DynamicPropertyFactory.getInstance().getStringProperty("zuul.default.host", null);


    @Override
    int filterOrder() {
        return 1
    }

    @Override
    String filterType() {
        return "pre"
    }

    boolean shouldFilter() {
        return true
    }


    Object staticRouting() {
        GroovyProcessor.instance.runFilters("healthcheck")
        GroovyProcessor.instance.runFilters("static")
    }

    Object run() {

        staticRouting() //runs the static Zuul

        ((NFRequestContext) RequestContext.currentContext).proxyVIP = defaultClient.get()
        String host = defaultHost.get()
        if(((NFRequestContext) RequestContext.currentContext).proxyVIP == null) ((NFRequestContext) RequestContext.currentContext).proxyVIP  = ZuulApplicationInfo.applicationName
        if (host != null) {
            final URL targetUrl = new URL(host)
            RequestContext.currentContext.setProxyHost(targetUrl);
            ((NFRequestContext) RequestContext.currentContext).proxyVIP=null
        }

        if (host == null && RequestContext.currentContext.proxyVIP  == null) {
            throw new ProxyException("default VIP or host not defined. Define: zuul.niws.defaultClient or zuul.default.host", 501, "zuul.niws.defaultClient or zuul.default.host not defined")
        }

        String uri = RequestContext.currentContext.request.getRequestURI()
        if (RequestContext.currentContext.requestURI != null) {
            uri = RequestContext.currentContext.requestURI
        }
        if (uri == null) uri = "/"
        if (uri.startsWith("/")) {
            uri = uri - "/"
        }



        ((NFRequestContext) RequestContext.currentContext).route = uri.substring(0, uri.indexOf("/") + 1)
    }
}