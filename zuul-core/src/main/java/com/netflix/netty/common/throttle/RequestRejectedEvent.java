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

public final class RequestRejectedEvent {
    private final HttpRequest request;
    private final StatusCategory nfStatus;
    private final HttpResponseStatus httpStatus;
    private final String reason;

    public RequestRejectedEvent(
            HttpRequest request, StatusCategory nfStatus, HttpResponseStatus httpStatus, String reason) {
        this.request = request;
        this.nfStatus = nfStatus;
        this.httpStatus = httpStatus;
        this.reason = reason;
    }

    public HttpRequest request() {
        return request;
    }

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
