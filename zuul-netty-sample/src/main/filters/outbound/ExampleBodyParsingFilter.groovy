/*
 * Copyright 2015 Netflix, Inc.
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
package outbound

import com.netflix.zuul.bytebuf.ByteBufUtils
import com.netflix.zuul.context.HttpResponseMessage
import com.netflix.zuul.filters.http.HttpOutboundFilter
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import rx.Observable

/**
 * User: michaels@netflix.com
 * Date: 5/28/15
 * Time: 9:22 AM
 */
class ExampleBodyParsingFilter extends HttpOutboundFilter
{
    protected static final Logger LOG = LoggerFactory.getLogger(ExampleBodyParsingFilter.class);

    @Override
    Observable<HttpResponseMessage> applyAsync(HttpResponseMessage response)
    {
        Observable<HttpResponseMessage> obs = response.getBodyStream()
        .doOnNext({bb ->
            // Pass each bytebuf to some other component for conversion/decryption.
            //bb.retain()
            println "bytebuf=" + new String(ByteBufUtils.toBytes(bb))

            return bb
        })
        .map({bb ->
            return response
        })
        .single()
        .doOnError({e ->
            e.printStackTrace()
        })
        .doOnCompleted({
            println "completed"
        })

        return obs
    }

    @Override
    boolean shouldFilter(HttpResponseMessage response) {
        return response.getRequest().getQueryParams().get("parse")
    }

    @Override
    int filterOrder() {
        return 10
    }
}
