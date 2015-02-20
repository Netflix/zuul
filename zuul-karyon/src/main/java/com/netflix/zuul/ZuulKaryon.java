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

import netflix.karyon.Karyon;
import netflix.karyon.KaryonBootstrapModule;
import netflix.karyon.KaryonServer;
import netflix.karyon.archaius.ArchaiusBootstrapModule;
import netflix.karyon.eureka.KaryonEurekaModule;
import com.netflix.zuul.filterstore.ClassPathFilterStore;
import com.netflix.zuul.filterstore.FilterStore;
import com.netflix.zuul.lifecycle.FilterStateFactory;
import io.netty.buffer.ByteBuf;
import io.reactivex.netty.protocol.http.server.HttpServer;
import io.reactivex.netty.protocol.http.server.HttpServerBuilder;
import io.reactivex.netty.servo.ServoEventsListenerFactory;

import java.util.HashMap;

public class ZuulKaryon {

    private ZuulKaryon() {
    }

    public static KaryonServer newZuulServer(String appName, int port, FilterStore<HashMap<String, Object>> filterStore) {
        return newZuulServer(appName, port, filterStore, Zuul.DEFAULT_STATE_FACTORY);
    }

    public static <T> KaryonServer newZuulServer(String appName, int port, FilterStore<T> filterStore,
                                                 FilterStateFactory<T> filterStateFactory) {
        return fromZuulServer(newZuulServerBuilder(appName, port, filterStore, filterStateFactory).build());
    }

    public static KaryonServer newZuulServer(int port, FilterStore<HashMap<String, Object>> filterStore) {
        return newZuulServer("zuul", port, filterStore);
    }

    public static <T> KaryonServer newZuulServer(int port, FilterStore<T> filterStore,
                                                 FilterStateFactory<T> filterStateFactory) {
        return newZuulServer("zuul", port, filterStore, filterStateFactory);
    }

    public static KaryonServer fromZuulServer(HttpServer<ByteBuf, ByteBuf> zuulServer) {
        return fromZuulServer("zuul", zuulServer);
    }

    public static KaryonServer fromZuulServer(String appName, HttpServer<ByteBuf, ByteBuf> zuulServer) {
        return Karyon.forHttpServer(zuulServer,
                                    new KaryonBootstrapModule(),
                                    KaryonEurekaModule.asBootstrapModule(),
                                    new ArchaiusBootstrapModule(appName));
    }

    private static HttpServerBuilder<ByteBuf, ByteBuf> newZuulServerBuilder(String appName, int port,
                                                                            FilterStore<HashMap<String, Object>> filterStore) {
        return newZuulServerBuilder(appName, port, filterStore, Zuul.DEFAULT_STATE_FACTORY);
    }

    private static <T> HttpServerBuilder<ByteBuf, ByteBuf> newZuulServerBuilder(String appName, int port,
                                                                                FilterStore<T> filterStore,
                                                                                FilterStateFactory<T> filterStateFactory) {
        return Zuul.newZuulServerBuilder(port, filterStore, filterStateFactory)
                   .withMetricEventsListenerFactory(new ServoEventsListenerFactory(appName + "-rxnetty-client",
                                                                                   appName + "-rxnetty-server"));
    }

    public static void main(String[] args) {
        int portDefault = 8888;
        String[] packagePrefixDefault = new String[]{"com.netflix.zuul.filter.example.pre",
                                              "com.netflix.zuul.filter.example.post",
                                              "com.netflix.zuul.filter.example.error",
                                              "com.netflix.zuul.karyon.filter.example.route"};
        ZuulArgumentParser argumentParser = new ZuulArgumentParser(portDefault, packagePrefixDefault, args);
        newZuulServer(argumentParser.getPort(), new ClassPathFilterStore<>(argumentParser.getPackagePrefix()))
                .startAndWaitTillShutdown();
    }
}

