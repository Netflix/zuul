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
import com.netflix.zuul.filterstore.FilterStore;
import com.netflix.zuul.lifecycle.FilterProcessor;
import com.netflix.zuul.lifecycle.FilterStateFactory;

import java.util.HashMap;

public class ZuulServer {

    public static void start(int port, FilterStore<HashMap<String, Object>, HashMap<String, Object>> filterStore) {
        startWithAdditionalModules(port, filterStore);
    }

    public static void startWithAdditionalModules(int port, FilterStore<HashMap<String, Object>,
                                                  HashMap<String, Object>> filterStore,
                                                  Module... additionalModules) {
        startWithAdditionalModules(port, filterStore, DEFAULT_STATE_FACTORY, additionalModules);
    }

    public static <T> void start(int port, FilterStore<T, T> filterStore, FilterStateFactory<T> typeFactory) {
        startWithAdditionalModules(port, filterStore, typeFactory);
    }

    public static <T> void startWithAdditionalModules(int port, FilterStore<T, T> filterStore,
                                                      FilterStateFactory<T> typeFactory, Module... additionalModules) {
        startWithAdditionalModules(port, filterStore, typeFactory, typeFactory, additionalModules);
    }

    public static <Request, Response> void start(int port, FilterStore<Request, Response> filterStore,
                                                 FilterStateFactory<Request> request,
                                                 FilterStateFactory<Response> response) {
        startWithAdditionalModules(port, filterStore, request, response);
    }

    public static <Request, Response> void startWithAdditionalModules(int port, FilterStore<Request, Response> filterStore,
                                                                      FilterStateFactory<Request> request,
                                                                      FilterStateFactory<Response> response,
                                                                      Module... additionalModules) {
        FilterProcessor<Request, Response> filterProcessor = new FilterProcessor<>(filterStore, request, response);
        //ZuulRxNettyServer<Request, Response> server = new ZuulRxNettyServer<>(port, filterProcessor);
        ZuulKaryonServer.startZuulServerWithAdditionalModule(port, filterProcessor, additionalModules);
    }

    private static final DefaultStateFactory DEFAULT_STATE_FACTORY = new DefaultStateFactory();

    private static class DefaultStateFactory implements FilterStateFactory<HashMap<String, Object>> {

        @Override
        public HashMap<String, Object> create() {
            return new HashMap<>();
        }

    }
}
