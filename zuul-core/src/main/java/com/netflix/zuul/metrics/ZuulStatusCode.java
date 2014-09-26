/**
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.zuul.metrics;

import com.netflix.numerus.NumerusRollingNumberEvent;

public enum ZuulStatusCode implements NumerusRollingNumberEvent {
    HTTP1xx("1xx", ZuulStatusCodeClass.HTTP1xx),
    HTTP200("200", ZuulStatusCodeClass.HTTP2xx),
    HTTP201("201", ZuulStatusCodeClass.HTTP2xx),
    HTTP202("202", ZuulStatusCodeClass.HTTP2xx),
    HTTP2xx("2xx", ZuulStatusCodeClass.HTTP2xx),
    HTTP301("301", ZuulStatusCodeClass.HTTP3xx),
    HTTP302("302", ZuulStatusCodeClass.HTTP3xx),
    HTTP304("304", ZuulStatusCodeClass.HTTP3xx),
    HTTP3xx("3xx", ZuulStatusCodeClass.HTTP3xx),
    HTTP400("400", ZuulStatusCodeClass.HTTP4xx),
    HTTP401("401", ZuulStatusCodeClass.HTTP4xx),
    HTTP403("403", ZuulStatusCodeClass.HTTP4xx),
    HTTP404("404", ZuulStatusCodeClass.HTTP4xx),
    HTTP410("410", ZuulStatusCodeClass.HTTP4xx),
    HTTP4xx("4xx", ZuulStatusCodeClass.HTTP4xx),
    HTTP500("500", ZuulStatusCodeClass.HTTP5xx),
    HTTP503("503", ZuulStatusCodeClass.HTTP5xx),
    HTTP5xx("5xx", ZuulStatusCodeClass.HTTP5xx),
    INIT("???", ZuulStatusCodeClass.INIT);

    private final String asString;
    private final ZuulStatusCodeClass statusCodeClass;

    private ZuulStatusCode(String s, ZuulStatusCodeClass statusCodeClass) {
        asString = s;
        this.statusCodeClass = statusCodeClass;
    }

    @Override
    public boolean isCounter() {
        return true;
    }

    @Override
    public boolean isMaxUpdater() {
        return false;
    }

    @Override
    public NumerusRollingNumberEvent[] getValues() {
        return values();
    }

    public String str() {
        return asString;
    }

    public static ZuulStatusCode from(final int statusCode) {
        switch (statusCode) {
            case 200: return HTTP200;
            case 201: return HTTP201;
            case 202: return HTTP202;
            case 301: return HTTP301;
            case 302: return HTTP302;
            case 304: return HTTP304;
            case 400: return HTTP400;
            case 401: return HTTP401;
            case 403: return HTTP403;
            case 404: return HTTP404;
            case 410: return HTTP410;
            case 500: return HTTP500;
            case 503: return HTTP503;
            default:
                if (statusCode >= 100 && statusCode < 200) {
                    return HTTP1xx;
                } else if (statusCode >= 200 && statusCode < 300) {
                    return HTTP2xx;
                } else if (statusCode >= 300 && statusCode < 400) {
                    return HTTP3xx;
                } else if (statusCode >= 400 && statusCode < 500) {
                    return HTTP4xx;
                } else return HTTP5xx;
        }
    }

    public ZuulStatusCodeClass getStatusClass() {
        return statusCodeClass;
    }
}
