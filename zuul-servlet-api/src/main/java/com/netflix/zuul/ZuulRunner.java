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

import com.netflix.zuul.context.SessionContext;
import com.netflix.zuul.exception.ZuulException;


/**
 * This class initializes holds the SessionContext and wraps the FilterProcessor calls
 * to preRoute(), route(),  postRoute(), and error() methods.
 *
 * It holds state and is intended to be created and discarded on each incoming session.
 *
 * @author mikey@netflix.com
 * @version 1.0
 */
public class ZuulRunner {

    private SessionContext ctx;

    /**
     * Creates a new <code>ZuulRunner</code> instance.
     */
    public ZuulRunner(SessionContext ctx) {
        this.ctx = ctx;
    }

    /**
     * executes "post" filterType  ZuulFilters
     *
     * @throws ZuulException
     */
    public void postRoute() throws ZuulException {
        this.ctx = FilterProcessor.getInstance().postRoute(ctx);
    }

    /**
     * executes "route" filterType  ZuulFilters
     *
     * @throws ZuulException
     */
    public void route() throws ZuulException {
        this.ctx = FilterProcessor.getInstance().route(ctx);
    }

    /**
     * executes "pre" filterType  ZuulFilters
     *
     * @throws ZuulException
     */
    public void preRoute() throws ZuulException {
        this.ctx = FilterProcessor.getInstance().preRoute(ctx);
    }

    /**
     * executes "error" filterType  ZuulFilters
     */
    public void error() {
        this.ctx = FilterProcessor.getInstance().error(ctx);
    }
}