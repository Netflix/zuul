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
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.PrintWriter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Created by IntelliJ IDEA.
 * User: mcohen
 * Date: 12/23/11
 * Time: 10:44 AM
 * To change this template use File | Settings | File Templates.
 */
public class Proxy extends HttpServlet {
    private ProxyRunner proxyRunner = new ProxyRunner();

    public void service(javax.servlet.ServletRequest servletRequest, javax.servlet.ServletResponse servletResponse) throws javax.servlet.ServletException, java.io.IOException {
        try {
            init((HttpServletRequest) servletRequest, (HttpServletResponse) servletResponse);

            // marks this request as having passed throught the "proxy engine", as opposed to servlets
            // explicitly bound in web.xml, for which requests will not have the same data attached
            RequestContext.getCurrentContext().setProxyEngineRan();

            try {
                preProxy();
            } catch (ProxyException e) {
                error(e);
                postProxy();
                return;
            }
            try {
                proxy();
            } catch (ProxyException e) {
                error(e);
                postProxy();
                return;
            }
            try {
                postProxy();
            } catch (ProxyException e) {
                error(e);
                return;
            }

        } catch (Throwable e) {
            error(new ProxyException(e, 500, "UNHANDLED_EXCEPTION_" +e.getClass().getName()));
        } finally {
//            RequestContext.getCurrentContext().unset();
        }
    }


    void postProxy() throws ProxyException {
        proxyRunner.postProxy();
    }

    void proxy() throws ProxyException {
        proxyRunner.proxy();
    }

    void preProxy() throws ProxyException {
        proxyRunner.preProxy();
    }

    void init(HttpServletRequest servletRequest, HttpServletResponse servletResponse) {
        proxyRunner.init(servletRequest, servletResponse);
    }

    void error(ProxyException e) {
        RequestContext.getCurrentContext().setThrowable(e);
        proxyRunner.error();
        e.printStackTrace();
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

            Proxy proxyServlet = new Proxy();
            proxyServlet = spy(proxyServlet);
            RequestContext context = spy(RequestContext.getCurrentContext());


            try {
                GroovyProcessor.setProcessor(processor);
                RequestContext.testSetCurrentContext(context);
                when(servletResponse.getWriter()).thenReturn(writer);

                proxyServlet.init(servletRequest, servletResponse);
                verify(proxyServlet, times(1)).init(servletRequest, servletResponse);
                assertTrue(RequestContext.getCurrentContext().getRequest() instanceof ProxyRequestWrapper);
                assertEquals(RequestContext.getCurrentContext().getResponse(), servletResponse);

                proxyServlet.preProxy();
                verify(processor, times(1)).preprocess();

                proxyServlet.postProxy();
                verify(processor, times(1)).postProcess();
//                verify(context, times(1)).unset();

                proxyServlet.proxy();
                verify(processor, times(1)).proxy();
                RequestContext.testSetCurrentContext(null);

            } catch (Exception e) {
                e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
            }


        }
    }

}
