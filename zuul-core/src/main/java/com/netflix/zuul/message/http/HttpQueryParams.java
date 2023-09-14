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
package com.netflix.zuul.message.http;

import com.google.common.base.Strings;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.ListMultimap;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;

/**
 * User: michaels
 * Date: 2/24/15
 * Time: 10:58 AM
 */
public class HttpQueryParams implements Cloneable
{
    private final ListMultimap<String, String> delegate;
    private final boolean immutable;
    private final HashMap<String, Boolean> trailingEquals;

    public HttpQueryParams()
    {
        delegate = ArrayListMultimap.create();
        immutable = false;
        trailingEquals = new HashMap<>();
    }

    private HttpQueryParams(ListMultimap<String, String> delegate)
    {
        this.delegate = delegate;
        immutable = ImmutableListMultimap.class.isAssignableFrom(delegate.getClass());
        trailingEquals = new HashMap<>();
    }

    public static HttpQueryParams parse(String queryString) {
        HttpQueryParams queryParams = new HttpQueryParams();
        if (queryString == null) {
            return queryParams;
        }

        StringTokenizer st = new StringTokenizer(queryString, "&");
        int i;
        while (st.hasMoreTokens()) {
            String s = st.nextToken();
            i = s.indexOf("=");
            // key-value query param
            if (i > 0) {
                String name = s.substring(0, i);
                String value = s.substring(i + 1);

                try {
                    name = URLDecoder.decode(name, "UTF-8");
                    value = URLDecoder.decode(value, "UTF-8");
                }
                catch (Exception e) {
                    // do nothing
                }

                queryParams.add(name, value);

                // respect trailing equals for key-only params
                if (s.endsWith("=") && value.isEmpty()) {
                    queryParams.setTrailingEquals(name, true);
                }
            }
            // key only
            else if (s.length() > 0) {
                String name = s;

                try {
                    name = URLDecoder.decode(name, "UTF-8");
                }
                catch (Exception e) {
                    // do nothing
                }

                queryParams.add(name, "");
            }
        }

        return queryParams;
    }

    /**
     * Get the first value found for this key even if there are multiple. If none, then
     * return null.
     */
    public String getFirst(String name)
    {
        List<String> values = delegate.get(name);
        if (values != null) {
            if (values.size() > 0) {
                return values.get(0);
            }
        }
        return null;
    }

    public List<String> get(String name)
    {
        return delegate.get(name.toLowerCase());
    }

    public boolean contains(String name)
    {
        return delegate.containsKey(name);
    }

    /**
     * Per https://tools.ietf.org/html/rfc7230#page-19, query params are to be treated as case sensitive.
     * However, as an utility, this exists to allow us to do a case insensitive match on demand.
     */
    public boolean containsIgnoreCase(String name) {
        return delegate.containsKey(name) || delegate.containsKey(name.toLowerCase(Locale.ROOT));
    }

    public boolean contains(String name, String value)
    {
        return delegate.containsEntry(name, value);
    }

    /**
     * Replace any/all entries with this key, with this single entry.
     */
    public void set(String name, String value)
    {
        delegate.removeAll(name);
        delegate.put(name,  value);
    }

    public void add(String name, String value)
    {
        delegate.put(name, value);
    }

    public void removeAll(String name)
    {
        delegate.removeAll(name);
    }

    public void clear()
    {
        delegate.clear();
    }

    public Collection<Map.Entry<String, String>> entries()
    {
        return delegate.entries();
    }

    public Set<String> keySet() {
        return delegate.keySet();
    }

    public String toEncodedString()
    {
        StringBuilder sb = new StringBuilder();
        try {
            for (Map.Entry<String, String> entry : entries()) {
                sb.append(URLEncoder.encode(entry.getKey(), "UTF-8"));
                if (!Strings.isNullOrEmpty(entry.getValue())) {
                    sb.append('=');
                    sb.append(URLEncoder.encode(entry.getValue(), "UTF-8"));
                }
                else if (isTrailingEquals(entry.getKey())) {
                    sb.append('=');
                }
                sb.append('&');
            }

            // Remove trailing '&'.
            if (sb.length() > 0 && '&' == sb.charAt(sb.length() - 1)) {
                sb.deleteCharAt(sb.length() - 1);
            }
        }
        catch (UnsupportedEncodingException e) {
            // Won't happen.
            e.printStackTrace();
        }
        return sb.toString();
    }

    @Override
    public String toString()
    {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : entries()) {
            sb.append(entry.getKey());
            if (!Strings.isNullOrEmpty(entry.getValue())) {
                sb.append('=');
                sb.append(entry.getValue());
            }
            sb.append('&');
        }

        // Remove trailing '&'.
        if (sb.length() > 0 && '&' == sb.charAt(sb.length() - 1)) {
            sb.deleteCharAt(sb.length() - 1);
        }
        return sb.toString();
    }

    @Override
    protected HttpQueryParams clone()
    {
        HttpQueryParams copy = new HttpQueryParams();
        copy.delegate.putAll(this.delegate);
        return copy;
    }

    public HttpQueryParams immutableCopy()
    {
        return new HttpQueryParams(ImmutableListMultimap.copyOf(delegate));
    }

    public boolean isImmutable()
    {
        return immutable;
    }

    public boolean isTrailingEquals(String key) {
        return trailingEquals.getOrDefault(key, false);
    }

    public void setTrailingEquals(String key, boolean trailingEquals) {
        this.trailingEquals.put(key, trailingEquals);
    }

    @Override
    public int hashCode()
    {
        return delegate.hashCode();
    }

    @Override
    public boolean equals(Object obj)
    {
        if (obj == null) {
            return false;
        }
        if (!(obj instanceof HttpQueryParams)) {
            return false;
        }

        HttpQueryParams hqp2 = (HttpQueryParams) obj;
        return Iterables.elementsEqual(delegate.entries(), hqp2.delegate.entries());
    }
}
