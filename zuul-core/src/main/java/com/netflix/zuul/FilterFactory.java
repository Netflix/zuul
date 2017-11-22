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
package com.netflix.zuul;

import com.netflix.zuul.filters.ZuulFilter;

/**
 * Interface to provide instances of ZuulFilter from a given class.
 */
public interface FilterFactory {
    
    /**
     * Returns an instance of the specified class.
     * 
     * @param clazz the Class to instantiate
     * @return an instance of ZuulFilter
     * @throws Exception if an error occurs
     */
    public ZuulFilter newInstance(Class clazz) throws Exception;
}
