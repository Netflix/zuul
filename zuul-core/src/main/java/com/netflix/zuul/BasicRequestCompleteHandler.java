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
