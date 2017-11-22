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
package com.netflix.zuul.monitoring;

/**
 * Abstraction layer to provide counter based monitoring.
 *
 * @author mhawthorne
 */
public abstract class CounterFactory {

    private static CounterFactory INSTANCE;

    /**
     * Pass in a CounterFactory Instance. This must be done to use Zuul as Zuul uses several internal counters
     *
     * @param f a <code>CounterFactory</code> value
     */
    public static final void initialize(CounterFactory f) {
        INSTANCE = f;
    }

    /**
     * return the singleton CounterFactory instance.
     *
     * @return a <code>CounterFactory</code> value
     */
    public static final CounterFactory instance() {
        if(INSTANCE == null) throw new IllegalStateException(String.format("%s not initialized", CounterFactory.class.getSimpleName()));
        return INSTANCE;
    }

    /**
     * Increments the counter of the given name
     *
     * @param name a <code>String</code> value
     */
    public abstract void increment(String name);

}
