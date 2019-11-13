/*
 * Copyright 2019 Netflix, Inc.
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

package com.netflix.zuul.netty.server;

public class ServerTimeout
{
    private final int connectionIdleTimeout;

    public ServerTimeout(int connectionIdleTimeout)
    {
        this.connectionIdleTimeout = connectionIdleTimeout;
    }

    public int connectionIdleTimeout()
    {
        return connectionIdleTimeout;
    }

    public int defaultRequestExpiryTimeout()
    {
        // Note this is the timeout for the inbound request to zuul, not for each outbound attempt.
        // It needs to align with the inbound connection idle timeout and/or the ELB idle timeout. So we
        // set it here to 1 sec less than that.
        return connectionIdleTimeout > 1000 ? connectionIdleTimeout - 1000 : 1000;
    }
}
