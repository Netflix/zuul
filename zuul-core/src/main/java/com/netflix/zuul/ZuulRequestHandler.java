package com.netflix.zuul;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.netflix.servo.monitor.DynamicTimer;
import com.netflix.servo.monitor.MonitorConfig;
import com.netflix.zuul.context.FilterStateFactory;
import com.netflix.zuul.context.RequestContext;
import com.netflix.zuul.lifecycle.FilterProcessor;
import com.netflix.zuul.lifecycle.HttpResponseMessage;
import com.netflix.zuul.metrics.Timing;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.reactivex.netty.protocol.http.server.HttpServerRequest;
import io.reactivex.netty.protocol.http.server.HttpServerResponse;
import io.reactivex.netty.protocol.http.server.RequestHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * An implementation of {@link RequestHandler} for zuul.
 *
 * @author Nitesh Kant
 * @author Mike Smith
 */
@Singleton
public class ZuulRequestHandler implements RequestHandler<ByteBuf, ByteBuf> {

    private static final Logger LOG = LoggerFactory.getLogger(ZuulRequestHandler.class);
    private static final Logger HTTP_DEBUG_LOGGER = LoggerFactory.getLogger("HTTP_DEBUG");

    @Inject
    private FilterProcessor filterProcessor;

    @Inject
    private FilterStateFactory<RequestContext> stateFactory;

    @Inject
    private HealthCheckRequestHandler healthCheckHandler;

    @Inject @Nullable
    private RequestCompleteHandler requestCompleteHandler;


    @Override
    public Observable<Void> handle(HttpServerRequest<ByteBuf> request, HttpServerResponse<ByteBuf> response) {

        final long startTime = System.nanoTime();

        /**
         * Convert the RxNetty {@link HttpServerRequest} into a Zuul domain object - a {@link IngressRequest}
         * this is synchronous
         */
        final RequestContext context = stateFactory.create(request);

        final Timing timing = context.getRequestTiming();
        timing.start();

        // TODO - Refactor this healthcheck impl to a standard karyon impl? See SimpleRouter in nf-prana.
        // Handle healthcheck requests.
        if (request.getHttpMethod().equals(HttpMethod.GET) && request.getUri().startsWith("/healthcheck")) {

            Observable<Void> o = healthCheckHandler.handle(request, response)
//                    .doOnCompleted(() -> {
//                        LOG.debug("Calling response.close()");
//                        response.close();
//                        LOG.debug("Called response.close()");
//                    })
                    .finallyDo(() -> {

                                timing.end();
                                long durationNs = System.nanoTime() - startTime;

                                // Ensure some response info is correct in RequestContext for logging/metrics purposes.
                                HttpResponseMessage zuulResp = (HttpResponseMessage) context.getResponse();
                                zuulResp.setStatus(response.getStatus().code());

                                if (requestCompleteHandler != null)
                                    requestCompleteHandler.handle(context, durationNs, 0);
                            }
                    );

            return o;
        }

        if (HTTP_DEBUG_LOGGER.isDebugEnabled()) {
            for (String headerName: request.getHeaders().names()) {
                HTTP_DEBUG_LOGGER.info("Incoming HTTP Header : " + headerName + " -> " + request.getHeaders().get(headerName));
            }
        }

        // We increment this as we write bytes to the response.
        final AtomicInteger bytesWritten = new AtomicInteger(0);

        /**
         * Delegate all of the filter application logic to {@link FilterProcessor}.
         * This work is some combination of synchronous and asynchronous.
         */
        return filterProcessor.applyAllFilters(context)
                .single()
                .doOnNext(respCtx -> {

                    HttpResponseMessage zuulResp = (HttpResponseMessage) respCtx.getResponse();

                    // Set the response status code.
                    response.setStatus(HttpResponseStatus.valueOf(zuulResp.getStatus()));

                    // Now set all of the response headers - note this is a multi-set in keeping with HTTP semantics
                    for (Map.Entry<String, String> entry : zuulResp.getHeaders().entries()) {
                        response.getHeaders().add(entry.getKey(), entry.getValue());
                    }

                    // Record the execution time reported by Origin.
                    recordReportedOriginDuration(respCtx);

                    if (zuulResp.getBody() != null) {
                        // Record size of response body written.
                        bytesWritten.addAndGet(zuulResp.getBody().length);

                        // Write response body bytes.
                        response.write(Unpooled.wrappedBuffer(zuulResp.getBody()));
                    }

                })
                //for each piece of content, write it out to the {@link HttpServerResponse}
//                .map(byteBuf -> {
//                    byteBuf.retain();
//                    // Record size of response body written.
//                    bytesWritten.addAndGet(byteBuf.maxCapacity());
//                    response.write(byteBuf);
//                    return null;
//                })
                // TODO - why is this needed/wanted?
                .ignoreElements()
                .cast(Void.class)
                .finallyDo(
                        () -> {
                            long durationNs = System.nanoTime() - startTime;
                            int responseBodySize = bytesWritten.get();
                            timing.end();

                            // TODO - this is relying on the all the filters having returned the a ref to the
                            // original RequestContext rather than a copy of. Not good. Need to find another
                            // way to do this.

                            // Publish request timings.
                            publishTimings(context);

                            // Ensure some response info is correct in RequestContext for logging/metrics purposes.
                            HttpResponseMessage zuulResp = (HttpResponseMessage) context.getResponse();
                            zuulResp.setStatus(response.getStatus().code());

                            try {
                                if (requestCompleteHandler != null)
                                    requestCompleteHandler.handle(context, durationNs, responseBodySize);
                            }
                            catch (Exception e) {
                                LOG.error("Error in RequestCompleteHandler.", e);
                            }
                        }
                );
    }

    protected void publishTimings(RequestContext context)
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
     * @param context
     */
    private void recordReportedOriginDuration(RequestContext context)
    {
        HttpResponseMessage resp = (HttpResponseMessage) context.getResponse();
        String reportedExecTimeStr = resp.getHeaders().getFirst("X-Netflix.execution-time");
        if (reportedExecTimeStr != null) {
            try {
                int reportedExecTime = Integer.parseInt(reportedExecTimeStr);
                context.setOriginReportedDuration(reportedExecTime);
            } catch (Exception e) {
                LOG.error("Error parsing the X-Netflix.execution-time response header. value={}", reportedExecTimeStr);
            }
        }
    }
}
