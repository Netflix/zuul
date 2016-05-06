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

package com.netflix.zuul.groovy

import com.netflix.zuul.context.RequestContext
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.runners.MockitoJUnitRunner

import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

import static org.junit.Assert.assertEquals
import static org.junit.Assert.assertNotNull

/**
 * Unit test class to verify groovy compatibility with RequestContext
 * Created by IntelliJ IDEA.
 * User: mcohen
 * Date: 1/3/12
 * Time: 4:55 PM
 */
@RunWith(MockitoJUnitRunner.class)
class GroovyCompatabilityTest {
    @Mock
    HttpServletResponse response
    @Mock
    HttpServletRequest request

    @Test
    public void testRequestContext() {
        RequestContext.getCurrentContext().setRequest(request)
        RequestContext.getCurrentContext().setResponse(response)
        assertNotNull(RequestContext.getCurrentContext().getRequest())
        assertNotNull(RequestContext.getCurrentContext().getResponse())
        assertEquals(RequestContext.getCurrentContext().request, request)
        RequestContext.getCurrentContext().test = "moo"
        assertNotNull(RequestContext.getCurrentContext().test)
        assertEquals(RequestContext.getCurrentContext().test, "moo")
        assertNotNull(RequestContext.getCurrentContext().get("test"))
        assertEquals(RequestContext.getCurrentContext().get("test"), "moo")
        RequestContext.getCurrentContext().set("test", "ik")
        assertEquals(RequestContext.getCurrentContext().get("test"), "ik")
        assertEquals(RequestContext.getCurrentContext().test, "ik")
        assertNotNull(RequestContext.currentContext)
        assertEquals(RequestContext.currentContext.test, "ik")

    }
}
