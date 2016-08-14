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
package com.netflix.zuul.exception;

import com.netflix.zuul.monitoring.CounterFactory;

/**
 * All handled exceptions in Zuul are ZuulExceptions
 * @author Mikey Cohen
 * Date: 10/20/11
 * Time: 4:33 PM
 */
public class ZuulException extends Exception {
    public int statusCode;
    public String errorCause;

    /**
     * Source Throwable, message, status code and info about the cause
     * @param throwable
     * @param message
     * @param statusCode
     * @param errorCause
     */
    public ZuulException(Throwable throwable, String message, int statusCode, String errorCause) {
        super(message, throwable);
        this.statusCode = statusCode;
        this.errorCause = errorCause;
        incrementCounter("ZUUL::EXCEPTION:" + errorCause + ":" + statusCode);
    }

    /**
     * error message, status code and info about the cause
     * @param message
     * @param statusCode
     * @param errorCause
     */
    public ZuulException(String message, int statusCode, String errorCause) {
        super(message);
        this.statusCode = statusCode;
        this.errorCause = errorCause;
        incrementCounter("ZUUL::EXCEPTION:" + errorCause + ":" + statusCode);
    }

    /**
     * Source Throwable,  status code and info about the cause
     * @param throwable
     * @param statusCode
     * @param errorCause
     */
    public ZuulException(Throwable throwable, int statusCode, String errorCause) {
        super(throwable.getMessage(), throwable);
        this.statusCode = statusCode;
        this.errorCause = errorCause;
        incrementCounter("ZUUL::EXCEPTION:" + errorCause + ":" + statusCode);
    }

    private static final void incrementCounter(String name) {
        CounterFactory.instance().increment(name);
    }

}
