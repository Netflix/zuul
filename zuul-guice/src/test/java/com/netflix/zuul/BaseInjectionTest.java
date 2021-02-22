/*
 * Copyright 2021 Netflix, Inc.
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
package com.netflix.zuul;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.netflix.zuul.init.InitTestModule;
import com.netflix.zuul.init.ZuulFiltersModule;
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
