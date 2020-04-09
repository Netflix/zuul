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

import java.util.Locale;

/**
 * Immutable, case-insensitive wrapper around Header name.
 *
 * User: Mike Smith
 * Date: 7/29/15
 * Time: 1:07 PM
 */
public final class HeaderName {
    private final String name;
    private final String normalised;
    private final int hashCode;

    public HeaderName(String name) {
        if (name == null) {
            throw new NullPointerException("HeaderName cannot be null!");
        }
        this.name = name;
        this.normalised = normalize(name);
        this.hashCode = this.normalised.hashCode();
    }

    HeaderName(String name, String normalised) {
        this.name = name;
        this.normalised = normalised;
        this.hashCode = normalised.hashCode();
    }

    /**
     * Gets the original, non-normalized name for this header.
     */
    public String getName() {
        return name;
    }

    public String getNormalised() {
        return normalised;
    }

    static String normalize(String s) {
        return s.toLowerCase(Locale.ROOT);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof HeaderName)) {
            return false;
        }
        HeaderName that = (HeaderName) o;
        return this.normalised.equals(that.normalised);
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    @Override
    public String toString() {
        return name;
    }
}
