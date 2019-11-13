/*
 * Copyright 2018 Netflix, Inc.
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

package com.netflix.zuul.sample.filters.inbound

import com.netflix.zuul.filters.http.HttpInboundFilter
import com.netflix.zuul.message.http.HttpRequestMessage
import com.netflix.zuul.sample.SampleService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import rx.Observable

import javax.inject.Inject

/**
 * Sample Service Filter to demonstrate Guice injection of services and
 * making external requests to slow endpoints.
 *
 * Author: Arthur Gonigberg
 * Date: January 04, 2018
 */
class SampleServiceFilter extends HttpInboundFilter {
    private static final Logger log = LoggerFactory.getLogger(SampleServiceFilter.class)

    private final SampleService sampleService

    @Inject
    SampleServiceFilter(SampleService sampleService) {
        this.sampleService = sampleService
    }

    @Override
    int filterOrder() {
        return 500
    }


    @Override
    boolean shouldFilter(HttpRequestMessage msg) {
        return sampleService.isHealthy()
    }

    @Override
    Observable<HttpRequestMessage> applyAsync(HttpRequestMessage request) {
        return sampleService.makeSlowRequest().map({ response ->
            log.info("Fetched sample service result: {}", response)

            return request
        })
    }
}
