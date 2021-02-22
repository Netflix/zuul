package com.netflix.zuul.guice;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.netflix.config.ConfigurationManager;
import com.netflix.spectator.api.NoopRegistry;
import com.netflix.spectator.api.Registry;
import com.netflix.zuul.DynamicCodeCompiler;
import com.netflix.zuul.DynamicFilterLoader;
import com.netflix.zuul.FilterFileManager;
import com.netflix.zuul.FilterLoader;
import com.netflix.zuul.filters.FilterRegistry;
import com.netflix.zuul.filters.MutableFilterRegistry;
import com.netflix.zuul.init.InitTestModule;
import com.netflix.zuul.init.ZuulFiltersModule;
import java.io.FilenameFilter;
import org.apache.commons.configuration.AbstractConfiguration;
import org.junit.Before;

/**
 * Base Injection Test
 *
 * @author Arthur Gonigberg
 * @since February 22, 2021
 */
public class BaseInjectionTest {
    protected Injector injector = Guice.createInjector(new InitTestModule(), new ZuulFiltersModule());

    @Before
    public void setup () {
        injector.injectMembers(this);
    }
}
