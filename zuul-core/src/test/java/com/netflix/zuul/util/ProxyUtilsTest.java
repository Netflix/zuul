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

import com.netflix.client.http.HttpResponse;
import com.netflix.zuul.message.http.HttpHeaderNames;
import com.netflix.zuul.message.http.HttpRequestMessage;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

/**
 * Unit tests for {@link ProxyUtils}.
 */
@RunWith(MockitoJUnitRunner.class)
public class ProxyUtilsTest {
    @Mock
    HttpResponse proxyResp;

    @Mock
    HttpRequestMessage request;

    @Test
    public void testIsValidResponseHeader() {
        Assert.assertTrue(ProxyUtils.isValidResponseHeader(HttpHeaderNames.get("test")));
        Assert.assertFalse(ProxyUtils.isValidResponseHeader(HttpHeaderNames.get("Keep-Alive")));
        Assert.assertFalse(ProxyUtils.isValidResponseHeader(HttpHeaderNames.get("keep-alive")));
    }
}
