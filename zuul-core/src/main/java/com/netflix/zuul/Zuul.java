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

import com.netflix.zuul.filterstore.ClassPathFilterStore;
import com.netflix.zuul.filterstore.FilterStore;
import com.netflix.zuul.lifecycle.FilterProcessor;
import com.netflix.zuul.lifecycle.FilterStateFactory;
import io.netty.buffer.ByteBuf;
import io.reactivex.netty.RxNetty;
import io.reactivex.netty.protocol.http.server.HttpServer;
import io.reactivex.netty.protocol.http.server.HttpServerBuilder;
import io.reactivex.netty.protocol.http.server.HttpServerPipelineConfigurator;
import io.reactivex.netty.protocol.http.server.HttpServerRequest;

import java.util.HashMap;

public class Zuul {

    public static final FilterStateFactory<HashMap<String, Object>> DEFAULT_STATE_FACTORY = new FilterStateFactory() {
        @Override
        public Object create(HttpServerRequest httpServerRequest) {
            return new HashMap();
        }
    };

    public static HttpServer<ByteBuf, ByteBuf> newZuulServer(int port, FilterStore<HashMap<String, Object>> filterStore) {
        return newZuulServer(port, filterStore, DEFAULT_STATE_FACTORY);
    }

    public static <T> HttpServer<ByteBuf, ByteBuf> newZuulServer(int port, FilterStore<T> filterStore,
                                                                 FilterStateFactory<T> filterStateFactory) {
        return newZuulServerBuilder(port, filterStore, filterStateFactory).build();
    }

    public static HttpServerBuilder<ByteBuf, ByteBuf> newZuulServerBuilder(int port,
                                                                           FilterStore<HashMap<String, Object>> filterStore) {
        return newZuulServerBuilder(port, filterStore, DEFAULT_STATE_FACTORY);
    }

    public static <T> HttpServerBuilder<ByteBuf, ByteBuf> newZuulServerBuilder(int port, FilterStore<T> filterStore,
                                                                               FilterStateFactory<T> filterStateFactory) {
        FilterProcessor<T> filterProcessor = new FilterProcessor<>(filterStore, filterStateFactory);
        return RxNetty.newHttpServerBuilder(port, new ZuulRequestHandler<>(filterProcessor))
                      .pipelineConfigurator(new HttpServerPipelineConfigurator<>());
    }

    public static void main(String[] args) {
        ZuulArgumentParser argumentParser = new ZuulArgumentParser(args);
        newZuulServer(argumentParser.getPort(),
                      new ClassPathFilterStore<>(argumentParser.getPackagePrefix())).startAndWait();
    }
}
