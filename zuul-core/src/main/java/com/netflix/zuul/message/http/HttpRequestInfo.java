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

package com.netflix.zuul.message.http;

import com.netflix.zuul.message.Headers;
import com.netflix.zuul.message.ZuulMessage;
import java.util.Optional;

/**
 * User: Mike Smith
 * Date: 7/15/15
 * Time: 1:18 PM
 */
public interface HttpRequestInfo extends ZuulMessage
{
    String getProtocol();

    String getMethod();

    String getPath();

    HttpQueryParams getQueryParams();

    String getPathAndQuery();

    Headers getHeaders();

    String getClientIp();

    String getScheme();

    int getPort();

    String getServerName();

    int getMaxBodySize();

    String getInfoForLogging();

    String getOriginalHost();

    String getOriginalScheme();

    String getOriginalProtocol();

    int getOriginalPort();

    /**
     * Reflects the actual destination port that the client intended to communicate with,
     * in preference to the port Zuul was listening on. In the case where proxy protocol is
     * enabled, this should reflect the destination IP encoded in the TCP payload by the load balancer.
     */
    default Optional<Integer> getClientDestinationPort() {
        throw new UnsupportedOperationException();
    }

    String reconstructURI();

    /** Parse and lazily cache the request cookies. */
    Cookies parseCookies();

    /**
     * Force parsing/re-parsing of the cookies. May want to do this if headers
     * have been mutated since cookies were first parsed.
     */
    Cookies reParseCookies();
}
