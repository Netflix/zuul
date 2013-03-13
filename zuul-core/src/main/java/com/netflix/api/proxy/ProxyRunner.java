package com.netflix.api.proxy;

import com.netflix.api.proxy.context.RequestContext;
import com.netflix.api.proxy.exception.ProxyException;
import com.netflix.api.proxy.groovy.GroovyProcessor;
import com.netflix.api.proxy.groovy.ProxyFilter;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.runners.MockitoJUnitRunner;

import javax.servlet.FilterChain;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.PrintWriter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


public class ProxyRunner {
    public ProxyRunner() {
    }

    public void postProxy() throws ProxyException {
        try {
            GroovyProcessor.getInstance().postProcess();
        } finally {
//            RequestContext.getCurrentContext().unset();
//            servletResponse.getWriter().flush();
        }
    }

    public void proxy() throws ProxyException {
        GroovyProcessor.getInstance().proxy();
    }

    public void preProxy() throws ProxyException {
        GroovyProcessor.getInstance().preprocess();
    }

    public void init(HttpServletRequest servletRequest, HttpServletResponse servletResponse) {
        RequestContext.getCurrentContext().setRequest(new ProxyRequestWrapper(servletRequest));
        RequestContext.getCurrentContext().setResponse(servletResponse);
    }

    public void error() {
        GroovyProcessor.getInstance().error();
    }


    @RunWith(MockitoJUnitRunner.class)
    public static class UnitTest {

        @Mock
        ProxyFilter filter;

        @Mock
        HttpServletRequest servletRequest;

        @Mock
        HttpServletResponse servletResponse;

        @Mock
        FilterChain filterChain;

        @Mock
        GroovyProcessor processor;


        @Mock
        PrintWriter writer;

        @Before
        public void before() {
            MockitoAnnotations.initMocks(this);
        }

        @Test
        public void testProcessProxyFilter() {

            ProxyRunner runner = new ProxyRunner();
            runner = spy(runner);
            RequestContext context = spy(RequestContext.getCurrentContext());


            try {
                GroovyProcessor.setProcessor(processor);
                RequestContext.testSetCurrentContext(context);
                when(servletResponse.getWriter()).thenReturn(writer);

                runner.init(servletRequest, servletResponse);
                verify(runner, times(1)).init(servletRequest, servletResponse);
                assertTrue(RequestContext.getCurrentContext().getRequest() instanceof ProxyRequestWrapper);
                assertEquals(RequestContext.getCurrentContext().getResponse(), servletResponse);

                runner.preProxy();
                verify(processor, times(1)).preprocess();

                runner.postProxy();
                verify(processor, times(1)).postProcess();
//                verify(context, times(1)).unset();

                runner.proxy();
                verify(processor, times(1)).proxy();
                RequestContext.testSetCurrentContext(null);

            } catch (Exception e) {
                e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
            }


        }
    }
}