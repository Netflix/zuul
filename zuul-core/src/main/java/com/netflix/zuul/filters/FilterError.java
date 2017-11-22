/*
 * Copyright 2018 Netflix, Inc.
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
package com.netflix.zuul.filters;

/**
 * User: michaels@netflix.com
 * Date: 5/7/15
 * Time: 10:19 AM
 */
public class FilterError implements Cloneable
{
    private String filterName;
    private String filterType;
    private Throwable exception = null;

    public FilterError(String filterName, String filterType, Throwable exception) {
        this.filterName = filterName;
        this.filterType = filterType;
        this.exception = exception;
    }

    public String getFilterName() {
        return filterName;
    }

    public String getFilterType() {
        return filterType;
    }

    public Throwable getException() {
        return exception;
    }

    @Override
    public Object clone() {
        return new FilterError(filterName, filterType, exception);
    }

    @Override
    public String toString() {
        return "FilterError{" +
                "filterName='" + filterName + '\'' +
                ", filterType='" + filterType + '\'' +
                ", exception=" + exception +
                '}';
    }
}
