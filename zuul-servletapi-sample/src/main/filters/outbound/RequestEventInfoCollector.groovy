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
package outbound

import com.netflix.appinfo.AmazonInfo
import com.netflix.appinfo.ApplicationInfoManager
import com.netflix.appinfo.InstanceInfo
import com.netflix.config.ConfigurationManager
import com.netflix.zuul.message.http.HttpRequestInfo
import com.netflix.zuul.message.http.HttpResponseInfo
import com.netflix.zuul.message.http.HttpResponseMessage
import com.netflix.zuul.filters.http.HttpOutboundSyncFilter
import com.netflix.zuul.stats.AmazonInfoHolder
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Collects request data to be sent to ESI, EventBus, Turbine and friends.
 *
 * @author mhawthorne
 */
class RequestEventInfoCollector extends HttpOutboundSyncFilter
{
    private static final Logger LOG = LoggerFactory.getLogger(RequestEventInfoCollector.class);
    private static final String VALUE_SEPARATOR = ","

    @Override
    int filterOrder() {
        return 99
    }

    @Override
    boolean shouldFilter(HttpResponseMessage response) {
        return true
    }

    @Override
    HttpResponseMessage apply(HttpResponseMessage response)
    {
        final Map<String, Object> event = response.getContext().getEventProperties();
        try {
            captureRequestData(event, response.getInboundRequest(), response);
            captureInstanceData(event);
        }
        catch (Exception e) {
            event.put("exception", e.toString());
            LOG.error(e.getMessage(), e);
        }
        return response
    }

    void captureRequestData(Map<String, Object> event, HttpRequestInfo req, HttpResponseInfo resp) {

        try {
            // basic request properties
            event.put("path", req.getPath());
            event.put("host", req.getHeaders().getFirst("host"));
            event.put("query", req.getQueryParams().toEncodedString());
            event.put("method", req.getMethod());
            event.put("currentTime", System.currentTimeMillis());
            event.put("status", resp.getStatus())

            // request headers
            for (String name : req.getHeaders().keySet())
            {
                final StringBuilder valBuilder = new StringBuilder();
                boolean firstValue = true;
                for (String value : req.getHeaders().get(name))
                {
                    // only prepends separator for non-first header values
                    if (firstValue) firstValue = false;
                    else {
                        valBuilder.append(VALUE_SEPARATOR);
                    }

                    valBuilder.append(value);
                }
                event.put("request.header." + name, valBuilder.toString());
            }

            // request params
            for (String name : req.getQueryParams().keySet())
            {
                final StringBuilder valBuilder = new StringBuilder();
                boolean firstValue = true;
                for (String value : req.getQueryParams().get(name))
                {
                    // only prepends separator for non-first header values
                    if (firstValue) firstValue = false;
                    else {
                        valBuilder.append(VALUE_SEPARATOR);
                    }

                    valBuilder.append(value);
                }
                event.put("param." + name, valBuilder.toString());
            }

            // response headers
            for (String name : resp.getHeaders().keySet())
            {
                final StringBuilder valBuilder = new StringBuilder();
                boolean firstValue = true;
                for (String value : resp.getHeaders().get(name))
                {
                    // only prepends separator for non-first header values
                    if (firstValue) firstValue = false;
                    else {
                        valBuilder.append(VALUE_SEPARATOR);
                    }

                    valBuilder.append(value);
                }
                event.put("response.header." + name, valBuilder.toString());
            }

        } finally {

        }
    }

    private static final void captureInstanceData(Map<String, Object> event) {

        try {
            final String stack = ConfigurationManager.getDeploymentContext().getDeploymentStack();
            if (stack != null) event.put("stack", stack);

            // TODO: add CLUSTER, ASG, etc.

            final InstanceInfo instanceInfo = ApplicationInfoManager.getInstance().getInfo();
            if (instanceInfo != null) {
                event.put("instance.id", instanceInfo.getId());
                for (final Map.Entry<String, String> e : instanceInfo.getMetadata().entrySet()) {
                    event.put("instance." + e.getKey(), e.getValue());
                }
            }

            // caches value after first call.  multiple threads could get here simultaneously, but I think that is fine
            final AmazonInfo amazonInfo = AmazonInfoHolder.getInfo();

            for (final Map.Entry<String, String> e : amazonInfo.getMetadata().entrySet()) {
                event.put("amazon." + e.getKey(), e.getValue());
            }
        } finally {

        }
    }
}
