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

package com.netflix.zuul.exception;

import com.netflix.client.ClientException;
import com.netflix.zuul.stats.status.StatusCategory;
import com.netflix.zuul.stats.status.ZuulStatusCategory;

/**
 * Outbound Error Type
 *
 * Author: Arthur Gonigberg
 * Date: November 28, 2017
 */
public enum OutboundErrorType implements ErrorType {
    READ_TIMEOUT(
            ERROR_TYPE_READ_TIMEOUT_STATUS.get(),
            ZuulStatusCategory.FAILURE_ORIGIN_READ_TIMEOUT,
            ClientException.ErrorType.READ_TIMEOUT_EXCEPTION),
    CONNECT_ERROR(
            ERROR_TYPE_CONNECT_ERROR_STATUS.get(),
            ZuulStatusCategory.FAILURE_ORIGIN_CONNECTIVITY,
            ClientException.ErrorType.CONNECT_EXCEPTION),
    SERVICE_UNAVAILABLE(
            ERROR_TYPE_SERVICE_UNAVAILABLE_STATUS.get(),
            ZuulStatusCategory.FAILURE_ORIGIN_THROTTLED,
            ClientException.ErrorType.SERVER_THROTTLED),
    ERROR_STATUS_RESPONSE(
            ERROR_TYPE_ERROR_STATUS_RESPONSE_STATUS.get(),
            ZuulStatusCategory.FAILURE_ORIGIN,
            ClientException.ErrorType.GENERAL),
    NO_AVAILABLE_SERVERS(
            ERROR_TYPE_NOSERVERS_STATUS.get(),
            ZuulStatusCategory.FAILURE_ORIGIN_NO_SERVERS,
            ClientException.ErrorType.CONNECT_EXCEPTION),
    ORIGIN_SERVER_MAX_CONNS(
            ERROR_TYPE_ORIGIN_SERVER_MAX_CONNS_STATUS.get(),
            ZuulStatusCategory.FAILURE_LOCAL_THROTTLED_ORIGIN_SERVER_MAXCONN,
            ClientException.ErrorType.CLIENT_THROTTLED),
    RESET_CONNECTION(
            ERROR_TYPE_ORIGIN_RESET_CONN_STATUS.get(),
            ZuulStatusCategory.FAILURE_ORIGIN_RESET_CONNECTION,
            ClientException.ErrorType.CONNECT_EXCEPTION),
    CLOSE_NOTIFY_CONNECTION(
            502,
            ZuulStatusCategory.FAILURE_ORIGIN_CLOSE_NOTIFY_CONNECTION,
            ClientException.ErrorType.CONNECT_EXCEPTION),
    CANCELLED(400, ZuulStatusCategory.FAILURE_CLIENT_CANCELLED, ClientException.ErrorType.SOCKET_TIMEOUT_EXCEPTION),
    ORIGIN_CONCURRENCY_EXCEEDED(
            ERROR_TYPE_ORIGIN_CONCURRENCY_EXCEEDED_STATUS.get(),
            ZuulStatusCategory.FAILURE_LOCAL_THROTTLED_ORIGIN_CONCURRENCY,
            ClientException.ErrorType.SERVER_THROTTLED),
    HEADER_FIELDS_TOO_LARGE(431, ZuulStatusCategory.FAILURE_LOCAL_HEADER_FIELDS_TOO_LARGE, ClientException.ErrorType.GENERAL),
    OTHER(ERROR_TYPE_OTHER_STATUS.get(), ZuulStatusCategory.FAILURE_LOCAL, ClientException.ErrorType.GENERAL);

    private static final String NAME_PREFIX = "ORIGIN_";

    private final int statusCodeToReturn;
    private final StatusCategory statusCategory;
    private final ClientException.ErrorType clientErrorType;

    OutboundErrorType(
            int statusCodeToReturn, StatusCategory statusCategory, ClientException.ErrorType clientErrorType) {
        this.statusCodeToReturn = statusCodeToReturn;
        this.statusCategory = statusCategory;
        this.clientErrorType = clientErrorType;
    }

    @Override
    public int getStatusCodeToReturn() {
        return statusCodeToReturn;
    }

    @Override
    public StatusCategory getStatusCategory() {
        return statusCategory;
    }

    @Override
    public ClientException.ErrorType getClientErrorType() {
        return clientErrorType;
    }

    @Override
    public String toString() {
        return NAME_PREFIX + name();
    }
}
