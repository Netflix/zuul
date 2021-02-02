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

package com.netflix.zuul.message;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.google.common.truth.Truth;
import com.netflix.zuul.exception.ZuulException;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for {@link Headers}.
 */
@RunWith(JUnit4.class)
public class HeadersTest {

    @Test
    public void copyOf() {
        Headers headers = new Headers();
        headers.set("Content-Length", "5");
        Headers headers2 = Headers.copyOf(headers);
        headers2.add("Via", "duct");

        Truth.assertThat(headers.getAll("Via")).isEmpty();
        Truth.assertThat(headers2.size()).isEqualTo(2);
        Truth.assertThat(headers2.getAll("Content-Length")).containsExactly("5");
    }

    @Test
    public void getFirst_normalizesName() {
        Headers headers = new Headers();
        headers.add("Via", "duct");
        headers.add("Cookie", "this=that");
        headers.add("Cookie", "frizzle=frazzle");

        Truth.assertThat(headers.getFirst("cOOkIE")).isEqualTo("this=that");
    }

    @Test
    public void getFirst_headerName_normalizesName() {
        Headers headers = new Headers();
        headers.add("Via", "duct");
        headers.add("Cookie", "this=that");
        headers.add("Cookie", "frizzle=frazzle");

        Truth.assertThat(headers.getFirst(new HeaderName("cOOkIE"))).isEqualTo("this=that");
    }

    @Test
    public void getFirst_returnsNull() {
        Headers headers = new Headers();
        headers.add("Via", "duct");
        headers.add("Cookie", "this=that");
        headers.add("Cookie", "frizzle=frazzle");

        Truth.assertThat(headers.getFirst("Date")).isNull();
    }

    @Test
    public void getFirst_headerName_returnsNull() {
        Headers headers = new Headers();
        headers.add("Via", "duct");
        headers.add("Cookie", "this=that");
        headers.add("Cookie", "frizzle=frazzle");

        Truth.assertThat(headers.getFirst(new HeaderName("Date"))).isNull();
    }

    @Test
    public void getFirst_returnsDefault() {
        Headers headers = new Headers();
        headers.add("Via", "duct");
        headers.add("Cookie", "this=that");
        headers.add("Cookie", "frizzle=frazzle");

        Truth.assertThat(headers.getFirst("Date", "tuesday")).isEqualTo("tuesday");
    }

    @Test
    public void getFirst_headerName_returnsDefault() {
        Headers headers = new Headers();
        headers.add("Via", "duct");
        headers.add("Cookie", "this=that");
        headers.add("Cookie", "frizzle=frazzle");

        Truth.assertThat(headers.getFirst(new HeaderName("Date"), "tuesday")).isEqualTo("tuesday");
    }

    @Test
    public void forEachNormalised() {
        Headers headers = new Headers();
        headers.add("Via", "duct");
        headers.add("Cookie", "this=that");
        headers.add("Cookie", "frizzle=Frazzle");
        Map<String, List<String>> result = new LinkedHashMap<>();

        headers.forEachNormalised((k, v) -> result.computeIfAbsent(k, discard -> new ArrayList<>()).add(v));

        Truth.assertThat(result).containsExactly(
                "via", Collections.singletonList("duct"),
                "cookie", Arrays.asList("this=that", "frizzle=Frazzle")).inOrder();
    }

    @Test
    public void getAll() {
        Headers headers = new Headers();
        headers.add("Via", "duct");
        headers.add("Cookie", "this=that");
        headers.add("Cookie", "frizzle=frazzle");

        Truth.assertThat(headers.getAll("CookiE")).containsExactly("this=that", "frizzle=frazzle").inOrder();
    }

    @Test
    public void getAll_headerName() {
        Headers headers = new Headers();
        headers.add("Via", "duct");
        headers.add("Cookie", "this=that");
        headers.add("Cookie", "frizzle=frazzle");

        Truth.assertThat(headers.getAll(new HeaderName("CookiE")))
                .containsExactly("this=that", "frizzle=frazzle").inOrder();
    }

    @Test
    public void setClearsExisting() {
        Headers headers = new Headers();
        headers.add("Via", "duct");
        headers.add("Cookie", "this=that");
        headers.add("Cookie", "frizzle=frazzle");

        headers.set("cookIe", "dilly=dally");

        Truth.assertThat(headers.getAll("CookiE")).containsExactly("dilly=dally");
        Truth.assertThat(headers.size()).isEqualTo(2);
    }

    @Test
    public void setClearsExisting_headerName() {
        Headers headers = new Headers();
        headers.add("Via", "duct");
        headers.add("Cookie", "this=that");
        headers.add("Cookie", "frizzle=frazzle");

        headers.set(new HeaderName("cookIe"), "dilly=dally");

        Truth.assertThat(headers.getAll("CookiE")).containsExactly("dilly=dally");
        Truth.assertThat(headers.size()).isEqualTo(2);
    }

    @Test
    public void setNullIsEmtpy() {
        Headers headers = new Headers();
        headers.add("Via", "duct");
        headers.add("Cookie", "this=that");
        headers.add("Cookie", "frizzle=frazzle");

        headers.set("cookIe", null);

        Truth.assertThat(headers.getAll("CookiE")).isEmpty();
        Truth.assertThat(headers.size()).isEqualTo(1);
    }

    @Test
    public void setNullIsEmtpy_headerName() {
        Headers headers = new Headers();
        headers.add("Via", "duct");
        headers.add("Cookie", "this=that");
        headers.add("Cookie", "frizzle=frazzle");

        headers.set(new HeaderName("cookIe"), null);

        Truth.assertThat(headers.getAll("CookiE")).isEmpty();
        Truth.assertThat(headers.size()).isEqualTo(1);
    }

    @Test
    public void setIfValidNullIsEmtpy() {
        Headers headers = new Headers();
        headers.add("Via", "duct");
        headers.add("Cookie", "this=that");
        headers.add("Cookie", "frizzle=frazzle");

        headers.setIfValid("cookIe", null);

        Truth.assertThat(headers.getAll("CookiE")).isEmpty();
        Truth.assertThat(headers.size()).isEqualTo(1);
    }

    @Test
    public void setIfValidNullIsEmtpy_headerName() {
        Headers headers = new Headers();
        headers.add("Via", "duct");
        headers.add("Cookie", "this=that");
        headers.add("Cookie", "frizzle=frazzle");

        headers.setIfValid(new HeaderName("cookIe"), null);

        Truth.assertThat(headers.getAll("CookiE")).isEmpty();
        Truth.assertThat(headers.size()).isEqualTo(1);
    }

    @Test
    public void setIfValidIgnoresInvalidValues() {
        Headers headers = new Headers();
        headers.add("X-Valid-K1", "abc-xyz");
        headers.add("X-Valid-K2", "def-xyz");
        headers.add("X-Valid-K3", "xyz-xyz");

        headers.setIfValid("X-Valid-K1", "abc\r\n-xy\r\nz");
        headers.setIfValid("X-Valid-K2", "abc\r-xy\rz");
        headers.setIfValid("X-Valid-K3", "abc\n-xy\nz");

        Truth.assertThat(headers.getAll("X-Valid-K1")).containsExactly("abc-xyz");
        Truth.assertThat(headers.getAll("X-Valid-K2")).containsExactly("def-xyz");
        Truth.assertThat(headers.getAll("X-Valid-K3")).containsExactly("xyz-xyz");
        Truth.assertThat(headers.size()).isEqualTo(3);
    }

    @Test
    public void setIfValidIgnoresInvalidValues_headerName() {
        Headers headers = new Headers();
        headers.add("X-Valid-K1", "abc-xyz");
        headers.add("X-Valid-K2", "def-xyz");
        headers.add("X-Valid-K3", "xyz-xyz");

        headers.setIfValid(new HeaderName("X-Valid-K1"), "abc\r\n-xy\r\nz");
        headers.setIfValid(new HeaderName("X-Valid-K2"), "abc\r-xy\rz");
        headers.setIfValid(new HeaderName("X-Valid-K3"), "abc\n-xy\nz");

        Truth.assertThat(headers.getAll("X-Valid-K1")).containsExactly("abc-xyz");
        Truth.assertThat(headers.getAll("X-Valid-K2")).containsExactly("def-xyz");
        Truth.assertThat(headers.getAll("X-Valid-K3")).containsExactly("xyz-xyz");
        Truth.assertThat(headers.size()).isEqualTo(3);
    }

    @Test
    public void setIfValidIgnoresInvalidKey() {
        Headers headers = new Headers();
        headers.add("X-Valid-K1", "abc-xyz");

        headers.setIfValid("X-K\r\ney-1", "abc-def");
        headers.setIfValid("X-K\ney-2", "def-xyz");
        headers.setIfValid("X-K\rey-3", "xyz-xyz");

        Truth.assertThat(headers.getAll("X-Valid-K1")).containsExactly("abc-xyz");
        Truth.assertThat(headers.getAll("X-K\r\ney-1")).isEmpty();
        Truth.assertThat(headers.getAll("X-K\ney-2")).isEmpty();
        Truth.assertThat(headers.getAll("X-K\rey-3")).isEmpty();
        Truth.assertThat(headers.size()).isEqualTo(1);
    }

    @Test
    public void setIfValidIgnoresInvalidKey_headerName() {
        Headers headers = new Headers();
        headers.add("X-Valid-K1", "abc-xyz");

        headers.setIfValid(new HeaderName("X-K\r\ney-1"), "abc-def");
        headers.setIfValid(new HeaderName("X-K\ney-2"), "def-xyz");
        headers.setIfValid(new HeaderName("X-K\rey-3"), "xyz-xyz");

        Truth.assertThat(headers.getAll("X-Valid-K1")).containsExactly("abc-xyz");
        Truth.assertThat(headers.getAll("X-K\r\ney-1")).isEmpty();
        Truth.assertThat(headers.getAll("X-K\ney-2")).isEmpty();
        Truth.assertThat(headers.getAll("X-K\rey-3")).isEmpty();
        Truth.assertThat(headers.size()).isEqualTo(1);
    }

    @Test
    public void setIfAbsentKeepsExisting() {
        Headers headers = new Headers();
        headers.add("Via", "duct");
        headers.add("Cookie", "this=that");
        headers.add("Cookie", "frizzle=frazzle");

        headers.setIfAbsent("cookIe", "dilly=dally");

        Truth.assertThat(headers.getAll("CookiE")).containsExactly("this=that", "frizzle=frazzle").inOrder();
    }

    @Test
    public void setIfAbsentKeepsExisting_headerName() {
        Headers headers = new Headers();
        headers.add("Via", "duct");
        headers.add("Cookie", "this=that");
        headers.add("Cookie", "frizzle=frazzle");

        headers.setIfAbsent(new HeaderName("cookIe"), "dilly=dally");

        Truth.assertThat(headers.getAll("CookiE")).containsExactly("this=that", "frizzle=frazzle").inOrder();
    }

    @Test
    public void setIfAbsentFailsOnNull() {
        Headers headers = new Headers();
        headers.add("Via", "duct");
        headers.add("Cookie", "this=that");
        headers.add("Cookie", "frizzle=frazzle");

        assertThrows(NullPointerException.class, () -> headers.setIfAbsent("cookIe", null));
    }

    @Test
    public void setIfAbsentFailsOnNull_headerName() {
        Headers headers = new Headers();
        headers.add("Via", "duct");
        headers.add("Cookie", "this=that");
        headers.add("Cookie", "frizzle=frazzle");

        assertThrows(NullPointerException.class, () -> headers.setIfAbsent(new HeaderName("cookIe"), null));
    }

    @Test
    public void setIfAbsent() {
        Headers headers = new Headers();
        headers.add("Via", "duct");
        headers.add("Cookie", "this=that");
        headers.add("Cookie", "frizzle=frazzle");

        headers.setIfAbsent("X-Netflix-Awesome", "true");

        Truth.assertThat(headers.getAll("X-netflix-Awesome")).containsExactly("true");
    }

    @Test
    public void setIfAbsent_headerName() {
        Headers headers = new Headers();
        headers.add("Via", "duct");
        headers.add("Cookie", "this=that");
        headers.add("Cookie", "frizzle=frazzle");

        headers.setIfAbsent(new HeaderName("X-Netflix-Awesome"), "true");

        Truth.assertThat(headers.getAll("X-netflix-Awesome")).containsExactly("true");
    }

    @Test
    public void setIfAbsentAndValid() {
        Headers headers = new Headers();
        headers.add("Via", "duct");
        headers.add("Cookie", "this=that");
        headers.add("Cookie", "frizzle=frazzle");

        headers.setIfAbsentAndValid("X-Netflix-Awesome", "true");
        headers.setIfAbsentAndValid("X-Netflix-Awesome", "True");

        Truth.assertThat(headers.getAll("X-netflix-Awesome")).containsExactly("true");
        Truth.assertThat(headers.size()).isEqualTo(4);
    }

    @Test
    public void setIfAbsentAndValidIgnoresInvalidValues() {
        Headers headers = new Headers();
        headers.add("Via", "duct");

        headers.setIfAbsentAndValid("X-Invalid-K1", "abc\r\nxy\r\nz");
        headers.setIfAbsentAndValid("X-Invalid-K2", "abc\rxy\rz");
        headers.setIfAbsentAndValid("X-Invalid-K3", "abc\nxy\nz");

        Truth.assertThat(headers.getAll("Via")).containsExactly("duct");
        Truth.assertThat(headers.getAll("X-Invalid-K1")).isEmpty();
        Truth.assertThat(headers.getAll("X-Invalid-K2")).isEmpty();
        Truth.assertThat(headers.getAll("X-Invalid-K3")).isEmpty();
        Truth.assertThat(headers.size()).isEqualTo(1);
    }

    @Test
    public void add() {
        Headers headers = new Headers();
        headers.add("Via", "duct");
        headers.add("Cookie", "this=that");
        headers.add("Cookie", "frizzle=frazzle");

        headers.add("via", "con Dios");

        Truth.assertThat(headers.getAll("Via")).containsExactly("duct", "con Dios").inOrder();
    }

    @Test
    public void add_headerName() {
        Headers headers = new Headers();
        headers.add("Via", "duct");
        headers.add("Cookie", "this=that");
        headers.add("Cookie", "frizzle=frazzle");

        headers.add(new HeaderName("via"), "con Dios");

        Truth.assertThat(headers.getAll("Via")).containsExactly("duct", "con Dios").inOrder();
    }

    @Test
    public void addIfValid() {
        Headers headers = new Headers();
        headers.addIfValid("Via", "duct");
        headers.addIfValid("Cookie", "abc=def");
        headers.addIfValid("cookie", "uvw=xyz");

        Truth.assertThat(headers.getAll("Via")).containsExactly("duct");
        Truth.assertThat(headers.getAll("Cookie")).containsExactly("abc=def", "uvw=xyz").inOrder();
        Truth.assertThat(headers.size()).isEqualTo(3);
    }

    @Test
    public void addIfValid_headerName() {
        Headers headers = new Headers();
        headers.addIfValid("Via", "duct");
        headers.addIfValid("Cookie", "abc=def");
        headers.addIfValid(new HeaderName("cookie"), "uvw=xyz");

        Truth.assertThat(headers.getAll("Via")).containsExactly("duct");
        Truth.assertThat(headers.getAll("Cookie")).containsExactly("abc=def", "uvw=xyz").inOrder();
        Truth.assertThat(headers.size()).isEqualTo(3);
    }

    @Test
    public void addIfValidIgnoresInvalidValues() {
        Headers headers = new Headers();
        headers.addIfValid("Via", "duct");
        headers.addIfValid("Cookie", "abc=def");
        headers.addIfValid("X-Invalid-K1", "abc\r\nxy\r\nz");
        headers.addIfValid("X-Invalid-K2", "abc\rxy\rz");
        headers.addIfValid("X-Invalid-K3", "abc\nxy\nz");

        Truth.assertThat(headers.getAll("Via")).containsExactly("duct");
        Truth.assertThat(headers.getAll("Cookie")).containsExactly("abc=def");
        Truth.assertThat(headers.getAll("X-Invalid-K1")).isEmpty();
        Truth.assertThat(headers.getAll("X-Invalid-K2")).isEmpty();
        Truth.assertThat(headers.getAll("X-Invalid-K3")).isEmpty();
        Truth.assertThat(headers.size()).isEqualTo(2);
    }

    @Test
    public void putAll() {
        Headers headers = new Headers();
        headers.add("Via", "duct");
        headers.add("Cookie", "this=that");
        headers.add("Cookie", "frizzle=frazzle");

        Headers other = new Headers();
        other.add("cookie", "a=b");
        other.add("via", "com");

        headers.putAll(other);

        // Only check the order per field, not for the entire set.
        Truth.assertThat(headers.getAll("Via")).containsExactly("duct", "com").inOrder();
        Truth.assertThat(headers.getAll("coOkiE")).containsExactly("this=that", "frizzle=frazzle", "a=b").inOrder();
        Truth.assertThat(headers.size()).isEqualTo(5);
    }


    @Test
    public void remove() {
        Headers headers = new Headers();
        headers.add("Via", "duct");
        headers.add("Cookie", "this=that");
        headers.add("Cookie", "frizzle=frazzle");
        headers.add("Soup", "salad");

        List<String> removed = headers.remove("Cookie");

        Truth.assertThat(headers.getAll("Cookie")).isEmpty();
        Truth.assertThat(headers.getAll("Soup")).containsExactly("salad");
        Truth.assertThat(headers.size()).isEqualTo(2);
        Truth.assertThat(removed).containsExactly("this=that", "frizzle=frazzle").inOrder();
    }

    @Test
    public void remove_headerName() {
        Headers headers = new Headers();
        headers.add("Via", "duct");
        headers.add("Cookie", "this=that");
        headers.add("Cookie", "frizzle=frazzle");
        headers.add("Soup", "salad");

        List<String> removed = headers.remove(new HeaderName("Cookie"));

        Truth.assertThat(headers.getAll("Cookie")).isEmpty();
        Truth.assertThat(headers.getAll("Soup")).containsExactly("salad");
        Truth.assertThat(headers.size()).isEqualTo(2);
        Truth.assertThat(removed).containsExactly("this=that", "frizzle=frazzle").inOrder();
    }

    @Test
    public void removeEmpty() {
        Headers headers = new Headers();
        headers.add("Via", "duct");
        headers.add("Cookie", "this=that");
        headers.add("Cookie", "frizzle=frazzle");
        headers.add("Soup", "salad");

        List<String> removed = headers.remove("Monkey");

        Truth.assertThat(headers.getAll("Cookie")).isNotEmpty();
        Truth.assertThat(headers.getAll("Soup")).containsExactly("salad");
        Truth.assertThat(headers.size()).isEqualTo(4);
        Truth.assertThat(removed).isEmpty();
    }

    @Test
    public void removeEmpty_headerName() {
        Headers headers = new Headers();
        headers.add("Via", "duct");
        headers.add("Cookie", "this=that");
        headers.add("Cookie", "frizzle=frazzle");
        headers.add("Soup", "salad");

        List<String> removed = headers.remove(new HeaderName("Monkey"));

        Truth.assertThat(headers.getAll("Cookie")).isNotEmpty();
        Truth.assertThat(headers.getAll("Soup")).containsExactly("salad");
        Truth.assertThat(headers.size()).isEqualTo(4);
        Truth.assertThat(removed).isEmpty();
    }

    @Test
    public void removeIf() {
        Headers headers = new Headers();
        headers.add("Via", "duct");
        headers.add("Cookie", "this=that");
        headers.add("COOkie", "frizzle=frazzle");
        headers.add("Soup", "salad");

        boolean removed = headers.removeIf(entry -> entry.getKey().getName().equals("Cookie"));

        assertTrue(removed);
        Truth.assertThat(headers.getAll("cOoKie")).containsExactly("frizzle=frazzle");
        Truth.assertThat(headers.size()).isEqualTo(3);
    }

    @Test
    public void keySet() {
        Headers headers = new Headers();
        headers.add("Via", "duct");
        headers.add("COOKie", "this=that");
        headers.add("cookIE", "frizzle=frazzle");
        headers.add("Soup", "salad");

        Set<HeaderName> keySet = headers.keySet();

        Truth.assertThat(keySet)
                .containsExactly(new HeaderName("COOKie"), new HeaderName("Soup"), new HeaderName("Via"));
        for (HeaderName headerName : keySet) {
            if (headerName.getName().equals("COOKie")) {
                return;
            }
        }
        throw new AssertionError("didn't find right cookie in keys: " + keySet);
    }

    @Test
    public void contains() {
        Headers headers = new Headers();
        headers.add("Via", "duct");
        headers.add("COOKie", "this=that");
        headers.add("cookIE", "frizzle=frazzle");
        headers.add("Soup", "salad");

        assertTrue(headers.contains("CoOkIe"));
        assertTrue(headers.contains(new HeaderName("CoOkIe")));
        assertFalse(headers.contains("Monkey"));
        assertFalse(headers.contains(new HeaderName("Monkey")));
    }

    @Test
    public void containsValue() {
        Headers headers = new Headers();
        headers.add("Via", "duct");
        headers.add("COOKie", "this=that");
        headers.add("cookIE", "frizzle=frazzle");
        headers.add("Soup", "salad");

        // note the swapping of the two cookie casings.
        assertTrue(headers.contains("CoOkIe", "frizzle=frazzle"));
        assertTrue(headers.contains(new HeaderName("CoOkIe"), "frizzle=frazzle"));
        assertFalse(headers.contains("Via", "lin"));
        assertFalse(headers.contains(new HeaderName("Soup"), "of the day"));
    }

    @Test
    public void testCaseInsensitiveKeys_Set() {
        Headers headers = new Headers();
        headers.set("Content-Length", "5");
        headers.set("content-length", "10");

        assertEquals("10", headers.getFirst("Content-Length"));
        assertEquals("10", headers.getFirst("content-length"));
        assertEquals(1, headers.getAll("content-length").size());
    }

    @Test
    public void testCaseInsensitiveKeys_Add() {
        Headers headers = new Headers();
        headers.add("Content-Length", "5");
        headers.add("content-length", "10");

        List<String> values = headers.getAll("content-length");
        assertTrue(values.contains("10"));
        assertTrue(values.contains("5"));
        assertEquals(2, values.size());
    }

    @Test
    public void testCaseInsensitiveKeys_SetIfAbsent() {
        Headers headers = new Headers();
        headers.set("Content-Length", "5");
        headers.setIfAbsent("content-length", "10");

        List<String> values = headers.getAll("content-length");
        assertEquals(1, values.size());
        assertEquals("5", values.get(0));
    }

    @Test
    public void testCaseInsensitiveKeys_PutAll() {
        Headers headers = new Headers();
        headers.add("Content-Length", "5");
        headers.add("content-length", "10");

        Headers headers2 = new Headers();
        headers2.putAll(headers);

        List<String> values = headers2.getAll("content-length");
        assertTrue(values.contains("10"));
        assertTrue(values.contains("5"));
        assertEquals(2, values.size());
    }

    @Test
    public void testSanitizeValues_CRLF() {
        Headers headers = new Headers();

        assertThrows(ZuulException.class, () -> headers.addAndValidate("x-test-break1", "a\r\nb\r\nc"));
        assertThrows(ZuulException.class, () -> headers.setAndValidate("x-test-break1", "a\r\nb\r\nc"));
    }

    @Test
    public void testSanitizeValues_LF() {
        Headers headers = new Headers();

        assertThrows(ZuulException.class, () -> headers.addAndValidate("x-test-break1", "a\nb\nc"));
        assertThrows(ZuulException.class, () -> headers.setAndValidate("x-test-break1", "a\nb\nc"));
    }

    @Test
    public void testSanitizeValues_ISO88591Value() {
        Headers headers = new Headers();

        headers.addAndValidate("x-test-ISO-8859-1", "P Venkmän");
        Truth.assertThat(headers.getAll("x-test-ISO-8859-1")).containsExactly("P Venkmän");
        Truth.assertThat(headers.size()).isEqualTo(1);

        headers.setAndValidate("x-test-ISO-8859-1", "Venkmän");
        Truth.assertThat(headers.getAll("x-test-ISO-8859-1")).containsExactly("Venkmän");
        Truth.assertThat(headers.size()).isEqualTo(1);
    }

    @Test
    public void testSanitizeValues_UTF8Value() {
        // Ideally Unicode characters should not appear in the Header values.
        Headers headers = new Headers();

        String rawHeaderValue = "\u017d" + "\u0172" + "\u016e" + "\u013F"; //ŽŲŮĽ
        byte[] bytes = rawHeaderValue.getBytes(StandardCharsets.UTF_8);
        String utf8HeaderValue = new String(bytes, StandardCharsets.UTF_8);
        headers.addAndValidate("x-test-UTF8", utf8HeaderValue);
        Truth.assertThat(headers.getAll("x-test-UTF8")).containsExactly(utf8HeaderValue);
        Truth.assertThat(headers.size()).isEqualTo(1);

        rawHeaderValue = "\u017d" + "\u0172" + "uuu" + "\u016e" + "\u013F"; //ŽŲuuuŮĽ
        bytes = rawHeaderValue.getBytes(StandardCharsets.UTF_8);
        utf8HeaderValue = new String(bytes, StandardCharsets.UTF_8);
        headers.setAndValidate("x-test-UTF8", utf8HeaderValue);
        Truth.assertThat(headers.getAll("x-test-UTF8")).containsExactly(utf8HeaderValue);
        Truth.assertThat(headers.size()).isEqualTo(1);
    }

    @Test
    public void testSanitizeValues_addSetHeaderName() {
        Headers headers = new Headers();

        assertThrows(ZuulException.class, () -> headers.setAndValidate(new HeaderName("x-test-break1"), "a\nb\nc"));
        assertThrows(ZuulException.class, () -> headers.addAndValidate(new HeaderName("x-test-break2"), "a\r\nb\r\nc"));
    }

    @Test
    public void testSanitizeValues_nameCRLF() {
        Headers headers = new Headers();

        assertThrows(ZuulException.class, () -> headers.addAndValidate("x-test-br\r\neak1", "a\r\nb\r\nc"));
        assertThrows(ZuulException.class, () -> headers.setAndValidate("x-test-br\r\neak2", "a\r\nb\r\nc"));
    }
}
