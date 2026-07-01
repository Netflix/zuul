/*
 * Copyright 2026 Netflix, Inc.
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

package com.netflix.zuul.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.extension.ResponseTransformerV2;
import com.github.tomakehurst.wiremock.http.Response;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * WireMock response transformer for allowing deterministic interactions on requests/responses. {@link #init()} before
 * the request, {@link #awaitRequest()} to block until the origin has received the request, then {@link #sendResponse()}
 * to let the response through.
 */
class GatedResponse implements ResponseTransformerV2 {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private volatile CountDownLatch requestLatch;
    private volatile CountDownLatch responseLatch;

    void init() {
        requestLatch = new CountDownLatch(1);
        responseLatch = new CountDownLatch(1);
    }

    void awaitRequest() throws InterruptedException {
        assertThat(requestLatch.await(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS))
                .as("origin did not receive request in a reasonable amount of time")
                .isTrue();
    }

    void sendResponse() {
        if (responseLatch != null) {
            responseLatch.countDown();
        }
    }

    void reset() {
        sendResponse();
        requestLatch = null;
        responseLatch = null;
    }

    @Override
    public Response transform(Response response, ServeEvent serveEvent) {
        CountDownLatch received = requestLatch;
        CountDownLatch released = responseLatch;
        if (received != null) {
            received.countDown();
            try {
                released.await(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        return response;
    }

    @Override
    public String getName() {
        return "gated-response";
    }
}
