/*
 * Copyright 2020 Netflix, Inc.
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
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Performs a simple cleanup of the cookie headers by removing values which shouldn't be part of a cookie header.
 * Note: It's a temporary hack put in place to clean cookie headers which will removed in future releases.
 */
@Deprecated
class CookiesSanitizer {
    private static final CachedDynamicBooleanProperty CLEAN_COOKIES = new CachedDynamicBooleanProperty(
            "zuul.HttpRequestMessage.cookies.clean", false);

    private static final List<Pattern> RE_STRIP;
    /**
     * ":::"-delimited list of regexes to strip out of the cookie headers.
     */
    private static final DynamicStringProperty REGEX_PTNS_TO_STRIP_PROP =
            new DynamicStringProperty("zuul.request.cookie.cleaner.strip", " Secure,");

    static {
        RE_STRIP = new ArrayList<>();
        for (String ptn : REGEX_PTNS_TO_STRIP_PROP.get().split(":::")) {
            RE_STRIP.add(Pattern.compile(ptn));
        }
    }

    public static String cleanCookieHeader(String cookie) {
        for (Pattern stripPtn : RE_STRIP) {
            Matcher matcher = stripPtn.matcher(cookie);
            if (matcher.find()) {
                cookie = matcher.replaceAll("");
            }
        }
        return cookie;
    }
}
