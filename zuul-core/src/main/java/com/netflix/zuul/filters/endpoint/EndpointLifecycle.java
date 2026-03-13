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

package com.netflix.zuul.filters.endpoint;

/**
 * Lifecycle contract for endpoints that manage their own async response flow.
 *
 * <p>Endpoints that acquire origin connections, manage streams, or otherwise hold
 * resources should implement this to ensure proper cleanup when a request
 * completes or errors.
 *
 * <p>The {@link #finish(boolean)} method is called by
 * {@link com.netflix.zuul.netty.filter.ZuulFilterChainHandler#fireEndpointFinish}
 * when the request lifecycle ends.
 */
public interface EndpointLifecycle {

    /**
     * Called when the request completes or errors, allowing the endpoint to release
     * resources (connections, streams, etc.).
     *
     * @param error {@code true} if the request ended due to an error
     */
    void finish(boolean error);
}
