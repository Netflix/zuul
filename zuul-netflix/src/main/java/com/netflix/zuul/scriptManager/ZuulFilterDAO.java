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

import java.util.List;

/**
 * @author Mikey Cohen
 * Date: 6/12/12
 * Time: 1:45 PM
 */
public interface ZuulFilterDAO {
    List<String> getAllFilterIDs();

    List<FilterInfo> getScriptsForFilter(String filter_id);

    FilterInfo getScript(String filter_id, int revision);

    FilterInfo getScriptForFilter(String filter_id, int revision);

    FilterInfo getLatestScriptForFilter(String filter_id);

    FilterInfo getActiveScriptForFilter(String filter_id);

    List<FilterInfo> getAllCanaryScripts();

    List<FilterInfo> getAllActiveScripts();


    FilterInfo setCanaryFilter(String filter_id, int revision);


    FilterInfo setScriptActive(String filter_id, int revision) throws Exception;

    FilterInfo deActivateScript(String filter_id, int revision) throws Exception;

    FilterInfo addFilter(String filtercode, String filter_type, String filter_name, String disableFilterPropertyName, String filter_order);

    String getFilterIdsRaw(String index);

    List<String> getFilterIdsIndex(String index);

    }
