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
import com.google.inject.Singleton;
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
        chain = chain.flatMap(msg -> contextFactory.write(msg, response));

        // After complete, update metrics and access log.
        chain = chain.map(msg -> {
            // End the timing.
            msg.getContext().getRequestTiming().end();

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
}
