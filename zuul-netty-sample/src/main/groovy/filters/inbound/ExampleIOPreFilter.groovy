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
 * Make a HTTP request to api-global just to demonstrate this kind of async IO call as part of a pre filter.
 *
 * Doing it in a hacky way by using the OriginManager for the remote call. As don't have another configured http
 * client to hand to use otherwise.
 *
 * User: Mike Smith
 * Date: 5/12/15
 * Time: 3:51 PM
 */
class ExampleIOPreFilter extends BaseFilter<HttpRequestMessage, HttpRequestMessage>
{
    protected static final Logger LOG = LoggerFactory.getLogger(ExampleIOPreFilter.class);

    @Override
    Observable<HttpRequestMessage> applyAsync(HttpRequestMessage request)
    {
        SessionContext context = request.getContext()

        // Get the origin to send request to.
        OriginManager originManager = context.getHelpers().get("origin_manager")
        String name = "api_netflix"
        Origin origin = originManager.getOrigin(name)
        if (origin == null) {
            throw new ZuulException("No Origin registered for name=${name}!")
        }

        // Make the request.
        HttpRequestMessage ioRequest = new HttpRequestMessage(context, "HTTP/1.1", "get", "/account/geo", null, null,
                request.getClientIp(), "http")
        Observable<HttpResponseMessage> resultObs = origin.request(ioRequest)

        resultObs = resultObs.map({ resp ->

            // Get the result of the call.
            int status = resp.getStatus()
            context.getAttributes().set("ExampleIOPreFilter_status", status)
            LOG.info("Received response for ExampleIOPreFilter http call. status=${status}")

            // Swap the original context back into the Observable returned.
            return request
        })

        return resultObs
    }

    @Override
    boolean shouldFilter(HttpRequestMessage request) {
        return request.getQueryParams().get("check")
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
