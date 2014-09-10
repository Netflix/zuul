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

import com.netflix.zuul.lifecycle.FilterProcessor;
import com.netflix.zuul.lifecycle.IngressRequest;
import io.netty.buffer.ByteBuf;
import io.reactivex.netty.RxNetty;
import io.reactivex.netty.pipeline.PipelineConfigurators;
import io.reactivex.netty.protocol.http.server.HttpServer;
import io.reactivex.netty.protocol.http.server.HttpServerRequest;
import io.reactivex.netty.protocol.http.server.HttpServerResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.functions.Func1;

import java.util.List;

public class ZuulRxNettyServer<State> {
    private static final Logger logger = LoggerFactory.getLogger(ZuulRxNettyServer.class);

    private final int port;
    private final FilterProcessor<State> filterProcessor;

    public ZuulRxNettyServer(int port, FilterProcessor<State> filterProcessor) {
        this.port = port;
        this.filterProcessor = filterProcessor;
    }

    public HttpServer<ByteBuf, ByteBuf> createServer() {
        HttpServer<ByteBuf, ByteBuf> server = RxNetty.newHttpServerBuilder(port,
                (HttpServerRequest<ByteBuf> request, HttpServerResponse<ByteBuf> response) -> {
                    final IngressRequest ingressReq = IngressRequest.from(request);
                    return filterProcessor.applyAllFilters(ingressReq).
                            flatMap(egressResp -> {
                                response.setStatus(egressResp.getStatus());
                                if (logger.isDebugEnabled()) {
                                    logger.debug("Setting Outgoing HTTP Status : " + egressResp.getStatus());
                                }

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
                }).pipelineConfigurator(PipelineConfigurators.<ByteBuf, ByteBuf>httpServerConfigurator()).build();

        logger.info("Started Zuul Netty HTTP Server!!");
        return server;
    }
}
