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
import com.netflix.karyon.transport.http.HttpRequestRouter;
import io.netty.buffer.ByteBuf;
import io.reactivex.netty.protocol.http.server.HttpServerRequest;
import io.reactivex.netty.protocol.http.server.HttpServerResponse;
import rx.Observable;
import rx.functions.Func1;

import java.util.Map;

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
    private static FilterProcessor<?, ?> filterProcessor;
    /* DON'T DO THIS AT HOME */

    public static <Request, Response> void startZuulServer(int port,
                                                           FilterProcessor<Request, Response> filterProcessor) {
        startZuulServerWithAdditionalModule(port, filterProcessor);
    }

    public static <Request, Response> void startZuulServerWithAdditionalModule(int port,
                                                                               FilterProcessor<Request, Response> filterProcessor,
                                                                               Module... additionalModules) {
        System.out.println("**** Starting Zuul with Karyon 2");
        /* DON'T DO THIS AT HOME */
        ZuulKaryonServer.port = port;
        ZuulKaryonServer.filterProcessor = filterProcessor;
        /* DON'T DO THIS AT HOME */

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
    }

    private static class ZuulRouter<Request, Response> implements HttpRequestRouter<ByteBuf, ByteBuf> {

        private final FilterProcessor<Request, Response> filterProcessor;

        public ZuulRouter(FilterProcessor<Request, Response> filterProcessor) {
            this.filterProcessor = filterProcessor;
        }

        @Override
        public Observable<Void> route(HttpServerRequest<ByteBuf> request, HttpServerResponse<ByteBuf> response) {
            final IngressRequest ingressReq = IngressRequest.from(request, response.getChannelHandlerContext());
            return filterProcessor.applyAllFilters(ingressReq).
                    flatMap(egressResp -> {
                        response.setStatus(egressResp.getStatus());
                        System.out.println("Setting Outgoing HTTP Status : " + egressResp.getStatus());

                        for (Map.Entry<String, String> entry: egressResp.getHeaders().entrySet()) {
                            //System.out.println("Setting Outgoing HTTP Header : " + entry.getKey() + " -> " + entry.getValue());
                            response.getHeaders().add(entry.getKey(), entry.getValue());
                        }

                        return egressResp.getContent();
                    }).
                    map(new Func1<ByteBuf, Void>() {
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
