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

package com.netflix.netty.common;

import io.netty.handler.codec.http.HttpResponse;

/**
 * User: michaels@netflix.com
 * Date: 2/8/17
 * Time: 9:58 AM
 */
public class Http1ConnectionExpiryHandler extends AbstrHttpConnectionExpiryHandler
{
    public Http1ConnectionExpiryHandler(int maxRequests, int maxRequestsUnderBrownout, int maxExpiry)
    {
        super(ConnectionCloseType.GRACEFUL, maxRequestsUnderBrownout, maxRequests, maxExpiry);
    }

    @Override
    protected boolean isResponseHeaders(Object msg)
    {
        return msg instanceof HttpResponse;
    }
}
