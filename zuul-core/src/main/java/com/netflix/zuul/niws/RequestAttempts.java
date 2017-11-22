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

package com.netflix.zuul.niws;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.netflix.zuul.context.CommonContextKeys;
import com.netflix.zuul.context.SessionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;

/**
 * User: michaels@netflix.com
 * Date: 6/25/15
 * Time: 1:03 PM
 */
public class RequestAttempts extends ArrayList<RequestAttempt>
{
    private static final Logger LOG = LoggerFactory.getLogger(RequestAttempts.class);
    private static ThreadLocal<RequestAttempts> threadLocal = new ThreadLocal<>().withInitial(() -> new RequestAttempts());
    private static final ObjectMapper JACKSON_MAPPER = new ObjectMapper();

    public RequestAttempts()
    {
        super();
    }

    public RequestAttempt getFinalAttempt()
    {
        if (size() > 0) {
            return get(size() - 1);
        }
        else {
            return null;
        }
    }

    public static RequestAttempts getFromSessionContext(SessionContext ctx)
    {
        return (RequestAttempts) ctx.get(CommonContextKeys.REQUEST_ATTEMPTS);
    }

    /**
     * This is only intended for use when running on a blocking server (ie. tomcat).
     * @return
     */
    public static RequestAttempts getThreadLocalInstance()
    {
        return threadLocal.get();
    }

    public static void setThreadLocalInstance(RequestAttempts instance)
    {
        threadLocal.set(instance);
    }

    public static void removeThreadLocalInstance()
    {
        threadLocal.remove();
    }

    public static RequestAttempts parse(String attemptsJson) throws IOException
    {
        return JACKSON_MAPPER.readValue(attemptsJson, RequestAttempts.class);
    }

    public String toJSON()
    {
        ArrayNode array = JACKSON_MAPPER.createArrayNode();
        for (RequestAttempt attempt : this) {
            array.add(attempt.toJsonNode());
        }

        try {
            return JACKSON_MAPPER.writeValueAsString(array);
        }
        catch (JsonProcessingException e) {
            throw new RuntimeException("Error serializing RequestAttempts!", e);
        }
    }

    @Override
    public String toString()
    {
        try {
            return toJSON();
        }
        catch (Throwable e) {
            LOG.error(e.getMessage(), e);
            return "";
        }
    }
}
