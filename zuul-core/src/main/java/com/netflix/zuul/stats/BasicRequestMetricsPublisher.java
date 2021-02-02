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

package com.netflix.zuul.stats;

import com.netflix.zuul.context.SessionContext;

/**
 * User: michaels@netflix.com
 * Date: 6/4/15
 * Time: 4:22 PM
 */
public class BasicRequestMetricsPublisher implements RequestMetricsPublisher
{
    @Override
    public void collectAndPublish(SessionContext context) {
        // Record metrics here.

    }
}
