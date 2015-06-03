/**
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
package com.netflix.zuul.context;

/**
 * User: Mike Smith
 * Date: 4/28/15
 * Time: 6:45 PM
 */

import com.netflix.zuul.filters.FilterError;
import com.netflix.zuul.stats.Timing;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents the context between client and origin server for the duration of the dedicated connection/session
 * between them. But we're currently still only modelling single request/response pair per session.
 *
 * NOTE: Not threadsafe, and not intended to be used concurrently.
 */
@SuppressWarnings("serial")
public class SessionContext implements Cloneable
{
    private final Attributes attributes;
    private final HashMap<String, Object> helpers = new HashMap<String, Object>();
    private final List<FilterError> filterErrors = new ArrayList<>();

    public SessionContext()
    {
        this.attributes = new Attributes();
    }

    private SessionContext(Attributes attributes)
    {
        this.attributes = attributes;
    }

    public Map getHelpers() {
        return helpers;
    }

    public Attributes getAttributes() {
        return attributes;
    }

    public List<FilterError> getFilterErrors() {
        return filterErrors;
    }

    /**
     * Makes a copy of the RequestContext. This is used for debugging.
     *
     * @return
     */
    @Override
    public SessionContext clone()
    {
        Attributes attributes = this.getAttributes().copy();
        SessionContext copy = new SessionContext(attributes);

        copy.getFilterErrors().clear();
        copy.getFilterErrors().addAll(getFilterErrors());

        // Don't copy the Helper objects.

        return copy;
    }


    /** Timers - TODO: remove the dedicated methods for these? */

    private Timing getTiming(String name)
    {
        Timing t = (Timing) attributes.get(name);
        if (t == null) {
            t = new Timing(name);
            attributes.put(name, t);
        }
        return t;
    }
    public Timing getRequestTiming()
    {
        return getTiming("_requestTiming");
    }
    public Timing getRequestProxyTiming()
    {
        return getTiming("_requestProxyTiming");
    }
    public void setOriginReportedDuration(int duration)
    {
        attributes.put("_originReportedDuration", duration);
    }
    public int getOriginReportedDuration()
    {
        Object value = attributes.get("_originReportedDuration");
        if (value != null) {
            return (Integer) value;
        }
        return -1;
    }
}