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
package com.netflix.zuul.filters

import com.netflix.zuul.context.RequestContext
import com.netflix.zuul.groovy.ZuulFilter
import java.util.regex.Pattern
import com.netflix.zuul.groovy.ZuulFilter

/**
 * @author Mikey Cohen
 * Date: 2/2/12
 * Time: 1:34 PM
 */
public abstract class StaticResponseFilter extends ZuulFilter {

    abstract def uri()

    abstract String responseBody()

    @Override
    String filterType() {
        return "static"
    }

    @Override
    int filterOrder() {
        return 0
    }

    boolean shouldFilter() {
        String path = RequestContext.currentContext.getRequest().getRequestURI()
        if (checkPath(path)) return true
        if (checkPath("/" + path)) return true
        return false
    }

    boolean checkPath(String path) {
        def uri = uri()
        if(uri instanceof String){
            return uri.equals(path)
        } else if(uri instanceof List){
           return uri.contains(path)
        } else if(uri instanceof Pattern){
            return uri.matcher(path).matches();
        }
        return false;
    }

    @Override
    Object run() {
        RequestContext ctx = RequestContext.getCurrentContext();
        // first StaticResponseFilter instance to match wins, others do not set body and/or status
        if(ctx.getResponseBody() == null) {
            ctx.setResponseBody(responseBody())
            ctx.sendProxyResponse = false;
        }
    }

}
