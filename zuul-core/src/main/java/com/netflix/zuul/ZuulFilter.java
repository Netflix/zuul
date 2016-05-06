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
package com.netflix.zuul;

import com.netflix.config.DynamicBooleanProperty;
import com.netflix.config.DynamicPropertyFactory;
import com.netflix.zuul.monitoring.Tracer;
import com.netflix.zuul.monitoring.TracerFactory;

/**
 * Base abstract class for ZuulFilters. The base class defines abstract methods to define:
 * filterType() - to classify a filter by type. Standard types in Zuul are "pre" for pre-routing filtering,
 * "route" for routing to an origin, "post" for post-routing filters, "error" for error handling.
 * We also support a "static" type for static responses see  StaticResponseFilter.
 * Any filterType made be created or added and run by calling FilterProcessor.runFilters(type)
 * <p/>
 * filterOrder() must also be defined for a filter. Filters may have the same  filterOrder if precedence is not
 * important for a filter. filterOrders do not need to be sequential.
 * <p/>
 * ZuulFilters may be disabled using Archius Properties.
 * <p/>
 * By default ZuulFilters are static; they don't carry state. This may be overridden by overriding the isStaticFilter() property to false
 *
 * @author Mikey Cohen
 *         Date: 10/26/11
 *         Time: 4:29 PM
 */
public abstract class ZuulFilter implements IZuulFilter, Comparable<ZuulFilter> {

    private final DynamicBooleanProperty filterDisabled =
            DynamicPropertyFactory.getInstance().getBooleanProperty(disablePropertyName(), false);

    /**
     * to classify a filter by type. Standard types in Zuul are "pre" for pre-routing filtering,
     * "route" for routing to an origin, "post" for post-routing filters, "error" for error handling.
     * We also support a "static" type for static responses see  StaticResponseFilter.
     * Any filterType made be created or added and run by calling FilterProcessor.runFilters(type)
     *
     * @return A String representing that type
     */
    abstract public String filterType();

    /**
     * filterOrder() must also be defined for a filter. Filters may have the same  filterOrder if precedence is not
     * important for a filter. filterOrders do not need to be sequential.
     *
     * @return the int order of a filter
     */
    abstract public int filterOrder();

    /**
     * By default ZuulFilters are static; they don't carry state. This may be overridden by overriding the isStaticFilter() property to false
     *
     * @return true by default
     */
    public boolean isStaticFilter() {
        return true;
    }

    /**
     * The name of the Archaius property to disable this filter. by default it is zuul.[classname].[filtertype].disable
     *
     * @return
     */
    public String disablePropertyName() {
        return "zuul." + this.getClass().getSimpleName() + "." + filterType() + ".disable";
    }

    /**
     * If true, the filter has been disabled by archaius and will not be run
     *
     * @return
     */
    public boolean isFilterDisabled() {
        return filterDisabled.get();
    }

    /**
     * runFilter checks !isFilterDisabled() and shouldFilter(). The run() method is invoked if both are true.
     *
     * @return the return from ZuulFilterResult
     */
    public ZuulFilterResult runFilter() {
        ZuulFilterResult zr = new ZuulFilterResult();
        if (!isFilterDisabled()) {
            if (shouldFilter()) {
                Tracer t = TracerFactory.instance().startMicroTracer("ZUUL::" + this.getClass().getSimpleName());
                try {
                    Object res = run();
                    zr = new ZuulFilterResult(res, ExecutionStatus.SUCCESS);
                } catch (Throwable e) {
                    t.setName("ZUUL::" + this.getClass().getSimpleName() + " failed");
                    zr = new ZuulFilterResult(ExecutionStatus.FAILED);
                    zr.setException(e);
                } finally {
                    t.stopAndLog();
                }
            } else {
                zr = new ZuulFilterResult(ExecutionStatus.SKIPPED);
            }
        }
        return zr;
    }

    public int compareTo(ZuulFilter filter) {
        return this.filterOrder() - filter.filterOrder();
    }
}
