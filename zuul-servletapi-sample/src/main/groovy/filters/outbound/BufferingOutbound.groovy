package filters.outbound

import com.netflix.zuul.context.HttpResponseMessage
import com.netflix.zuul.context.ZuulMessage
import com.netflix.zuul.filters.MessageBodyBufferFilter

/**
 * User: michaels@netflix.com
 * Date: 5/28/15
 * Time: 11:36 AM
 */
class BufferingOutbound extends MessageBodyBufferFilter
{
    @Override
    boolean shouldFilter(ZuulMessage msg) {
        HttpResponseMessage response = msg
        return response.getRequest().getQueryParams().getFirst("buffer")
    }

    @Override
    String filterType() {
        return "out"
    }
}
