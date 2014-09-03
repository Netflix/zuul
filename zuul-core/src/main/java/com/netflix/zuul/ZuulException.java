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
package com.netflix.zuul;

public class ZuulException extends Throwable {
    private static final long serialVersionUID = 1681070811387639304L;

    private final int statusCode;
    private final String causeType;

    public ZuulException(String msg, int statusCode) {
        super(msg);
        this.statusCode = statusCode;
        this.causeType = null;
    }

    public ZuulException(String msg, int statusCode, String causeType) {
        super(msg);
        this.statusCode = statusCode;
        this.causeType = causeType;
    }

    public ZuulException(String msg, Throwable cause, int statusCode) {
        super(msg, cause);
        this.statusCode = statusCode;
        this.causeType = null;
    }

    public ZuulException(String msg, Throwable cause, int statusCode, String causeType) {
        super(msg, cause);
        this.statusCode = statusCode;
        this.causeType = causeType;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getCauseType() {
        return causeType;
    }
}
