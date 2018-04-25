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

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.ListMultimap;
import org.apache.commons.lang3.StringUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.*;

import static junit.framework.Assert.assertEquals;

/**
 * User: michaels
 * Date: 2/24/15
 * Time: 10:58 AM
 */
public class HttpQueryParams implements Cloneable
{
    private final ListMultimap<String, String> delegate;
    private final boolean immutable;

    public HttpQueryParams()
    {
        delegate = ArrayListMultimap.create();
        immutable = false;
    }

    private HttpQueryParams(ListMultimap<String, String> delegate)
    {
        this.delegate = delegate;
        immutable = ImmutableListMultimap.class.isAssignableFrom(delegate.getClass());
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
     *
     * @param name
     * @return
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

    public boolean contains(String name, String value)
    {
        return delegate.containsEntry(name, value);
    }

    /**
     * Replace any/all entries with this key, with this single entry.
     *
     * @param name
     * @param value
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
                if (StringUtils.isNotEmpty(entry.getValue())) {
                    sb.append('=');
                    sb.append(URLEncoder.encode(entry.getValue(), "UTF-8"));
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
            if (StringUtils.isNotEmpty(entry.getValue())) {
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


    @Override
    public int hashCode()
    {
        return delegate.hashCode();
    }

    @Override
    public boolean equals(Object obj)
    {
        if (obj == null)
            return false;
        if (! (obj instanceof HttpQueryParams))
            return false;

        HttpQueryParams hqp2 = (HttpQueryParams) obj;
        return Iterables.elementsEqual(delegate.entries(), hqp2.delegate.entries());
    }


    @RunWith(MockitoJUnitRunner.class)
    public static class TestUnit
    {
        @Test
        public void testMultiples()
        {
            HttpQueryParams qp = new HttpQueryParams();
            qp.add("k1", "v1");
            qp.add("k1", "v2");
            qp.add("k2", "v3");

            assertEquals("k1=v1&k1=v2&k2=v3", qp.toEncodedString());
        }

        @Test
        public void testToEncodedString()
        {
            HttpQueryParams qp = new HttpQueryParams();
            qp.add("k'1", "v1&");
            assertEquals("k%271=v1%26", qp.toEncodedString());

            qp = new HttpQueryParams();
            qp.add("k+", "\n");
            assertEquals("k%2B=%0A", qp.toEncodedString());
        }

        @Test
        public void testToString()
        {
            HttpQueryParams qp = new HttpQueryParams();
            qp.add("k'1", "v1&");
            assertEquals("k'1=v1&", qp.toString());

            qp = new HttpQueryParams();
            qp.add("k+", "\n");
            assertEquals("k+=\n", qp.toString());
        }

        @Test
        public void testEquals()
        {
            HttpQueryParams qp1 = new HttpQueryParams();
            qp1.add("k1", "v1");
            qp1.add("k2", "v2");
            HttpQueryParams qp2 = new HttpQueryParams();
            qp2.add("k1", "v1");
            qp2.add("k2", "v2");

            assertEquals(qp1, qp2);
        }

        @Test
        public void testParseKeysWithoutValues()
        {
            HttpQueryParams expected = new HttpQueryParams();
            expected.add("k1", "");
            expected.add("k2", "v2");
            expected.add("k3", "");

            HttpQueryParams actual = HttpQueryParams.parse("k1=&k2=v2&k3=");

            assertEquals(expected, actual);

            assertEquals("k1&k2=v2&k3", actual.toEncodedString());
        }

        @Test
        public void testParseKeyWithoutValueEquals()
        {
            HttpQueryParams expected = new HttpQueryParams();
            expected.add("k1", "");

            HttpQueryParams actual = HttpQueryParams.parse("k1=");

            assertEquals(expected, actual);

            assertEquals("k1", actual.toEncodedString());
        }

        @Test
        public void testParseKeyWithoutValue()
        {
            HttpQueryParams expected = new HttpQueryParams();
            expected.add("k1", "");

            HttpQueryParams actual = HttpQueryParams.parse("k1");

            assertEquals(expected, actual);

            assertEquals("k1", actual.toEncodedString());
        }

        @Test
        public void testParseKeyWithoutValueShort()
        {
            HttpQueryParams expected = new HttpQueryParams();
            expected.add("=", "");

            HttpQueryParams actual = HttpQueryParams.parse("=");

            assertEquals(expected, actual);

            assertEquals("%3D", actual.toEncodedString());
        }
    }
}
