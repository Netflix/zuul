package filters.pre

import com.netflix.zuul.context.HttpRequestMessage
import com.netflix.zuul.context.HttpResponseMessage
import com.netflix.zuul.context.SessionContext
import com.netflix.zuul.exception.ZuulException
import com.netflix.zuul.filters.AsyncFilter
import com.netflix.zuul.filters.BaseFilter
import com.netflix.zuul.rxnetty.RxNettyOrigin
import com.netflix.zuul.rxnetty.RxNettyOriginManager
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import rx.Observable

/**
 * Make a HTTP request to api-global just to demonstrate this kind of async IO call as part of a pre filter.
 *
 * Doing it in a hacky way by using the OriginManager for the remote call. As don't have another configured http
 * client to hand to use otherwise.
 *
 * User: Mike Smith
 * Date: 5/12/15
 * Time: 3:51 PM
 */
class ExampleIOPreFilter extends BaseFilter implements AsyncFilter
{
    protected static final Logger LOG = LoggerFactory.getLogger(ExampleIOPreFilter.class);

    @Override
    Observable<SessionContext> applyAsync(SessionContext context)
    {
        // Get the origin to send request to.
        RxNettyOriginManager originManager = context.getHelpers().get("origin_manager")
        String name = "netflix"
        RxNettyOrigin origin = originManager.getOrigin("netflix")
        if (origin == null) {
            throw new ZuulException("No Origin registered for name=${name}!", 500, "UNKNOWN_VIP")
        }

        // Make the request.
        HttpRequestMessage request = new HttpRequestMessage("HTTP/1.1", "get", "/account/geo", null, null,
                context.getHttpRequest().getClientIp(), "http")
        Observable<HttpResponseMessage> resultObs = origin.request(request)

        resultObs = resultObs.map({ resp ->

            // Get the result of the call.
            int status = resp.getStatus()
            context.getAttributes().set("ExampleIOPreFilter_status", status)
            LOG.info("Received response for ExampleIOPreFilter http call. status=${status}")

            // Swap the original context back into the Observable returned.
            return context
        })

        return resultObs
    }

    @Override
    boolean shouldFilter(SessionContext ctx) {
        return ctx.getHttpRequest().getPath().startsWith("/account/")
    }

    @Override
    int filterOrder() {
        return 5
    }

    @Override
    String filterType() {
        return "pre"
    }
}
