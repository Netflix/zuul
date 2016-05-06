/*
 * Copyright 2013 Netflix, Inc.
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
package com.netflix.zuul.context;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

import static com.netflix.zuul.context.Debug.addRequestDebug;
import static com.netflix.zuul.context.Debug.addRoutingDebug;
import static com.netflix.zuul.context.Debug.debugRequest;
import static com.netflix.zuul.context.Debug.debugRouting;
import static com.netflix.zuul.context.Debug.getRequestDebug;
import static com.netflix.zuul.context.Debug.getRoutingDebug;
import static com.netflix.zuul.context.Debug.setDebugRequest;
import static com.netflix.zuul.context.Debug.setDebugRouting;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(MockitoJUnitRunner.class)
public class DebugTest {

    @Test
    public void testRequestDebug() {
        assertFalse(debugRouting());
        assertFalse(debugRequest());
        setDebugRouting(true);
        setDebugRequest(true);
        assertTrue(debugRouting());
        assertTrue(debugRequest());

        addRoutingDebug("test1");
        assertTrue(getRoutingDebug().contains("test1"));

        addRequestDebug("test2");
        assertTrue(getRequestDebug().contains("test2"));


    }
}
