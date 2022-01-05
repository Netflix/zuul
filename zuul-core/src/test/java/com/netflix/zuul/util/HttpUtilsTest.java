/*
 * Copyright 2019 Netflix, Inc.
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

package com.netflix.zuul.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.netflix.zuul.message.Headers;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for {@link HttpUtils}.
 */
@RunWith(JUnit4.class)
public class HttpUtilsTest {

    @Test
    public void detectsGzip() {
        assertTrue(HttpUtils.isCompressed("gzip"));
    }

    @Test
    public void detectsDeflate() {
        assertTrue(HttpUtils.isCompressed("deflate"));
    }

    @Test
    public void detectsCompress() {
        assertTrue(HttpUtils.isCompressed("compress"));
    }

    @Test
    public void detectsBR() {
        assertTrue(HttpUtils.isCompressed("br"));
    }

    @Test
    public void detectsNonGzip() {
        assertFalse(HttpUtils.isCompressed("identity"));
    }

    @Test
    public void detectsGzipAmongOtherEncodings() {
        assertTrue(HttpUtils.isCompressed("gzip, deflate"));
    }

    @Test
    public void acceptsGzip() {
        Headers headers = new Headers();
        headers.add("Accept-Encoding", "gzip, deflate");
        assertTrue(HttpUtils.acceptsGzip(headers));
    }

    @Test
    public void acceptsGzip_only() {
        Headers headers = new Headers();
        headers.add("Accept-Encoding", "deflate");
        assertFalse(HttpUtils.acceptsGzip(headers));
    }

    @Test
    public void stripMaliciousHeaderChars() {
        assertEquals("something", HttpUtils.stripMaliciousHeaderChars("some\r\nthing"));
        assertEquals("some thing", HttpUtils.stripMaliciousHeaderChars("some thing"));
        assertEquals("something", HttpUtils.stripMaliciousHeaderChars("\nsome\r\nthing\r"));
        assertEquals("", HttpUtils.stripMaliciousHeaderChars("\r"));
        assertEquals("", HttpUtils.stripMaliciousHeaderChars(""));
        assertNull(HttpUtils.stripMaliciousHeaderChars(null));
    }
}
