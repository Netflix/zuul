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
package com.netflix.zuul.contrib.servopublisher;

import com.netflix.zuul.Zuul;
import com.netflix.zuul.ZuulArgumentParser;
import com.netflix.zuul.filterstore.ClassPathFilterStore;
import com.netflix.zuul.filterstore.FilterStore;
import com.netflix.zuul.lifecycle.FilterStateFactory;
import com.netflix.zuul.metrics.ZuulGlobalMetricsPublisher;
import com.netflix.zuul.metrics.ZuulMetricsPublisherFactory;
import com.netflix.zuul.metrics.ZuulPlugins;
import io.netty.buffer.ByteBuf;
import io.reactivex.netty.protocol.http.server.HttpServer;

import java.util.HashMap;

public class ZuulServo {

    public static HttpServer<ByteBuf, ByteBuf> newZuulServer(int port, FilterStore<HashMap<String, Object>> filterStore) {
        return forZuulServer(Zuul.newZuulServer(port, filterStore));
    }

    public static <T> HttpServer<ByteBuf, ByteBuf> newZuulServer(int port, FilterStore<T> filterStore,
                                                                 FilterStateFactory<T> filterStateFactory) {
        return forZuulServer(Zuul.newZuulServer(port, filterStore, filterStateFactory));
    }

    public static HttpServer<ByteBuf, ByteBuf> forZuulServer(HttpServer<ByteBuf, ByteBuf> zuulServer) {
        ZuulPlugins.getInstance().registerMetricsPublisher(ZuulServoMetricsPublisher.getInstance());
        ZuulGlobalMetricsPublisher globalMetricsPublisher = ZuulMetricsPublisherFactory.createOrRetrieveGlobalPublisher();
        return zuulServer;
    }

    public static void main(final String[] args) {
        ZuulArgumentParser argumentParser = new ZuulArgumentParser(args);
        newZuulServer(argumentParser.getPort(),
                      new ClassPathFilterStore<>(argumentParser.getPackagePrefix())).startAndWait();
    }
}
