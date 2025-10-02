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

package com.netflix.zuul;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class AttrsTest {
    @Test
    void keysAreUnique() {
        Attrs attrs = Attrs.newInstance();
        Attrs.Key<String> key1 = Attrs.newKey("foo");
        key1.put(attrs, "bar");
        Attrs.Key<String> key2 = Attrs.newKey("foo");
        key2.put(attrs, "baz");

        assertThat(attrs.keySet()).containsExactlyInAnyOrder(key1, key2);
    }

    @Test
    void newKeyFailsOnNull() {
        assertThatThrownBy(() -> Attrs.newKey(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void attrsPutFailsOnNull() {
        Attrs attrs = Attrs.newInstance();
        Attrs.Key<String> key = Attrs.newKey("foo");

        assertThatThrownBy(() -> key.put(attrs, null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void attrsPutReplacesOld() {
        Attrs attrs = Attrs.newInstance();
        Attrs.Key<String> key = Attrs.newKey("foo");
        key.put(attrs, "bar");
        key.put(attrs, "baz");

        assertThat(key.get(attrs)).isEqualTo("baz");
        assertThat(attrs.keySet()).containsExactly(key);
    }

    @Test
    void getReturnsNull() {
        Attrs attrs = Attrs.newInstance();
        Attrs.Key<String> key = Attrs.newKey("foo");

        assertThat(key.get(attrs)).isNull();
    }

    @Test
    void getOrDefault_picksDefault() {
        Attrs attrs = Attrs.newInstance();
        Attrs.Key<String> key = Attrs.newKey("foo");

        assertThat(key.getOrDefault(attrs, "bar")).isEqualTo("bar");
    }

    @Test
    void getOrDefault_failsOnNullDefault() {
        Attrs attrs = Attrs.newInstance();
        Attrs.Key<String> key = Attrs.newKey("foo");
        key.put(attrs, "bar");

        assertThatThrownBy(() -> key.getOrDefault(attrs, null)).isInstanceOf(NullPointerException.class);
    }
}
