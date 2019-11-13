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
import com.netflix.config.DynamicIntProperty;
import com.netflix.zuul.stats.status.StatusCategory;

/**
 * Error Type
 *
 * Author: Arthur Gonigberg
 * Date: November 28, 2017
 */
public interface ErrorType {

    String PROP_PREFIX = "zuul.error.outbound";
    DynamicIntProperty ERROR_TYPE_READ_TIMEOUT_STATUS = new DynamicIntProperty(PROP_PREFIX + ".readtimeout.status", 504);
    DynamicIntProperty ERROR_TYPE_CONNECT_ERROR_STATUS = new DynamicIntProperty(PROP_PREFIX + ".connecterror.status", 502);
    DynamicIntProperty ERROR_TYPE_SERVICE_UNAVAILABLE_STATUS = new DynamicIntProperty(PROP_PREFIX + ".serviceunavailable.status", 503);
    DynamicIntProperty ERROR_TYPE_ORIGIN_CONCURRENCY_EXCEEDED_STATUS = new DynamicIntProperty(PROP_PREFIX + ".originconcurrencyexceeded.status", 503);
    DynamicIntProperty ERROR_TYPE_ERROR_STATUS_RESPONSE_STATUS = new DynamicIntProperty(PROP_PREFIX + ".errorstatusresponse.status", 500);
    DynamicIntProperty ERROR_TYPE_NOSERVERS_STATUS = new DynamicIntProperty(PROP_PREFIX + ".noservers.status", 502);
    DynamicIntProperty ERROR_TYPE_ORIGIN_SERVER_MAX_CONNS_STATUS = new DynamicIntProperty(PROP_PREFIX + ".servermaxconns.status", 503);
    DynamicIntProperty ERROR_TYPE_ORIGIN_RESET_CONN_STATUS = new DynamicIntProperty(PROP_PREFIX + ".originresetconnection.status", 504);
    DynamicIntProperty ERROR_TYPE_OTHER_STATUS = new DynamicIntProperty(PROP_PREFIX + ".other.status", 500);


    int getStatusCodeToReturn();

    StatusCategory getStatusCategory();

    ClientException.ErrorType getClientErrorType();
}

