package filters.endpoint

import com.netflix.zuul.context.HttpRequestMessage
import com.netflix.zuul.context.HttpResponseMessage
import com.netflix.zuul.filters.Endpoint
import rx.Observable

/**
 * User: Mike Smith
 * Date: 5/12/15
 * Time: 1:53 PM
 */
class ExampleStaticFilter extends Endpoint<HttpRequestMessage, HttpResponseMessage>
{
    @Override
    Observable<HttpResponseMessage> applyAsync(HttpRequestMessage request)
    {
        HttpResponseMessage response = new HttpResponseMessage(request.getContext(), request, 200)

        response.setStatus(200)
        response.getHeaders().set("Content-Type", "text/plain")
        response.setBody("blah blah\n".getBytes("UTF-8"))

        return Observable.just(response)
    }
}
