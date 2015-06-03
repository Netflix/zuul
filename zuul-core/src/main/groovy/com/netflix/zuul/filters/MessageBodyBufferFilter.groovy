package com.netflix.zuul.filters

import com.netflix.zuul.context.ZuulMessage
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import rx.Observable

/**
 * User: michaels@netflix.com
 * Date: 5/28/15
 * Time: 10:54 AM
 */
public abstract class MessageBodyBufferFilter extends BaseFilter<ZuulMessage, ZuulMessage>
{
    private static final Logger LOG = LoggerFactory.getLogger(MessageBodyBufferFilter.class);

    @Override
    Observable<ZuulMessage> applyAsync(ZuulMessage msg)
    {
        return msg.bufferBody()
                    .map({bytes -> msg})
    }

    @Override
    boolean shouldFilter(ZuulMessage msg) {
        return true
    }

    @Override
    int filterOrder() {
        return -10
    }
}