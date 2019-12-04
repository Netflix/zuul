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

package com.netflix.zuul.netty.server.push;

import com.google.common.truth.Truth;
import com.netflix.zuul.message.http.Cookies;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for {@link PushAuthHandler}.
 */
@RunWith(JUnit4.class)
public class PushAuthHandlerTest {

    @Test
    public void parseCookie() {
        PushAuthHandler handler = new PushAuthHandler("pushConnectionPath", "originDomain"){
            @Override
            protected boolean isDelayedAuth(FullHttpRequest req, ChannelHandlerContext ctx) {
                return false;
            }

            @Override
            protected PushUserAuth doAuth(FullHttpRequest req) {
                return null;
            }
        };

        DefaultFullHttpRequest req = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/");
        req.headers().set(HttpHeaderNames.COOKIE, "robots=people");
        Cookies cookies = handler.parseCookies(req);

        Truth.assertWithMessage(cookies.toString()).that(cookies.getFirstValue("robots")).isEqualTo("people");
    }
}
