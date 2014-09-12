/**
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.zuul;

import com.google.inject.Module;
import com.google.inject.binder.AnnotatedBindingBuilder;
import com.netflix.governator.annotations.Modules;
import com.netflix.karyon.KaryonBootstrap;
import com.netflix.karyon.archaius.ArchaiusBootstrap;
import com.netflix.karyon.eureka.KaryonEurekaModule;
import com.netflix.karyon.transport.http.AbstractHttpModule;
import com.netflix.karyon.transport.http.HttpRequestHandler;
import com.netflix.karyon.transport.http.HttpRequestRouter;
import com.netflix.zuul.lifecycle.FilterProcessor;
import com.netflix.zuul.lifecycle.IngressRequest;
import com.netflix.zuul.metrics.ZuulGlobalMetricsPublisher;
import com.netflix.zuul.metrics.ZuulMetricsPublisherFactory;
import io.netty.buffer.ByteBuf;
import io.reactivex.netty.protocol.http.server.HttpServerBuilder;
import io.reactivex.netty.protocol.http.server.HttpServerRequest;
import io.reactivex.netty.protocol.http.server.HttpServerResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.functions.Func1;

import java.util.List;
import java.util.concurrent.TimeUnit;

public class ZuulKaryonServer {

    private ZuulKaryonServer() {
    }

    /**
     * I'd fire myself if I wrote this code for real
     * 
     * We are working on the real API solution with karyon-core/karyon-governator
     */
    
    /* DON'T DO THIS AT HOME */
    private static int port = 8888;
    private static FilterProcessor<?> filterProcessor;
    /* DON'T DO THIS AT HOME */

    private static final Logger logger = LoggerFactory.getLogger(ZuulKaryonServer.class);

    public static <State> void startZuulServer(int port, FilterProcessor<State> filterProcessor) {
        startZuulServerWithAdditionalModule(port, filterProcessor);
    }

    public static <State> void startZuulServerWithAdditionalModule(int port, FilterProcessor<State> filterProcessor,
                                                                               Module... additionalModules) {
        logger.info("**** Starting Zuul with Karyon 2");
        /* DON'T DO THIS AT HOME */
        ZuulKaryonServer.port = port;
        ZuulKaryonServer.filterProcessor = filterProcessor;
        /* DON'T DO THIS AT HOME */

        ZuulGlobalMetricsPublisher zuulGlobalMetricsPublisher = ZuulMetricsPublisherFactory.createOrRetrieveGlobalPublisher();

        new ZuulBootstrap(ZuulApp.class, additionalModules).startAndAwait(); // I need this to be an instance, not a class ... I hate annotations
    }

    @ArchaiusBootstrap
    @KaryonBootstrap(name = "zuul")
    // this being statically defined means I can't inject config
    @Modules(include = { TemporaryStaticHackThing.class, KaryonEurekaModule.class })
    public class ZuulApp {

    }

    public static class TemporaryStaticHackThing extends AbstractHttpModule<ByteBuf, ByteBuf> {

        public TemporaryStaticHackThing() {
            super(ByteBuf.class, ByteBuf.class);
        }

        @Override
        public int serverPort() {
            return ZuulKaryonServer.port;
        }

        @Override
        public int shutdownPort() {
            return ZuulKaryonServer.port + 1; // NEVER DO THIS
        }

        @Override
        protected void bindRequestRouter(AnnotatedBindingBuilder<HttpRequestRouter<ByteBuf, ByteBuf>> bind) {
            bind.toInstance(new ZuulRouter(ZuulKaryonServer.filterProcessor));
        }

        @Override
        protected HttpServerBuilder<ByteBuf, ByteBuf> newServerBuilder(int port, HttpRequestHandler<ByteBuf, ByteBuf> requestHandler) {
            /**
             * Why do we need to cache the content here longer?
             *
             * This is the processing flow:
             *
             *  (1) RxNetty server receives request headers -> (2) invokes Zuul's request handler ->
             *  (3) Runs through pre-filters -> (4) Submits request to the origin (RxClient) ->
             *  (5) RxNetty connects to origin -> (6) Write headers -> (7) Subscribes to the ServerRequest content
             *  -> (8) Writes each content to the connection.
             *
             *  Since step (4) terminates the server pipeline handling for Http Server request, RxNetty will start reading
             *  content from the inbound, potentially even before step (5) and the subscription on step (7)
             *  If we do not cache data here, the data will be lost.
             *  OTOH, we can not eagerly subscribe to the content as the RxClient does the subscription only after the
             *  connection to origin is successful. The behavior of RxClient is appropriate as we do not really want to
             *  subscribe to content before we are ready to write.
             *  In this case, we are not requesting content from upstream on demand so we need to cache.
             *
             *  What happens in a streaming scenario?
             *
             *  We would have to leverage backpressure if we handle cases wherein the content is streamed as part of the
             *  Server request. So, when we are overwhelmed with data (client slow) we can stop reading from upstream.
             *
             *  TODO: Should the timeout be configurable?
             */
            return super.newServerBuilder(port, requestHandler).withRequestContentSubscriptionTimeout(1, TimeUnit.MINUTES);
        }
    }

    private static class ZuulRouter<State> implements HttpRequestRouter<ByteBuf, ByteBuf> {

        private final FilterProcessor<State> filterProcessor;

        public ZuulRouter(FilterProcessor<State> filterProcessor) {
            this.filterProcessor = filterProcessor;
        }

        @Override
        public Observable<Void> route(HttpServerRequest<ByteBuf> request, HttpServerResponse<ByteBuf> response) {
            final IngressRequest ingressReq = IngressRequest.from(request);
            logger.info(request.getHttpMethod().name() + " " + request.getUri() + " " + request.getNettyChannel().remoteAddress().toString());
            return filterProcessor.applyAllFilters(ingressReq).
                    flatMap(egressResp -> {
                        response.setStatus(egressResp.getStatus());
                        if (logger.isDebugEnabled()) {
                            logger.debug("Setting Outgoing HTTP Status : " + egressResp.getStatus());
                        }
                        logger.info(egressResp.getStatus().code() + " " + request.getHttpMethod().name() + " " + request.getUri() + " " + request.getNettyChannel().remoteAddress().toString());


                        for (String headerName : egressResp.getHeaders().keySet()) {
                            List<String> headerValues = egressResp.getHeaders().get(headerName);
                            if (logger.isDebugEnabled()) {
                                for (String headerValue : headerValues) {
                                    logger.debug("Setting Outgoing HTTP Header : " + headerName + " -> " + headerValue);
                                }
                            }
                            response.getHeaders().add(headerName, headerValues);
                        }

                        return egressResp.getContent();
                    }).map(new Func1<ByteBuf, Void>() {
                        @Override
                        public Void call(ByteBuf byteBuf) {
                            byteBuf.retain();
                            response.write(byteBuf);
                            return null;
                        }
                    }).
                    doOnCompleted(response::close);
        }
    }
}
