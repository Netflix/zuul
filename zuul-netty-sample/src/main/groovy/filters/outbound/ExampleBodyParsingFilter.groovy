package filters.outbound

import com.netflix.zuul.bytebuf.ByteBufUtils
import com.netflix.zuul.context.HttpResponseMessage
import com.netflix.zuul.filters.BaseFilter
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import rx.Observable

/**
 * User: michaels@netflix.com
 * Date: 5/28/15
 * Time: 9:22 AM
 */
class ExampleBodyParsingFilter extends BaseFilter<HttpResponseMessage, HttpResponseMessage>
{
    protected static final Logger LOG = LoggerFactory.getLogger(ExampleBodyParsingFilter.class);

    @Override
    Observable<HttpResponseMessage> applyAsync(HttpResponseMessage response)
    {
        Observable<HttpResponseMessage> obs = response.getBodyStream()
        .doOnNext({bb ->
            // Pass each bytebuf to some other component for conversion/decryption.
            //bb.retain()
            println "bytebuf=" + new String(ByteBufUtils.toBytes(bb))

            return bb
        })
        .map({bb ->
            return response
        })
        .single()
        .doOnError({e ->
            e.printStackTrace()
        })
        .doOnCompleted({
            println "completed"
        })

        return obs
    }

    @Override
    boolean shouldFilter(HttpResponseMessage response) {
        return response.getRequest().getQueryParams().get("parse")
    }

    @Override
    int filterOrder() {
        return 10
    }

    @Override
    String filterType() {
        return "out"
    }
}
