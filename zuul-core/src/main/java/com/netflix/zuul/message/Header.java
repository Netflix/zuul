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

/**
 * User: Mike Smith
 * Date: 7/29/15
 * Time: 1:06 PM
 */
public class Header implements Cloneable
{
    private final HeaderName name;
    private final String value;

    public Header(HeaderName name, String value)
    {
        if (name == null) throw new NullPointerException("Header name cannot be null!");
        this.name = name;
        this.value = value;
    }

    public String getKey()
    {
        return name.getName();
    }

    public HeaderName getName()
    {
        return name;
    }

    public String getValue()
    {
        return value;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Header header = (Header) o;

        if (!name.equals(header.name)) return false;
        return !(value != null ? !value.equals(header.value) : header.value != null);

    }

    @Override
    public int hashCode()
    {
        int result = name.hashCode();
        result = 31 * result + (value != null ? value.hashCode() : 0);
        return result;
    }

    @Override
    public String toString()
    {
        return String.format("%s: %s", name, value);
    }
}
