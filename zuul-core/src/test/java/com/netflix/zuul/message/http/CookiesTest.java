/*
 * Copyright 2020 Netflix, Inc.
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

import com.netflix.zuul.message.Headers;
import io.netty.handler.codec.http.Cookie;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.List;

import static com.netflix.zuul.message.http.Cookies.cleanCookieHeader;
import static org.junit.Assert.*;

@RunWith(MockitoJUnitRunner.class)
public class CookiesTest {

    @Test
    public void testFromHeaders() {

        Headers headers = new Headers();
        headers.add(HttpHeaderNames.COOKIE, "a=1; b=2");

        Cookies cookies = Cookies.fromHeaders(headers);
        List<Cookie> decodedCookies = cookies.getAll();

        assertEquals(2, decodedCookies.size());
        assertTrue("Decoded cookies have a=1 cookie", hasCookie(decodedCookies, "a", "1"));
        assertTrue("Decoded cookies have b=1 cookie", hasCookie(decodedCookies, "b", "2"));
    }

    @Test
    public void testFromHeadersDuplicateNames() {

        Headers headers = new Headers();
        headers.add(HttpHeaderNames.COOKIE, "a=1; a=2");

        Cookies cookies = Cookies.fromHeaders(headers);
        List<Cookie> decodedCookies = cookies.getAll();

        assertEquals(2, decodedCookies.size());
        assertTrue("Decoded cookies have a=1 cookie", hasCookie(decodedCookies, "a", "1"));
        assertTrue("Decoded cookies have a=2 cookie", hasCookie(decodedCookies, "a", "2"));
    }

    @Test
    public void testFromHeadersEmptyValue() {

        Headers headers = new Headers();
        headers.add(HttpHeaderNames.COOKIE, "a=1; a=");

        Cookies cookies = Cookies.fromHeaders(headers);
        List<Cookie> decodedCookies = cookies.getAll();

        assertEquals(2, decodedCookies.size());
        assertTrue("Decoded cookies have a=1 cookie", hasCookie(decodedCookies, "a", "1"));
        assertTrue("Decoded cookies have a= cookie", hasCookie(decodedCookies, "a", ""));
    }

    @Test
    public void testGetFirst() {
        Headers headers = new Headers();
        headers.add(HttpHeaderNames.COOKIE, "a=1; a=2");
        Cookies cookies = Cookies.fromHeaders(headers);

        Cookie firstCookie = cookies.getFirst("a");

        assertNotNull(firstCookie);
        assertEquals("1", firstCookie.value());
    }

    @Test
    public void testGetFirstValue() {
        Headers headers = new Headers();
        headers.add(HttpHeaderNames.COOKIE, "a=1; a=2");
        Cookies cookies = Cookies.fromHeaders(headers);

        String firstValue = cookies.getFirstValue("a");

        assertEquals("1", firstValue);
    }

    @Test
    public void testGet() {
        Headers headers = new Headers();
        headers.add(HttpHeaderNames.COOKIE, "a=1; a=2");
        Cookies cookies = Cookies.fromHeaders(headers);

        List<Cookie> decodedCookies = cookies.get("a");

        assertEquals(2, decodedCookies.size());
        assertTrue("Decoded cookies have a=1 cookie", hasCookie(decodedCookies, "a", "1"));
        assertTrue("Decoded cookies have a=2 cookie", hasCookie(decodedCookies, "a", "2"));
    }

    @Test
    public void testGetAll() {
        Headers headers = new Headers();
        headers.add(HttpHeaderNames.COOKIE, "a=1; a=2; b=; c=3");
        Cookies cookies = Cookies.fromHeaders(headers);

        List<Cookie> decodedCookies = cookies.getAll();

        assertEquals(4, decodedCookies.size());
        assertTrue("Decoded cookies have a=1 cookie", hasCookie(decodedCookies, "a", "1"));
        assertTrue("Decoded cookies have a=2 cookie", hasCookie(decodedCookies, "a", "2"));
        assertTrue("Decoded cookies have b= cookie", hasCookie(decodedCookies, "b", ""));
        assertTrue("Decoded cookies have c=3 cookie", hasCookie(decodedCookies, "c", "3"));
    }


    @Test
    public void testCleanCookieHeaders() {
        assertEquals("BlahId=12345; something=67890;",
                cleanCookieHeader("BlahId=12345; Secure, something=67890;"));
        assertEquals("BlahId=12345; something=67890;",
                cleanCookieHeader("BlahId=12345; something=67890;"));
        assertEquals(" BlahId=12345; something=67890;",
                cleanCookieHeader(" Secure, BlahId=12345; Secure, something=67890;"));
        assertEquals("", cleanCookieHeader(""));
    }

    private boolean hasCookie(List<Cookie> cookies, String name, String value) {
        for (Cookie cookie : cookies) {
            if (cookie.name().equals(name) && cookie.value().equals(value)) {
                return true;
            }
        }
        return false;
    }
}
