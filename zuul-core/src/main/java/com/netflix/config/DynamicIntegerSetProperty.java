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

package com.netflix.config;

import java.util.Set;

public class DynamicIntegerSetProperty extends DynamicSetProperty<Integer>
{
    public DynamicIntegerSetProperty(String propName, String defaultValue) {
        super(propName, defaultValue);
    }

    public DynamicIntegerSetProperty(String propName, String defaultValue, String delimiterRegex) {
        super(propName, defaultValue, delimiterRegex);
    }

    public DynamicIntegerSetProperty(String propName, Set<Integer> defaultValue) {
        super(propName, defaultValue);
    }

    public DynamicIntegerSetProperty(String propName, Set<Integer> defaultValue, String delimiterRegex) {
        super(propName, defaultValue, delimiterRegex);
    }

    @Override
    protected Integer from(String value) {
        return Integer.valueOf(value);
    }
}
