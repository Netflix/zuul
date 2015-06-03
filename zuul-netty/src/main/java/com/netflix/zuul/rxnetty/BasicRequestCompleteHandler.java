package com.netflix.zuul.rxnetty;

import com.google.inject.Inject;
import com.netflix.zuul.RequestCompleteHandler;
import com.netflix.zuul.accesslog.AccessLogPublisher;
import com.netflix.zuul.accesslog.SimpleAccessRecord;
import com.netflix.zuul.context.HttpRequestMessage;
import com.netflix.zuul.context.HttpResponseMessage;
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
public class BasicRequestCompleteHandler implements RequestCompleteHandler
{
    private final Logger LOG = LoggerFactory.getLogger(this.getClass());

    @Inject
    private AccessLogPublisher accessLogPublisher;

    @Inject @Nullable
    private RequestMetricsPublisher requestMetricsPublisher;

    @Override
    public void handle(HttpResponseMessage response)
    {
        HttpRequestMessage request = response.getRequest();
        SessionContext context = response.getContext();

        long duration = context.getRequestTiming().getDuration();
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
