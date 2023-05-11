/*
 * Copyright 2023 Netflix, Inc.
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

package com.netflix.zuul.spring;

import com.netflix.appinfo.ApplicationInfoManager;
import com.netflix.netty.common.metrics.EventLoopGroupMetrics;
import com.netflix.netty.common.status.ServerStatusManager;
import com.netflix.spectator.api.Registry;
import com.netflix.zuul.DynamicCodeCompiler;
import com.netflix.zuul.FilterFactory;
import com.netflix.zuul.FilterFileManager.FilterFileManagerConfig;
import java.io.File;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.support.GenericApplicationContext;

/**
 * @author Justin Guerra
 * @since 4/14/23
 */
@AutoConfiguration
public class ZuulSpringAutoConfiguration {

    @Bean
    @Primary
    public FilterFactory zuulSpringGetFilterFactory(GenericApplicationContext context) {
        return new SpringFilterFactory(context);
    }

    @Bean
    @ConditionalOnMissingBean(FilterFileManagerConfig.class)
    public FilterFileManagerConfig zuulSpringGetEmptyFilterFileManagerConfig() {
        return new FilterFileManagerConfig(new String[0], new String[0], Integer.MAX_VALUE, (a,b) -> false, false);
    }

    @Bean
    @ConditionalOnMissingBean(ServerStatusManager.class)
    public ServerStatusManager zuulSpringGetServerStatusManager(ApplicationInfoManager applicationInfoManager) {
        return new ServerStatusManager(applicationInfoManager);
    }

    @Bean
    @ConditionalOnMissingBean(EventLoopGroupMetrics.class)
    public EventLoopGroupMetrics zuulSpringGetEventLoopGroupMetrics(Registry registry) {
        return new EventLoopGroupMetrics(registry);
    }

    @Bean
    @ConditionalOnMissingBean(DynamicCodeCompiler.class)
    public DynamicCodeCompiler zuulSpringGetNoOpDynamicCodeCompiler() {
        return new DynamicCodeCompiler() {
            @Override
            public Class<?> compile(String sCode, String sName) {
                return null;
            }

            @Override
            public Class<?> compile(File file) {
                return null;
            }
        };
    }


}
