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

import com.netflix.zuul.filters.FilterType;

import java.util.List;

/**
 * Interface for data access to persist filters in a persistent store
 * @author Mikey Cohen
 * Date: 6/12/12
 * Time: 1:45 PM
 */
public interface ZuulFilterDAO {
    /**
     *
     * @return a list of all filterIds
     */
    List<String> getAllFilterIDs();

    /**
     * returns all filter revisions for the given filter_id
     * @param filter_id
     * @return returns all filter revisions for the given filter_id
     */
    List<FilterInfo> getZuulFiltersForFilterId(String filter_id);

    /**
     *
     * @param filter_id
     * @param revision
     * @return returns a specific revision for a filter
     */
    FilterInfo getFilterInfo(String filter_id, int revision);

    /**
     *
     * @param filter_id
     * @param revision
     * @return returns a specific revision for a filter
     */
    FilterInfo getFilterInfoForFilter(String filter_id, int revision);

    /**
     *
     * @param filter_id
     * @return returns the latest version of a given filter
     */
    FilterInfo getLatestFilterInfoForFilter(String filter_id);

    /**
     * returns the active filter for a given filter_id
     * @param filter_id
     * @return
     */
    FilterInfo getActiveFilterInfoForFilter(String filter_id);

    /**
     *
     * @return all filters active in the "canary" mode
     */
    List<FilterInfo> getAllCanaryFilters();

    /**
     *
     * @return all active filters
     */
    List<FilterInfo> getAllActiveFilters();

    /**
     * sets a filter and revison as active in a "canary"
     * @param filter_id
     * @param revision
     * @return the filter
     */
    FilterInfo setCanaryFilter(String filter_id, int revision);


    /**
     * sets a filter and revision as active
     * @param filter_id
     * @param revision
     * @return the filter
     * @throws Exception
     */
    FilterInfo setFilterActive(String filter_id, int revision) throws Exception;

    /**
     * Deactiviates a filter; removes it from being active.
     * @param filter_id
     * @param revision
     * @return the filter
     * @throws Exception
     */
    FilterInfo deActivateFilter(String filter_id, int revision) throws Exception;

    /**
     * adds a new filter to the persistent store
     * @param filtercode
     * @param filter_type
     * @param filter_name
     * @param disableFilterPropertyName
     * @param filter_order
     * @return the filter
     */
    FilterInfo addFilter(String filtercode, FilterType filter_type, String filter_name, String disableFilterPropertyName, String filter_order);

    /**
     *
     * @param index
     * @return all filter_ids for a given index as a | delimited list
     */
    String getFilterIdsRaw(String index);

    /**
     *
     * @param index
     * @return returns filter_ids for a given index as a parsed list
     */
    List<String> getFilterIdsIndex(String index);

    }
