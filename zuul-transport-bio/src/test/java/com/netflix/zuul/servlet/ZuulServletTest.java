package com.netflix.zuul.servlet;

import com.netflix.zuul.FilterFileManager;
import com.netflix.zuul.FilterProcessor;
import com.netflix.zuul.ZuulHttpProcessor;
import com.netflix.zuul.context.ServletSessionContextFactory;
import com.netflix.zuul.context.SessionCleaner;
import com.netflix.zuul.context.SessionContext;
import com.netflix.zuul.message.http.HttpRequestMessage;
import com.netflix.zuul.message.http.HttpResponseMessage;
import com.netflix.zuul.message.http.HttpResponseMessageImpl;
import com.netflix.zuul.monitoring.MonitoringHelper;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Matchers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.runners.MockitoJUnitRunner;
import rx.Single;

import javax.servlet.ServletInputStream;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;

@RunWith(MockitoJUnitRunner.class)
public class ZuulServletTest {
    @Mock
    HttpServletRequest servletRequest;
    @Mock
    HttpServletResponse servletResponse;
    @Mock
    ServletOutputStream servletOutputStream;
    @Mock
    FilterProcessor processor;
    @Mock
    FilterFileManager filterFileMgr;
    @Mock
    SessionCleaner sessionCleaner;

    @Mock
    HttpRequestMessage request;

    SessionContext context;
    HttpResponseMessage response;

    ZuulServlet servlet;
    ZuulHttpProcessor zuulProcessor;
    ServletInputStreamWrapper servletInputStream;
    ServletSessionContextFactory contextFactory;


    @Before
    public void before() throws Exception {
        MonitoringHelper.initMocks();
        MockitoAnnotations.initMocks(this);

        contextFactory = new ServletSessionContextFactory();
        zuulProcessor = new ZuulHttpProcessor(processor, contextFactory, null, null, sessionCleaner);
        servlet = new ZuulServlet(zuulProcessor);
        servlet = Mockito.spy(servlet);

        Mockito.when(servletRequest.getHeaderNames()).thenReturn(Collections.<String>emptyEnumeration());
        Mockito.when(servletRequest.getAttributeNames()).thenReturn(Collections.<String>emptyEnumeration());
        servletInputStream = new ServletInputStreamWrapper("{}".getBytes());
        Mockito.when(servletRequest.getInputStream()).thenReturn(servletInputStream);
        Mockito.when(servletResponse.getOutputStream()).thenReturn(servletOutputStream);

        //when(contextFactory.create(context, servletRequest)).thenReturn(Observable.just(request));

        response = new HttpResponseMessageImpl(context, request, 299);
        response.setBody("blah".getBytes());

        Mockito.when(processor.applyInboundFilters(Matchers.any())).thenReturn(Single.just(request));
        Mockito.when(processor.applyEndpointFilter(Matchers.any())).thenReturn(Single.just(response));
        Mockito.when(processor.applyOutboundFilters(Matchers.any())).thenReturn(Single.just(response));
    }

    @Test
    public void testService() throws Exception
    {
        Mockito.when(servletRequest.getMethod()).thenReturn("get");
        Mockito.when(servletRequest.getRequestURI()).thenReturn("/some/where?k1=v1");
        response.getHeaders().set("new", "value");

        servlet.service(servletRequest, servletResponse);

        Mockito.verify(servletResponse).setStatus(299);
        Mockito.verify(servletResponse).addHeader("new", "value");
    }

    static class ServletInputStreamWrapper extends ServletInputStream
    {
        private byte[] data;
        private int idx = 0;

        public ServletInputStreamWrapper(byte[] data) {
            if(data == null) {
                data = new byte[0];
            }

            this.data = data;
        }

        public int read() throws IOException {
            return this.idx == this.data.length?-1:this.data[this.idx++] & 255;
        }
    }
}