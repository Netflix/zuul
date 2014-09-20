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

import java.util.List;

/**
 * An implementation of {@link RequestHandler} for zuul.
 *
 * @author Nitesh Kant
 */
public class ZuulRequestHandler<State> implements RequestHandler<ByteBuf, ByteBuf> {

    private static final Logger logger = LoggerFactory.getLogger(ZuulRequestHandler.class);

    private final FilterProcessor<State> filterProcessor;
    private final AccessLogPublisher accessLogPublisher;

    public ZuulRequestHandler(FilterProcessor<State> filterProcessor) {
        this.filterProcessor = filterProcessor;
        accessLogPublisher = new AccessLogPublisher();
    }

    @Override
    public Observable<Void> handle(HttpServerRequest<ByteBuf> request, HttpServerResponse<ByteBuf> response) {
        final IngressRequest ingressReq = IngressRequest.from(request);
        if (request.getHttpMethod().equals(HttpMethod.GET) && request.getUri().startsWith("/healthcheck")) {
            response.setStatus(HttpResponseStatus.OK);
            return response.close();
        }

        return filterProcessor.applyAllFilters(ingressReq)
                              .flatMap(egressResp -> {
                                  response.setStatus(egressResp.getStatus());
                                  if (logger.isDebugEnabled()) {
                                      logger.debug("Setting Outgoing HTTP Status : " + egressResp.getStatus());
                                  }

                                  accessLogPublisher.publish(new SimpleAccessRecord(egressResp.getStatus().code(),
                                                                                    request.getHttpMethod().name(),
                                                                                    request.getUri(),
                                                                                    request.getNettyChannel().remoteAddress()));

                                  for (String headerName : egressResp.getHeaders().keySet()) {
                                      List<String> headerValues = egressResp.getHeaders().get(headerName);
                                      if (logger.isDebugEnabled()) {
                                          for (String headerValue : headerValues) {
                                              logger.debug("Setting Outgoing HTTP Header : " + headerName + " -> "
                                                           + headerValue);
                                          }
                                      }
                                      response.getHeaders().add(headerName, headerValues);
                                  }

                                  return egressResp.getContent();
                              })
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
