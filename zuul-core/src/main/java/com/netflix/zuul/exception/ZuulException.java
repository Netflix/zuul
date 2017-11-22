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
package com.netflix.zuul.exception;

/**
 * All handled exceptions in Zuul are ZuulExceptions
 * @author Mikey Cohen
 * Date: 10/20/11
 * Time: 4:33 PM
 */
public class ZuulException extends RuntimeException
{
    private String errorCause;
    private int statusCode = 500;
    private boolean shouldLogAsError = true;

    /**
     * Source Throwable, message, status code and info about the cause
     * @param sMessage
     * @param throwable
     * @param errorCause
     */
    public ZuulException(String sMessage, Throwable throwable, String errorCause) {
        super(sMessage, throwable);
        this.errorCause = errorCause;
    }

    /**
     * error message, status code and info about the cause
     * @param sMessage
     * @param errorCause
     */
    public ZuulException(String sMessage, String errorCause) {
        this(sMessage, errorCause, false);
    }

    public ZuulException(String sMessage, String errorCause, boolean noStackTrace) {
        super(sMessage, null, noStackTrace, ! noStackTrace);
        this.errorCause = errorCause;
    }

    public ZuulException(Throwable throwable, String sMessage, boolean noStackTrace) {
        super(sMessage, throwable, noStackTrace, ! noStackTrace);
        this.errorCause = "GENERAL";
    }

    public ZuulException(Throwable throwable) {
        super(throwable);
        this.errorCause = "GENERAL";
    }

    public ZuulException(String sMessage) {
        this(sMessage, false);
    }

    public ZuulException(String sMessage, boolean noStackTrace) {
        super(sMessage, null, noStackTrace, ! noStackTrace);
        this.errorCause = "GENERAL";
    }

    public int getStatusCode()
    {
        return statusCode;
    }
    public void setStatusCode(int statusCode)
    {
        this.statusCode = statusCode;
    }

    public void dontLogAsError() {
        shouldLogAsError = false;
    }

    public boolean shouldLogAsError() {
        return shouldLogAsError;
    }

    public String getErrorCause() {
        return errorCause;
    }
}
