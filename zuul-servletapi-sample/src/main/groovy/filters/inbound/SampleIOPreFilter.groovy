package filters.inbound

import com.netflix.client.ClientFactory
import com.netflix.client.http.HttpRequest
import com.netflix.client.http.HttpResponse
import com.netflix.niws.client.http.RestClient
import com.netflix.zuul.context.HttpRequestMessage
import com.netflix.zuul.context.SessionContext
import com.netflix.zuul.exception.ZuulException
import com.netflix.zuul.filters.http.HttpInboundFilter
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
class SampleIOPreFilter extends HttpInboundFilter
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
        int sampleStatus = makeSampleRemoteRequest("origin")

        // Get the result of the call.
        context.getAttributes().set("SampleIOPreFilter_status", sampleStatus)
        LOG.info("Received response for SampleIOPreFilter http call. status=${sampleStatus}")

        return Observable.just(request)
    }

    int makeSampleRemoteRequest(String restClientName)
    {
        RestClient client = (RestClient) ClientFactory.getNamedClient(restClientName);

        URI uri = URI.create("/account/geo");
        HttpRequest.Verb verb = HttpRequest.Verb.GET
        HttpRequest.Builder builder = HttpRequest.newBuilder().
                verb(verb).
                uri(uri);
        HttpRequest httpClientRequest = builder.build();

        // Execute the request.
        HttpResponse ribbonResp;
        try {
            ribbonResp = client.executeWithLoadBalancer(httpClientRequest);
        }
        catch (Exception e) {
            throw new ZuulException(e);
        }

        return ribbonResp.getStatus()
    }

    @Override
    boolean shouldFilter(HttpRequestMessage request) {
        return request.getQueryParams().get("check")
    }

    @Override
    int filterOrder() {
        return 5
    }
}
