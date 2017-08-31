package com.netflix.zuul.init;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.when;

import org.apache.commons.configuration.AbstractConfiguration;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class ZuulFiltersModuleTest {

    @Mock
    AbstractConfiguration configuration;

    ZuulFiltersModule module = new ZuulFiltersModule();

    @Test
    public void testDefaultFilterLocations() {
        when(configuration.getString(eq("zuul.filters.locations"), anyString())).thenReturn("inbound,outbound,endpoint");

        String[] filterLocations = module.findFilterLocations(configuration);

        assertThat(filterLocations.length, equalTo(3));
        assertThat(filterLocations[1], equalTo("outbound"));
    }

    @Test
    public void testEmptyFilterLocations() {
        when(configuration.getString(eq("zuul.filters.locations"), anyString())).thenReturn("  ");

        String[] filterLocations = module.findFilterLocations(configuration);

        assertThat(filterLocations.length, equalTo(0));
    }

    @Test
    public void testEmptyClassNames() {
        when(configuration.getString(eq("zuul.filters.classes"), anyString())).thenReturn("  ");
        when(configuration.getString(eq("zuul.filters.packages"), anyString())).thenReturn("  ");

        String[] classNames = module.findClassNames(configuration);

        assertThat(classNames.length, equalTo(0));
    }

    @Test
    public void testClassNamesOnly() {

        Class expectedClass = TestZuulFilter.class;

        when(configuration.getString(eq("zuul.filters.classes"), anyString())).thenReturn("com.netflix.zuul.init.TestZuulFilter");
        when(configuration.getString(eq("zuul.filters.packages"), anyString())).thenReturn("");

        String[] classNames = module.findClassNames(configuration);

        assertThat(classNames.length, equalTo(1));
        assertThat(classNames[0], equalTo(expectedClass.getCanonicalName()));

    }

    @Test
    public void testClassNamesPackagesOnly() {

        Class expectedClass = TestZuulFilter.class;

        when(configuration.getString(eq("zuul.filters.classes"), anyString())).thenReturn("");
        when(configuration.getString(eq("zuul.filters.packages"), anyString())).thenReturn("com.netflix.zuul.init");

        String[] classNames = module.findClassNames(configuration);

        assertThat(classNames.length, equalTo(1));
        assertThat(classNames[0], equalTo(expectedClass.getCanonicalName()));

    }
}