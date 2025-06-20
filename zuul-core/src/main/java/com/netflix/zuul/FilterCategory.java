/*
 * Copyright 2022 Netflix, Inc.
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

/**
 * Categorization of filters.
 */
public enum FilterCategory {
    ABUSE(
            "abuse",
            "Abuse detection and protection filters, such as rate-limiting, malicious request detection, geo-blocking"),
    ACCESS("access", "Authentication and authorization filters"),
    ADMIN("admin", "Admin only filters providing operational support"),
    CHAOS("chaos", "Failure injection testing and resilience support"),
    CONTEXT_DECORATOR("context-decorator", "Decorate context based on request and detected client"),
    HEALTHCHECK("healthcheck", "Support for healthcheck endpoints"),
    HTTP("http", "Filter operating on HTTP request/response protocol features"),
    ORIGIN("origin", "Origin connectivity filters"),
    OBSERVABILITY("observability", "Filters providing observability features"),
    OVERLOAD("overload", "Filters to respond on the server being in an overloaded state such as brownout"),
    ROUTING("routing", "Filters which make routing decisions"),
    UNSPECIFIED("unspecified", "Default category when no category is specified"),
    ;

    private final String code;
    private final String description;

    FilterCategory(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    @Override
    public String toString() {
        return code;
    }
}
