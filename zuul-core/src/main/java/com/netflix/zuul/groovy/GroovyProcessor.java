package com.netflix.zuul.groovy;

import com.netflix.zuul.context.Debug;
import com.netflix.zuul.context.RequestContext;
import com.netflix.zuul.exception.ProxyException;
import com.netflix.zuul.monitoring.MonitoringHelper;
import groovy.lang.Binding;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.runners.MockitoJUnitRunner;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Created by IntelliJ IDEA.
 * User: mcohen
 * Date: 10/24/11
 * Time: 12:47 PM
 * To change this template use File | Settings | File Templates.
 */
public class GroovyProcessor {

    static GroovyProcessor INSTANCE = new GroovyProcessor();

    GroovyProcessor() {
    }

    public static GroovyProcessor getInstance() {
        return INSTANCE;
    }

    public static void setProcessor(GroovyProcessor processor) {
        INSTANCE = processor;
    }


    public void postProcess() throws ProxyException {
        try {
            runFilters("post");
        } catch (Throwable e) {
            if (e instanceof ProxyException) {
                throw (ProxyException) e;
            }
            throw new ProxyException(e, 500, "UNCAUGHT_EXCEPTION_IN_POST_FILTER_" +e.getClass().getName());
        }

    }

    public void error() {
        try {
            runFilters("error");
        } catch (Throwable e) {
            e.printStackTrace();
            //todo weird fallback scenario
        }
    }

    public void proxy() throws ProxyException {
        try {
            runFilters("proxy");
        } catch (Throwable e) {
            if (e instanceof ProxyException) {
                throw (ProxyException) e;
            }
            throw new ProxyException(e, 500, "UNCAUGHT_EXCEPTION_IN_PROXY_FILTER_" +e.getClass().getName());
        }
    }

    public void preprocess() throws ProxyException {
        try {
            runFilters("pre");
        } catch (Throwable e) {
            if (e instanceof ProxyException) {
                throw (ProxyException) e;
            }
            throw new ProxyException(e, 500, "UNCAUGHT_EXCEPTION_IN_PRE_FILTER_" +e.getClass().getName());
        }
    }

    public Object runFilters(String sType) throws Throwable {
        if (RequestContext.getCurrentContext().debugProxy()) {
            Debug.addProxyDebug("Invoking {" + sType + "} type filters");
        }
        boolean bResult = false;
        List<ProxyFilter> list = GroovyLoader.getInstance().getFiltersByType(sType);
        if (list != null) {
            for (int i = 0; i < list.size(); i++) {
                ProxyFilter proxyFilter = list.get(i);
                Object result  = processProxyFilter(proxyFilter);
                if(result != null && result instanceof Boolean){
                    bResult |= ((Boolean)result);
                }
            }
        }
        return bResult;
    }

    public Object processProxyFilter(ProxyFilter filter) throws ProxyException {

        boolean bDebug = RequestContext.getCurrentContext().debugProxy();
        try {
            long ltime = System.currentTimeMillis();
            RequestContext copy = null;
            if (bDebug) {

//                Debug.addProxyDebug("Filter " + filter.filterType() + " " + filter.filterOrder() +  " " +filter.getClass().getSimpleName());
                copy = RequestContext.getCurrentContext().copy();
            }
            Object result = filter.runFilter();
            if (bDebug) {
                if (filter.shouldFilter()) {
                    Debug.addProxyDebug("Filter {" + filter.getClass().getSimpleName() + " TYPE:" + filter.filterType() + " ORDER:" + filter.filterOrder() + "} Execution time = " + (System.currentTimeMillis() - ltime) +"ms");
                    Debug.compareProxyContextState(filter.getClass().getSimpleName(), copy);
                } else {
                    //don't show filters not applied.
                }
            }
            return result;
        } catch (ProxyException e) {
            if (bDebug) {
                Debug.addProxyDebug("Running Filter failed " + filter.getClass().getSimpleName() + " type:" + filter.filterType() + " order:" + filter.filterOrder() +
                        " " + e.getMessage());
            }
            throw e;
        } catch (Throwable e) {
            if (bDebug) {
                Debug.addProxyDebug("Running Filter failed " + filter.getClass().getSimpleName() + " type:" + filter.filterType() + " order:" + filter.filterOrder() +
                        " " + e.getMessage());
            }
            ProxyException ex = new ProxyException(e, "Filter threw Exception", 500, filter.filterType() + ":" + filter.getClass().getSimpleName());
            throw ex;
        }
    }


    @RunWith(MockitoJUnitRunner.class)
    public static class UnitTest {

        @Mock
        ProxyFilter filter;

        @Before
        public void before() {
            MonitoringHelper.mockForTests();
            MockitoAnnotations.initMocks(this);
        }

        @Test
        public void testProcessProxyFilter() {
            GroovyProcessor processor = new GroovyProcessor();
            processor = spy(processor);
            try {
                processor.processProxyFilter(filter);
                verify(processor, times(1)).processProxyFilter(filter);
                verify(filter, times(1)).runFilter();

            } catch (Throwable e) {
                e.printStackTrace();
            }
        }

        @Test
        public void testProcessProxyFilterException() {
            GroovyProcessor processor = new GroovyProcessor();
            processor = spy(processor);

            try {
                when(filter.runFilter()).thenThrow(new Exception("Test"));
                processor.processProxyFilter(filter);
                assertFalse(true);
            } catch (Throwable e) {
                assertEquals(e.getCause().getMessage(), "Test");
            }
        }


        @Test
        public void testPostProcess() {
            GroovyProcessor processor = new GroovyProcessor();
            processor = spy(processor);
            try {
                processor.postProcess();
                verify(processor, times(1)).runFilters("post");
            } catch (Throwable e) {
                e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
            }
        }

        @Test
        public void testPreProcess() {
            GroovyProcessor processor = new GroovyProcessor();
            processor = spy(processor);
            try {
                processor.preprocess();
                verify(processor, times(1)).runFilters("pre");
            } catch (Throwable e) {
                e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
            }
        }

        @Test
        public void testProxyProcess() {
            GroovyProcessor processor = new GroovyProcessor();
            processor = spy(processor);
            try {
                processor.proxy();
                verify(processor, times(1)).runFilters("proxy");
            } catch (Throwable e) {
                e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
            }
        }

        @Test
        public void testProxyProcessHttpException() {
            HttpServletRequest request = mock(HttpServletRequest.class);
            HttpServletResponse response = mock(HttpServletResponse.class);
            RequestContext.getCurrentContext().setRequest(request);
            RequestContext.getCurrentContext().setResponse(response);

            GroovyProcessor processor = new GroovyProcessor();
            processor = spy(processor);
            try {
                when(processor.runFilters("proxy")).thenThrow(new ProxyException("test", 400, "test"));
                processor.proxy();
            } catch (ProxyException e) {
                assertEquals(e.getMessage(), "test");
                assertEquals(e.nStatusCode, 400);
            } catch (Throwable e) {
                e.printStackTrace();
                assertFalse(true);

            }

        }

        @Test
        public void testProxyProcessException() {
            HttpServletRequest request = mock(HttpServletRequest.class);
            HttpServletResponse response = mock(HttpServletResponse.class);
            RequestContext.getCurrentContext().setRequest(request);
            RequestContext.getCurrentContext().setResponse(response);

            GroovyProcessor processor = new GroovyProcessor();
            processor = spy(processor);

            try {
                when(processor.runFilters("proxy")).thenThrow(new Throwable("test"));
                processor.proxy();
            } catch (ProxyException e) {
                assertEquals(e.getMessage(), "test");
                assertEquals(e.nStatusCode, 500);
            } catch (Throwable e) {
                assertFalse(true);
            }

        }

        @Test
        public void testPreProcessException() {
            HttpServletRequest request = mock(HttpServletRequest.class);
            HttpServletResponse response = mock(HttpServletResponse.class);
            RequestContext.getCurrentContext().setRequest(request);
            RequestContext.getCurrentContext().setResponse(response);

            GroovyProcessor processor = new GroovyProcessor();
            processor = spy(processor);

            try {
                when(processor.runFilters("pre")).thenThrow(new Throwable("test"));
                processor.preprocess();
            } catch (ProxyException e) {
                assertEquals(e.getMessage(), "test");
                assertEquals(e.nStatusCode, 500);
            } catch (Throwable e) {
                assertFalse(true);
            }

        }

        @Test
        public void testPreProcessHttpException() {
            HttpServletRequest request = mock(HttpServletRequest.class);
            HttpServletResponse response = mock(HttpServletResponse.class);
            RequestContext.getCurrentContext().setRequest(request);
            RequestContext.getCurrentContext().setResponse(response);

            GroovyProcessor processor = new GroovyProcessor();
            processor = spy(processor);
            try {
                when(processor.runFilters("pre")).thenThrow(new ProxyException("test", 400, "test"));
                processor.preprocess();
            } catch (ProxyException e) {
                assertEquals(e.getMessage(), "test");
                assertEquals(e.nStatusCode, 400);
            } catch (Throwable e) {
                e.printStackTrace();
                assertFalse(true);

            }

        }


        @Test
        public void testPostProcessException() {
            HttpServletRequest request = mock(HttpServletRequest.class);
            HttpServletResponse response = mock(HttpServletResponse.class);
            RequestContext.getCurrentContext().setRequest(request);
            RequestContext.getCurrentContext().setResponse(response);

            GroovyProcessor processor = new GroovyProcessor();
            processor = spy(processor);

            try {
                when(processor.runFilters("post")).thenThrow(new Throwable("test"));
                processor.postProcess();
            } catch (ProxyException e) {
                assertEquals(e.getMessage(), "test");
                assertEquals(e.nStatusCode, 500);
            } catch (Throwable e) {
                assertFalse(true);
            }

        }

        @Test
        public void testPostProcessHttpException() {
            HttpServletRequest request = mock(HttpServletRequest.class);
            HttpServletResponse response = mock(HttpServletResponse.class);
            RequestContext.getCurrentContext().setRequest(request);
            RequestContext.getCurrentContext().setResponse(response);

            GroovyProcessor processor = new GroovyProcessor();
            processor = spy(processor);
            try {
                when(processor.runFilters("post")).thenThrow(new ProxyException("test", 400, "test"));
                processor.postProcess();
            } catch (ProxyException e) {
                assertEquals(e.getMessage(), "test");
                assertEquals(e.nStatusCode, 400);
            } catch (Throwable e) {
                e.printStackTrace();
                assertFalse(true);

            }

        }


        @Test
        public void testErrorException() {
            GroovyProcessor processor = new GroovyProcessor();
            processor = spy(processor);

            try {
                when(processor.runFilters("error")).thenThrow(new Exception("test"));
                processor.error();
                assertTrue(true);
            } catch (Throwable e) {
                assertFalse(true);
            }

        }

        @Test
        public void testErrorHttpException() {
            HttpServletRequest request = mock(HttpServletRequest.class);
            HttpServletResponse response = mock(HttpServletResponse.class);
            RequestContext.getCurrentContext().setRequest(request);
            RequestContext.getCurrentContext().setResponse(response);

            GroovyProcessor processor = new GroovyProcessor();
            processor = spy(processor);
            try {
                when(processor.runFilters("error")).thenThrow(new ProxyException("test", 400, "test"));
                processor.error();
                assertTrue(true);
            } catch (Throwable e) {
                e.printStackTrace();
                assertFalse(true);

            }

        }


    }
}



