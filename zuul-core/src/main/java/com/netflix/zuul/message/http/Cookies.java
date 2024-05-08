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

import io.netty.handler.codec.http.cookie.Cookie;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * User: Mike Smith
 * Date: 6/18/15
 * Time: 12:04 AM
 */
public class Cookies {
    private final Map<String, List<Cookie>> map = new HashMap<>();
    private final List<Cookie> all = new ArrayList<>();

    public void add(Cookie cookie) {
        map.computeIfAbsent(cookie.name(), k -> new ArrayList<>(1)).add(cookie);
        all.add(cookie);
    }

    public List<Cookie> getAll() {
        return all;
    }

    public List<Cookie> get(String name) {
        return map.get(name);
    }

    public Cookie getFirst(String name) {
        List<Cookie> found = map.get(name);
        if (found == null || found.isEmpty()) {
            return null;
        }
        return found.get(0);
    }

    public String getFirstValue(String name) {
        Cookie c = getFirst(name);
        String value;
        if (c != null) {
            value = c.value();
        } else {
            value = null;
        }
        return value;
    }
}
