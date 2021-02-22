/*
 * Copyright 2018 Netflix, Inc.
 *
 *      Licensed under the Apache License, Version 2.0 (the "License");
 *      you may not use this file except in compliance with the License.
 *      You may obtain a copy of the License at
 *
 *          http://www.apache.org/licenses/LICENSE-2.0
 *
 *      Unless required by applicable law or agreed to in writing, software
 *      distributed under the License is distributed on an "AS IS" BASIS,
 *      WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *      See the License for the specific language governing permissions and
 *      limitations under the License.
 */

package com.netflix.zuul.guice;

import static org.junit.Assert.assertNotNull;

import com.google.inject.Inject;
import com.netflix.zuul.BaseInjectionIntegTest;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.BlockJUnit4ClassRunner;

@RunWith(BlockJUnit4ClassRunner.class)
public class GuiceFilterFactoryIntegTest extends BaseInjectionIntegTest {

    @Inject
    private GuiceFilterFactory filterFactory;

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