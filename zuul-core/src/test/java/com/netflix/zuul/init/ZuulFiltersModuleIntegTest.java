package com.netflix.zuul.init;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

import com.netflix.archaius.bridge.StaticAbstractConfiguration;
import com.netflix.archaius.bridge.StaticArchaiusBridgeModule;
import com.netflix.archaius.bridge.StaticDeploymentContext;
import com.netflix.archaius.guice.ArchaiusModule;
import com.netflix.archaius.test.TestPropertyOverride;
import com.netflix.governator.guice.test.ModulesForTesting;
import com.netflix.governator.guice.test.junit4.GovernatorJunit4ClassRunner;
import com.netflix.zuul.FilterFileManager.FilterFileManagerConfig;
import javax.inject.Inject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(GovernatorJunit4ClassRunner.class)
@ModulesForTesting({ ZuulFiltersModule.class, ArchaiusModule.class, StaticArchaiusBridgeModule.class })
public class ZuulFiltersModuleIntegTest {
    @Inject
    FilterFileManagerConfig filterFileManagerConfig;

    @Before
    public void before() throws NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException {
        StaticAbstractConfiguration.reset();
        StaticDeploymentContext.reset();
    }

    @Test
    @TestPropertyOverride({"zuul.filters.packages=com.netflix.zuul.init"})
    public void scanningWorks() {
        String[] filterLocations = filterFileManagerConfig.getDirectories();
        String[] classNames = filterFileManagerConfig.getClassNames();

        assertThat(filterLocations.length, equalTo(3));
        assertThat(filterLocations[1], equalTo("outbound"));

        assertThat(classNames.length, equalTo(1));
        assertThat(classNames[0], equalTo("com.netflix.zuul.init.TestZuulFilter"));
    }
}
