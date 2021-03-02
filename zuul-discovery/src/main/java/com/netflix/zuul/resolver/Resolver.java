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

package com.netflix.zuul.resolver;

/**
 * @author Argha C
 * @since 2/25/21
 *
 * Resolves a key to a discovery result type.
 */
public interface Resolver<T> {

    /**
     *
     * @param key unique identifier that may be used by certain resolvers as part of lookup. Implementations
     *            can narrow this down to be nullable.
     * @return the result of a resolver lookup
     */
    //TODO(argha-c) Param needs to be typed, once the ribbon LB lookup API is figured out.
    T resolve(Object key);
}
