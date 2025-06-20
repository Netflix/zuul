/*
 * Copyright 2025 Netflix, Inc.
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

import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * Runs integration tests using a single event loop thread for deterministic results around connection pooling
 *
 * @author Justin Guerra
 * @since 6/9/25
 */
public class SingleEventLoopIntegrationTest extends BaseIntegrationTest {

    @RegisterExtension
    static ZuulServerExtension ZUUL_EXTENSION = ZuulServerExtension.newBuilder()
            .withEventLoopThreads(1)
            .withOriginReadTimeout(ORIGIN_READ_TIMEOUT)
            .build();

    public SingleEventLoopIntegrationTest() {
        super(ZUUL_EXTENSION.getServerPort());
    }
}
