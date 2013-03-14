package com.netflix.zuul.filters;


import com.netflix.zuul.ProxyRunner;
import com.netflix.zuul.context.RequestContext;
import com.netflix.zuul.exception.ProxyException;
import com.netflix.zuul.monitoring.MonitoringHelper;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.runners.MockitoJUnitRunner;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;

import static org.junit.Assert.fail;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Created by IntelliJ IDEA.
 * User: mcohen
 * Date: 10/12/11
 * Time: 2:54 PM
 * To change this template use File | Settings | File Templates.
 */
public class GroovyFilter implements Filter {


    private ProxyRunner proxyRunner = new ProxyRunner();

    public void init(FilterConfig filterConfig) throws ServletException {

    }

    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
        try {
            init((HttpServletRequest) servletRequest, (HttpServletResponse) servletResponse);
            try {
                preProxy();
            } catch (ProxyException e) {
                error(e);
                postProxy();
                return;
            }
            filterChain.doFilter(servletRequest, servletResponse);
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
            error(new ProxyException(e, 500, "UNCAUGHT_EXCEPTION_FROM_FILTER_" +e.getClass().getName()));
        } finally {
            RequestContext.getCurrentContext().unset();
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

    public void destroy() {
        //To change body of implemented methods use File | Settings | File Templates.
    }


    @RunWith(MockitoJUnitRunner.class)
    public static class UnitTest {

        @Mock
        HttpServletRequest servletRequest;

        @Mock
        HttpServletResponse servletResponse;

        @Mock
        FilterChain filterChain;
        @Mock
        ProxyRunner proxyRunner;


        @Before
        public void before() {
            MonitoringHelper.mockForTests();
            MockitoAnnotations.initMocks(this);
        }

        @Test
        public void testProxyException() {

            RequestContext.getCurrentContext().setRequest(servletRequest);
            RequestContext.getCurrentContext().setResponse(servletResponse);

            GroovyFilter groovyFilter = new GroovyFilter();
            groovyFilter = spy(groovyFilter);

            try {
                groovyFilter.proxyRunner = proxyRunner;
                when(servletResponse.getWriter()).thenReturn(new PrintWriter("moo"));
                ProxyException e = new ProxyException("test", 510, "test");
                doThrow(e).when(groovyFilter).proxy();

                groovyFilter.doFilter(servletRequest, servletResponse, filterChain);
                verify(groovyFilter, times(1)).proxy();
                verify(groovyFilter, times(1)).error(e);

            } catch (Exception e) {
                e.printStackTrace();
                fail(e.toString());
            }


        }

        @Test
        public void testPreProxyException() {

            GroovyFilter groovyFilter = new GroovyFilter();
            groovyFilter = spy(groovyFilter);
            RequestContext.getCurrentContext().setRequest(servletRequest);
            RequestContext.getCurrentContext().setResponse(servletResponse);


            try {
                groovyFilter.proxyRunner = proxyRunner;
                when(servletResponse.getWriter()).thenReturn(new PrintWriter("moo"));
                ProxyException e = new ProxyException("test", 510, "test");
                doThrow(e).when(groovyFilter).preProxy();

                groovyFilter.doFilter(servletRequest, servletResponse, filterChain);
                verify(groovyFilter, times(1)).preProxy();
                verify(groovyFilter, times(1)).error(e);

            } catch (Exception e) {
                e.printStackTrace();
                fail(e.toString());

            }


        }


        @Test
        public void testPostProxyException() {
            RequestContext.getCurrentContext().setRequest(servletRequest);
            RequestContext.getCurrentContext().setResponse(servletResponse);

            GroovyFilter groovyFilter = new GroovyFilter();
            groovyFilter = spy(groovyFilter);

            try {
                groovyFilter.proxyRunner = proxyRunner;
                when(servletResponse.getWriter()).thenReturn(new PrintWriter("moo"));
                ProxyException e = new ProxyException("test", 510, "test");
                doThrow(e).when(groovyFilter).postProxy();

                groovyFilter.doFilter(servletRequest, servletResponse, filterChain);
                verify(groovyFilter, times(1)).postProxy();
                verify(groovyFilter, times(1)).error(e);

            } catch (Exception e) {
                e.printStackTrace();
                fail(e.toString());
            }


        }


        @Test
        public void testProcessProxyFilter() {

            GroovyFilter groovyFilter = new GroovyFilter();
            groovyFilter = spy(groovyFilter);

            try {

                groovyFilter.proxyRunner = proxyRunner;
                when(servletResponse.getWriter()).thenReturn(new PrintWriter("moo"));
                groovyFilter.doFilter(servletRequest, servletResponse, filterChain);

                verify(proxyRunner, times(1)).init(servletRequest, servletResponse);
                verify(proxyRunner, times(1)).preProxy();
                verify(proxyRunner, times(1)).proxy();
                verify(proxyRunner, times(1)).postProxy();


            } catch (Exception e) {
                e.printStackTrace();
                fail(e.toString());
            }


        }
    }

}
