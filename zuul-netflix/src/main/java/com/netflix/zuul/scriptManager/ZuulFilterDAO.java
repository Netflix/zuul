package com.netflix.zuul.scriptManager;

import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: mcohen
 * Date: 6/12/12
 * Time: 1:45 PM
 * To change this template use File | Settings | File Templates.
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
