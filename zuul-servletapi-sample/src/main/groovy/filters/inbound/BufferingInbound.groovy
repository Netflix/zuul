package filters.inbound

import com.netflix.zuul.context.HttpRequestMessage
import com.netflix.zuul.context.ZuulMessage
import com.netflix.zuul.filters.MessageBodyBufferFilter

/**
 * User: michaels@netflix.com
 * Date: 5/28/15
 * Time: 11:32 AM
 */
class BufferingInbound extends MessageBodyBufferFilter
{
    @Override
    boolean shouldFilter(ZuulMessage msg) {
        HttpRequestMessage request = msg
        return request.getQueryParams().getFirst("buffer")
    }

    @Override
    String filterType() {
        return "in"
    }
}
