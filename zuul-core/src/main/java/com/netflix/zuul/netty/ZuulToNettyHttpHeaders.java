/*
 * Copyright 2026 Netflix, Inc.
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
package com.netflix.zuul.netty;

import io.netty.handler.codec.CharSequenceValueConverter;
import io.netty.handler.codec.DateFormatter;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.util.AsciiString;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import lombok.NonNull;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Stores headers copied from Zuul messages for cheap one-pass Netty construction and encoding.
 */
@NullMarked
public class ZuulToNettyHttpHeaders extends HttpHeaders {
    private @Nullable CharSequence[] nameValuePairs;
    private int size;

    /**
     * Creates empty headers with capacity for the expected number of entries.
     */
    public ZuulToNettyHttpHeaders(int initialSize) {
        if (initialSize < 0) {
            throw new IllegalArgumentException("initialSize must be non-negative");
        }

        this.nameValuePairs = new CharSequence[Math.multiplyExact(initialSize, 2)];
    }

    @Override
    public @Nullable String get(String name) {
        CharSequence value = this.firstValue(name);
        return value == null ? null : value.toString();
    }

    @Override
    public @Nullable Integer getInt(CharSequence name) {
        CharSequence value = this.firstValue(name);
        return value == null ? null : CharSequenceValueConverter.INSTANCE.convertToInt(value);
    }

    @Override
    public int getInt(CharSequence name, int defaultValue) {
        CharSequence value = this.firstValue(name);
        return value == null ? defaultValue : CharSequenceValueConverter.INSTANCE.convertToInt(value);
    }

    @Override
    public @Nullable Short getShort(CharSequence name) {
        CharSequence value = this.firstValue(name);
        return value == null ? null : CharSequenceValueConverter.INSTANCE.convertToShort(value);
    }

    @Override
    public short getShort(CharSequence name, short defaultValue) {
        CharSequence value = this.firstValue(name);
        return value == null ? defaultValue : CharSequenceValueConverter.INSTANCE.convertToShort(value);
    }

    @Override
    public @Nullable Long getTimeMillis(CharSequence name) {
        CharSequence value = this.firstValue(name);
        return value == null ? null : CharSequenceValueConverter.INSTANCE.convertToTimeMillis(value);
    }

    @Override
    public long getTimeMillis(CharSequence name, long defaultValue) {
        CharSequence value = this.firstValue(name);
        return value == null ? defaultValue : CharSequenceValueConverter.INSTANCE.convertToTimeMillis(value);
    }

    @Override
    public List<String> getAll(@NonNull String name) {
        List<String> values = new ArrayList<>();

        for (int i = 0; i < this.size(); i++) {
            if (this.nameEquals(this.name(i), name)) {
                values.add(this.value(i).toString());
            }
        }

        return values;
    }

    @Override
    public List<Map.Entry<String, String>> entries() {
        List<Map.Entry<String, String>> result = new ArrayList<>(this.size());

        for (int i = 0; i < this.size(); i++) {
            result.add(new SimpleImmutableEntry<>(
                    this.name(i).toString(), this.value(i).toString()));
        }

        return result;
    }

    @Override
    public boolean contains(String name) {
        return this.firstValue(name) != null;
    }

    @Override
    public Iterator<Map.Entry<String, String>> iterator() {
        return this.entries().iterator();
    }

    @Override
    public Iterator<Map.Entry<CharSequence, CharSequence>> iteratorCharSequence() {
        return new EntryIterator();
    }

    @Override
    public Iterator<CharSequence> valueCharSequenceIterator(CharSequence name) {
        return new ValueIterator(name);
    }

    @Override
    public boolean isEmpty() {
        return this.size == 0;
    }

    @Override
    public int size() {
        return this.size;
    }

    @Override
    public Set<String> names() {
        Set<String> names = new LinkedHashSet<>();

        for (int i = 0; i < this.size(); i++) {
            names.add(this.name(i).toString());
        }

        return names;
    }

    @Override
    public HttpHeaders add(@NonNull String name, @NonNull Object value) {
        CharSequence headerValue = this.asCharSequence(value);
        this.ensureCapacity(this.size + 1);
        this.nameValuePairs[this.size * 2] = name;
        this.nameValuePairs[this.size * 2 + 1] = headerValue;
        this.size++;
        return this;
    }

    @Override
    public HttpHeaders add(@NonNull String name, @NonNull Iterable<?> values) {

        for (Object value : values) {
            if (value == null) {
                break;
            }

            this.add(name, value);
        }

        return this;
    }

    @Override
    public HttpHeaders addInt(CharSequence name, int value) {
        return this.add(name, CharSequenceValueConverter.INSTANCE.convertInt(value));
    }

    @Override
    public HttpHeaders addShort(CharSequence name, short value) {
        return this.add(name, CharSequenceValueConverter.INSTANCE.convertShort(value));
    }

    @Override
    public HttpHeaders set(String name, Object value) {
        CharSequence headerValue = this.asCharSequence(value);
        this.remove(name);
        return this.add(name, headerValue);
    }

    @Override
    public HttpHeaders set(String name, Iterable<?> values) {
        this.remove(name);
        return this.add(name, values);
    }

    @Override
    public HttpHeaders setInt(CharSequence name, int value) {
        return this.set(name, CharSequenceValueConverter.INSTANCE.convertInt(value));
    }

    @Override
    public HttpHeaders setShort(CharSequence name, short value) {
        return this.set(name, CharSequenceValueConverter.INSTANCE.convertShort(value));
    }

    @Override
    public HttpHeaders remove(@NonNull String name) {
        int writeIndex = 0;

        for (int readIndex = 0; readIndex < this.size; readIndex++) {
            if (!this.nameEquals(this.name(readIndex), name)) {
                if (writeIndex != readIndex) {
                    this.nameValuePairs[writeIndex * 2] = this.name(readIndex);
                    this.nameValuePairs[writeIndex * 2 + 1] = this.value(readIndex);
                }

                writeIndex++;
            }
        }

        Arrays.fill(this.nameValuePairs, writeIndex * 2, this.size * 2, null);
        this.size = writeIndex;
        return this;
    }

    @Override
    public HttpHeaders clear() {
        Arrays.fill(this.nameValuePairs, 0, this.size * 2, null);
        this.size = 0;
        return this;
    }

    private @Nullable CharSequence firstValue(@NonNull CharSequence name) {

        for (int i = 0; i < this.size(); i++) {
            if (this.nameEquals(this.name(i), name)) {
                return this.value(i);
            }
        }

        return null;
    }

    private boolean nameEquals(CharSequence left, CharSequence right) {
        return AsciiString.contentEqualsIgnoreCase(left, right);
    }

    private CharSequence name(int index) {
        return Objects.requireNonNull(this.nameValuePairs[index * 2]);
    }

    private CharSequence value(int index) {
        return Objects.requireNonNull(this.nameValuePairs[index * 2 + 1]);
    }

    private CharSequence asCharSequence(Object value) {
        if (value instanceof Date date) {
            return DateFormatter.format(date);
        }

        if (value instanceof Calendar calendar) {
            return DateFormatter.format(calendar.getTime());
        }

        return CharSequenceValueConverter.INSTANCE.convertObject(value);
    }

    private void ensureCapacity(int requiredSize) {
        int requiredLength = Math.multiplyExact(requiredSize, 2);

        if (requiredLength > this.nameValuePairs.length) {
            int newLength = Math.max(requiredLength, Math.max(2, this.nameValuePairs.length * 2));
            this.nameValuePairs = Arrays.copyOf(this.nameValuePairs, newLength);
        }
    }

    private class EntryIterator
            implements Iterator<Map.Entry<CharSequence, CharSequence>>, Map.Entry<CharSequence, CharSequence> {
        private int nextIndex;
        private int currentIndex = -1;

        @Override
        public boolean hasNext() {
            return this.nextIndex < ZuulToNettyHttpHeaders.this.size();
        }

        @Override
        public Map.Entry<CharSequence, CharSequence> next() {
            if (!this.hasNext()) {
                throw new NoSuchElementException();
            }

            this.currentIndex = this.nextIndex++;
            return this;
        }

        @Override
        public CharSequence getKey() {
            this.checkCurrent();
            return ZuulToNettyHttpHeaders.this.name(this.currentIndex);
        }

        @Override
        public CharSequence getValue() {
            this.checkCurrent();
            return ZuulToNettyHttpHeaders.this.value(this.currentIndex);
        }

        @Override
        public CharSequence setValue(@NonNull CharSequence value) {
            this.checkCurrent();
            CharSequence previous = ZuulToNettyHttpHeaders.this.value(this.currentIndex);
            ZuulToNettyHttpHeaders.this.nameValuePairs[this.currentIndex * 2 + 1] = value;
            return previous;
        }

        private void checkCurrent() {
            if (this.currentIndex < 0) {
                throw new IllegalStateException("next() has not been called");
            }
        }
    }

    private class ValueIterator implements Iterator<CharSequence> {
        private final CharSequence name;
        private int nextIndex;

        private ValueIterator(@NonNull CharSequence name) {
            this.name = name;
            this.nextIndex = this.findNext(0);
        }

        @Override
        public boolean hasNext() {
            return this.nextIndex >= 0;
        }

        @Override
        public CharSequence next() {
            if (!this.hasNext()) {
                throw new NoSuchElementException();
            }

            int currentIndex = this.nextIndex;
            this.nextIndex = this.findNext(currentIndex + 1);
            return ZuulToNettyHttpHeaders.this.value(currentIndex);
        }

        private int findNext(int startIndex) {
            for (int i = startIndex; i < ZuulToNettyHttpHeaders.this.size(); i++) {
                if (ZuulToNettyHttpHeaders.this.nameEquals(ZuulToNettyHttpHeaders.this.name(i), this.name)) {
                    return i;
                }
            }

            return -1;
        }
    }
}
