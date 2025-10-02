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

        assertThat(qp.toEncodedString()).isEqualTo("k1=v1&k1=v2&k2=v3");
    }

    @Test
    void testDuplicateKv() {
        HttpQueryParams qp = new HttpQueryParams();
        qp.add("k1", "v1");
        qp.add("k1", "v1");
        qp.add("k1", "v1");

        assertThat(qp.toEncodedString()).isEqualTo("k1=v1&k1=v1&k1=v1");
        assertThat(qp.get("k1")).isEqualTo(List.of("v1", "v1", "v1"));
    }

    @Test
    void testToEncodedString() {
        HttpQueryParams qp = new HttpQueryParams();
        qp.add("k'1", "v1&");
        assertThat(qp.toEncodedString()).isEqualTo("k%271=v1%26");

        qp = new HttpQueryParams();
        qp.add("k+", "\n");
        assertThat(qp.toEncodedString()).isEqualTo("k%2B=%0A");
    }

    @Test
    void testToString() {
        HttpQueryParams qp = new HttpQueryParams();
        qp.add("k'1", "v1&");
        assertThat(qp.toString()).isEqualTo("k'1=v1&");

        qp = new HttpQueryParams();
        qp.add("k+", "\n");
        assertThat(qp.toString()).isEqualTo("k+=\n");
    }

    @Test
    void testEquals() {
        HttpQueryParams qp1 = new HttpQueryParams();
        qp1.add("k1", "v1");
        qp1.add("k2", "v2");
        HttpQueryParams qp2 = new HttpQueryParams();
        qp2.add("k1", "v1");
        qp2.add("k2", "v2");

        assertThat(qp2).isEqualTo(qp1);
    }

    @Test
    void parseKeysWithoutValues() {
        HttpQueryParams expected = new HttpQueryParams();
        expected.add("k1", "");
        expected.add("k2", "v2");
        expected.add("k3", "");

        HttpQueryParams actual = HttpQueryParams.parse("k1=&k2=v2&k3=");

        assertThat(actual).isEqualTo(expected);

        assertThat(actual.toEncodedString()).isEqualTo("k1=&k2=v2&k3=");
    }

    @Test
    void parseKeyWithoutValueEquals() {
        HttpQueryParams expected = new HttpQueryParams();
        expected.add("k1", "");

        HttpQueryParams actual = HttpQueryParams.parse("k1=");

        assertThat(actual).isEqualTo(expected);

        assertThat(actual.toEncodedString()).isEqualTo("k1=");
    }

    @Test
    void parseKeyWithoutValue() {
        HttpQueryParams expected = new HttpQueryParams();
        expected.add("k1", "");

        HttpQueryParams actual = HttpQueryParams.parse("k1");

        assertThat(actual).isEqualTo(expected);

        assertThat(actual.toEncodedString()).isEqualTo("k1");
    }

    @Test
    void parseKeyWithoutValueShort() {
        HttpQueryParams expected = new HttpQueryParams();
        expected.add("=", "");

        HttpQueryParams actual = HttpQueryParams.parse("=");

        assertThat(actual).isEqualTo(expected);

        assertThat(actual.toEncodedString()).isEqualTo("%3D");
    }

    @Test
    void parseKeysWithoutValuesMixedTrailers() {
        HttpQueryParams expected = new HttpQueryParams();
        expected.add("k1", "");
        expected.add("k2", "v2");
        expected.add("k3", "");
        expected.add("k4", "v4");

        HttpQueryParams actual = HttpQueryParams.parse("k1=&k2=v2&k3&k4=v4");

        assertThat(actual).isEqualTo(expected);

        assertThat(actual.toEncodedString()).isEqualTo("k1=&k2=v2&k3&k4=v4");
    }

    @Test
    void parseKeysIgnoreCase() {
        String camelCaseKey = "keyName";
        HttpQueryParams queryParams = new HttpQueryParams();
        queryParams.add("foo", "bar");
        queryParams.add(camelCaseKey.toLowerCase(Locale.ROOT), "value");

        assertThat(queryParams.containsIgnoreCase(camelCaseKey)).isTrue();
    }

    @Test
    void maintainsOrderOnToString() {
        String queryString =
                IntStream.range(0, 100).mapToObj(i -> "k%d=v%d".formatted(i, i)).collect(Collectors.joining("&"));
        HttpQueryParams queryParams = HttpQueryParams.parse(queryString);
        assertThat(queryParams.toEncodedString()).isEqualTo(queryString);
        assertThat(queryParams.toString()).isEqualTo(queryString);
        assertThat(queryParams.immutableCopy().toString()).isEqualTo(queryString);
    }
}
