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
import java.util.List;
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
}
