/*
 * Copyright 2018 Netflix, Inc.
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

import static java.util.Objects.requireNonNull;

import com.google.common.annotations.VisibleForTesting;
import com.netflix.spectator.api.Counter;
import com.netflix.spectator.api.Spectator;
import com.netflix.zuul.exception.ZuulException;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import javax.annotation.Nullable;

/**
 * An abstraction over a collection of http headers. Allows multiple headers with same name, and header names are
 * compared case insensitively.
 *
 * There are methods for getting and setting headers by String AND by HeaderName. When possible, use the HeaderName
 * variants and cache the HeaderName instances somewhere, to avoid case-insensitive String comparisons.
 */
public final class Headers {
    private static final int ABSENT = -1;

    private final List<String> originalNames;
    private final List<String> names;
    private final List<String> values;

    private static final Counter invalidHeaderCounter = Spectator.globalRegistry().counter("zuul.header.invalid.char");

    public static Headers copyOf(Headers original) {
        return new Headers(requireNonNull(original, "original"));
    }

    public Headers() {
        originalNames = new ArrayList<>();
        names = new ArrayList<>();
        values = new ArrayList<>();
    }

    private Headers(Headers original) {
        originalNames = new ArrayList<>(original.originalNames);
        names = new ArrayList<>(original.names);
        values = new ArrayList<>(original.values);
    }

    /**
     * Get the first value found for this key even if there are multiple. If none, then
     * return {@code null}.
     */
    @Nullable
    public String getFirst(String headerName) {
        String normalName = HeaderName.normalize(requireNonNull(headerName, "headerName"));
        return getFirstNormal(normalName);
    }

    /**
     * Get the first value found for this key even if there are multiple. If none, then
     * return {@code null}.
     */
    @Nullable
    public String getFirst(HeaderName headerName) {
        String normalName = requireNonNull(headerName, "headerName").getNormalised();
        return getFirstNormal(normalName);
    }

    @Nullable
    private String getFirstNormal(String name) {
        for (int i = 0; i < size(); i++) {
            if (name(i).equals(name)) {
                return value(i);
            }
        }
        return null;
    }

    /**
     * Get the first value found for this key even if there are multiple. If none, then
     * return the specified defaultValue.
     */
    public String getFirst(String headerName, String defaultValue) {
        requireNonNull(defaultValue, "defaultValue");
        String value = getFirst(headerName);
        if (value != null) {
            return value;
        }
        return defaultValue;
    }

    /**
     * Get the first value found for this key even if there are multiple. If none, then
     * return the specified defaultValue.
     */
    public String getFirst(HeaderName headerName, String defaultValue) {
        requireNonNull(defaultValue, "defaultValue");
        String value = getFirst(headerName);
        if (value != null) {
            return value;
        }
        return defaultValue;
    }

    /**
     * Returns all header values associated with the name.
     */
    public List<String> getAll(String headerName) {
        String normalName = HeaderName.normalize(requireNonNull(headerName, "headerName"));
        return getAllNormal(normalName);
    }

    /**
     * Returns all header values associated with the name.
     */
    public List<String> getAll(HeaderName headerName) {
        String normalName = requireNonNull(headerName, "headerName").getNormalised();
        return getAllNormal(normalName);
    }

    private List<String> getAllNormal(String normalName) {
        List<String> results = null;
        for (int i = 0; i < size(); i++) {
            if (name(i).equals(normalName)) {
                if (results == null) {
                    results = new ArrayList<>(1);
                }
                results.add(value(i));
            }
        }
        if (results == null) {
            return Collections.emptyList();
        } else {
            return Collections.unmodifiableList(results);
        }
    }

    /**
     * Iterates over the header entries with the given consumer.  The first argument will be the normalised header
     * name as returned by {@link HeaderName#getNormalised()}.  The second argument will be the value.  Do not modify
     * the headers during iteration.
     */
    public void forEachNormalised(BiConsumer<? super String, ? super String> entryConsumer) {
        for (int i = 0; i < size(); i++) {
            entryConsumer.accept(name(i), value(i));
        }
    }

    /**
     * Replace any/all entries with this key, with this single entry.
     *
     * If value is {@code null}, then not added, but any existing header of same name is removed.
     */
    public void set(String headerName, @Nullable String value) {
        String normalName = HeaderName.normalize(requireNonNull(headerName, "headerName"));
        setNormal(headerName, normalName, value);
    }

    /**
     * Replace any/all entries with this key, with this single entry.
     *
     * If value is {@code null}, then not added, but any existing header of same name is removed.
     */
    public void set(HeaderName headerName, String value) {
        String normalName = requireNonNull(headerName, "headerName").getNormalised();
        setNormal(headerName.getName(), normalName, value);
    }

    /**
     * Replace any/all entries with this key, with this single entry and validate.
     *
     * If value is {@code null}, then not added, but any existing header of same name is removed.
     *
     * @throws ZuulException on invalid name or value
     */
    public void setAndValidate(String headerName, @Nullable String value) {
        String normalName = HeaderName.normalize(requireNonNull(headerName, "headerName"));
        setNormal(validateField(headerName), validateField(normalName), validateField(value));
    }

    /**
     * Replace any/all entries with this key, with this single entry if the key and entry are valid.
     *
     * If value is {@code null}, then not added, but any existing header of same name is removed.
     */
    public void setIfValid(HeaderName headerName, String value) {
        requireNonNull(headerName, "headerName");
        if (isValid(headerName.getName()) && isValid(value)) {
            String normalName = headerName.getNormalised();
            setNormal(headerName.getName(), normalName, value);
        }
    }

    /**
     * Replace any/all entries with this key, with this single entry if the key and entry are valid.
     *
     * If value is {@code null}, then not added, but any existing header of same name is removed.
     */
    public void setIfValid(String headerName, @Nullable String value) {
        requireNonNull(headerName, "headerName");
        if (isValid(headerName) && isValid(value)) {
            String normalName = HeaderName.normalize(headerName);
            setNormal(headerName, normalName, value);
        }
    }

    /**
     * Replace any/all entries with this key, with this single entry and validate.
     *
     * If value is {@code null}, then not added, but any existing header of same name is removed.
     *
     * @throws ZuulException on invalid name or value
     */
    public void setAndValidate(HeaderName headerName, String value) {
        String normalName = requireNonNull(headerName, "headerName").getNormalised();
        setNormal(validateField(headerName.getName()), validateField(normalName), validateField(value));
    }

    private void setNormal(String originalName, String normalName, @Nullable String value) {
        int i = findNormal(normalName);
        if (i == ABSENT) {
            if (value != null) {
                addNormal(originalName, normalName, value);
            }
            return;
        }
        if (value != null) {
            value(i, value);
            originalName(i, originalName);
            i++;
        }
        clearMatchingStartingAt(i, normalName, /* removed= */ null);
    }

    /**
     * Returns the first index entry that has a matching name.  Returns {@link #ABSENT} if absent.
     */
    private int findNormal(String normalName) {
        for (int i = 0; i < size(); i++) {
            if (name(i).equals(normalName)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Removes entries that match the name, starting at the given index.
     */
    private void clearMatchingStartingAt(int i, String normalName, @Nullable Collection<? super String> removed) {
        // This works by having separate read and write indexes, that iterate along the list.
        // Values that don't match are moved to the front, leaving garbage values in place.
        // At the end, all values at and values are garbage and are removed.
        int w = i;
        for (int r = i; r < size(); r++) {
            if (!name(r).equals(normalName)) {
                originalName(w, originalName(r));
                name(w, name(r));
                value(w, value(r));
                w++;
            } else if (removed != null) {
                removed.add(value(r));
            }
        }
        truncate(w);
    }

    /**
     * Adds the name and value to the headers, except if the name is already present.  Unlike
     * {@link #set(String, String)}, this method does not accept a {@code null} value.
     *
     * @return if the value was successfully added.
     */
    public boolean setIfAbsent(String headerName, String value) {
        requireNonNull(value, "value");
        String normalName = HeaderName.normalize(requireNonNull(headerName, "headerName"));
        return setIfAbsentNormal(headerName, normalName, value);
    }

    /**
     * Adds the name and value to the headers, except if the name is already present.  Unlike
     * {@link #set(HeaderName, String)}, this method does not accept a {@code null} value.
     *
     * @return if the value was successfully added.
     */
    public boolean setIfAbsent(HeaderName headerName, String value) {
        requireNonNull(value, "value");
        String normalName = requireNonNull(headerName, "headerName").getNormalised();
        return setIfAbsentNormal(headerName.getName(), normalName, value);
    }

    private boolean setIfAbsentNormal(String originalName, String normalName, String value) {
        int i = findNormal(normalName);
        if (i != ABSENT) {
            return false;
        }
        addNormal(originalName, normalName, value);
        return true;
    }

    /**
     * Validates and adds the name and value to the headers, except if the name is already present.  Unlike
     * {@link #set(String, String)}, this method does not accept a {@code null} value.
     *
     * @return if the value was successfully added.
     */
    public boolean setIfAbsentAndValid(String headerName, String value) {
        requireNonNull(value, "value");
        requireNonNull(headerName, "headerName");
        if (isValid(headerName) && isValid(value)) {
            String normalName = HeaderName.normalize(headerName);
            return setIfAbsentNormal(headerName, normalName, value);
        }
        return false;
    }

    /**
     * Validates and adds the name and value to the headers, except if the name is already present.  Unlike
     * {@link #set(HeaderName, String)}, this method does not accept a {@code null} value.
     *
     * @return if the value was successfully added.
     */
    public boolean setIfAbsentAndValid(HeaderName headerName, String value) {
        requireNonNull(value, "value");
        requireNonNull(headerName, "headerName");
        if (isValid(headerName.getName()) && isValid((value))) {
            String normalName = headerName.getNormalised();
            return setIfAbsentNormal(headerName.getName(), normalName, value);
        }
        return false;
    }

    /**
     * Adds the name and value to the headers.
     */
    public void add(String headerName, String value) {
        String normalName = HeaderName.normalize(requireNonNull(headerName, "headerName"));
        requireNonNull(value, "value");
        addNormal(headerName, normalName, value);
    }

    /**
     * Adds the name and value to the headers.
     */
    public void add(HeaderName headerName, String value) {
        String normalName = requireNonNull(headerName, "headerName").getNormalised();
        requireNonNull(value, "value");
        addNormal(headerName.getName(), normalName, value);
    }

    /**
     * Adds the name and value to the headers and validate.
     *
     * @throws ZuulException on invalid name or value
     */
    public void addAndValidate(String headerName, String value) {
        String normalName = HeaderName.normalize(requireNonNull(headerName, "headerName"));
        requireNonNull(value, "value");
        addNormal(validateField(headerName), validateField(normalName), validateField(value));
    }

    /**
     * Adds the name and value to the headers and validate
     *
     * @throws ZuulException on invalid name or value
     */
    public void addAndValidate(HeaderName headerName, String value) {
        String normalName = requireNonNull(headerName, "headerName").getNormalised();
        requireNonNull(value, "value");
        addNormal(validateField(headerName.getName()), validateField(normalName), validateField(value));
    }

    /**
     * Adds the name and value to the headers if valid
     */
    public void addIfValid(String headerName, String value) {
        requireNonNull(headerName, "headerName");
        requireNonNull(value, "value");
        if (isValid(headerName) && isValid(value)) {
            String normalName = HeaderName.normalize(headerName);
            addNormal(headerName, normalName, value);
        }
    }

    /**
     * Adds the name and value to the headers if valid
     */
    public void addIfValid(HeaderName headerName, String value) {
        requireNonNull(headerName, "headerName");
        requireNonNull(value, "value");
        if (isValid(headerName.getName()) && isValid(value)) {
            String normalName = headerName.getNormalised();
            addNormal(headerName.getName(), normalName, value);
        }
    }

    /**
     * Adds all the headers into this headers object.
     */
    public void putAll(Headers headers) {
        for (int i = 0; i < headers.size(); i++) {
            addNormal(headers.originalName(i), headers.name(i), headers.value(i));
        }
    }

    /**
     * Removes the header entries that match the given header name, and returns them as a list.
     */
    public List<String> remove(String headerName) {
        String normalName = HeaderName.normalize(requireNonNull(headerName, "headerName"));
        return removeNormal(normalName);
    }

    /**
     * Removes the header entries that match the given header name, and returns them as a list.
     */
    public List<String> remove(HeaderName headerName) {
        String normalName = requireNonNull(headerName, "headerName").getNormalised();
        return removeNormal(normalName);
    }

    private List<String> removeNormal(String normalName) {
        List<String> removed = new ArrayList<>();
        clearMatchingStartingAt(0, normalName, removed);
        return Collections.unmodifiableList(removed);
    }

    /**
     * Removes all header entries that match the given predicate.   Do not access the header list from inside the
     * {@link Predicate#test} body.
     *
     * @return if any elements were removed.
     */
    public boolean removeIf(Predicate<? super Map.Entry<HeaderName, String>> filter) {
        requireNonNull(filter, "filter");
        boolean removed = false;
        int w = 0;
        for (int r = 0; r < size(); r++) {
            if (filter.test(new SimpleImmutableEntry<>(new HeaderName(originalName(r), name(r)), value(r)))) {
                removed = true;
            } else {
                originalName(w, originalName(r));
                name(w, name(r));
                value(w, value(r));
                w++;
            }
        }
        truncate(w);
        return removed;
    }

    /**
     * Returns the collection of headers.
     */
    public Collection<Header> entries() {
        List<Header> entries = new ArrayList<>(size());
        for (int i = 0; i < size(); i++) {
            entries.add(new Header(new HeaderName(originalName(i), name(i)), value(i)));
        }
        return Collections.unmodifiableList(entries);
    }

    /**
     * Returns a set of header names found in this headers object.  If there are duplicate header names, the first
     * one present takes precedence.
     */
    public Set<HeaderName> keySet() {
        Set<HeaderName> headerNames = new LinkedHashSet<>(size());
        for (int i = 0 ; i < size(); i++) {
            HeaderName headerName = new HeaderName(originalName(i), name(i));
            // We actually do need to check contains before adding to the set because the original name may change.
            // In this case, the first name wins.
            if (!headerNames.contains(headerName)) {
                headerNames.add(headerName);
            }
        }
        return Collections.unmodifiableSet(headerNames);
    }

    /**
     * Returns if there is a header entry that matches the given name.
     */
    public boolean contains(String headerName) {
        String normalName = HeaderName.normalize(requireNonNull(headerName, "headerName"));
        return findNormal(normalName) != ABSENT;
    }

    /**
     * Returns if there is a header entry that matches the given name.
     */
    public boolean contains(HeaderName headerName) {
        String normalName = requireNonNull(headerName, "headerName").getNormalised();
        return findNormal(normalName) != ABSENT;
    }

    /**
     * Returns if there is a header entry that matches the given name and value.
     */
    public boolean contains(String headerName, String value) {
        String normalName = HeaderName.normalize(requireNonNull(headerName, "headerName"));
        requireNonNull(value, "value");
        return containsNormal(normalName, value);
    }

    /**
     * Returns if there is a header entry that matches the given name and value.
     */
    public boolean contains(HeaderName headerName, String value) {
        String normalName = requireNonNull(headerName, "headerName").getNormalised();
        requireNonNull(value, "value");
        return containsNormal(normalName, value);
    }

    private boolean containsNormal(String normalName, String value) {
        for (int i = 0; i < size(); i++) {
            if (name(i).equals(normalName) && value(i).equals(value)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns the number of header entries.
     */
    public int size() {
        return names.size();
    }

    /**
     * This method should only be used for testing, as it is expensive to call.
     */
    @Override
    @VisibleForTesting
    public int hashCode() {
        return asMap().hashCode();
    }

    /**
     * Equality on headers is not clearly defined, but this method makes an attempt to do so.   This method should
     * only be used for testing, as it is expensive to call.  Two headers object are considered equal if they have
     * the same, normalized header names, and have the corresponding header values in the same order.
     */
    @Override
    @VisibleForTesting
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (!(obj instanceof Headers)) {
            return false;
        }
        Headers other = (Headers) obj;

        return asMap().equals(other.asMap());
    }

    private Map<String, List<String>> asMap() {
        Map<String, List<String>> map = new LinkedHashMap<>(size());
        for (int i = 0; i < size(); i++) {
            map.computeIfAbsent(name(i), k -> new ArrayList<>(1)).add(value(i));
        }
        // Return an unwrapped collection since it should not ever be returned on the API.
        return map;
    }

    /**
     * This is used for debugging.  It is fairly expensive to construct, so don't call it on a hot path.
     */
    @Override
    public String toString() {
        return asMap().toString();
    }

    private String originalName(int i) {
        return originalNames.get(i);
    }

    private void originalName(int i, String originalName) {
        originalNames.set(i, originalName);
    }

    private String name(int i) {
        return names.get(i);
    }

    private void name(int i, String name) {
        names.set(i, name);
    }

    private String value(int i) {
        return values.get(i);
    }

    private void value(int i, String val) {
        values.set(i, val);
    }

    private void addNormal(String originalName, String normalName, String value) {
        originalNames.add(originalName);
        names.add(normalName);
        values.add(value);
    }

    /**
     * Removes all elements at and after the given index.
     */
    private void truncate(int i) {
        for (int k = size() - 1; k >= i; k--) {
            originalNames.remove(k);
            names.remove(k);
            values.remove(k);
        }
    }

    /**
     * Checks if the given value is compliant with our RFC 7230 based check
     */
    private static boolean isValid(@Nullable String value) {
        if (value == null || findInvalid(value) == ABSENT) {
            return true;
        }
        invalidHeaderCounter.increment();
        return false;
    }

    /**
     * Checks if the input value is compliant with our RFC 7230 based check
     * Returns input value if valid, raises ZuulException otherwise
     */
    private static String validateField(@Nullable String value) {
        if (value != null) {
            int pos = findInvalid(value);
            if (pos != ABSENT) {
                invalidHeaderCounter.increment();
                throw new ZuulException("Invalid header field: char " + (int) value.charAt(pos) + " in string " + value
                        + " does not comply with RFC 7230");
            }
        }
        return value;
    }

    /**
     * Validated the input value based on RFC 7230 but more lenient.
     * Currently, only ASCII control characters are considered invalid.
     *
     * Returns the index of first invalid character. Returns {@link #ABSENT} if absent.
     */
    private static int findInvalid(String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            // ASCII non-control characters, per RFC 7230 but slightly more lenient
            if (c < 31 || c == 127) {
                return i;
            }
        }
        return ABSENT;
    }
}
