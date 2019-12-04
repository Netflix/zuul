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

import static java.util.Collections.unmodifiableList;
import static java.util.stream.Collectors.toList;

import io.netty.handler.codec.http.Cookie;

import io.netty.handler.codec.http.DefaultCookie;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import javax.annotation.Nullable;

/**
 * User: Mike Smith
 * Date: 6/18/15
 * Time: 12:04 AM
 */
public final class Cookies {
    private final Map<String, List<io.netty.handler.codec.http.cookie.Cookie>> map = new LinkedHashMap<>();
    private final List<io.netty.handler.codec.http.cookie.Cookie> all = new ArrayList<>();

    public void add(io.netty.handler.codec.http.cookie.Cookie cookie) {
        Objects.requireNonNull(cookie, "cookie");
        String name = Objects.requireNonNull(cookie.name(), "name");
        map.computeIfAbsent(name, key -> new ArrayList<>()).add(cookie);
        all.add(cookie);
    }

    /**
     * Use {@link #add(io.netty.handler.codec.http.cookie.Cookie)} instead.
     */
    @Deprecated
    public void add(Cookie cookie) {
        add((io.netty.handler.codec.http.cookie.Cookie) cookie);
    }

    /**
     * Use {@link #getAllCookies()} instead.
     */
    @Deprecated
    public List<Cookie> getAll() {
        return unmodifiableList(getAllCookies().stream().map(Cookies::convert).collect(toList()));
    }

    /**
     * Returns all cookies.
     */
    public List<io.netty.handler.codec.http.cookie.Cookie> getAllCookies() {
        return unmodifiableList(new ArrayList<>(all));
    }

    /**
     * Returns all cookies for a given name, or an empty list if there are none..
     */
    public List<io.netty.handler.codec.http.cookie.Cookie> getCookies(String name) {
        Objects.requireNonNull(name, "name");
        return unmodifiableList(new ArrayList<>(map.computeIfAbsent(name, key -> Collections.emptyList())));
    }

    /**
     * Returns all cookies for the given name, or {@code null} if absent.  Use {@link #getCookies(String)} instead.
     */
    @Deprecated
    @Nullable
    public List<Cookie> get(String name) {
        List<io.netty.handler.codec.http.cookie.Cookie> cookies = getCookies(name);
        if (!cookies.isEmpty()) {
            return unmodifiableList(cookies.stream().map(Cookies::convert).collect(toList()));
        }
        return null;
    }

    /**
     * Returns the first cookie value set for the given name, or {@code null} if absent.
     */
    @Nullable
    public io.netty.handler.codec.http.cookie.Cookie getFirstCookie(String name) {
        Objects.requireNonNull(name, "name");
        List<io.netty.handler.codec.http.cookie.Cookie> cookies = map.get(name);
        if (cookies == null || cookies.isEmpty()) {
            return null;
        }
        return cookies.get(0);
    }

    /**
     * Use {@link #getFirstCookie(String)} instead.
     */
    @Deprecated
    @Nullable
    public Cookie getFirst(String name) {
        io.netty.handler.codec.http.cookie.Cookie cookie = getFirstCookie(name);
        if (cookie != null) {
            return convert(cookie);
        }
        return null;
    }

    public String getFirstValue(String name) {
        Cookie c = getFirst(name);
        String value;
        if (c != null) {
            value = c.getValue();
        } else {
            value = null;
        }
        return value;
    }

    @SuppressWarnings("deprecation")
    private static final Cookie convert(io.netty.handler.codec.http.cookie.Cookie cookie) {
        if (cookie instanceof Cookie) {
            return (Cookie) cookie;
        }
        Cookie c = new DefaultCookie(cookie.name(), cookie.value());
        c.setHttpOnly(cookie.isHttpOnly());
        c.setDomain(cookie.domain());
        return c;

    }
}
