package com.netflix.zuul.filters;

import com.netflix.zuul.context.ZuulMessage;
import rx.Observable;

/**
 * User: michaels@netflix.com
 * Date: 5/8/15
 * Time: 2:46 PM
 */
public abstract class BaseSyncFilter<I extends ZuulMessage, O extends ZuulMessage> extends BaseFilter<I,O>
{
    /**
     * A wrapper implementation of applyAsync() that is intended just to aggregate a non-blocking apply() method
     * in an Observable.
     *
     * A subclass filter should override this method if doing any IO.
     *
     * @param input
     * @return
     */
    @Override
    public Observable<O> applyAsync(I input)
    {
        return Observable.just(this.apply(input));
    }

    public abstract O apply(I input);
}
