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
import com.netflix.config.DynamicStringProperty;
import com.netflix.zuul.message.Headers;
import io.netty.handler.codec.http.Cookie;
import io.netty.handler.codec.http.DefaultCookie;
import io.netty.handler.codec.http.cookie.ServerCookieDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * User: Mike Smith
 * Date: 6/18/15
 * Time: 12:04 AM
 */
public class Cookies
{
    private static final Logger LOG = LoggerFactory.getLogger(Cookies.class);

    private static final CachedDynamicBooleanProperty CLEAN_COOKIES = new CachedDynamicBooleanProperty(
            "zuul.HttpRequestMessage.cookies.clean", false);

    private static final List<Pattern> RE_STRIP;
    /** ":::"-delimited list of regexes to strip out of the cookie headers. */
    private static final DynamicStringProperty REGEX_PTNS_TO_STRIP_PROP =
            new DynamicStringProperty("zuul.request.cookie.cleaner.strip", " Secure,");

    static {
        RE_STRIP = new ArrayList<>();
        for (String ptn : REGEX_PTNS_TO_STRIP_PROP.get().split(":::")) {
            RE_STRIP.add(Pattern.compile(ptn));
        }
    }

    private Map<String, List<Cookie>> map = new HashMap<>();
    private List<Cookie> all = new ArrayList<>();

    public void add(Cookie cookie)
    {
        List<Cookie> existing = map.get(cookie.getName());
        if (existing == null) {
            existing = new ArrayList<>();
            map.put(cookie.getName(), existing);
        }
        existing.add(cookie);
        all.add(cookie);
    }

    public List<Cookie> getAll()
    {
        return all;
    }

    public List<Cookie> get(String name)
    {
        return map.get(name);
    }

    public Cookie getFirst(String name)
    {
        List<Cookie> found = map.get(name);
        if (found == null || found.size() == 0) {
            return null;
        }
        return found.get(0);
    }

    public String getFirstValue(String name)
    {
        Cookie c = getFirst(name);
        String value;
        if (c != null) {
            value = c.getValue();
        } else {
            value = null;
        }
        return value;
    }

    public static Cookies fromHeaders(Headers headers) {
        Cookies cookies = new Cookies();
        for (String aCookieHeader : headers.get(HttpHeaderNames.COOKIE)) {
            try {
                if (CLEAN_COOKIES.get()) {
                    aCookieHeader = cleanCookieHeader(aCookieHeader);
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

    static String cleanCookieHeader(String cookie) {
        for (Pattern stripPtn : RE_STRIP) {
            Matcher matcher = stripPtn.matcher(cookie);
            if (matcher.find()) {
                cookie = matcher.replaceAll("");
            }
        }
        return cookie;
    }
}
