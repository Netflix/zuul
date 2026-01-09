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

package com.netflix.zuul.message.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.handler.codec.http.cookie.Cookie;
import io.netty.handler.codec.http.cookie.DefaultCookie;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

public class CookiesTest {

    @Test
    public void getNamesReturnsEmptySetWhenNoCookies() {
        Cookies cookies = new Cookies();

        Set<String> names = cookies.getNames();

        assertNotNull(names);
        assertTrue(names.isEmpty());
    }

    @Test
    public void getNamesReturnsSingleCookieName() {
        Cookies cookies = new Cookies();
        cookies.add(new DefaultCookie("sessionId", "abc123"));

        Set<String> names = cookies.getNames();

        assertEquals(1, names.size());
        assertTrue(names.contains("sessionId"));
    }

    @Test
    public void getNamesReturnsMultipleCookieNames() {
        Cookies cookies = new Cookies();
        cookies.add(new DefaultCookie("NetflixId", "value1"));
        cookies.add(new DefaultCookie("SecureNetflixId", "value2"));
        cookies.add(new DefaultCookie("vdid", "value3"));

        Set<String> names = cookies.getNames();

        assertEquals(3, names.size());
        assertTrue(names.contains("NetflixId"));
        assertTrue(names.contains("SecureNetflixId"));
        assertTrue(names.contains("vdid"));
    }

    @Test
    public void getNamesReturnsAllUniqueNames() {
        Cookies cookies = new Cookies();
        cookies.add(new DefaultCookie("first", "1"));
        cookies.add(new DefaultCookie("second", "2"));
        cookies.add(new DefaultCookie("third", "3"));

        Set<String> names = cookies.getNames();

        assertEquals(3, names.size());
        assertTrue(names.contains("first"));
        assertTrue(names.contains("second"));
        assertTrue(names.contains("third"));
    }

    @Test
    public void getNamesReturnsUniqueNames() {
        Cookies cookies = new Cookies();
        cookies.add(new DefaultCookie("duplicate", "value1"));
        cookies.add(new DefaultCookie("duplicate", "value2"));
        cookies.add(new DefaultCookie("other", "value3"));

        Set<String> names = cookies.getNames();

        assertEquals(2, names.size());
        assertTrue(names.contains("duplicate"));
        assertTrue(names.contains("other"));
    }

    @Test
    public void getAllReturnsAllCookies() {
        Cookies cookies = new Cookies();
        Cookie c1 = new DefaultCookie("a", "1");
        Cookie c2 = new DefaultCookie("b", "2");
        cookies.add(c1);
        cookies.add(c2);

        List<Cookie> all = cookies.getAll();

        assertEquals(2, all.size());
        assertEquals(c1, all.get(0));
        assertEquals(c2, all.get(1));
    }

    @Test
    public void getReturnsMatchingCookies() {
        Cookies cookies = new Cookies();
        cookies.add(new DefaultCookie("session", "value1"));
        cookies.add(new DefaultCookie("session", "value2"));
        cookies.add(new DefaultCookie("other", "value3"));

        List<Cookie> sessionCookies = cookies.get("session");

        assertNotNull(sessionCookies);
        assertEquals(2, sessionCookies.size());
        assertEquals("session", sessionCookies.get(0).name());
        assertEquals("value1", sessionCookies.get(0).value());
        assertEquals("session", sessionCookies.get(1).name());
        assertEquals("value2", sessionCookies.get(1).value());
    }

    @Test
    public void getReturnsNullForNonExistentCookie() {
        Cookies cookies = new Cookies();
        cookies.add(new DefaultCookie("exists", "value"));

        List<Cookie> result = cookies.get("doesNotExist");

        assertNull(result);
    }

    @Test
    public void getFirstReturnsFirstMatchingCookie() {
        Cookies cookies = new Cookies();
        cookies.add(new DefaultCookie("session", "first"));
        cookies.add(new DefaultCookie("session", "second"));

        Cookie first = cookies.getFirst("session");

        assertNotNull(first);
        assertEquals("session", first.name());
        assertEquals("first", first.value());
    }

    @Test
    public void getFirstReturnsNullForNonExistentCookie() {
        Cookies cookies = new Cookies();

        Cookie result = cookies.getFirst("doesNotExist");

        assertNull(result);
    }

    @Test
    public void getFirstValueReturnsValueOfFirstMatchingCookie() {
        Cookies cookies = new Cookies();
        cookies.add(new DefaultCookie("token", "abc123"));
        cookies.add(new DefaultCookie("token", "def456"));

        String value = cookies.getFirstValue("token");

        assertEquals("abc123", value);
    }

    @Test
    public void getFirstValueReturnsNullForNonExistentCookie() {
        Cookies cookies = new Cookies();

        String value = cookies.getFirstValue("doesNotExist");

        assertNull(value);
    }
}
