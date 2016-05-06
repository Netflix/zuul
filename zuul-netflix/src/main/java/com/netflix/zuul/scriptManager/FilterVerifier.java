/*
 * Copyright 2013 Netflix, Inc.
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

import com.netflix.zuul.ZuulFilter;
import groovy.lang.GroovyClassLoader;
import org.codehaus.groovy.control.CompilationFailedException;

/**
 * verifies that the given source code is compilable in Groovy, can be instanciated, and is a ZuulFilter type
 *
 * @author Mikey Cohen
 *         Date: 6/12/12
 *         Time: 7:12 PM
 */
public class FilterVerifier {
    private static final FilterVerifier INSTANCE = new FilterVerifier();

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
     * @throws CompilationFailedException
     *
     * @throws IllegalAccessException
     * @throws InstantiationException
     */
    public FilterInfo verifyFilter(String sFilterCode) throws CompilationFailedException, IllegalAccessException, InstantiationException {
        Class groovyClass = compileGroovy(sFilterCode);
        Object instance = instanciateClass(groovyClass);
        checkZuulFilterInstance(instance);
        ZuulFilter filter = (ZuulFilter) instance;

        return new FilterInfo(sFilterCode, groovyClass.getSimpleName(), filter);
    }

    Object instanciateClass(Class groovyClass) throws InstantiationException, IllegalAccessException {
        return groovyClass.newInstance();
    }

    void checkZuulFilterInstance(Object zuulFilter) throws InstantiationException {
        if (!(zuulFilter instanceof ZuulFilter)) {
            throw new InstantiationException("Code is not a ZuulFilter Class ");
        }
    }

    /**
     * compiles the Groovy source code
     *
     * @param sFilterCode
     * @return
     * @throws CompilationFailedException
     *
     */
    public Class compileGroovy(String sFilterCode) throws CompilationFailedException {
        GroovyClassLoader loader = new GroovyClassLoader();
        return loader.parseClass(sFilterCode);
    }

}
