/**
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.zuul.dependency.httpclient.hystrix;

import com.netflix.config.DynamicPropertyFactory;
import com.netflix.hystrix.HystrixCommand;
import com.netflix.hystrix.HystrixCommandGroupKey;
import com.netflix.hystrix.HystrixCommandProperties;
import com.netflix.zuul.constants.ZuulConstants;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;

import java.io.IOException;

/**
 * Hystrix wrapper around apache http client
 *
 * @author Mikey Cohen
 *         Date: 2/6/12
 *         Time: 4:30 PM
 */
public class HostCommand extends HystrixCommand<HttpResponse> {

    HttpClient httpclient;
    HttpHost httpHost;
    HttpRequest httpRequest;

    public HostCommand(HttpClient httpclient, HttpHost httpHost, HttpRequest httpRequest) {
        this("default", httpclient, httpHost, httpRequest);
    }

    public HostCommand(String commandKey, HttpClient httpclient, HttpHost httpHost, HttpRequest httpRequest) {

        super(Setter.withGroupKey(HystrixCommandGroupKey.Factory.asKey(commandKey)).andCommandPropertiesDefaults(
                // we want to default to semaphore-isolation since this wraps
                // 2 others commands that are already thread isolated
                HystrixCommandProperties.Setter()
                        .withExecutionIsolationStrategy(HystrixCommandProperties.ExecutionIsolationStrategy.SEMAPHORE)
                        .withExecutionIsolationSemaphoreMaxConcurrentRequests(DynamicPropertyFactory.getInstance().
                                getIntProperty(ZuulConstants.ZUUL_HTTPCLIENT + commandKey + ".semaphore.maxSemaphores", 100).get())));

        this.httpclient = httpclient;
        this.httpHost = httpHost;
        this.httpRequest = httpRequest;
    }

    @Override
    protected HttpResponse run() throws Exception {
        try {
            return forward();
        } catch (IOException e) {
            throw e;
        }
    }

    HttpResponse forward() throws IOException {
        return httpclient.execute(httpHost, httpRequest);
    }


}
