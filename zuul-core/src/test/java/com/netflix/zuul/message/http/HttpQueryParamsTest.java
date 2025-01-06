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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HttpQueryParamsTest {

    @Test
    void testMultiples() {
        HttpQueryParams qp = new HttpQueryParams();
        qp.add("k1", "v1");
        qp.add("k1", "v2");
        qp.add("k2", "v3");

        assertEquals("k1=v1&k1=v2&k2=v3", qp.toEncodedString());
    }

    @Test
    void testDuplicateKv() {
        HttpQueryParams qp = new HttpQueryParams();
        qp.add("k1", "v1");
        qp.add("k1", "v1");
        qp.add("k1", "v1");

        assertEquals("k1=v1&k1=v1&k1=v1", qp.toEncodedString());
        assertEquals(List.of("v1", "v1", "v1"), qp.get("k1"));
    }

    @Test
    void testToEncodedString() {
        HttpQueryParams qp = new HttpQueryParams();
        qp.add("k'1", "v1&");
        assertEquals("k%271=v1%26", qp.toEncodedString());

        qp = new HttpQueryParams();
        qp.add("k+", "\n");
        assertEquals("k%2B=%0A", qp.toEncodedString());
    }

    @Test
    void testToString() {
        HttpQueryParams qp = new HttpQueryParams();
        qp.add("k'1", "v1&");
        assertEquals("k'1=v1&", qp.toString());

        qp = new HttpQueryParams();
        qp.add("k+", "\n");
        assertEquals("k+=\n", qp.toString());
    }

    @Test
    void testEquals() {
        HttpQueryParams qp1 = new HttpQueryParams();
        qp1.add("k1", "v1");
        qp1.add("k2", "v2");
        HttpQueryParams qp2 = new HttpQueryParams();
        qp2.add("k1", "v1");
        qp2.add("k2", "v2");

        assertEquals(qp1, qp2);
    }

    @Test
    void parseKeysWithoutValues() {
        HttpQueryParams expected = new HttpQueryParams();
        expected.add("k1", "");
        expected.add("k2", "v2");
        expected.add("k3", "");

        HttpQueryParams actual = HttpQueryParams.parse("k1=&k2=v2&k3=");

        assertEquals(expected, actual);

        assertEquals("k1=&k2=v2&k3=", actual.toEncodedString());
    }

    @Test
    void parseKeyWithoutValueEquals() {
        HttpQueryParams expected = new HttpQueryParams();
        expected.add("k1", "");

        HttpQueryParams actual = HttpQueryParams.parse("k1=");

        assertEquals(expected, actual);

        assertEquals("k1=", actual.toEncodedString());
    }

    @Test
    void parseKeyWithoutValue() {
        HttpQueryParams expected = new HttpQueryParams();
        expected.add("k1", "");

        HttpQueryParams actual = HttpQueryParams.parse("k1");

        assertEquals(expected, actual);

        assertEquals("k1", actual.toEncodedString());
    }

    @Test
    void parseKeyWithoutValueShort() {
        HttpQueryParams expected = new HttpQueryParams();
        expected.add("=", "");

        HttpQueryParams actual = HttpQueryParams.parse("=");

        assertEquals(expected, actual);

        assertEquals("%3D", actual.toEncodedString());
    }

    @Test
    void parseKeysWithoutValuesMixedTrailers() {
        HttpQueryParams expected = new HttpQueryParams();
        expected.add("k1", "");
        expected.add("k2", "v2");
        expected.add("k3", "");
        expected.add("k4", "v4");

        HttpQueryParams actual = HttpQueryParams.parse("k1=&k2=v2&k3&k4=v4");

        assertEquals(expected, actual);

        assertEquals("k1=&k2=v2&k3&k4=v4", actual.toEncodedString());
    }

    @Test
    void parseKeysIgnoreCase() {
        String camelCaseKey = "keyName";
        HttpQueryParams queryParams = new HttpQueryParams();
        queryParams.add("foo", "bar");
        queryParams.add(camelCaseKey.toLowerCase(Locale.ROOT), "value");

        assertTrue(queryParams.containsIgnoreCase(camelCaseKey));
    }

    @Test
    void maintainsOrderOnToString() {
        String queryString =
                IntStream.range(0, 100).mapToObj(i -> "k%d=v%d".formatted(i, i)).collect(Collectors.joining("&"));
        HttpQueryParams queryParams = HttpQueryParams.parse(queryString);
        assertEquals(queryString, queryParams.toEncodedString());
        assertEquals(queryString, queryParams.toString());
        assertEquals(queryString, queryParams.immutableCopy().toString());
    }
}
