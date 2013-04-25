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
package com.netflix.zuul.monitoring;

/**
 * Abstraction layer to provide counter based monitoring.
 *
 * @author mhawthorne
 */
public abstract class CounterFactory {

    private static CounterFactory INSTANCE;

    public static final void initialize(CounterFactory f) {
        INSTANCE = f;
    }

    public static final CounterFactory instance() {
        if(INSTANCE == null) throw new IllegalStateException(String.format("%s not initialized", CounterFactory.class.getSimpleName()));
        return INSTANCE;
    }

    public abstract void increment(String name);

}
