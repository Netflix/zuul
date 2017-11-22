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

import com.netflix.zuul.message.ZuulMessage;

/**
 * User: Mike Smith
 * Date: 7/15/15
 * Time: 5:36 PM
 */
public interface HttpRequestMessage extends HttpRequestInfo
{
    void setProtocol(String protocol);

    void setMethod(String method);

    void setPath(String path);

    void setClientIp(String clientIp);

    void setScheme(String scheme);

    void setPort(int port);

    void setServerName(String serverName);

    ZuulMessage clone();

    void storeInboundRequest();

    HttpRequestInfo getInboundRequest();

    void setQueryParams(HttpQueryParams queryParams);
}
