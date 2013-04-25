/*
 * Copyright 2013 Netflix, Inc.
 *
 *      Licensed under the Apache License, Version 2.0 (the "License");
 *      you may not use this file except in compliance with the License.
 *      You may obtain a copy of the License at
 *
 *          http://www.apache.org/licenses/LICENSE-2.0
 *
 *      Unless required by applicable law or agreed to in writing, software
 *      distributed under the License is distributed on an "AS IS" BASIS,
 *      WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *      See the License for the specific language governing permissions and
 *      limitations under the License.
 */
package com.netflix.zuul.dependency;


import com.netflix.config.DynamicIntProperty;
import com.netflix.config.DynamicPropertyFactory;
import com.netflix.hystrix.HystrixCommand;
import com.netflix.hystrix.HystrixCommandGroupKey;
import com.netflix.hystrix.HystrixCommandProperties;
import com.netflix.niws.client.http.HttpClientRequest;
import com.netflix.niws.client.http.HttpClientResponse;
import com.netflix.zuul.context.NFRequestContext;
import com.netflix.niws.client.http.RestClient;

import javax.ws.rs.core.MultivaluedMap;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;

/**
 * @author Mikey Cohen
 * Date: 2/6/12
 * Time: 2:54 PM
 */
public class NIWSCommand extends HystrixCommand<HttpClientResponse> {

    RestClient restClient;
    HttpClientRequest.Verb verb;
    URI uri;
    MultivaluedMap<String, String> headers;
    MultivaluedMap<String, String> params;
    InputStream requestEntity;



    public NIWSCommand(RestClient restClient,
                        HttpClientRequest.Verb verb,
                        String uri,
                        MultivaluedMap<String, String> headers,
                        MultivaluedMap<String, String> params,
                        InputStream requestEntity) throws URISyntaxException {
        this("default", restClient, verb, uri, headers, params, requestEntity);
    }


        public NIWSCommand( String commandKey,
            RestClient restClient,
                       HttpClientRequest.Verb verb,
                       String uri,
                       MultivaluedMap<String, String> headers,
                       MultivaluedMap<String, String> params,
                       InputStream requestEntity) throws URISyntaxException {

        super(Setter
                .withGroupKey(HystrixCommandGroupKey.Factory.asKey(commandKey))
                .andCommandPropertiesDefaults(
                        // we want to default to semaphore-isolation since this wraps
                        // 2 others commands that are already thread isolated
                        HystrixCommandProperties.Setter()
                                .withExecutionIsolationStrategy(HystrixCommandProperties.ExecutionIsolationStrategy.SEMAPHORE)
                                .withExecutionIsolationSemaphoreMaxConcurrentRequests(DynamicPropertyFactory.getInstance().
                                        getIntProperty("zuul.eureka."+commandKey +".semaphore.maxSemaphores", 100).get())));

        this.restClient = restClient;
        this.verb = verb;
        this.uri = new URI(uri);
        this.headers = headers;
        this.params = params;
        this.requestEntity = requestEntity;
    }

    @Override
    protected HttpClientResponse run() throws Exception {
        try {
            return proxy();
        } catch (Exception e) {
            throw e;
        }
    }

    HttpClientResponse proxy() throws Exception {

        NFRequestContext context = NFRequestContext.getCurrentContext();
        HttpClientRequest httpClientRequest = HttpClientRequest.newBuilder().
                setVerb(verb).
                setUri(uri).
                setHeaders(headers).
                setEntity(requestEntity).
                setQueryParams(params).build();

        HttpClientResponse response = restClient.executeWithLoadBalancer(httpClientRequest);
        context.setProxyResponse(response);
        return response;
    }


}
