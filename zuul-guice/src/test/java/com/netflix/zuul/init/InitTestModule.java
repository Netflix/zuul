/*
 * Copyright 2020 Netflix, Inc.
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

package com.netflix.zuul.init;

import com.google.inject.AbstractModule;
import com.netflix.config.ConfigurationManager;
import com.netflix.spectator.api.NoopRegistry;
import com.netflix.spectator.api.Registry;
import java.io.FilenameFilter;
import org.apache.commons.configuration.AbstractConfiguration;

public class InitTestModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(AbstractConfiguration.class).toInstance(ConfigurationManager.getConfigInstance());
        bind(FilenameFilter.class).toInstance((dir, name) -> false);
        bind(Registry.class).to(NoopRegistry.class);
    }

}
