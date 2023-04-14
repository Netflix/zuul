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

import com.netflix.zuul.FilterFactory;
import com.netflix.zuul.filters.ZuulFilter;
import java.util.Objects;
import javax.annotation.Nonnull;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.context.support.GenericApplicationContext;

/**
 * @author Justin Guerra
 * @since 4/14/23
 */
public class SpringFilterFactory implements FilterFactory {

    private final GenericApplicationContext context;

    public SpringFilterFactory(GenericApplicationContext context) {
        this.context = context;
    }

    @Override
    public ZuulFilter<?, ?> newInstance(Class<?> clazz) {
        if(!ZuulFilter.class.isAssignableFrom(Objects.requireNonNull(clazz))) {
            throw new IllegalArgumentException(clazz + "is not a ZuulFilter");
        }

        if(!context.containsBeanDefinition(clazz.getCanonicalName())) {
            AbstractBeanDefinition definition = BeanDefinitionBuilder.rootBeanDefinition(clazz)
                                                                     .getBeanDefinition();
            context.registerBeanDefinition(clazz.getCanonicalName(), definition);
        }
        return context.getBean(clazz.asSubclass(ZuulFilter.class));
    }
}
