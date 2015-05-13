package filters.pre

import com.netflix.zuul.context.HttpRequestMessage
import com.netflix.zuul.context.HttpResponseMessage
import com.netflix.zuul.exception.ZuulException
import com.netflix.zuul.filters.BaseFilter
import com.netflix.zuul.context.SessionContext
import com.netflix.zuul.filters.AsyncFilter
import com.netflix.zuul.origins.Origin
import com.netflix.zuul.origins.OriginManager
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import rx.Observable

/**
 * User: michaels@netflix.com
 * Date: 5/8/15
 * Time: 2:44 PM
 */
class SampleIOPreFilter extends BaseFilter implements AsyncFilter
{
    protected static final Logger LOG = LoggerFactory.getLogger(SampleIOPreFilter.class);

    @Override
    Observable<SessionContext> applyAsync(SessionContext context)
    {
        // Get the origin to send request to.
        OriginManager originManager = context.getHelpers().get("origin_manager")
        String name = "origin"
        Origin origin = originManager.getOrigin(name)
        if (origin == null) {
            throw new ZuulException("No Origin registered for name=${name}!")
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
