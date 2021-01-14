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

package com.netflix.zuul.origins;

import com.netflix.zuul.message.http.HttpRequestMessage;
import javax.annotation.Nullable;

/**
 * User: michaels@netflix.com
 * Date: 10/8/14
 * Time: 6:15 PM
 */
public interface InstrumentedOrigin extends Origin {

    double getErrorPercentage();

    double getErrorAllPercentage();

    void adjustRetryPolicyIfNeeded(HttpRequestMessage zuulRequest);

    void preRequestChecks(HttpRequestMessage zuulRequest);

    void recordSuccessResponse();

    void recordProxyRequestEnd();

    /**
     * Returns the mutable origin stats for this origin.  Unlike the other methods in this interface,
     * External callers are expected to update these numbers, rather than this object itself.
     * @return
     */
    default OriginStats stats() {
        throw new UnsupportedOperationException();
    }
}
