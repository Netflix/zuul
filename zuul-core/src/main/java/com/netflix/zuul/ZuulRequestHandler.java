package com.netflix.zuul;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.netflix.zuul.accesslog.AccessLogPublisher;
import com.netflix.zuul.accesslog.SimpleAccessRecord;
import com.netflix.zuul.lifecycle.FilterProcessor;
import com.netflix.zuul.lifecycle.IngressRequest;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.reactivex.netty.protocol.http.server.HttpServerRequest;
import io.reactivex.netty.protocol.http.server.HttpServerResponse;
import io.reactivex.netty.protocol.http.server.RequestHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * An implementation of {@link RequestHandler} for zuul.
 *
 * @author Nitesh Kant
 */
@Singleton
public class ZuulRequestHandler<State> implements RequestHandler<ByteBuf, ByteBuf> {

    private static final Logger LOG = LoggerFactory.getLogger(ZuulRequestHandler.class);
    private static final Logger HTTP_DEBUG_LOGGER = LoggerFactory.getLogger("HTTP_DEBUG");

    @Inject
    private FilterProcessor<State> filterProcessor;

    @Inject
    private AccessLogPublisher accessLogPublisher;

    @Inject
    private HealthCheckRequestHandler healthCheckHandler;

    @Override
    public Observable<Void> handle(HttpServerRequest<ByteBuf> request, HttpServerResponse<ByteBuf> response) {

        // TODO - Refactor this healthcheck impl to a standard karyon impl?
        // Handle healthcheck requests.
        if (request.getHttpMethod().equals(HttpMethod.GET) && request.getUri().startsWith("/healthcheck")) {
            return healthCheckHandler.handle(request, response);
        }

        /**
         * Convert the RxNetty {@link HttpServerRequest} into a Zuul domain object - a {@link IngressRequest}
         * this is synchronous
         */
        final IngressRequest ingressReq = IngressRequest.from(request);

        final long startTime = System.nanoTime();

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
         *
         * At the end of the filter chain, we have an {@link Observable<EgressResponse>}.
         * We now need to convert it back into the the RxNetty domain - a {@link HttpServerResponse}
         * This {@link HttpServerResponse needs to get written to and then we signal to RxNetty we are done
         * writing via a call to {@link HttpServerResponse#close()}.
         */
        return filterProcessor.applyAllFilters(ingressReq)

                //first set the status
                .flatMap(egressResp -> {
                    response.setStatus(egressResp.getStatus());
                    if (HTTP_DEBUG_LOGGER.isDebugEnabled()) {
                        HTTP_DEBUG_LOGGER.debug("Setting Outgoing HTTP Status : " + egressResp.getStatus());
                    }

                    // Now set all of the response headers - note this is a multi-set in keeping with HTTP semantics
                    for (String headerName : egressResp.getHeaders().keySet()) {
                        List<String> headerValues = egressResp.getHeaders().get(headerName);
                        if (HTTP_DEBUG_LOGGER.isDebugEnabled()) {
                            for (String headerValue : headerValues) {
                                HTTP_DEBUG_LOGGER.info("Setting Outgoing HTTP Header : " + headerName + " -> "
                                        + headerValue);
                            }
                        }
                        response.getHeaders().add(headerName, headerValues);
                    }

                    //now work with the HTTP response content - note this may be a stream of response data as in a chunked response
                    return egressResp.getContent();
                })
                //for each piece of content, write it out to the {@link HttpServerResponse}
                // TODO - are we handling chunked responses from origins here?
                .map(byteBuf -> {
                    byteBuf.retain();
                    // Record size of response body written.
                    bytesWritten.addAndGet(byteBuf.maxCapacity());
                    response.write(byteBuf);
                    return null;
                })
                .ignoreElements()
                .cast(Void.class)
                .doOnCompleted(response::close)
                .finallyDo(
                        () -> {
                            long durationNs = System.nanoTime() - startTime;
                            int responseBodySize = bytesWritten.get();

                            // TODO - publish this duration to metrics.

                            // Write to access log.
                            // TODO - can we do this after finishing writing out the response body so that we can log
                            // the response size?
                            accessLogPublisher.publish(new SimpleAccessRecord(LocalDateTime.now(),
                                    response.getStatus().code(),
                                    request.getHttpMethod().name(),
                                    request.getPath(),
                                    request.getQueryString(),
                                    durationNs,
                                    responseBodySize,
                                    request.getHeaders(),
                                    response.getHeaders()
                            ));
                        }
                );
    }
}
