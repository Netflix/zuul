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

package com.netflix.zuul;

import com.google.inject.Inject;
import com.netflix.zuul.message.http.HttpRequestInfo;
import com.netflix.zuul.message.http.HttpResponseMessage;
import com.netflix.zuul.context.SessionContext;
import com.netflix.zuul.stats.RequestMetricsPublisher;

import javax.annotation.Nullable;

/**
 * User: michaels@netflix.com
 * Date: 6/4/15
 * Time: 4:26 PM
 */
public class BasicRequestCompleteHandler implements RequestCompleteHandler
{
    @Inject @Nullable
    private RequestMetricsPublisher requestMetricsPublisher;

    @Override
    public void handle(HttpRequestInfo inboundRequest, HttpResponseMessage response)
    {
        SessionContext context = inboundRequest.getContext();

        // Publish request-level metrics.
        if (requestMetricsPublisher != null) {
            requestMetricsPublisher.collectAndPublish(context);
        }
    }
}
