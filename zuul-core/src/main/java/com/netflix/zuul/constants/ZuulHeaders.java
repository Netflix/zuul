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
package com.netflix.zuul.constants;

/**
 * HTTP Headers that are accessed or added by Zuul
 * User: mcohen
 * Date: 5/15/13
 * Time: 4:38 PM
 */
public class ZuulHeaders {

    /* Standard headers */

    public static final String TRANSFER_ENCODING = "transfer-encoding";
    public static final String CHUNKED = "chunked";
    public static final String ORIGIN = "Origin";
    public static final String CONTENT_ENCODING = "Content-Encoding";
    public static final String ACCEPT_ENCODING = "accept-encoding";
    public static final String CONNECTION = "Connection";
    public static final String KEEP_ALIVE = "keep-alive";
    public static final String X_FORWARDED_PROTO = "X-Forwarded-Proto";
    public static final String X_FORWARDED_FOR = "X-Forwarded-For";
    public static final String HOST = "Host";
    public static final String X_ORIGINATING_URL = "X-Originating-URL";

    /* X-Zuul headers */

    public static final String X_ZUUL = "X-Zuul";
    public static final String X_ZUUL_STATUS = X_ZUUL + "-Status";
    public static final String X_ZUUL_PROXY_ATTEMPTS = X_ZUUL + "-Proxy-Attempts";
    public static final String X_ZUUL_INSTANCE = X_ZUUL + "-Instance";
    public static final String X_ZUUL_ERROR_CAUSE = X_ZUUL + "-Error-Cause";
    public static final String X_ZUUL_SURGICAL_FILTER = X_ZUUL + "-Surgical-Filter";
    public static final String X_ZUUL_FILTER_EXECUTION_STATUS = X_ZUUL + "-Filter-Executions";

    // Prevent instantiation
    private ZuulHeaders() {
        throw new AssertionError("Must not instantiate constant utility class");
    }

}
