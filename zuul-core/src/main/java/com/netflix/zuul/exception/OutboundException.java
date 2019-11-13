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

import com.netflix.zuul.niws.RequestAttempt;
import com.netflix.zuul.niws.RequestAttempts;


/**
 * Outbound Exception Decorator
 *
 * User: Mike Smith
 * Date: 10/21/15
 * Time: 11:46 AM
 */
public class OutboundException extends ZuulException
{
    private final ErrorType outboundErrorType;
    private final RequestAttempts requestAttempts;

    public OutboundException(ErrorType outboundErrorType, RequestAttempts requestAttempts)
    {
        super(outboundErrorType.toString(), outboundErrorType.toString(), true);
        this.outboundErrorType = outboundErrorType;
        this.requestAttempts = requestAttempts;
        this.setStatusCode(outboundErrorType.getStatusCodeToReturn());
        this.dontLogAsError();
    }

    public OutboundException(ErrorType outboundErrorType, RequestAttempts requestAttempts, Throwable cause)
    {
        super(outboundErrorType.toString(), cause.getMessage(), true);
        this.outboundErrorType = outboundErrorType;
        this.requestAttempts = requestAttempts;
        this.setStatusCode(outboundErrorType.getStatusCodeToReturn());
        this.dontLogAsError();
    }

    public RequestAttempt getFinalRequestAttempt()
    {
        return requestAttempts == null ? null : requestAttempts.getFinalAttempt();
    }


    public ErrorType getOutboundErrorType()
    {
        return outboundErrorType;
    }

}
