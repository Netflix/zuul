/**
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.zuul.context;

import com.netflix.zuul.origins.OriginManager;
import com.netflix.zuul.routing.RoutingResult;

import javax.inject.Inject;
import java.util.UUID;

/**
 * User: michaels@netflix.com
 * Date: 5/11/15
 * Time: 5:17 PM
 */
public class SampleSessionContextDecorator implements SessionContextDecorator
{
    private final OriginManager originManager;

    @Inject
    public SampleSessionContextDecorator(OriginManager originManager) {
        this.originManager = originManager;
    }

    @Override
    public SessionContext decorate(SessionContext ctx)
    {
        // Add the configured OriginManager to context for use in route filter.
        ctx.put("origin_manager", originManager);

        // Generate a UUID for this session.
        ctx.setUUID(UUID.randomUUID().toString());
        ctx.setRoutingResult(new RoutingResult());
        return ctx;
    }
}
