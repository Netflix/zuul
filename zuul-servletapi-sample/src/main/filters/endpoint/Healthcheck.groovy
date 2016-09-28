/*
 * Copyright 2013 Netflix, Inc.
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
package endpoint

import com.netflix.zuul.filters.BaseFilterTest
import com.netflix.zuul.filters.http.HttpSyncEndpoint
import com.netflix.zuul.message.http.HttpRequestMessage
import com.netflix.zuul.message.http.HttpResponseMessage
import com.netflix.zuul.message.http.HttpResponseMessageImpl
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.mockito.runners.MockitoJUnitRunner

/**
 * @author Mikey Cohen
 * Date: 2/1/12
 * Time: 7:56 AM
 */
class Healthcheck extends HttpSyncEndpoint
{
    @Override
    HttpResponseMessage apply(HttpRequestMessage request)
    {
        HttpResponseMessage response = new HttpResponseMessageImpl(request.getContext(), request, 200)
        response.headers.set('Content-Type', 'application/xml')

        String bodyStr = "<health>ok</health>"
        response.setBody(bodyStr.getBytes("UTF-8"))

        return response
    }


    @RunWith(MockitoJUnitRunner.class)
    public static class TestUnit extends BaseFilterTest
    {
        Healthcheck filter

        @Before
        public void setup() {
            super.setup()
            filter = new Healthcheck()
        }

        @Test
        public void testHealthcheck() {

            Mockito.when(request.getPath()).thenReturn("/healthcheck")
            Assert.assertTrue(filter.shouldFilter(request))

            HttpResponseMessage response = filter.apply(request)

            Assert.assertTrue(response.getBody() != null)
            String bodyStr = new String(response.getBody(), "UTF-8")
            Assert.assertTrue(bodyStr.contains("<health>ok</health>"))
        }

    }
}
