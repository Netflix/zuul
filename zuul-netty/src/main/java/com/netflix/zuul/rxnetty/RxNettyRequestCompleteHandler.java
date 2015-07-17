/**
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.zuul.rxnetty;

import com.google.inject.Inject;
import com.netflix.zuul.RequestCompleteHandler;
import com.netflix.zuul.accesslog.AccessLogPublisher;
import com.netflix.zuul.accesslog.SimpleAccessRecord;
import com.netflix.zuul.message.http.HttpRequestInfo;
import com.netflix.zuul.message.http.HttpResponseMessage;
import com.netflix.zuul.context.SessionContext;
import com.netflix.zuul.stats.RequestMetricsPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.time.LocalDateTime;

/**
 * User: michaels@netflix.com
 * Date: 5/8/15
 * Time: 4:44 PM
 */
public class RxNettyRequestCompleteHandler implements RequestCompleteHandler
{
    private final Logger LOG = LoggerFactory.getLogger(this.getClass());

    @Inject
    private AccessLogPublisher accessLogPublisher;

    @Inject @Nullable
    private RequestMetricsPublisher requestMetricsPublisher;

    @Override
    public void handle(HttpResponseMessage response)
    {
        HttpRequestInfo request = response.getInboundRequest();
        SessionContext context = response.getContext();

        long duration = context.getTimings().getRequest().getDuration();
        int responseBodySize = response.getBody() == null ? 0 : response.getBody().length;

        // Write to access log.
        accessLogPublisher.publish(new SimpleAccessRecord(LocalDateTime.now(),
                response.getStatus(),
                request.getMethod(),
                request.getPathAndQuery(),
                duration,
                responseBodySize,
                request.getHeaders(),
                response.getHeaders()
        ));

        // Publish request-level metrics.
        if (requestMetricsPublisher != null) {
            requestMetricsPublisher.collectAndPublish(context);
        }
    }
}
