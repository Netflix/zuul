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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SessionContextTest {

    @Test
    void testBoolean() {
        SessionContext context = new SessionContext();
        assertThat(context.getBoolean("boolean_test")).isEqualTo(false);
        assertThat(context.getBoolean("boolean_test", true)).isEqualTo(true);
    }

    @Test
    void keysAreUnique() {
        SessionContext context = new SessionContext();
        SessionContext.Key<String> key1 = SessionContext.newKey("foo");
        context.put(key1, "bar");
        SessionContext.Key<String> key2 = SessionContext.newKey("foo");
        context.put(key2, "baz");

        assertThat(context.keys()).containsExactlyInAnyOrder(key1, key2);
    }

    @Test
    void newKeyFailsOnNull() {
        assertThatThrownBy(() -> SessionContext.newKey(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void putFailsOnNull() {
        SessionContext context = new SessionContext();
        SessionContext.Key<String> key = SessionContext.newKey("foo");

        assertThatThrownBy(() -> context.put(key, null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void putReplacesOld() {
        SessionContext context = new SessionContext();
        SessionContext.Key<String> key = SessionContext.newKey("foo");
        context.put(key, "bar");
        context.put(key, "baz");

        assertThat(context.get(key)).isEqualTo("baz");
        assertThat(context.keys()).containsExactly(key);
    }

    @Test
    void getReturnsNull() {
        SessionContext context = new SessionContext();
        SessionContext.Key<String> key = SessionContext.newKey("foo");

        assertThat(context.get(key)).isNull();
    }

    @Test
    void getOrDefault_picksDefault() {
        SessionContext context = new SessionContext();
        SessionContext.Key<String> key = SessionContext.newKey("foo");

        assertThat(context.getOrDefault(key, "bar")).isEqualTo("bar");
    }

    @Test
    void getOrDefault_failsOnNullDefault() {
        SessionContext context = new SessionContext();
        SessionContext.Key<String> key = SessionContext.newKey("foo");
        context.put(key, "bar");

        assertThatThrownBy(() -> context.getOrDefault(key, null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void getUsesDefaultValueSupplier() {
        SessionContext context = new SessionContext();
        SessionContext.Key<String> key = SessionContext.newKey("foo", () -> "bar");
        assertThat(context.get(key)).isEqualTo("bar");
    }

    @Test
    void getOrDefaultUsesDefaultValueSupplier() {
        SessionContext context = new SessionContext();
        SessionContext.Key<String> key = SessionContext.newKey("foo", () -> "bar");
        assertThat(context.getOrDefault(key)).isEqualTo("bar");
    }

    @Test
    void getOrDefaultUsesDefaultValueSupplierFailsWithout() {
        SessionContext context = new SessionContext();
        SessionContext.Key<String> key = SessionContext.newKey("foo");
        assertThatThrownBy(() -> context.getOrDefault(key)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void remove() {
        SessionContext context = new SessionContext();
        SessionContext.Key<String> key = SessionContext.newKey("foo");
        context.put(key, "bar");

        assertThat(context.get(key)).isEqualTo("bar");

        String val = context.remove(key);
        assertThat(context.get(key)).isNull();
        assertThat(val).isEqualTo("bar");
    }

    @Test
    void containsKey() {
        SessionContext context = new SessionContext();
        SessionContext.Key<String> key = SessionContext.newKey("foo");
        context.put(key, "bar");

        assertThat(context.containsKey(key)).isTrue();

        String val = context.remove(key);
        assertThat(val).isEqualTo("bar");

        assertThat(context.containsKey(key)).isFalse();
    }

    @Test
    void putAndGetStringKey() {
        SessionContext context = new SessionContext();
        assertThat(context.get("foo")).isNull();

        context.put("foo", "bar");
        assertThat(context.get("foo")).isEqualTo("bar");
    }

    @Test
    void putStringKeyReturnsPreviousValue() {
        SessionContext context = new SessionContext();
        assertThat(context.put("foo", "bar")).isNull();
        assertThat(context.put("foo", "baz")).isEqualTo("bar");
        assertThat(context.get("foo")).isEqualTo("baz");
    }

    @Test
    void getOrDefaultStringKey() {
        SessionContext context = new SessionContext();
        assertThat(context.getOrDefault("foo", "fallback")).isEqualTo("fallback");

        context.put("foo", "bar");
        assertThat(context.getOrDefault("foo", "fallback")).isEqualTo("bar");
    }

    @Test
    void containsStringKey() {
        SessionContext context = new SessionContext();
        assertThat(context.containsKey("foo")).isFalse();

        context.put("foo", "bar");
        assertThat(context.containsKey("foo")).isTrue();
    }

    @Test
    void removeStringKey() {
        SessionContext context = new SessionContext();
        context.put("foo", "bar");

        assertThat(context.remove("foo")).isEqualTo("bar");
        assertThat(context.containsKey("foo")).isFalse();
    }

    @Test
    void removeStringKeyOnlyWhenValueMatches() {
        SessionContext context = new SessionContext();
        context.put("foo", "bar");

        assertThat(context.remove("foo", "other")).isFalse();
        assertThat(context.get("foo")).isEqualTo("bar");

        assertThat(context.remove("foo", "bar")).isTrue();
        assertThat(context.containsKey("foo")).isFalse();
    }

    @Test
    void sizeReflectsStringKeys() {
        SessionContext context = new SessionContext();
        int initialSize = context.size();

        context.put("foo", "bar");
        assertThat(context.size()).isEqualTo(initialSize + 1);

        SessionContext.Key<String> key = SessionContext.newKey("foo");
        context.put(key, "yo");
        assertThat(context.size()).isEqualTo(initialSize + 2);

        context.remove("foo");
        assertThat(context.size()).isEqualTo(initialSize + 1);

        context.remove(key);
        assertThat(context.size()).isEqualTo(initialSize);
    }

    @Test
    void cloneCopiesEntriesAndFlags() {
        SessionContext context = new SessionContext();
        SessionContext.Key<String> key = SessionContext.newKey("typed");
        context.put("stringKey", "stringValue");
        context.put(key, "typedValue");
        context.setInBrownoutMode("overloaded");
        context.stopFilterProcessing();
        context.setShouldSendErrorResponse(true);
        context.setErrorResponseSent(true);
        context.cancel();

        SessionContext copy = context.clone();

        assertThat(copy).isNotSameAs(context);
        assertThat(copy.get("stringKey")).isEqualTo("stringValue");
        assertThat(copy.get(key)).isEqualTo("typedValue");
        assertThat(copy.isInBrownoutMode()).isTrue();
        assertThat(copy.getBrownoutReason()).isEqualTo("overloaded");
        assertThat(copy.shouldStopFilterProcessing()).isTrue();
        assertThat(copy.shouldSendErrorResponse()).isTrue();
        assertThat(copy.errorResponseSent()).isTrue();
        assertThat(copy.isCancelled()).isTrue();
    }

    @Test
    void setInBrownoutModeWithReason() {
        SessionContext context = new SessionContext();
        assertThat(context.getBrownoutReason()).isNull();
        context.setInBrownoutMode("High CPU usage");

        assertThat(context.isInBrownoutMode()).isTrue();
        assertThat(context.getBrownoutReason()).isEqualTo("High CPU usage");
    }
}
