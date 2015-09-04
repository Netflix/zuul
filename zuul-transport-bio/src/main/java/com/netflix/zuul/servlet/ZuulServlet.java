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
package com.netflix.zuul.servlet;

import com.netflix.zuul.ZuulHttpProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Core Zuul servlet which intializes and orchestrates zuulFilter execution
 *
 * @author Mikey Cohen
 *         Date: 12/23/11
 *         Time: 10:44 AM
 */
@Singleton
public class ZuulServlet extends HttpServlet {
    
    private static final long serialVersionUID = -3374242278843351501L;
    private static Logger LOG = LoggerFactory.getLogger(ZuulServlet.class);

    private ZuulHttpProcessor zuulProcessor;

    @Inject
    public ZuulServlet(ZuulHttpProcessor zuulProcessor)
    {
        super();
        this.zuulProcessor = zuulProcessor;
    }

    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init(config);
    }

    @Override
    public void service(HttpServletRequest servletRequest, HttpServletResponse servletResponse) throws ServletException, IOException
    {
        try {
            zuulProcessor
                    .process(servletRequest, servletResponse)
                    .doOnNext(msg -> {
                        // Store this response as an attribute for any later ServletFilters that may want access to info in it.
                        servletRequest.setAttribute("_zuul_response", msg);
                    })
                    .subscribe();
        }
        catch (Throwable e) {
            LOG.error("Unexpected error running ZuulHttpProcessor for this request.", e);
            throw new ServletException("Unexpected error running ZuulHttpProcessor for this request.");
        }
    }
}
