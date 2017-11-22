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

package com.netflix.zuul.filters.passport;

import com.netflix.zuul.message.ZuulMessage;
import com.netflix.zuul.filters.SyncZuulFilterAdapter;
import com.netflix.zuul.passport.CurrentPassport;
import com.netflix.zuul.passport.PassportState;

/**
 * Created by saroskar on 2/18/17.
 */
public abstract class PassportStampingFilter<T extends ZuulMessage> extends SyncZuulFilterAdapter<T, T> {

    private final PassportState stamp;
    private final String name;

    public PassportStampingFilter(PassportState stamp) {
        this.stamp = stamp;
        this.name = filterType().name()+"-"+stamp.name()+"-Filter";
    }

    @Override
    public String filterName() {
        return name;
    }

    @Override
    public T getDefaultOutput(T input) {
        return input;
    }

    @Override
    public T apply(T input) {
        CurrentPassport.fromSessionContext(input.getContext()).add(stamp);
        return input;
    }

}
