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
package com.netflix.zuul.filters;


import com.netflix.zuul.ZuulRunner;
import com.netflix.zuul.context.RequestContext;
import com.netflix.zuul.exception.ZuulException;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Zuul Servlet filter to run Zuul within a Servlet Filter. The filter invokes pre-routing filters first,
 * then routing filters, then post routing filters. Handled exceptions in pre-routing and routing
 * call the error filters, then call post-routing filters. Errors in post-routing only invoke the error filters.
 * Unhandled exceptions only invoke the error filters
 *
 * @author Mikey Cohen
 *         Date: 10/12/11
 *         Time: 2:54 PM
 */
public class ZuulServletFilter implements Filter {

    private ZuulRunner zuulRunner;

    public void init(FilterConfig filterConfig) throws ServletException {

        String bufferReqsStr = filterConfig.getInitParameter("buffer-requests");
        boolean bufferReqs = bufferReqsStr != null && bufferReqsStr.equals("true") ? true : false;

        zuulRunner = new ZuulRunner(bufferReqs);
    }

    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
        try {
            init((HttpServletRequest) servletRequest, (HttpServletResponse) servletResponse);
            try {
                preRouting();
            } catch (ZuulException e) {
                error(e);
                postRouting();
                return;
            }
            filterChain.doFilter(servletRequest, servletResponse);
            try {
                routing();
            } catch (ZuulException e) {
                error(e);
                postRouting();
                return;
            }
            try {
                postRouting();
            } catch (ZuulException e) {
                error(e);
                return;
            }
        } catch (Throwable e) {
            error(new ZuulException(e, 500, "UNCAUGHT_EXCEPTION_FROM_FILTER_" + e.getClass().getName()));
        } finally {
            RequestContext.getCurrentContext().unset();
        }
    }

    void postRouting() throws ZuulException {
        zuulRunner.postRoute();
    }

    void routing() throws ZuulException {
        zuulRunner.route();
    }

    void preRouting() throws ZuulException {
        zuulRunner.preRoute();
    }

    void init(HttpServletRequest servletRequest, HttpServletResponse servletResponse) {
        zuulRunner.init(servletRequest, servletResponse);
    }

    void error(ZuulException e) {
        RequestContext.getCurrentContext().setThrowable(e);
        zuulRunner.error();
    }

    public void destroy() {

    }

    public ZuulRunner getZuulRunner() {
        return zuulRunner;
    }

    public void setZuulRunner(ZuulRunner zuulRunner) {
        this.zuulRunner = zuulRunner;
    }
}
