package filters.pre

import com.netflix.zuul.filters.BaseFilter
import com.netflix.zuul.context.SessionContext
import com.netflix.zuul.filters.AsyncFilter
import rx.Observable

/**
 * User: michaels@netflix.com
 * Date: 5/8/15
 * Time: 2:44 PM
 */
class SampleIOPreFilter extends BaseFilter implements AsyncFilter
{
    @Override
    Observable<SessionContext> applyAsync(SessionContext context) {
        return null
    }

    @Override
    boolean shouldFilter(SessionContext ctx) {
        return false
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
