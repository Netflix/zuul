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

import com.netflix.zuul.filters.FilterType;
import com.netflix.zuul.message.http.HttpResponseMessage;
import com.netflix.zuul.passport.PassportState;

import static com.netflix.zuul.filters.FilterType.OUTBOUND;

/**
 * Created by saroskar on 3/14/17.
 */
public final class OutboundPassportStampingFilter extends PassportStampingFilter<HttpResponseMessage> {

    public OutboundPassportStampingFilter(PassportState stamp) {
        super(stamp);
    }

    @Override
    public FilterType filterType() {
        return OUTBOUND;
    }

}
