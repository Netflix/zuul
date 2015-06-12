/* Copyright 2013 Netflix, Inc.
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


import com.netflix.zuul.context.RequestContext;
import com.netflix.zuul.http.HttpServletRequestWrapper;
import com.netflix.zuul.http.HttpServletResponseWrapper;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.runners.MockitoJUnitRunner;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.PrintWriter;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


/**
 * This class initializes servlet requests and responses into the RequestContext and wraps the com.netflix.zuul.FilterProcessor calls
 * to preRoute(), route(),  postRoute(), and error() methods
 *
 * @author mikey@netflix.com
 * @version 1.0
 */

@RunWith(MockitoJUnitRunner.class)
public class ZuulRunnerTest {
    @Mock
    ZuulFilter filter;

    @Mock
    HttpServletRequest servletRequest;

    @Mock
    HttpServletResponse servletResponse;

    @Mock
    FilterProcessor processor;

    @Mock
    PrintWriter writer;

    public static RequestContext context = spy(RequestContext.getCurrentContext());


    @Before
    public void before() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testProcessZuulFilter() {

        ZuulRunner runner = new ZuulRunner();
        runner = Mockito.spy(runner);

        try {
            FilterProcessor.setProcessor(processor);
            RequestContext.testSetCurrentContext(context);
            when(servletResponse.getWriter()).thenReturn(writer);

            runner.init(servletRequest, servletResponse);
            verify(runner, times(1)).init(servletRequest, servletResponse);
            assertTrue(RequestContext.getCurrentContext().getRequest() instanceof HttpServletRequestWrapper);
            assertTrue(RequestContext.getCurrentContext().getResponse() instanceof HttpServletResponseWrapper);

            runner.preRoute();
            verify(processor, times(1)).preRoute();

            runner.postRoute();
            verify(processor, times(1)).postRoute();

            runner.route();
            verify(processor, times(1)).route();
            RequestContext.testSetCurrentContext(null);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}