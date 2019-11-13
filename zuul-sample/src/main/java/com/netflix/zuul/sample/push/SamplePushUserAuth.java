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

import com.netflix.zuul.netty.server.push.PushUserAuth;

/**
 * Author: Susheel Aroskar
 * Date: 5/16/18
 */
public class SamplePushUserAuth implements PushUserAuth {

    private String customerId;
    private int statusCode;

    private SamplePushUserAuth(String customerId, int statusCode) {
        this.customerId = customerId;
        this.statusCode = statusCode;
    }

    // Successful auth
    public SamplePushUserAuth(String customerId) {
        this(customerId, 200);
    }

    // Failed auth
    public SamplePushUserAuth(int statusCode) {
        this("", statusCode);
    }

    @Override
    public boolean isSuccess() {
        return statusCode == 200;
    }

    @Override
    public int statusCode() {
        return statusCode;
    }

    @Override
    public String getClientIdentity() {
        return customerId;
    }
}
