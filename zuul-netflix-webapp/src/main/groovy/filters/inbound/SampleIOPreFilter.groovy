package filters.inbound

import com.netflix.zuul.context.HttpRequestMessage
import com.netflix.zuul.context.HttpResponseMessage
import com.netflix.zuul.context.SessionContext
import com.netflix.zuul.exception.ZuulException
import com.netflix.zuul.filters.BaseFilter
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
class SampleIOPreFilter extends BaseFilter<HttpRequestMessage, HttpRequestMessage>
{
    protected static final Logger LOG = LoggerFactory.getLogger(SampleIOPreFilter.class);

    @Override
    Observable<HttpRequestMessage> applyAsync(HttpRequestMessage request)
    {
        SessionContext context = request.getContext()

        // Get the origin to send request to.
        OriginManager originManager = context.getHelpers().get("origin_manager")
        String name = "origin"
        Origin origin = originManager.getOrigin(name)
        if (origin == null) {
            throw new ZuulException("No Origin registered for name=${name}!")
        }

        // Make the request.
        HttpRequestMessage testRequest = new HttpRequestMessage("HTTP/1.1", "get", "/account/geo", null, null,
                request.getClientIp(), "http")
        Observable<HttpResponseMessage> resultObs = origin.request(testRequest)

        resultObs = resultObs.map({ resp ->

            // Get the result of the call.
            int status = resp.getStatus()
            context.getAttributes().set("SampleIOPreFilter_status", status)
            LOG.info("Received response for SampleIOPreFilter http call. status=${status}")

            // Swap the original request back into the Observable returned.
            return request
        })

        return resultObs
    }

    @Override
    boolean shouldFilter(HttpRequestMessage request) {
        return request.getPath().startsWith("/account/")
    }

    @Override
    int filterOrder() {
        return 5
    }

    @Override
    String filterType() {
        return "in"
    }
}
