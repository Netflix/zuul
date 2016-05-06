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
package filters.post

import com.netflix.zuul.ZuulFilter
import com.netflix.zuul.context.RequestContext
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * @author Mikey Cohen
 * Date: 2/3/12
 * Time: 2:48 PM
 */
class Stats extends ZuulFilter {
    private static final Logger LOG = LoggerFactory.getLogger(Stats.class);

    @Override
    String filterType() {
        return "post"
    }

    @Override
    int filterOrder() {
        return 2000
    }

    @Override
    boolean shouldFilter() {
        return true
    }

    @Override
    Object run() {
        dumpRoutingDebug()
        dumpRequestDebug()
    }

    public void dumpRequestDebug() {
        List<String> rd = (List<String>) RequestContext.getCurrentContext().get("requestDebug");
        rd?.each {
            LOG.info("REQUEST_DEBUG::${it}");
        }
    }

    public void dumpRoutingDebug() {
        List<String> rd = (List<String>) RequestContext.getCurrentContext().get("routingDebug");
        rd?.each {
            LOG.info("ZUUL_DEBUG::${it}");
        }
    }

}
