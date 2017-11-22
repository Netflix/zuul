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

import com.netflix.zuul.message.HeaderName;

import java.util.concurrent.ConcurrentHashMap;

/**
 * User: Mike Smith
 * Date: 8/5/15
 * Time: 1:08 PM
 */
public class HttpHeaderNamesCache
{
    private final ConcurrentHashMap<String, HeaderName> cache;
    private final int maxSize;

    public HttpHeaderNamesCache(int initialSize, int maxSize)
    {
        this.cache = new ConcurrentHashMap<>(initialSize);
        this.maxSize = maxSize;
    }

    public boolean isFull()
    {
        return cache.size() >= maxSize;
    }

    public HeaderName get(String name)
    {
        // Check in the static cache for this headername if available.
        // NOTE: we do this lookup case-sensitively, as doing case-INSENSITIVELY removes the purpose of
        // caching the object in the first place (ie. the expensive operation we want to avoid by caching
        // is the case-insensitive string comparisons).
        HeaderName hn = cache.get(name);
        if (hn == null) {
            // Here we're accepting that the isFull check is not happening atomically with the put, as we don't mind
            // too much if the cache overfills a bit.
            if (isFull()) {
                hn = new HeaderName(name);
            }
            else {
                hn = cache.computeIfAbsent(name, (newName) -> new HeaderName(newName));
            }
        }
        return hn;
    }
}
