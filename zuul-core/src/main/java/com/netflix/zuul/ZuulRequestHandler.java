package com.netflix.zuul;

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

/**
 * An implementation of {@link RequestHandler} for zuul.
 *
 * @author Nitesh Kant
 */
public class ZuulRequestHandler<State> implements RequestHandler<ByteBuf, ByteBuf> {

    private static final Logger HTTP_DEBUG_LOGGER = LoggerFactory.getLogger("HTTP_DEBUG");

    private final FilterProcessor<State> filterProcessor;
    private final AccessLogPublisher accessLogPublisher;

    public ZuulRequestHandler(FilterProcessor<State> filterProcessor) {
        this.filterProcessor = filterProcessor;
        accessLogPublisher = new AccessLogPublisher();
    }

    @Override
    public Observable<Void> handle(HttpServerRequest<ByteBuf> request, HttpServerResponse<ByteBuf> response) {
        /**
         * Convert the RxNetty {@link HttpServerRequest} into a Zuul domain object - a {@link IngressRequest}
         * this is synchronous
         */
        final IngressRequest ingressReq = IngressRequest.from(request);
        if (request.getHttpMethod().equals(HttpMethod.GET) && request.getUri().startsWith("/healthcheck")) {
            response.setStatus(HttpResponseStatus.OK);
            return response.close();
        }

        if (HTTP_DEBUG_LOGGER.isDebugEnabled()) {
            for (String headerName: request.getHeaders().names()) {
                HTTP_DEBUG_LOGGER.info("Incoming HTTP Header : " + headerName + " -> " + request.getHeaders().get(headerName));
            }
        }

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
                    accessLogPublisher.publish(new SimpleAccessRecord(LocalDateTime.now(),
                            egressResp.getStatus().code(),
                            request.getHttpMethod().name(),
                            request.getPath()));
                    //now set all of the response headers - note this is a multi-set in keeping with HTTP semantics
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
                .map(byteBuf -> {
                    byteBuf.retain();
                    response.write(byteBuf);
                    return null;
                })
                .ignoreElements()
                .cast(Void.class)
                .doOnCompleted(response::close);
    }
}
