/**
 * Copyright 2018 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.zuul.sample.push;

import com.google.common.base.Strings;
import com.netflix.zuul.message.http.Cookies;
import com.netflix.zuul.netty.server.push.PushAuthHandler;
import com.netflix.zuul.netty.server.push.PushUserAuth;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.Cookie;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;

/**
 * Takes cookie value of the cookie "userAuthCookie" as a customerId WITHOUT ANY actual validation.
 * For sample puprose only. In real life the cookies at minimum should be HMAC signed to prevent tampering/spoofing,
 * probably encrypted too if it can be exchanged on plain HTTP.
 *
 * Author: Susheel Aroskar
 * Date: 5/16/18
 */
@ChannelHandler.Sharable
public class SamplePushAuthHandler extends PushAuthHandler {

    public SamplePushAuthHandler(String path) {
        super(path, ".sample.netflix.com");
    }

    /**
     * We support only cookie based auth in this sample
     * @param req
     * @param ctx
     * @return
     */
    @Override
    protected boolean isDelayedAuth(FullHttpRequest req, ChannelHandlerContext ctx) {
        return false;
    }

    @Override
    protected PushUserAuth doAuth(FullHttpRequest req) {
        final Cookies cookies = parseCookies(req);
        for (final Cookie c : cookies.getAll()) {
            if(c.getName().equals("userAuthCookie")) {
                final String customerId = c.getValue();
                if (!Strings.isNullOrEmpty(customerId)) {
                    return new SamplePushUserAuth(customerId);
                }
            }
        }
        return new SamplePushUserAuth(HttpResponseStatus.UNAUTHORIZED.code());
    }

}
