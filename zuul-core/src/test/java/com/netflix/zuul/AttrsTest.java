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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

import com.google.common.truth.Truth;
import com.netflix.zuul.Attrs.Key;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class AttrsTest {
    @Test
    public void keysAreUnique() {
        Attrs attrs = Attrs.newInstance();
        Key<String> key1 = Attrs.newKey("foo");
        key1.put(attrs, "bar");
        Key<String> key2 = Attrs.newKey("foo");
        key2.put(attrs, "baz");

        Truth.assertThat(attrs.keySet()).containsExactly(key1, key2);
    }

    @Test
    public void newKeyFailsOnNull() {
        assertThrows(NullPointerException.class, () -> Attrs.newKey(null));
    }

    @Test
    public void attrsPutFailsOnNull() {
        Attrs attrs = Attrs.newInstance();
        Key<String> key = Attrs.newKey("foo");

        assertThrows(NullPointerException.class, () -> key.put(attrs, null));
    }

    @Test
    public void attrsPutReplacesOld() {
        Attrs attrs = Attrs.newInstance();
        Key<String> key = Attrs.newKey("foo");
        key.put(attrs, "bar");
        key.put(attrs, "baz");

        assertEquals("baz", key.get(attrs));
        Truth.assertThat(attrs.keySet()).containsExactly(key);
    }

    @Test
    public void getReturnsNull() {
        Attrs attrs = Attrs.newInstance();
        Key<String> key = Attrs.newKey("foo");

        assertNull(key.get(attrs));
    }

    @Test
    public void getOrDefault_picksDefault() {
        Attrs attrs = Attrs.newInstance();
        Key<String> key = Attrs.newKey("foo");

        assertEquals("bar", key.getOrDefault(attrs, "bar"));
    }

    @Test
    public void getOrDefault_failsOnNullDefault() {
        Attrs attrs = Attrs.newInstance();
        Key<String> key = Attrs.newKey("foo");
        key.put(attrs, "bar");

        assertThrows(NullPointerException.class, () -> key.getOrDefault(attrs, null));
    }
}
