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

import com.netflix.zuul.context.Attributes
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.runners.MockitoJUnitRunner

import static org.junit.Assert.assertEquals
import static org.junit.Assert.assertNotNull

/**
 * Unit test class to verify groovy compatibility with RequestContext
 * Created by IntelliJ IDEA.
 * User: mcohen
 * Date: 1/3/12
 * Time: 4:55 PM
 */
class GroovyCompatability {


    @RunWith(MockitoJUnitRunner.class)
    public static class TestUnit {

        @Test
        public void testRequestContext() {
            Attributes attrs = new Attributes()

            attrs.test = "moo"
            assertNotNull(attrs.test)
            assertEquals(attrs.test, "moo")
            assertNotNull(attrs.get("test"))
            assertEquals(attrs.get("test"), "moo")

            attrs.set("test", "ik")
            assertEquals(attrs.get("test"), "ik")
            assertEquals(attrs.test, "ik")
            assertNotNull(attrs)
            assertEquals(attrs.test, "ik")

        }

    }

}
