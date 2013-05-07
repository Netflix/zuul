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
    public int nStatusCode;
    public String errorCause;

    /**
     * Source Throwable, message, status code and info about the cause
     * @param throwable
     * @param sMessage
     * @param nStatusCode
     * @param errorCause
     */
    public ZuulException(Throwable throwable, String sMessage, int nStatusCode, String errorCause) {
        super(sMessage, throwable);
        this.nStatusCode = nStatusCode;
        this.errorCause = errorCause;
        incrementCounter("ZUUL::EXCEPTION:" + errorCause + ":" + nStatusCode);
    }

    /**
     * error message, status code and info about the cause
     * @param sMessage
     * @param nStatusCode
     * @param errorCause
     */
    public ZuulException(String sMessage, int nStatusCode, String errorCause) {
        super(sMessage);
        this.nStatusCode = nStatusCode;
        this.errorCause = errorCause;
        incrementCounter("ZUUL::EXCEPTION:" + errorCause + ":" + nStatusCode);

    }

    /**
     * Source Throwable,  status code and info about the cause
     * @param throwable
     * @param nStatusCode
     * @param errorCause
     */
    public ZuulException(Throwable throwable, int nStatusCode, String errorCause) {
        super(throwable.getMessage(), throwable);
        this.nStatusCode = nStatusCode;
        this.errorCause = errorCause;
        incrementCounter("ZUUL::EXCEPTION:" + errorCause + ":" + nStatusCode);

    }

    private static final void incrementCounter(String name) {
        CounterFactory.instance().increment(name);
    }

}
