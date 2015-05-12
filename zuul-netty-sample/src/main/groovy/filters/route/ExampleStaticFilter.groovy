package filters.route

import com.netflix.zuul.context.SessionContext
import com.netflix.zuul.filters.AsyncFilter
import com.netflix.zuul.filters.BaseFilter
import rx.Observable

/**
 * User: Mike Smith
 * Date: 5/12/15
 * Time: 1:53 PM
 */
class ExampleStaticFilter extends BaseFilter implements AsyncFilter
{
    @Override
    Observable<SessionContext> applyAsync(SessionContext context)
    {
        context.getHttpResponse().setStatus(200)
        context.getHttpResponse().getHeaders().set("Content-Type", "text/plain")
        context.getHttpResponse().setBody("blah blah\n".getBytes("UTF-8"))

        return Observable.just(context)
    }

    @Override
    boolean shouldFilter(SessionContext ctx) {
        return "example" == ctx.getAttributes().get("static_endpoint")
    }

    @Override
    int filterOrder() {
        return 20
    }

    @Override
    String filterType() {
        return "route"
    }
}
