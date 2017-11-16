package com.netflix.zuul.init;

import static org.junit.Assert.assertEquals;

import com.netflix.governator.guice.test.ModulesForTesting;
import com.netflix.governator.guice.test.junit4.GovernatorJunit4ClassRunner;
import com.netflix.zuul.FilterFileManager.FilterFileManagerConfig;
import javax.inject.Inject;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(GovernatorJunit4ClassRunner.class)
@ModulesForTesting({ ZuulFiltersModule.class })
public class ZuulFiltersModuleIntegTest {
    @Inject
    FilterFileManagerConfig filterFileManagerConfig;

    @Test
    public void scanningWorks() {
        String[] filterLocations = filterFileManagerConfig.getDirectories();
        String[] classNames = filterFileManagerConfig.getClassNames();

        assertEquals(3, filterLocations.length);
        assertEquals("outbound", filterLocations[1]);

        // No good way to set zuul.filters.packages=com.netflix.zuul.init
        //assertEquals(1, classNames.length);
        //assertEquals("com.netflix.zuul.init.TestZuulFilter", classNames[0]);
    }
}
