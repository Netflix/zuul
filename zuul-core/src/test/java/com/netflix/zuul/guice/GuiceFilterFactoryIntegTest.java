package com.netflix.zuul.guice;

import static org.junit.Assert.*;

import com.netflix.governator.guice.test.ModulesForTesting;
import com.netflix.governator.guice.test.junit4.GovernatorJunit4ClassRunner;
import javax.inject.Inject;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(GovernatorJunit4ClassRunner.class)
@ModulesForTesting()
public class GuiceFilterFactoryIntegTest {

    @Inject
    GuiceFilterFactory filterFactory;

    @Test
    public void ctorInjection() throws Exception {
        TestGuiceConstructorFilter filter = (TestGuiceConstructorFilter) filterFactory.newInstance(TestGuiceConstructorFilter.class);

        assertNotNull(filter.injector);
    }

    @Test
    public void fieldInjection() throws Exception {
        TestGuiceFieldFilter filter = (TestGuiceFieldFilter) filterFactory.newInstance(TestGuiceFieldFilter.class);

        assertNotNull(filter.injector);
    }

}