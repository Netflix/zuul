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

package com.netflix.zuul.filters.processor;

import com.netflix.zuul.Filter;
import com.netflix.zuul.filters.FilterType;

/**
 * Used to test generated code.
 */
@Filter(order = 20, type = FilterType.INBOUND)
final class TopLevelFilter extends TestFilter {

    @Filter(order = 21, type = FilterType.INBOUND)
    static final class StaticSubclassFilter extends TestFilter {}

    @SuppressWarnings("unused") // This should be ignored by the processor, since it is abstract
    @Filter(order = 22, type = FilterType.INBOUND)
    static abstract class AbstractSubclassFilter extends TestFilter {}

    @SuppressWarnings("InnerClassMayBeStatic") // The purpose of this test
    @Filter(order = 23, type = FilterType.INBOUND)
    final class SubclassFilter extends TestFilter {}

    static {
        // This should be ignored by the processor, since it is private.
        // See https://bugs.openjdk.java.net/browse/JDK-6587158
        @SuppressWarnings("unused")
        @Filter(order = 23, type = FilterType.INBOUND)
        final class MethodClassFilter {}
    }
}
@Filter(order = 24, type = FilterType.INBOUND)
final class OuterClassFilter extends TestFilter {}
