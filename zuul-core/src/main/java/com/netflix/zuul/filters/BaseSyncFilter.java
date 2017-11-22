/*
 * Copyright 2018 Netflix, Inc.
 *
 *      Licensed under the Apache License, Version 2.0 (the "License");
 *      you may not use this file except in compliance with the License.
 *      You may obtain a copy of the License at
 *
 *          http://www.apache.org/licenses/LICENSE-2.0
 *
 *      Unless required by applicable law or agreed to in writing, software
 *      distributed under the License is distributed on an "AS IS" BASIS,
 *      WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *      See the License for the specific language governing permissions and
 *      limitations under the License.
 */
package com.netflix.zuul.filters;

import com.netflix.zuul.message.ZuulMessage;
import rx.Observable;

/**
 * User: michaels@netflix.com
 * Date: 5/8/15
 * Time: 2:46 PM
 */
public abstract class BaseSyncFilter<I extends ZuulMessage, O extends ZuulMessage> extends BaseFilter<I,O> implements SyncZuulFilter<I, O>
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

    @Override
    public FilterSyncType getSyncType()
    {
        return FilterSyncType.SYNC;
    }
}
