/*
 *
 *
 *  Copyright 2013-2015 Netflix, Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 * /
 */

package com.netflix.zuul.message;

/**
 * Immutable, case-insensitive wrapper around Header name.
 *
 * User: Mike Smith
 * Date: 7/29/15
 * Time: 1:07 PM
 */
class HeaderName
{
    private final String name;
    private final String normalised;

    public HeaderName(String name)
    {
        if (name == null) throw new NullPointerException("HeaderName cannot be null!");
        this.name = name;
        this.normalised = name.toLowerCase();
    }

    public String getName()
    {
        return name;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        HeaderName that = (HeaderName) o;

        // Ignore case when comparing.
        return normalised.equals(that.normalised);
    }

    @Override
    public int hashCode()
    {
        return normalised.hashCode();
    }

    @Override
    public String toString()
    {
        return name;
    }
}
