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

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.netflix.zuul.netty.server.push.PushConnectionRegistry;
import com.netflix.zuul.netty.server.push.PushMessageSender;
import com.netflix.zuul.netty.server.push.PushMessageSenderInitializer;

/**
 * Author: Susheel Aroskar
 * Date: 5/16/18
 */
@Singleton
public class SamplePushMessageSenderInitializer extends PushMessageSenderInitializer {

    private final PushMessageSender pushMessageSender;

    @Inject
    public SamplePushMessageSenderInitializer(PushConnectionRegistry pushConnectionRegistry) {
        super(pushConnectionRegistry);
        pushMessageSender = new SamplePushMessageSender(pushConnectionRegistry);
    }

    @Override
    protected PushMessageSender getPushMessageSender(PushConnectionRegistry pushConnectionRegistry) {
        return pushMessageSender;
    }

}
