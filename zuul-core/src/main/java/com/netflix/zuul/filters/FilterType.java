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
 * User: Mike Smith
 * Date: 11/13/15
 * Time: 7:50 PM
 */
public enum FilterType
{
    INBOUND("in"), ENDPOINT("end"), OUTBOUND("out");

    private final String shortName;

    private FilterType(String shortName) {
        this.shortName = shortName;
    }

    @Override
    public String toString()
    {
        return shortName;
    }

    public static FilterType parse(String str)
    {
        str = str.toLowerCase();
        switch (str) {
        case "in":
            return INBOUND;
        case "out":
            return OUTBOUND;
        case "end":
            return ENDPOINT;
        default:
            throw new IllegalArgumentException("Unknown filter type! type=" + String.valueOf(str));
        }
    }
}
