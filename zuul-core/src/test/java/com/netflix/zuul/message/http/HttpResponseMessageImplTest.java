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

import static org.assertj.core.api.Assertions.assertThat;

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

        assertThat(response.hasSetCookieWithName("c1")).isTrue();
        assertThat(response.hasSetCookieWithName("c2")).isTrue();
        assertThat(response.hasSetCookieWithName("XX")).isFalse();
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

        assertThat(response.getHeaders().size()).isEqualTo(1);
        assertThat(response.hasSetCookieWithName("c1")).isFalse();
        assertThat(response.hasSetCookieWithName("c2")).isTrue();
    }

    @Test
    void testContentLengthHeaderHasCorrectValue() {
        assertThat(response.getHeaders().getAll("Content-Length").size()).isEqualTo(0);

        response.setBodyAsText(TEXT1);
        assertThat(response.getHeaders().getFirst("Content-Length")).isEqualTo(String.valueOf(TEXT1.length()));

        response.setBody(TEXT2.getBytes(StandardCharsets.UTF_8));
        assertThat(response.getHeaders().getFirst("Content-Length")).isEqualTo(String.valueOf(TEXT2.length()));
    }
}
