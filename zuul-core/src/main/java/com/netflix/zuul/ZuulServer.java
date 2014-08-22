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

import java.util.HashMap;

public class ZuulServer {

    public static void start(int port, FilterStore<HashMap<String, Object>, HashMap<String, Object>> filterStore) {
        start(port, filterStore, DEFAULT_STATE_FACTORY);
    }

    public static <T> void start(int port, FilterStore<T, T> filterStore, FilterStateFactory<T> typeFactory) {
        start(port, filterStore, typeFactory, typeFactory);
    }

    public static <Request, Response> void start(int port, FilterStore<Request, Response> filterStore, FilterStateFactory<Request> request, FilterStateFactory<Response> response) {
        FilterProcessor<Request, Response> filterProcessor = new FilterProcessor<>(filterStore, request, response);
        //        ZuulRxNettyServer<Request, Response> server = new ZuulRxNettyServer<>(port, filterProcessor);
        try {
            ZuulKaryonServer.createServer(port, filterProcessor).startAndAwait();
        } catch (Exception e) {
            throw new RuntimeException("all hell hath broken loose", e);
            // TODO why is there a checked exception here?!
        }

    }

    private static final DefaultStateFactory DEFAULT_STATE_FACTORY = new DefaultStateFactory();

    private static class DefaultStateFactory implements FilterStateFactory<HashMap<String, Object>> {

        @Override
        public HashMap<String, Object> create() {
            return new HashMap<String, Object>();
        }

    }
}
