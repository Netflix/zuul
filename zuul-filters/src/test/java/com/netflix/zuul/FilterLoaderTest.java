package com.netflix.zuul;

import com.netflix.zuul.filters.BaseFilter;
import com.netflix.zuul.filters.FilterRegistry;
import com.netflix.zuul.filters.ZuulFilter;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Matchers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class FilterLoaderTest {

    @Mock
    File file;

    @Mock
    DynamicCodeCompiler compiler;

    @Mock
    FilterRegistry registry;

    FilterLoader loader;

    TestZuulFilter filter = new TestZuulFilter();

    @Before
    public void before() throws Exception
    {
        MockitoAnnotations.initMocks(this);

        loader = Mockito.spy(new FilterLoader());
        loader.setCompiler(compiler);
        loader.setFilterRegistry(registry);

        Mockito.doReturn(TestZuulFilter.class).when(compiler).compile(file);
        Mockito.when(file.getAbsolutePath()).thenReturn("/filters/in/SomeFilter.groovy");
    }

    @Test
    public void testGetFilterFromFile() throws Exception {
        Assert.assertTrue(loader.putFilter(file));
        Mockito.verify(registry).put(Matchers.any(String.class), Matchers.any(BaseFilter.class));
    }

    @Test
    public void testGetFiltersByType() throws Exception {
        Assert.assertTrue(loader.putFilter(file));

        Mockito.verify(registry).put(Matchers.any(String.class), Matchers.any(ZuulFilter.class));

        final List<ZuulFilter> filters = new ArrayList<ZuulFilter>();
        filters.add(filter);
        Mockito.when(registry.getAllFilters()).thenReturn(filters);

        List<ZuulFilter> list = loader.getFiltersByType("test");
        Assert.assertTrue(list != null);
        Assert.assertTrue(list.size() == 1);
        ZuulFilter filter = list.get(0);
        Assert.assertTrue(filter != null);
        Assert.assertTrue(filter.filterType().equals("test"));
    }


    @Test
    public void testGetFilterFromString() throws Exception {
        String string = "";
        Mockito.doReturn(TestZuulFilter.class).when(compiler).compile(string, string);
        ZuulFilter filter = loader.getFilter(string, string);

        Assert.assertNotNull(filter);
        Assert.assertTrue(filter.getClass() == TestZuulFilter.class);
//            assertTrue(loader.filterInstanceMapSize() == 1);
    }
}