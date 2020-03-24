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

import com.netflix.zuul.filters.FilterType;
import com.netflix.zuul.filters.ZuulFilter;
import java.io.File;
import java.util.List;

/**
 * This class is one of the core classes in Zuul. It compiles, loads from a File, and checks if source code changed.
 * It also holds ZuulFilters by filterType.
 */
public interface FilterLoader {
    /**
     * From a file this will read the ZuulFilter source code, compile it, and add it to the list of current filters
     * a true response means that it was successful.
     *
     * @param file the file to load
     * @return true if the filter in file successfully read, compiled, verified and added to Zuul
     */
    boolean putFilter(File file);

    /**
     * Load and cache filters by className.
     *
     * @param classNames The class names to load
     * @return List of the loaded filters
     * @throws Exception If any specified filter fails to load, this will abort. This is a safety mechanism so we can
     * prevent running in a partially loaded state.
     */
    List<ZuulFilter<?, ?>> putFiltersForClasses(String[] classNames) throws Exception;


    ZuulFilter<?, ?> putFilterForClassName(String className) throws Exception;

    /**
     * Returns a list of filters by the filterType specified.
     */
    List<ZuulFilter<?, ?>> getFiltersByType(FilterType filterType);

    ZuulFilter<?, ?> getFilterByNameAndType(String name, FilterType type);
}
