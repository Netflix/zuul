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
package com.netflix.zuul.filters.http;

import static org.junit.Assert.assertEquals;

import com.netflix.zuul.filters.FilterSyncType;
import com.netflix.zuul.message.http.HttpRequestMessage;
import com.netflix.zuul.message.http.HttpResponseMessage;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class HttpSyncEndpointTest {

    @Test
    public void blah() {
        HttpSyncEndpoint endpoint = new HttpSyncEndpoint() {
            @Override
            public HttpResponseMessage apply(HttpRequestMessage input) {
                return null;
            }
        };

        assertEquals(FilterSyncType.SYNC, endpoint.getSyncType());
    }
}
