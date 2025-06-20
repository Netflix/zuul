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

package com.netflix.zuul.message.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.netflix.zuul.context.SessionContext;
import com.netflix.zuul.message.Headers;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link HttpResponseMessageImpl}.
 */
@ExtendWith(MockitoExtension.class)
class HttpResponseMessageImplTest {
    private static final String TEXT1 = "Hello World!";
    private static final String TEXT2 = "Goodbye World!";

    @Mock
    private HttpRequestMessage request;

    private HttpResponseMessageImpl response;

    @BeforeEach
    void setup() {
        response = new HttpResponseMessageImpl(new SessionContext(), new Headers(), request, 200);
    }

    @Test
    void testHasSetCookieWithName() {
        response.getHeaders()
                .add(
                        "Set-Cookie",
                        "c1=1234; Max-Age=-1; Expires=Tue, 01 Sep 2015 22:49:57 GMT; Path=/; Domain=.netflix.com");
        response.getHeaders()
                .add(
                        "Set-Cookie",
                        "c2=4567; Max-Age=-1; Expires=Tue, 01 Sep 2015 22:49:57 GMT; Path=/; Domain=.netflix.com");

        assertTrue(response.hasSetCookieWithName("c1"));
        assertTrue(response.hasSetCookieWithName("c2"));
        assertFalse(response.hasSetCookieWithName("XX"));
    }

    @Test
    void testRemoveExistingSetCookie() {
        response.getHeaders()
                .add(
                        "Set-Cookie",
                        "c1=1234; Max-Age=-1; Expires=Tue, 01 Sep 2015 22:49:57 GMT; Path=/; Domain=.netflix.com");
        response.getHeaders()
                .add(
                        "Set-Cookie",
                        "c2=4567; Max-Age=-1; Expires=Tue, 01 Sep 2015 22:49:57 GMT; Path=/; Domain=.netflix.com");

        response.removeExistingSetCookie("c1");

        assertEquals(1, response.getHeaders().size());
        assertFalse(response.hasSetCookieWithName("c1"));
        assertTrue(response.hasSetCookieWithName("c2"));
    }

    @Test
    void testContentLengthHeaderHasCorrectValue() {
        assertEquals(0, response.getHeaders().getAll("Content-Length").size());

        response.setBodyAsText(TEXT1);
        assertEquals(String.valueOf(TEXT1.length()), response.getHeaders().getFirst("Content-Length"));

        response.setBody(TEXT2.getBytes(StandardCharsets.UTF_8));
        assertEquals(String.valueOf(TEXT2.length()), response.getHeaders().getFirst("Content-Length"));
    }
}
