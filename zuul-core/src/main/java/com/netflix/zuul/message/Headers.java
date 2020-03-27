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

import com.netflix.zuul.message.http.HttpHeaderNames;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

import static com.netflix.zuul.util.HttpUtils.stripMaliciousHeaderChars;
import static java.util.Objects.requireNonNull;

/**
 * An abstraction over a collection of http headers. Allows multiple headers with same name, and header names are
 * compared case insensitively.
 *
 * There are methods for getting and setting headers by String AND by HeaderName. When possible, use the HeaderName
 * variants and cache the HeaderName instances somewhere, to avoid case-insensitive String comparisons.
 *
 * User: michaels@netflix.com
 * Date: 2/20/15
 * Time: 3:13 PM
 */
public final class Headers {
    private final List<HeaderName> names;
    private final List<String> values;

    public static Headers copyOf(Headers original) {
        return new Headers(requireNonNull(original, "original"));
    }

    public Headers() {
        names = new ArrayList<>();
        values = new ArrayList<>();
    }

    private Headers(Headers original) {
        names = new ArrayList<>(original.names);
        values = new ArrayList<>(original.values);
    }

    private HeaderName getHeaderName(String name) {
        return HttpHeaderNames.get(name);
    }

    private boolean set(HeaderName name, String value) {
        return entries.put(hn, stripMaliciousHeaderChars(value));
    }

    private void delegatePutAll(Headers headers) {
        // enforce using above delegatePut method, for stripping malicious characters
        headers.delegate.entries().forEach(entry -> delegatePut(entry.getKey(), entry.getValue()));
    }

    /**
     * Get the first value found for this key even if there are multiple. If none, then
     * return null.
     */
    @Nullable
    public String getFirst(String name) {
        HeaderName hn = getHeaderName(requireNonNull(name, "name"));
        return getFirst(hn);
    }

    @Nullable
    public String getFirst(HeaderName headerName) {
        requireNonNull(headerName, "headerName");
        for (int i = 0; i < names.size(); i++) {
            if (names.get(i).equals(headerName)) {
                return values.get(i);
            }
        }
        return null;
    }

    /**
     * Get the first value found for this key even if there are multiple. If none, then
     * return the specified defaultValue.
     */
    public String getFirst(String name, String defaultValue) {
        requireNonNull(defaultValue);
        String value = getFirst(name);
        if (value != null) {
            return value;
        }
        return defaultValue;
    }

    public String getFirst(HeaderName name, String defaultValue) {
        requireNonNull(defaultValue);
        String value = getFirst(hn);
        if (value != null) {
            return value;
        }
        return defaultValue;
    }

    public List<String> getAll(String name) {
        HeaderName hn = getHeaderName(requireNonNull(name, "name"));
        return getAll(hn);
    }

    public List<String> getAll(HeaderName name) {
        requireNonNull(name, "name");
        List<String> results = null;
        for (int i = 0; i < names.size(); i++) {
            if (names.get(i).equals(name)) {
                if (results == null) {
                    results = new ArrayList<>(1);
                }
                results.add(values.get(i));
            }
        }
        if (results == null) {
            return Collections.emptyList();
        } else {
            return Collections.unmodifiableList(results);
        }
    }

    /**
     * Replace any/all entries with this key, with this single entry.
     *
     * If value is null, then not added, but any existing header of same name is removed.
     */
    public void set(String name, String value)
    {
        HeaderName hn = getHeaderName(name);
        set(hn, value);
    }
    public void set(HeaderName hn, String value)
    {
        delegate.removeAll(hn);
        if (value != null) {
            delegatePut(hn, value);
        }
    }

    public boolean setIfAbsent(String name, String value)
    {
        HeaderName hn = getHeaderName(name);
        return setIfAbsent(hn, value);
    }
    public boolean setIfAbsent(HeaderName hn, String value)
    {
        boolean did = false;
        if (! contains(hn)) {
            set(hn, value);
            did = true;
        }
        return did;
    }

    public void add(String name, String value)
    {
        HeaderName hn = getHeaderName(name);
        add(hn, value);
    }


    public void add(HeaderName name, String value) {
        requireNonNull(name, "name");
        requireNonNull(value, "value");
        names.add(name);
        values.add(value);
    }

    public void putAll(Headers headers)
    {
        delegatePutAll(headers);
    }

    public List<String> remove(String name)
    {
        HeaderName hn = getHeaderName(name);
        return remove(hn);
    }
    public List<String> remove(HeaderName hn)
    {
        return delegate.removeAll(hn);
    }

    public boolean removeIf(Predicate<? super Map.Entry<HeaderName, String>> filter) {
        return delegate.entries().removeIf(filter);
    }

    public Collection<Header> entries()
    {
        return delegate.entries()
                .stream()
                .map(entry -> new Header(entry.getKey(), entry.getValue()))
                .collect(Collectors.toList());
    }

    public Set<HeaderName> keySet()
    {
        return delegate.keySet();
    }

    public boolean contains(String name)
    {
        return contains(getHeaderName(name));
    }
    public boolean contains(HeaderName hn)
    {
        return delegate.containsKey(hn);
    }

    public boolean contains(String name, String value)
    {
        HeaderName hn = getHeaderName(name);
        return contains(hn, value);
    }
    public boolean contains(HeaderName hn, String value)
    {
        return delegate.containsEntry(hn, value);
    }

    public int size()
    {
        return delegate.size();
    }

    public Headers immutableCopy()
    {
        return new Headers(ImmutableListMultimap.copyOf(delegate));
    }

    @Deprecated // To be removed in a later Zuul Release.
    public boolean isImmutable() {
        return false;
    }

    @Override
    public int hashCode()
    {
        return super.hashCode();
    }

    @Override
    public boolean equals(Object obj)
    {
        if (obj == null)
            return false;
        if (! (obj instanceof Headers))
            return false;

        Headers h2 = (Headers) obj;
        return Iterables.elementsEqual(delegate.entries(), h2.delegate.entries());
    }

    @Override
    public String toString()
    {
        return delegate.toString();
    }
}
