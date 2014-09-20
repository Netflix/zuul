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
package com.netflix.zuul.groovy;

import com.netflix.zuul.Zuul;
import com.netflix.zuul.ZuulArgumentParser;
import com.netflix.zuul.filterstore.FilterStore;
import com.netflix.zuul.lifecycle.FilterStateFactory;
import io.netty.buffer.ByteBuf;
import io.reactivex.netty.protocol.http.server.HttpServer;

import java.io.File;

public class ZuulGroovy {

    public static HttpServer<ByteBuf, ByteBuf> newZuulServer(int port) {
        return newZuulServer(port, Zuul.DEFAULT_STATE_FACTORY);
    }

    public static <T> HttpServer<ByteBuf, ByteBuf> newZuulServer(int port, FilterStateFactory<T> filterStateFactory) {
        return newZuulServer(port, "zuul-groovy/src/main/groovy/com/netflix/zuul/groovy/filter", 15l, filterStateFactory);
    }

    public static <T> HttpServer<ByteBuf, ByteBuf> newZuulServer(int port, String filterDir, long fsPollingInterval,
                                                                 FilterStateFactory<T> filterStateFactory) {
        FilterStore<T> filterStore = new GroovyFileSystemFilterStore<>(new File(filterDir), fsPollingInterval);
        return Zuul.newZuulServer(port, filterStore, filterStateFactory);
    }

    public static void main(final String[] args) {
        ZuulArgumentParser argumentParser = new ZuulArgumentParser(args);
        newZuulServer(argumentParser.getPort()).startAndWait();
    }
}