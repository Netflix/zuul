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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

import com.netflix.zuul.exception.ZuulException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link Headers}.
 */
class HeadersTest {

    @Test
    void copyOf() {
        Headers headers = new Headers();
        headers.set("Content-Length", "5");
        Headers headers2 = Headers.copyOf(headers);
        headers2.add("Via", "duct");

        assertThat(headers.getAll("Via")).isEmpty();
        assertThat(headers2.size()).isEqualTo(2);
        assertThat(headers2.getAll("Content-Length")).containsExactly("5");
    }

    @Test
    void getFirst_normalizesName() {
        Headers headers = new Headers();
        headers.add("Via", "duct");
        headers.add("Cookie", "this=that");
        headers.add("Cookie", "frizzle=frazzle");

        assertThat(headers.getFirst("cOOkIE")).isEqualTo("this=that");
    }

    @Test
    void getFirst_headerName_normalizesName() {
        Headers headers = new Headers();
        headers.add("Via", "duct");
        headers.add("Cookie", "this=that");
        headers.add("Cookie", "frizzle=frazzle");

        assertThat(headers.getFirst(new HeaderName("cOOkIE"))).isEqualTo("this=that");
    }

    @Test
    void getFirst_returnsNull() {
        Headers headers = new Headers();
        headers.add("Via", "duct");
        headers.add("Cookie", "this=that");
        headers.add("Cookie", "frizzle=frazzle");

        assertThat(headers.getFirst("Date")).isNull();
    }

    @Test
    void getFirst_headerName_returnsNull() {
        Headers headers = new Headers();
        headers.add("Via", "duct");
        headers.add("Cookie", "this=that");
        headers.add("Cookie", "frizzle=frazzle");

        assertThat(headers.getFirst(new HeaderName("Date"))).isNull();
    }

    @Test
    void getFirst_returnsDefault() {
        Headers headers = new Headers();
        headers.add("Via", "duct");
        headers.add("Cookie", "this=that");
        headers.add("Cookie", "frizzle=frazzle");

        assertThat(headers.getFirst("Date", "tuesday")).isEqualTo("tuesday");
    }

    @Test
    void getFirst_headerName_returnsDefault() {
        Headers headers = new Headers();
        headers.add("Via", "duct");
        headers.add("Cookie", "this=that");
        headers.add("Cookie", "frizzle=frazzle");

        assertThat(headers.getFirst(new HeaderName("Date"), "tuesday")).isEqualTo("tuesday");
    }

    @Test
    void forEachNormalised() {
        Headers headers = new Headers();
        headers.add("Via", "duct");
        headers.add("Cookie", "this=that");
        headers.add("Cookie", "frizzle=Frazzle");
        Map<String, List<String>> result = new LinkedHashMap<>();

        headers.forEachNormalised((k, v) ->
                result.computeIfAbsent(k, discard -> new ArrayList<>()).add(v));

        assertThat(result)
                .containsExactly(
                        entry("via", Collections.singletonList("duct")),
                        entry("cookie", Arrays.asList("this=that", "frizzle=Frazzle")));
    }

    @Test
    void getAll() {
        Headers headers = new Headers();
        headers.add("Via", "duct");
        headers.add("Cookie", "this=that");
        headers.add("Cookie", "frizzle=frazzle");

        assertThat(headers.getAll("CookiE")).containsExactly("this=that", "frizzle=frazzle");
    }

    @Test
    void getAll_headerName() {
        Headers headers = new Headers();
        headers.add("Via", "duct");
        headers.add("Cookie", "this=that");
        headers.add("Cookie", "frizzle=frazzle");

        assertThat(headers.getAll(new HeaderName("CookiE"))).containsExactly("this=that", "frizzle=frazzle");
    }

    @Test
    void setClearsExisting() {
        Headers headers = new Headers();
        headers.add("Via", "duct");
        headers.add("Cookie", "this=that");
        headers.add("Cookie", "frizzle=frazzle");

        headers.set("cookIe", "dilly=dally");

        assertThat(headers.getAll("CookiE")).containsExactly("dilly=dally");
        assertThat(headers.size()).isEqualTo(2);
    }

    @Test
    void setClearsExisting_headerName() {
        Headers headers = new Headers();
        headers.add("Via", "duct");
        headers.add("Cookie", "this=that");
        headers.add("Cookie", "frizzle=frazzle");

        headers.set(new HeaderName("cookIe"), "dilly=dally");

        assertThat(headers.getAll("CookiE")).containsExactly("dilly=dally");
        assertThat(headers.size()).isEqualTo(2);
    }

    @Test
    void setNullIsEmtpy() {
        Headers headers = new Headers();
        headers.add("Via", "duct");
        headers.add("Cookie", "this=that");
        headers.add("Cookie", "frizzle=frazzle");

        headers.set("cookIe", null);

        assertThat(headers.getAll("CookiE")).isEmpty();
        assertThat(headers.size()).isEqualTo(1);
    }

    @Test
    void setNullIsEmtpy_headerName() {
        Headers headers = new Headers();
        headers.add("Via", "duct");
        headers.add("Cookie", "this=that");
        headers.add("Cookie", "frizzle=frazzle");

        headers.set(new HeaderName("cookIe"), null);

        assertThat(headers.getAll("CookiE")).isEmpty();
        assertThat(headers.size()).isEqualTo(1);
    }

    @Test
    void setIfValidNullIsEmtpy() {
        Headers headers = new Headers();
        headers.add("Via", "duct");
        headers.add("Cookie", "this=that");
        headers.add("Cookie", "frizzle=frazzle");

        headers.setIfValid("cookIe", null);

        assertThat(headers.getAll("CookiE")).isEmpty();
        assertThat(headers.size()).isEqualTo(1);
    }

    @Test
    void setIfValidNullIsEmtpy_headerName() {
        Headers headers = new Headers();
        headers.add("Via", "duct");
        headers.add("Cookie", "this=that");
        headers.add("Cookie", "frizzle=frazzle");

        headers.setIfValid(new HeaderName("cookIe"), null);

        assertThat(headers.getAll("CookiE")).isEmpty();
        assertThat(headers.size()).isEqualTo(1);
    }

    @Test
    void setIfValidIgnoresInvalidValues() {
        Headers headers = new Headers();
        headers.add("X-Valid-K1", "abc-xyz");
        headers.add("X-Valid-K2", "def-xyz");
        headers.add("X-Valid-K3", "xyz-xyz");

        headers.setIfValid("X-Valid-K1", "abc\r\n-xy\r\nz");
        headers.setIfValid("X-Valid-K2", "abc\r-xy\rz");
        headers.setIfValid("X-Valid-K3", "abc\n-xy\nz");

        assertThat(headers.getAll("X-Valid-K1")).containsExactly("abc-xyz");
        assertThat(headers.getAll("X-Valid-K2")).containsExactly("def-xyz");
        assertThat(headers.getAll("X-Valid-K3")).containsExactly("xyz-xyz");
        assertThat(headers.size()).isEqualTo(3);
    }

    @Test
    void setIfValidIgnoresInvalidValues_headerName() {
        Headers headers = new Headers();
        headers.add("X-Valid-K1", "abc-xyz");
        headers.add("X-Valid-K2", "def-xyz");
        headers.add("X-Valid-K3", "xyz-xyz");

        headers.setIfValid(new HeaderName("X-Valid-K1"), "abc\r\n-xy\r\nz");
        headers.setIfValid(new HeaderName("X-Valid-K2"), "abc\r-xy\rz");
        headers.setIfValid(new HeaderName("X-Valid-K3"), "abc\n-xy\nz");

        assertThat(headers.getAll("X-Valid-K1")).containsExactly("abc-xyz");
        assertThat(headers.getAll("X-Valid-K2")).containsExactly("def-xyz");
        assertThat(headers.getAll("X-Valid-K3")).containsExactly("xyz-xyz");
        assertThat(headers.size()).isEqualTo(3);
    }

    @Test
    void setIfValidIgnoresInvalidKey() {
        Headers headers = new Headers();
        headers.add("X-Valid-K1", "abc-xyz");

        headers.setIfValid("X-K\r\ney-1", "abc-def");
        headers.setIfValid("X-K\ney-2", "def-xyz");
        headers.setIfValid("X-K\rey-3", "xyz-xyz");

        assertThat(headers.getAll("X-Valid-K1")).containsExactly("abc-xyz");
        assertThat(headers.getAll("X-K\r\ney-1")).isEmpty();
        assertThat(headers.getAll("X-K\ney-2")).isEmpty();
        assertThat(headers.getAll("X-K\rey-3")).isEmpty();
        assertThat(headers.size()).isEqualTo(1);
    }

    @Test
    void setIfValidIgnoresInvalidKey_headerName() {
        Headers headers = new Headers();
        headers.add("X-Valid-K1", "abc-xyz");

        headers.setIfValid(new HeaderName("X-K\r\ney-1"), "abc-def");
        headers.setIfValid(new HeaderName("X-K\ney-2"), "def-xyz");
        headers.setIfValid(new HeaderName("X-K\rey-3"), "xyz-xyz");

        assertThat(headers.getAll("X-Valid-K1")).containsExactly("abc-xyz");
        assertThat(headers.getAll("X-K\r\ney-1")).isEmpty();
        assertThat(headers.getAll("X-K\ney-2")).isEmpty();
        assertThat(headers.getAll("X-K\rey-3")).isEmpty();
        assertThat(headers.size()).isEqualTo(1);
    }

    @Test
    void setIfAbsentKeepsExisting() {
        Headers headers = new Headers();
        headers.add("Via", "duct");
        headers.add("Cookie", "this=that");
        headers.add("Cookie", "frizzle=frazzle");

        headers.setIfAbsent("cookIe", "dilly=dally");

        assertThat(headers.getAll("CookiE")).containsExactly("this=that", "frizzle=frazzle");
    }

    @Test
    void setIfAbsentKeepsExisting_headerName() {
        Headers headers = new Headers();
        headers.add("Via", "duct");
        headers.add("Cookie", "this=that");
        headers.add("Cookie", "frizzle=frazzle");

        headers.setIfAbsent(new HeaderName("cookIe"), "dilly=dally");

        assertThat(headers.getAll("CookiE")).containsExactly("this=that", "frizzle=frazzle");
    }

    @Test
    void setIfAbsentFailsOnNull() {
        Headers headers = new Headers();
        headers.add("Via", "duct");
        headers.add("Cookie", "this=that");
        headers.add("Cookie", "frizzle=frazzle");

        assertThatThrownBy(() -> headers.setIfAbsent("cookIe", null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void setIfAbsentFailsOnNull_headerName() {
        Headers headers = new Headers();
        headers.add("Via", "duct");
        headers.add("Cookie", "this=that");
        headers.add("Cookie", "frizzle=frazzle");

        assertThatThrownBy(() -> headers.setIfAbsent(new HeaderName("cookIe"), null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void setIfAbsent() {
        Headers headers = new Headers();
        headers.add("Via", "duct");
        headers.add("Cookie", "this=that");
        headers.add("Cookie", "frizzle=frazzle");

        headers.setIfAbsent("X-Netflix-Awesome", "true");

        assertThat(headers.getAll("X-netflix-Awesome")).containsExactly("true");
    }

    @Test
    void setIfAbsent_headerName() {
        Headers headers = new Headers();
        headers.add("Via", "duct");
        headers.add("Cookie", "this=that");
        headers.add("Cookie", "frizzle=frazzle");

        headers.setIfAbsent(new HeaderName("X-Netflix-Awesome"), "true");

        assertThat(headers.getAll("X-netflix-Awesome")).containsExactly("true");
    }

    @Test
    void setIfAbsentAndValid() {
        Headers headers = new Headers();
        headers.add("Via", "duct");
        headers.add("Cookie", "this=that");
        headers.add("Cookie", "frizzle=frazzle");

        headers.setIfAbsentAndValid("X-Netflix-Awesome", "true");
        headers.setIfAbsentAndValid("X-Netflix-Awesome", "True");

        assertThat(headers.getAll("X-netflix-Awesome")).containsExactly("true");
        assertThat(headers.size()).isEqualTo(4);
    }

    @Test
    void setIfAbsentAndValidIgnoresInvalidValues() {
        Headers headers = new Headers();
        headers.add("Via", "duct");

        headers.setIfAbsentAndValid("X-Invalid-K1", "abc\r\nxy\r\nz");
        headers.setIfAbsentAndValid("X-Invalid-K2", "abc\rxy\rz");
        headers.setIfAbsentAndValid("X-Invalid-K3", "abc\nxy\nz");

        assertThat(headers.getAll("Via")).containsExactly("duct");
        assertThat(headers.getAll("X-Invalid-K1")).isEmpty();
        assertThat(headers.getAll("X-Invalid-K2")).isEmpty();
        assertThat(headers.getAll("X-Invalid-K3")).isEmpty();
        assertThat(headers.size()).isEqualTo(1);
    }

    @Test
    void add() {
        Headers headers = new Headers();
        headers.add("Via", "duct");
        headers.add("Cookie", "this=that");
        headers.add("Cookie", "frizzle=frazzle");

        headers.add("via", "con Dios");

        assertThat(headers.getAll("Via")).containsExactly("duct", "con Dios");
    }

    @Test
    void add_headerName() {
        Headers headers = new Headers();
        headers.add("Via", "duct");
        headers.add("Cookie", "this=that");
        headers.add("Cookie", "frizzle=frazzle");

        headers.add(new HeaderName("via"), "con Dios");

        assertThat(headers.getAll("Via")).containsExactly("duct", "con Dios");
    }

    @Test
    void addIfValid() {
        Headers headers = new Headers();
        headers.addIfValid("Via", "duct");
        headers.addIfValid("Cookie", "abc=def");
        headers.addIfValid("cookie", "uvw=xyz");

        assertThat(headers.getAll("Via")).containsExactly("duct");
        assertThat(headers.getAll("Cookie")).containsExactly("abc=def", "uvw=xyz");
        assertThat(headers.size()).isEqualTo(3);
    }

    @Test
    void addIfValid_headerName() {
        Headers headers = new Headers();
        headers.addIfValid("Via", "duct");
        headers.addIfValid("Cookie", "abc=def");
        headers.addIfValid(new HeaderName("cookie"), "uvw=xyz");

        assertThat(headers.getAll("Via")).containsExactly("duct");
        assertThat(headers.getAll("Cookie")).containsExactly("abc=def", "uvw=xyz");
        assertThat(headers.size()).isEqualTo(3);
    }

    @Test
    void addIfValidIgnoresInvalidValues() {
        Headers headers = new Headers();
        headers.addIfValid("Via", "duct");
        headers.addIfValid("Cookie", "abc=def");
        headers.addIfValid("X-Invalid-K1", "abc\r\nxy\r\nz");
        headers.addIfValid("X-Invalid-K2", "abc\rxy\rz");
        headers.addIfValid("X-Invalid-K3", "abc\nxy\nz");

        assertThat(headers.getAll("Via")).containsExactly("duct");
        assertThat(headers.getAll("Cookie")).containsExactly("abc=def");
        assertThat(headers.getAll("X-Invalid-K1")).isEmpty();
        assertThat(headers.getAll("X-Invalid-K2")).isEmpty();
        assertThat(headers.getAll("X-Invalid-K3")).isEmpty();
        assertThat(headers.size()).isEqualTo(2);
    }

    @Test
    void putAll() {
        Headers headers = new Headers();
        headers.add("Via", "duct");
        headers.add("Cookie", "this=that");
        headers.add("Cookie", "frizzle=frazzle");

        Headers other = new Headers();
        other.add("cookie", "a=b");
        other.add("via", "com");

        headers.putAll(other);

        // Only check the order per field, not for the entire set.
        assertThat(headers.getAll("Via")).containsExactly("duct", "com");
        assertThat(headers.getAll("coOkiE")).containsExactly("this=that", "frizzle=frazzle", "a=b");
        assertThat(headers.size()).isEqualTo(5);
    }

    @Test
    void remove() {
        Headers headers = new Headers();
        headers.add("Via", "duct");
        headers.add("Cookie", "this=that");
        headers.add("Cookie", "frizzle=frazzle");
        headers.add("Soup", "salad");

        List<String> removed = headers.remove("Cookie");

        assertThat(headers.getAll("Cookie")).isEmpty();
        assertThat(headers.getAll("Soup")).containsExactly("salad");
        assertThat(headers.size()).isEqualTo(2);
        assertThat(removed).containsExactly("this=that", "frizzle=frazzle");
    }

    @Test
    void remove_headerName() {
        Headers headers = new Headers();
        headers.add("Via", "duct");
        headers.add("Cookie", "this=that");
        headers.add("Cookie", "frizzle=frazzle");
        headers.add("Soup", "salad");

        List<String> removed = headers.remove(new HeaderName("Cookie"));

        assertThat(headers.getAll("Cookie")).isEmpty();
        assertThat(headers.getAll("Soup")).containsExactly("salad");
        assertThat(headers.size()).isEqualTo(2);
        assertThat(removed).containsExactly("this=that", "frizzle=frazzle");
    }

    @Test
    void removeEmpty() {
        Headers headers = new Headers();
        headers.add("Via", "duct");
        headers.add("Cookie", "this=that");
        headers.add("Cookie", "frizzle=frazzle");
        headers.add("Soup", "salad");

        List<String> removed = headers.remove("Monkey");

        assertThat(headers.getAll("Cookie")).isNotEmpty();
        assertThat(headers.getAll("Soup")).containsExactly("salad");
        assertThat(headers.size()).isEqualTo(4);
        assertThat(removed).isEmpty();
    }

    @Test
    void removeEmpty_headerName() {
        Headers headers = new Headers();
        headers.add("Via", "duct");
        headers.add("Cookie", "this=that");
        headers.add("Cookie", "frizzle=frazzle");
        headers.add("Soup", "salad");

        List<String> removed = headers.remove(new HeaderName("Monkey"));

        assertThat(headers.getAll("Cookie")).isNotEmpty();
        assertThat(headers.getAll("Soup")).containsExactly("salad");
        assertThat(headers.size()).isEqualTo(4);
        assertThat(removed).isEmpty();
    }

    @Test
    void removeIf() {
        Headers headers = new Headers();
        headers.add("Via", "duct");
        headers.add("Cookie", "this=that");
        headers.add("COOkie", "frizzle=frazzle");
        headers.add("Soup", "salad");

        boolean removed = headers.removeIf(entry -> entry.getKey().getName().equals("Cookie"));

        assertThat(removed).isTrue();
        assertThat(headers.getAll("cOoKie")).containsExactly("frizzle=frazzle");
        assertThat(headers.size()).isEqualTo(3);
    }

    @Test
    void keySet() {
        Headers headers = new Headers();
        headers.add("Via", "duct");
        headers.add("COOKie", "this=that");
        headers.add("cookIE", "frizzle=frazzle");
        headers.add("Soup", "salad");

        Set<HeaderName> keySet = headers.keySet();

        assertThat(keySet)
                .containsExactlyInAnyOrder(new HeaderName("COOKie"), new HeaderName("Soup"), new HeaderName("Via"));
        for (HeaderName headerName : keySet) {
            if (headerName.getName().equals("COOKie")) {
                return;
            }
        }
        throw new AssertionError("didn't find right cookie in keys: " + keySet);
    }

    @Test
    void contains() {
        Headers headers = new Headers();
        headers.add("Via", "duct");
        headers.add("COOKie", "this=that");
        headers.add("cookIE", "frizzle=frazzle");
        headers.add("Soup", "salad");

        assertThat(headers.contains("CoOkIe")).isTrue();
        assertThat(headers.contains(new HeaderName("CoOkIe"))).isTrue();
        assertThat(headers.contains("cookie")).isTrue();
        assertThat(headers.contains(new HeaderName("cookie"))).isTrue();
        assertThat(headers.contains("Cookie")).isTrue();
        assertThat(headers.contains(new HeaderName("Cookie"))).isTrue();
        assertThat(headers.contains("COOKIE")).isTrue();
        assertThat(headers.contains(new HeaderName("COOKIE"))).isTrue();
        assertThat(headers.contains("Monkey")).isFalse();
        assertThat(headers.contains(new HeaderName("Monkey"))).isFalse();

        headers.remove("cookie");
        assertThat(headers.contains("cookie")).isFalse();
        assertThat(headers.contains(new HeaderName("cookie"))).isFalse();
    }

    @Test
    void containsValue() {
        Headers headers = new Headers();
        headers.add("Via", "duct");
        headers.add("COOKie", "this=that");
        headers.add("cookIE", "frizzle=frazzle");
        headers.add("Soup", "salad");

        // note the swapping of the two cookie casings.
        assertThat(headers.contains("CoOkIe", "frizzle=frazzle")).isTrue();
        assertThat(headers.contains(new HeaderName("CoOkIe"), "frizzle=frazzle"))
                .isTrue();
        assertThat(headers.contains("Via", "lin")).isFalse();
        assertThat(headers.contains(new HeaderName("Soup"), "of the day")).isFalse();
    }

    @Test
    void testCaseInsensitiveKeys_Set() {
        Headers headers = new Headers();
        headers.set("Content-Length", "5");
        headers.set("content-length", "10");

        assertThat(headers.getFirst("Content-Length")).isEqualTo("10");
        assertThat(headers.getFirst("content-length")).isEqualTo("10");
        assertThat(headers.getAll("content-length").size()).isEqualTo(1);
    }

    @Test
    void testCaseInsensitiveKeys_Add() {
        Headers headers = new Headers();
        headers.add("Content-Length", "5");
        headers.add("content-length", "10");

        List<String> values = headers.getAll("content-length");
        assertThat(values.contains("10")).isTrue();
        assertThat(values.contains("5")).isTrue();
        assertThat(values.size()).isEqualTo(2);
    }

    @Test
    void testCaseInsensitiveKeys_SetIfAbsent() {
        Headers headers = new Headers();
        headers.set("Content-Length", "5");
        headers.setIfAbsent("content-length", "10");

        List<String> values = headers.getAll("content-length");
        assertThat(values.size()).isEqualTo(1);
        assertThat(values.get(0)).isEqualTo("5");
    }

    @Test
    void testCaseInsensitiveKeys_PutAll() {
        Headers headers = new Headers();
        headers.add("Content-Length", "5");
        headers.add("content-length", "10");

        Headers headers2 = new Headers();
        headers2.putAll(headers);

        List<String> values = headers2.getAll("content-length");
        assertThat(values.contains("10")).isTrue();
        assertThat(values.contains("5")).isTrue();
        assertThat(values.size()).isEqualTo(2);
    }

    @Test
    void testSanitizeValues_CRLF() {
        Headers headers = new Headers();

        assertThatThrownBy(() -> headers.addAndValidate("x-test-break1", "a\r\nb\r\nc"))
                .isInstanceOf(ZuulException.class);
        assertThatThrownBy(() -> headers.setAndValidate("x-test-break1", "a\r\nb\r\nc"))
                .isInstanceOf(ZuulException.class);
    }

    @Test
    void testSanitizeValues_LF() {
        Headers headers = new Headers();

        assertThatThrownBy(() -> headers.addAndValidate("x-test-break1", "a\nb\nc"))
                .isInstanceOf(ZuulException.class);
        assertThatThrownBy(() -> headers.setAndValidate("x-test-break1", "a\nb\nc"))
                .isInstanceOf(ZuulException.class);
    }

    @Test
    void testSanitizeValues_ISO88591Value() {
        Headers headers = new Headers();

        headers.addAndValidate("x-test-ISO-8859-1", "P Venkmän");
        assertThat(headers.getAll("x-test-ISO-8859-1")).containsExactly("P Venkmän");
        assertThat(headers.size()).isEqualTo(1);

        headers.setAndValidate("x-test-ISO-8859-1", "Venkmän");
        assertThat(headers.getAll("x-test-ISO-8859-1")).containsExactly("Venkmän");
        assertThat(headers.size()).isEqualTo(1);
    }

    @Test
    void testSanitizeValues_UTF8Value() {
        // Ideally Unicode characters should not appear in the Header values.
        Headers headers = new Headers();

        String rawHeaderValue = "\u017d" + "\u0172" + "\u016e" + "\u013F"; // ŽŲŮĽ
        byte[] bytes = rawHeaderValue.getBytes(StandardCharsets.UTF_8);
        String utf8HeaderValue = new String(bytes, StandardCharsets.UTF_8);
        headers.addAndValidate("x-test-UTF8", utf8HeaderValue);
        assertThat(headers.getAll("x-test-UTF8")).containsExactly(utf8HeaderValue);
        assertThat(headers.size()).isEqualTo(1);

        rawHeaderValue = "\u017d" + "\u0172" + "uuu" + "\u016e" + "\u013F"; // ŽŲuuuŮĽ
        bytes = rawHeaderValue.getBytes(StandardCharsets.UTF_8);
        utf8HeaderValue = new String(bytes, StandardCharsets.UTF_8);
        headers.setAndValidate("x-test-UTF8", utf8HeaderValue);
        assertThat(headers.getAll("x-test-UTF8")).containsExactly(utf8HeaderValue);
        assertThat(headers.size()).isEqualTo(1);
    }

    @Test
    void testSanitizeValues_addSetHeaderName() {
        Headers headers = new Headers();

        assertThatThrownBy(() -> headers.setAndValidate(new HeaderName("x-test-break1"), "a\nb\nc"))
                .isInstanceOf(ZuulException.class);
        assertThatThrownBy(() -> headers.addAndValidate(new HeaderName("x-test-break2"), "a\r\nb\r\nc"))
                .isInstanceOf(ZuulException.class);
    }

    @Test
    void testSanitizeValues_nameCRLF() {
        Headers headers = new Headers();

        assertThatThrownBy(() -> headers.addAndValidate("x-test-br\r\neak1", "a\r\nb\r\nc"))
                .isInstanceOf(ZuulException.class);
        assertThatThrownBy(() -> headers.setAndValidate("x-test-br\r\neak2", "a\r\nb\r\nc"))
                .isInstanceOf(ZuulException.class);
    }
}
