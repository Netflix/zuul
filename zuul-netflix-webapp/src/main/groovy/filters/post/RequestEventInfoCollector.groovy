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

import com.netflix.appinfo.AmazonInfo
import com.netflix.appinfo.ApplicationInfoManager
import com.netflix.appinfo.InstanceInfo
import com.netflix.config.ConfigurationManager
import com.netflix.util.Pair
import com.netflix.zuul.ZuulFilter
import com.netflix.zuul.context.NFRequestContext
import com.netflix.zuul.stats.AmazonInfoHolder
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import javax.servlet.http.HttpServletRequest

/**
 * Collects request data to be sent to ESI, EventBus, Turbine and friends.
 *
 * @author mhawthorne
 */
class RequestEventInfoCollectorFilter extends ZuulFilter {


    private static final Logger LOG = LoggerFactory.getLogger(RequestEventInfoCollectorFilter.class);
    private static final String VALUE_SEPARATOR = ",";


    @Override
    int filterOrder() {
        return 99
    }

    @Override
    String filterType() {
        return "post"
    }

    boolean shouldFilter() {
        return true
    }

    Object run() {
        NFRequestContext ctx = NFRequestContext.getCurrentContext();
        final Map<String, Object> event = ctx.getEventProperties();

        try {
            captureRequestData(event, ctx.request);
            captureInstanceData(event);


        } catch (Exception e) {
            event.put("exception", e.toString());
            LOG.error(e.getMessage(), e);
        }
    }

    void captureRequestData(Map<String, Object> event, HttpServletRequest req) {

        try {
            // basic request properties
            event.put("path", req.getPathInfo());
            event.put("host", req.getHeader("host"));
            event.put("query", req.getQueryString());
            event.put("method", req.getMethod());
            event.put("currentTime", System.currentTimeMillis());

            // request headers
            for (final Enumeration names = req.getHeaderNames(); names.hasMoreElements();) {
                final String name = names.nextElement();
                final StringBuilder valBuilder = new StringBuilder();
                boolean firstValue = true;
                for (final Enumeration vals = req.getHeaders(name); vals.hasMoreElements();) {
                    // only prepends separator for non-first header values
                    if (firstValue) firstValue = false;
                    else {
                        valBuilder.append(VALUE_SEPARATOR);
                    }

                    valBuilder.append(vals.nextElement());
                }

                event.put("request.header." + name, valBuilder.toString());
            }

            // request params
            final Map params = req.getParameterMap();
            for (final Object key : params.keySet()) {
                final String keyString = key.toString();
                final Object val = params.get(key);
                String valString;
                if (val instanceof String[]) {
                    final String[] valArray = (String[]) val;
                    if (valArray.length == 1)
                        valString = valArray[0];
                    else
                        valString = Arrays.asList((String[]) val).toString();
                } else {
                    valString = val.toString();
                }
                event.put("param." + key, valString);

                // some special params get promoted to top-level fields
                if (keyString.equals("esn")) {
                    event.put("esn", valString);
                }
            }

            // response headers
            NFRequestContext.getCurrentContext().getZuulResponseHeaders()?.each { Pair<String, String> it ->
                event.put("response.header." + it.first().toLowerCase(), it.second())
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
