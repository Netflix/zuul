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

import com.netflix.zuul.exception.ZuulException;
import com.netflix.zuul.stats.status.StatusCategory;

public abstract class OriginThrottledException extends ZuulException
{
    private final String originName;
    private final StatusCategory statusCategory;

    public OriginThrottledException(String originName, String msg, StatusCategory statusCategory)
    {
        // Ensure this exception does not fill its stacktrace as causes too much load.
        super(msg + ", origin=" + originName, true);
        this.originName = originName;
        this.statusCategory = statusCategory;
        this.setStatusCode(503);
    }

    public String getOriginName()
    {
        return originName;
    }

    public StatusCategory getStatusCategory()
    {
        return statusCategory;
    }
}
