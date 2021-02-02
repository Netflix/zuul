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

import com.google.common.annotations.VisibleForTesting;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import javax.annotation.Nullable;

/**
 * A heterogeneous map of attributes.
 *
 * <p>Implementation Note: this class is not a proper Map or Collection, in order to encourage callers
 * to refer to the keys by their literal name.   In the past, finding where a Key was used was difficult,
 * so this class is somewhat of an experiment to try to make tracking down usage easier.  If it becomes
 * too onerous to use this, consider making this class extend AbstractMap.
 */
public final class Attrs {

    final Map<Key<?>, Object> storage = new IdentityHashMap<>();

    public static <T> Key<T> newKey(String keyName) {
        return new Key<>(keyName);
    }

    public static final class Key<T> {

        private final String name;

        /**
         * Returns the value in the attributes, or {@code null} if absent.
         */
        @Nullable
        @SuppressWarnings("unchecked")
        public T get(Attrs attrs) {
            Objects.requireNonNull(attrs, "attrs");
            return (T) attrs.storage.get(this);
        }

        /**
         * Returns the value in the attributes or {@code defaultValue} if absent.
         * @throws NullPointerException if defaultValue is null.
         */
        @SuppressWarnings("unchecked")
        public T getOrDefault(Attrs attrs, T defaultValue) {
            Objects.requireNonNull(attrs, "attrs");
            Objects.requireNonNull(defaultValue, "defaultValue");
            T result = (T) attrs.storage.get(this);
            if (result != null) {
                return result;
            }
            return defaultValue;
        }

        public void put(Attrs attrs, T value) {
            Objects.requireNonNull(attrs, "attrs");
            Objects.requireNonNull(value);
            attrs.storage.put(this, value);
        }

        public String name() {
            return name;
        }

        private Key(String name) {
            this.name = Objects.requireNonNull(name);
        }

        @Override
        public String toString() {
            return "Key{" + name + '}';
        }
    }

    private Attrs() {}

    public static Attrs newInstance() {
        return new Attrs();
    }

    public Set<Key<?>> keySet() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(storage.keySet()));
    }

    public void forEach(BiConsumer<? super Key<?>, Object> consumer) {
        storage.forEach(consumer);
    }

    public int size() {
        return storage.size();
    }

    @Override
    public String toString() {
        return "Attrs{" + storage + '}';
    }

    @Override
    @VisibleForTesting
    public boolean equals(Object other) {
        if (!(other instanceof Attrs)) {
            return false;
        }
        Attrs that = (Attrs) other;
        return Objects.equals(this.storage, that.storage);
    }

    @Override
    @VisibleForTesting
    public int hashCode() {
        return Objects.hash(storage);
    }
}
