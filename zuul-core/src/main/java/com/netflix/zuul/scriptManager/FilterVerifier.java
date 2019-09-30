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
package com.netflix.zuul.scriptManager;

import com.google.common.annotations.VisibleForTesting;
import com.netflix.zuul.ZuulApplicationInfo;
import com.netflix.zuul.filters.BaseFilter;
import groovy.lang.GroovyClassLoader;

/**
 * verifies that the given source code is compilable in Groovy, can be instanciated, and is a ZuulFilter type
 *
 * @author Mikey Cohen
 *         Date: 6/12/12
 *         Time: 7:12 PM
 */
public class FilterVerifier {
    @VisibleForTesting
    static final FilterVerifier INSTANCE = new FilterVerifier();

    /**
     * @return Singleton
     */
    public static FilterVerifier getInstance() {
        return INSTANCE;
    }

    /**
     * verifies compilation, instanciation and that it is a ZuulFilter
     *
     * @param sFilterCode
     * @return a FilterInfo object representing that code
     * @throws org.codehaus.groovy.control.CompilationFailedException
     *
     * @throws IllegalAccessException
     * @throws InstantiationException
     */
    public FilterInfo verifyFilter(String sFilterCode) throws org.codehaus.groovy.control.CompilationFailedException, IllegalAccessException, InstantiationException {
        Class groovyClass = compileGroovy(sFilterCode);
        Object instance = instanciateClass(groovyClass);
        checkZuulFilterInstance(instance);
        BaseFilter filter = (BaseFilter) instance;


        String filter_id = FilterInfo.buildFilterID(ZuulApplicationInfo.getApplicationName(), filter.filterType(), groovyClass.getSimpleName());

        return new FilterInfo(filter_id, sFilterCode, filter.filterType(), groovyClass.getSimpleName(), filter.disablePropertyName(), "" + filter.filterOrder(), ZuulApplicationInfo.getApplicationName());
    }

    Object instanciateClass(Class groovyClass) throws InstantiationException, IllegalAccessException {
        return groovyClass.newInstance();
    }

    void checkZuulFilterInstance(Object zuulFilter) throws InstantiationException {
        if (!(zuulFilter instanceof BaseFilter)) {
            throw new InstantiationException("Code is not a ZuulFilter Class ");
        }
    }

    /**
     * compiles the Groovy source code
     *
     * @param sFilterCode
     * @return
     * @throws org.codehaus.groovy.control.CompilationFailedException
     *
     */
    public Class compileGroovy(String sFilterCode) throws org.codehaus.groovy.control.CompilationFailedException {
        GroovyClassLoader loader = new GroovyClassLoader();
        return loader.parseClass(sFilterCode);
    }
}


