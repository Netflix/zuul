/*
 * Copyright 2020 Netflix, Inc.
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

package com.netflix.netty.common.throttle;

/**
 * Indicates a rejection type for DoS protection.  While similar, rejection is distinct from throttling in that
 * throttling is intended for non-malicious traffic.
 */
public enum RejectionType {
    // "It's not you, it's me."

    /**
     * Indicates that the request should not be allowed.  An HTTP response will be generated as a result.
     */
    REJECT,

    /**
     * Indicates that the connection should be closed, not allowing the request to proceed.  No HTTP response will be
     * returned.
     */
    CLOSE,

    /**
     * Allows the request to proceed, followed by closing the connection.  This is typically used in conjunction with
     * throttling handling, where the response may need to be handled by the filter chain.  It is not expected that the
     * request will be proxied.
     */
    ALLOW_THEN_CLOSE;
}
