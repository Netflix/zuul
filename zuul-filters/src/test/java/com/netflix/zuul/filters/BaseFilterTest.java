package com.netflix.zuul.filters;

import com.netflix.zuul.TestZuulFilter;
import com.netflix.zuul.message.ZuulMessage;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.mockito.Mockito.*;

public class BaseFilterTest {
    @Before
    public void before() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testShouldFilter() {

        TestZuulFilter tf1 = spy(new TestZuulFilter());
        TestZuulFilter tf2 = spy(new TestZuulFilter());

        when(tf1.shouldFilter(req)).thenReturn(true);
        when(tf2.shouldFilter(req)).thenReturn(false);
    }

    @Mock
    private BaseFilter f1;
    @Mock
    private BaseFilter f2;
    @Mock
    private ZuulMessage req;
}
