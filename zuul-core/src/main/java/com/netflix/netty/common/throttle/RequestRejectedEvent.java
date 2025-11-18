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

import com.netflix.zuul.stats.status.StatusCategory;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import jakarta.annotation.Nullable;

public record RequestRejectedEvent(
        HttpRequest request,
        StatusCategory nfStatus,
        HttpResponseStatus httpStatus,
        String reason,
        @Nullable String reasonMessage) {

    public RequestRejectedEvent(
            HttpRequest request, StatusCategory nfStatus, HttpResponseStatus httpStatus, String reason) {
        this(request, nfStatus, httpStatus, reason, null);
    }

    // leaving behind old getters for backwards compatibility

    public StatusCategory getNfStatus() {
        return nfStatus;
    }

    public HttpResponseStatus getHttpStatus() {
        return httpStatus;
    }

    public String getReason() {
        return reason;
    }
}
