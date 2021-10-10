/*
 * Copyright 2021 Netflix, Inc.
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

package com.netflix.zuul;

import com.netflix.zuul.message.ZuulMessage;
import com.netflix.zuul.message.http.HttpResponseMessage;

import static org.junit.Assert.assertEquals;

public class TestUtils {
    public static void assertContentLength(final ZuulMessage msg, final int expectedValue) {
        assertEquals(1, msg.getHeaders().getAll("Content-Length").size());
        assertEquals(String.valueOf(expectedValue), msg.getHeaders().getFirst("Content-Length"));
    }

    public static void assertContentLengthIsAbsent(final ZuulMessage msg) {
        assertEquals(0, msg.getHeaders().getAll("Content-Length").size());
    }

    public static void assertContentLength(final HttpResponseMessage msg, final int expectedValue) {
        assertEquals(1, msg.getHeaders().getAll("Content-Length").size());
        assertEquals(String.valueOf(expectedValue), msg.getHeaders().getFirst("Content-Length"));
    }

    public static void assertContentLengthIsAbsent(final HttpResponseMessage msg) {
        assertEquals(0, msg.getHeaders().getAll("Content-Length").size());
    }
}
