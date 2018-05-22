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
import com.google.inject.Singleton;
import com.netflix.zuul.netty.server.push.PushConnectionRegistry;
import com.netflix.zuul.netty.server.push.PushMessageSender;
import com.netflix.zuul.netty.server.push.PushUserAuth;
import io.netty.channel.ChannelHandler;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;

/**
 * Author: Susheel Aroskar
 * Date: 5/16/18
 */
@Singleton
@ChannelHandler.Sharable
public class SamplePushMessageSender extends PushMessageSender {

    public SamplePushMessageSender(PushConnectionRegistry pushConnectionRegistry) {
        super(pushConnectionRegistry);
    }

    @Override
    protected PushUserAuth getPushUserAuth(FullHttpRequest request) {
        final String cid = request.headers().get("X-CUSTOMER_ID");
        if (Strings.isNullOrEmpty(cid)) {
            return new SamplePushUserAuth(HttpResponseStatus.UNAUTHORIZED.code());
        }
        return new SamplePushUserAuth(cid);

    }

}

