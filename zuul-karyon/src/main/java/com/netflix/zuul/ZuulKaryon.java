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

import com.netflix.karyon.Karyon;
import com.netflix.karyon.KaryonBootstrapSuite;
import com.netflix.karyon.KaryonServer;
import com.netflix.karyon.archaius.ArchaiusSuite;
import com.netflix.karyon.eureka.KaryonEurekaModule;
import com.netflix.karyon.servo.KaryonServoModule;
import com.netflix.zuul.filterstore.ClassPathFilterStore;
import com.netflix.zuul.filterstore.FilterStore;
import com.netflix.zuul.lifecycle.FilterStateFactory;
import io.netty.buffer.ByteBuf;
import io.reactivex.netty.protocol.http.server.HttpServer;

import java.util.HashMap;

public class ZuulKaryon {

    private ZuulKaryon() {
    }

    public static KaryonServer newZuulServer(int port, FilterStore<HashMap<String, Object>> filterStore) {
        return fromZuulServer(Zuul.newZuulServer(port, filterStore));
    }

    public static <T> KaryonServer newZuulServer(int port, FilterStore<T> filterStore,
                                                 FilterStateFactory<T> filterStateFactory) {
        return fromZuulServer(Zuul.newZuulServer(port, filterStore, filterStateFactory));
    }

    public static KaryonServer fromZuulServer(HttpServer<ByteBuf, ByteBuf> zuulServer) {
        return Karyon.forHttpServer(zuulServer,
                                    new KaryonBootstrapSuite(),
                                    KaryonEurekaModule.asSuite(),
                                    new ArchaiusSuite("zuul"),
                                    KaryonServoModule.asSuite());
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

