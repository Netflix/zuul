package com.netflix.zuul.rxnetty;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.netflix.servo.monitor.DynamicTimer;
import com.netflix.servo.monitor.MonitorConfig;
import com.netflix.zuul.FilterFileManager;
import com.netflix.zuul.FilterProcessor;
import com.netflix.zuul.RequestCompleteHandler;
import com.netflix.zuul.context.*;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.HttpMethod;
import io.reactivex.netty.protocol.http.server.HttpServerRequest;
import io.reactivex.netty.protocol.http.server.HttpServerResponse;
import io.reactivex.netty.protocol.http.server.RequestHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;

import javax.annotation.Nullable;

/**
 * An implementation of {@link RequestHandler} for zuul.
 *
 * @author Nitesh Kant
 * @author Mike Smith
 */
@Singleton
public class ZuulRequestHandler implements RequestHandler<ByteBuf, ByteBuf> {

    private static final Logger LOG = LoggerFactory.getLogger(ZuulRequestHandler.class);

    @Inject
    private FilterProcessor filterProcessor;

    @Inject
    private SessionContextFactory contextFactory;

    @javax.inject.Inject
    @Nullable
    private SessionContextDecorator decorator;

    @Inject
    private HealthCheckRequestHandler healthCheckHandler;

    @Inject @Nullable
    private RequestCompleteHandler requestCompleteHandler;

    /** Ensure that this is initialized. */
    @Inject
    private FilterFileManager filterManager;


    @Override
    public Observable<Void> handle(HttpServerRequest<ByteBuf> request, HttpServerResponse<ByteBuf> response)
    {
        // Setup the context for this request.
        SessionContext context = new SessionContext();
        // Optionally decorate the context.
        if (decorator != null) {
            context = decorator.decorate(context);
        }

        // Build a ZuulMessage from the netty request.
        Observable<ZuulMessage> chain = contextFactory.create(context, request);

        // Start timing the request.
        chain = chain.doOnNext(msg -> {
            msg.getContext().getRequestTiming().start();
        });

        // Choose if this is a Healtcheck request, or a normal request, and build the chain accordingly.
        if (request.getHttpMethod().equals(HttpMethod.GET) && request.getUri().startsWith("/healthcheck")) {
            // Handle healthcheck requests.
            // TODO - Refactor this healthcheck impl to a standard karyon impl? See SimpleRouter in nf-prana.
            chain = chain.map(msg -> healthCheckHandler.handle((HttpRequestMessage) msg) );
        }
        else {
            /**
             * Delegate all of the filter application logic to {@link FilterProcessor}.
             * This work is some combination of synchronous and asynchronous.
             */
            chain = filterProcessor.applyInboundFilters(chain);
            chain = filterProcessor.applyEndpointFilter(chain);
            chain = filterProcessor.applyOutboundFilters(chain);
        }

        // After the request is handled, write out the response.
        chain = chain.doOnNext(ctx -> contextFactory.write(ctx, response));

        // Record the execution time reported by Origin.
        chain = chain.doOnNext(msg -> recordReportedOriginDuration((HttpResponseMessage) msg));

        // After complete, update metrics and access log.
        chain = chain.map(msg -> {
            // End the timing.
            msg.getContext().getRequestTiming().end();

            // Publish request timings.
            publishTimings(msg.getContext());

            // Notify requestComplete listener if configured.
            try {
                if (requestCompleteHandler != null)
                    requestCompleteHandler.handle((HttpResponseMessage) msg);
            }
            catch (Exception e) {
                LOG.error("Error in RequestCompleteHandler.", e);
            }
            return msg;
        });

        // Convert to an Observable<Void>.
        return chain
            .ignoreElements()
            .cast(Void.class);
    }

    protected void publishTimings(SessionContext context)
    {
        // Request timings.
        long totalRequestTime = context.getRequestTiming().getDuration();
        long requestProxyTime = context.getRequestProxyTiming().getDuration();
        int originReportedDuration = context.getOriginReportedDuration();

        // Approximation of time spent just within Zuul's own processing of the request.
        long totalInternalTime = totalRequestTime - requestProxyTime;

        // Approximation of time added to request by addition of Zuul+NIWS
        // (ie. the time added compared to if ELB sent request direct to Origin).
        // if -1, means we don't have that metric.
        long totalTimeAddedToOrigin = -1;
        if (originReportedDuration > -1) {
            totalTimeAddedToOrigin = totalRequestTime - originReportedDuration;
        }

        // Publish
        final String METRIC_TIMINGS_REQ_PREFIX = "zuul.timings.request.";
        recordRequestTiming(METRIC_TIMINGS_REQ_PREFIX + "total", totalRequestTime);
        recordRequestTiming(METRIC_TIMINGS_REQ_PREFIX + "proxy", requestProxyTime);
        recordRequestTiming(METRIC_TIMINGS_REQ_PREFIX + "internal", totalInternalTime);
        recordRequestTiming(METRIC_TIMINGS_REQ_PREFIX + "added", totalTimeAddedToOrigin);
    }

    private void recordRequestTiming(String name, long time) {
        if(time > -1) {
            DynamicTimer.record(MonitorConfig.builder(name).build(), time);
        }
    }

    /**
     * Record the duration that origin reports (if available).
     *
     * @param resp
     */
    private void recordReportedOriginDuration(HttpResponseMessage resp)
    {
        String reportedExecTimeStr = resp.getHeaders().getFirst("X-Netflix.execution-time");
        if (reportedExecTimeStr != null) {
            try {
                int reportedExecTime = Integer.parseInt(reportedExecTimeStr);
                resp.getContext().setOriginReportedDuration(reportedExecTime);
            }
            catch (Exception e) {
                LOG.error("Error parsing the X-Netflix.execution-time response header. value={}", reportedExecTimeStr);
            }
        }
    }
}
