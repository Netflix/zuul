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

package com.netflix.zuul.message.http;

import com.netflix.config.DynamicStringProperty;
import com.netflix.zuul.message.Header;
import com.netflix.zuul.message.Headers;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;

/**
 * Can be used to strip out configured regexes from Cookies headers on inbound requests.
 *
 * User: Mike Smith
 * Date: 8/28/15
 * Time: 5:49 PM
 */
public class CookieCleaner
{
    /** ":::"-delimited list of regexes to strip out of the cookie headers. */
    private static final DynamicStringProperty REGEX_PTNS_TO_STRIP_PROP =
            new DynamicStringProperty("zuul.request.cookie.cleaner.strip", " Secure,");
    private static final List<Pattern> RE_STRIP;
    static {
        RE_STRIP = new ArrayList<>();
        for (String ptn : REGEX_PTNS_TO_STRIP_PROP.get().split(":::")) {
            RE_STRIP.add(Pattern.compile(ptn));
        }
    }

    public static Headers cleanCookieHeaders(Headers headers)
    {
        Headers cleanedHeaders = new Headers();

        for (Header h : headers.entries()) {
            if (h.getName() == HttpHeaderNames.COOKIE) {
                String cookie = h.getValue();
                for (Pattern stripPtn : RE_STRIP) {
                    Matcher matcher = stripPtn.matcher(cookie);
                    if (matcher.find()) {
                        cookie = matcher.replaceAll("");
                    }
                }
                cleanedHeaders.add(HttpHeaderNames.COOKIE, cookie);
            }
            else {
                cleanedHeaders.add(h.getName(), h.getValue());
            }
        }
        return cleanedHeaders;
    }

    @RunWith(MockitoJUnitRunner.class)
    public static class TestUnit
    {
        private Headers headers = new Headers();

        @Test
        public void testCleanCookieHeaders()
        {
            headers.add("Cookie", "BlahId=12345; Secure, something=67890;");
            headers.add("Content-Type", "text/plain");

            Headers cleaned = CookieCleaner.cleanCookieHeaders(headers);

            assertEquals(2, cleaned.size());
            assertEquals("text/plain", cleaned.getFirst("Content-Type"));
            assertEquals("BlahId=12345; something=67890;", cleaned.getFirst("Cookie"));
        }

        @Test
        public void testCleanCookieHeaders_MultipleCookies()
        {
            headers.add("Cookie", "BlahId=12345; Secure, something=67890;");
            headers.add("Cookie", "FooId=XX; another=YY;");
            headers.add("Content-Type", "text/plain");

            Headers cleaned = CookieCleaner.cleanCookieHeaders(headers);

            assertEquals(3, cleaned.size());
            assertEquals("text/plain", cleaned.getFirst("Content-Type"));
            assertEquals("BlahId=12345; something=67890;", cleaned.get("Cookie").get(0));
            assertEquals("FooId=XX; another=YY;", cleaned.get("Cookie").get(1));
        }

        @Test
        public void testCleanCookieHeaders_NoCookies()
        {
            headers.add("Content-Type", "text/plain");

            Headers cleaned = CookieCleaner.cleanCookieHeaders(headers);

            assertEquals(1, cleaned.size());
            assertEquals("text/plain", cleaned.getFirst("Content-Type"));
        }
    }
}
