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

import com.netflix.config.CachedDynamicBooleanProperty;
import com.netflix.zuul.message.Headers;
import io.netty.handler.codec.http.Cookie;
import io.netty.handler.codec.http.DefaultCookie;
import io.netty.handler.codec.http.cookie.ServerCookieDecoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * User: Mike Smith Date: 6/18/15 Time: 12:04 AM
 */
public class Cookies {

    private static final Logger LOG = LoggerFactory.getLogger(Cookies.class);

    private static final CachedDynamicBooleanProperty CLEAN_COOKIES = new CachedDynamicBooleanProperty(
            "zuul.HttpRequestMessage.cookies.clean", false);

    private Map<String, List<Cookie>> map = new HashMap<>();
    private List<Cookie> all = new ArrayList<>();

    public static Cookies fromHeaders(Headers headers) {
        Cookies cookies = new Cookies();
        for (String aCookieHeader : headers.get(HttpHeaderNames.COOKIE)) {
            try {
                if (CLEAN_COOKIES.get()) {
                    aCookieHeader = CookiesSanitizer.cleanCookieHeader(aCookieHeader);
                }
                List<io.netty.handler.codec.http.cookie.Cookie> decodedCookies =
                        ServerCookieDecoder.LAX.decodeAll(aCookieHeader);
                // Temporarily map to the deprecated objects until Zuul moves to the new interfaces.
                List<Cookie> mappedCookies = decodedCookies.stream()
                        .map(cookie -> new DefaultCookie(cookie.name(), cookie.value()))
                        .collect(Collectors.toList());
                for (Cookie cookie : mappedCookies) {
                    cookies.add(cookie);
                }
            } catch (Exception e) {
                LOG.error(String.format("Error parsing request Cookie header. cookie=%s", aCookieHeader));
            }
        }
        return cookies;
    }

    public void add(Cookie cookie) {
        List<Cookie> existing = map.get(cookie.getName());
        if (existing == null) {
            existing = new ArrayList<>();
            map.put(cookie.getName(), existing);
        }
        existing.add(cookie);
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
        if (found == null || found.size() == 0) {
            return null;
        }
        return found.get(0);
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
}
