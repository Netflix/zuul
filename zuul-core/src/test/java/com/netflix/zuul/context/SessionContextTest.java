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
package com.netflix.zuul.context;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

import com.google.common.truth.Truth;
import com.netflix.zuul.context.SessionContext.Key;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class SessionContextTest {

    @Test
    public void testBoolean() {
        SessionContext context = new SessionContext();
        assertEquals(context.getBoolean("boolean_test"), Boolean.FALSE);
        assertEquals(context.getBoolean("boolean_test", true), true);
    }

    @Test
    public void keysAreUnique() {
        SessionContext context = new SessionContext();
        Key<String> key1 = SessionContext.newKey("foo");
        context.put(key1, "bar");
        Key<String> key2 = SessionContext.newKey("foo");
        context.put(key2, "baz");

        Truth.assertThat(context.keys()).containsExactly(key1, key2);
    }

    @Test
    public void newKeyFailsOnNull() {
        assertThrows(NullPointerException.class, () -> SessionContext.newKey(null));
    }

    @Test
    public void putFailsOnNull() {
        SessionContext context = new SessionContext();
        Key<String> key = SessionContext.newKey("foo");

        assertThrows(NullPointerException.class, () -> context.put(key, null));
    }

    @Test
    public void putReplacesOld() {
        SessionContext context = new SessionContext();
        Key<String> key = SessionContext.newKey("foo");
        context.put(key, "bar");
        context.put(key, "baz");

        assertEquals("baz", context.get(key));
        Truth.assertThat(context.keys()).containsExactly(key);
    }

    @Test
    public void getReturnsNull() {
        SessionContext context = new SessionContext();
        Key<String> key = SessionContext.newKey("foo");

        assertNull(context.get(key));
    }

    @Test
    public void getOrDefault_picksDefault() {
        SessionContext context = new SessionContext();
        Key<String> key = SessionContext.newKey("foo");

        assertEquals("bar", context.getOrDefault(key, "bar"));
    }

    @Test
    public void getOrDefault_failsOnNullDefault() {
        SessionContext context = new SessionContext();
        Key<String> key = SessionContext.newKey("foo");
        context.put(key, "bar");

        assertThrows(NullPointerException.class, () -> context.getOrDefault(key, null));
    }

    @Test
    public void remove() {
        SessionContext context = new SessionContext();
        Key<String> key = SessionContext.newKey("foo");
        context.put(key, "bar");

        Truth.assertThat(context.get(key)).isEqualTo("bar");

        String val = context.remove(key);
        Truth.assertThat(context.get(key)).isNull();
        Truth.assertThat(val).isEqualTo("bar");
    }
}
