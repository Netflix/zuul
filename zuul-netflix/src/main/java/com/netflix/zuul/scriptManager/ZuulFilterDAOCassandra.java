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

import com.netflix.astyanax.Keyspace;
import com.netflix.astyanax.model.ColumnList;
import com.netflix.astyanax.model.Row;
import com.netflix.astyanax.model.Rows;
import com.netflix.zuul.FilterId;
import com.netflix.zuul.ZuulApplicationInfo;
import com.netflix.zuul.event.ZuulEvent;
import net.jcip.annotations.ThreadSafe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Observable;

/**
 * from zuul_filters
 * DAO for CRUD operations on filter scripts.
 */
@ThreadSafe
public class ZuulFilterDAOCassandra extends Observable implements ZuulFilterDAO {
    private static final Logger logger = LoggerFactory.getLogger(ZuulFilterDAOCassandra.class);

    private final CassandraGateway cassandraGateway;

    private final static String APPLICATION_SCRIPTS = "APPLICATION_";
    private final static String ACTIVE_SCRIPTS = "ACTIVE_";
    private final static String CANARY_SCRIPTS = "CANARY_";
    private final static String SCRIPTS_FOR_FILTER = "FILTERSCRIPTS_";
    private final static String FILTER_ID = "FILTER_ID_";

    static Keyspace keyspace;

    public static Keyspace getCassKeyspace() {
        return keyspace;
    }


    public ZuulFilterDAOCassandra(Keyspace keyspace) {
        this(new CassandraGatewayProd(keyspace));
        this.keyspace = keyspace;
    }


    public ZuulFilterDAOCassandra(CassandraGateway cassandraGateway) {
        this.cassandraGateway = cassandraGateway;
    }

    public void addFilterIdToIndex(String index, String filter_id) {
        String filterIds = getFilterIdsRaw(index);
        if (filterIds.contains(filter_id)) return;
        if ("".equals(filterIds)) {
            filterIds += filter_id;
        } else {
            filterIds += "|" + filter_id;
        }
        cassandraGateway.updateFilterIndex(index, filterIds);
    }

    public String getFilterIdsRaw(String index) {
        Rows<String, String> result = cassandraGateway.select("select filter_ids from zuul_filter_indices where index_name = '" + index + "'");
        if (result == null || result.isEmpty()) {
            return "";
        } else {
            Iterator<Row<String, String>> iterator = result.iterator();
            if (iterator.hasNext()) {
                Row<String, String> row = iterator.next();
                try {
                    String filter_ids = row.getColumns().getColumnByName("filter_ids").getStringValue();
                    if (filter_ids == null) return "";
                    return filter_ids;
                } catch (Exception e) {
                    // unable to retrieve data for this row, could be missing the uri column (which shouldn't happen)
                    logger.warn("Unable to retrieve uri for row", e);
                }
            }
            return "";
        }
    }


    public List<String> getFilterIdsIndex(String index) {

        String filter_ids = getFilterIdsRaw(index);
        if (filter_ids == null || "".equals(filter_ids)) {
            return new ArrayList<String>();
        }

        String[] aFilterIds = filter_ids.split("[|]");
        List l = Arrays.asList(aFilterIds);
        ArrayList<String> list = new ArrayList<String>();
        list.addAll(l);
        return list;

    }

    public List<FilterInfo> getFiltersForIndex(String index) {

        List<String> filterInfoList = getFilterIdsIndex(index);
        if (filterInfoList.isEmpty()) {
            return Collections.emptyList();
        }
        Rows<String, String> result = cassandraGateway.getByFilterIds(filterInfoList);
        if (result == null || result.isEmpty()) {
            return Collections.emptyList();
        } else {
            List<FilterInfo> filterInfos = new ArrayList<FilterInfo>();
            Iterator<Row<String, String>> rows = result.iterator();
            while (rows.hasNext()) {
                Row<String, String> row = rows.next();
                FilterInfo script = getFilterScriptFromCassandraRow(row);
                if (script != null) {
                    filterInfos.add(script);
                }
            }
            Collections.sort(filterInfos);
            return filterInfos;
        }
    }

    @Override
    public List<String> getAllFilterIDs() {
        return getFilterIdsIndex(FILTER_ID + ZuulApplicationInfo.getApplicationName());
    }

    public FilterInfo getFilterInfo(String filter_id, int revision) {
        List<FilterInfo> filters = getZuulFiltersForFilterId(filter_id);
        if (filters == null) return null;
        for (FilterInfo filter : filters) {
            if (filter.getRevision() == revision) return filter;
        }
        return null;
    }

    private String getScriptsForFilterIndexKey(String filter_id) {
        return SCRIPTS_FOR_FILTER + ZuulApplicationInfo.getApplicationName() + "_" + filter_id;
    }

    @Override
    public List<FilterInfo> getZuulFiltersForFilterId(String filter_id) {

        List<FilterInfo> filterInfos = getFiltersForIndex(getScriptsForFilterIndexKey(filter_id));
        if (filterInfos == null) return Collections.emptyList();
        if (filterInfos.size() == 0) return filterInfos;
        Collections.sort(filterInfos);
        return filterInfos;
    }

    @Override
    public FilterInfo getFilterInfoForFilter(String filter_id, int revision) {
        return getFilterInfo(filter_id, revision);
    }

    @Override
    public FilterInfo getLatestFilterInfoForFilter(String filter_id) {
        int largestRevision = 0;
        FilterInfo latestfilterInfo = null;
        List<FilterInfo> filterInfos = getFiltersForIndex(getScriptsForFilterIndexKey(filter_id));
        if (filterInfos == null) return null;
        if (filterInfos.size() == 0) return null;
        for (Iterator<FilterInfo> iterator = filterInfos.iterator(); iterator.hasNext(); ) {
            FilterInfo filterInfo = iterator.next();
            if (filterInfo.getRevision() > largestRevision) {
                largestRevision = filterInfo.getRevision();
                latestfilterInfo = filterInfo;
            }
        }
        return latestfilterInfo;
    }

    @Override
    public FilterInfo getActiveFilterInfoForFilter(String filter_id) {
        List<FilterInfo> filterInfos = getFiltersForIndex(ACTIVE_SCRIPTS + ZuulApplicationInfo.getApplicationName());

        for (int i = 0; i < filterInfos.size(); i++) {
            FilterInfo filterInfo = filterInfos.get(i);
            if (filterInfo.getFilterID().equals(filter_id)) return filterInfo;
        }
        return null;
    }

    public FilterInfo getCanaryScriptForFilter(String filter_id) {
        List<FilterInfo> filterInfos = getFiltersForIndex(CANARY_SCRIPTS + ZuulApplicationInfo.getApplicationName());

        for (int i = 0; i < filterInfos.size(); i++) {
            FilterInfo filterInfo = filterInfos.get(i);
            if (filterInfo.getFilterID().equals(filter_id)) return filterInfo;
        }
        return null;
    }


    @Override
    public List<FilterInfo> getAllCanaryFilters() {

        List<FilterInfo> filterInfos = getFiltersForIndex(CANARY_SCRIPTS + ZuulApplicationInfo.getApplicationName());
        if (filterInfos == null || filterInfos.size() == 0) {
            return Collections.emptyList();
        } else {
            Collections.sort(filterInfos);
            return filterInfos;

        }
    }

    @Override
    public List<FilterInfo> getAllActiveFilters() {
        List<FilterInfo> filterInfos = getFiltersForIndex(ACTIVE_SCRIPTS + ZuulApplicationInfo.getApplicationName());
        if (filterInfos == null || filterInfos.size() == 0) {
            return Collections.emptyList();
        } else {
            Collections.sort(filterInfos);
            return filterInfos;

        }
    }

    /**
     * Utility method for pulling data from Cassandra Row into an FilterInfo object
     *
     * @param row
     * @return
     */
    public FilterInfo getFilterScriptFromCassandraRow(Row<String, String> row) {
        String filterName = null;
        int revision = -1;
        try {
            ColumnList<String> columns = row.getColumns();

            filterName = columns.getColumnByName("filter_name").getStringValue();
            String filter_id = columns.getColumnByName("filter_id").getStringValue();
            String filterType = columns.getColumnByName("filter_type").getStringValue();
            String filterDisable = columns.getColumnByName("filter_disable") != null ? columns.getColumnByName("filter_disable").getStringValue() : "?";
            String filterOrder = columns.getColumnByName("filter_order") != null ? columns.getColumnByName("filter_order").getStringValue() : "?";
            revision = (int) columns.getColumnByName("revision").getLongValue();
            boolean isActive = columns.getColumnByName("active").getBooleanValue();
            boolean isCanary = columns.getColumnByName("canary").getBooleanValue();
            Date creationDate = columns.getColumnByName("creation_date").getDateValue();
            String filterCode = new String(columns.getColumnByName("filter_code").getByteArrayValue());
            String application_name = columns.getColumnByName("application_name").getStringValue();

            FilterInfo filterInfo = new FilterInfo(filter_id, revision, creationDate, isActive, isCanary, filterCode, filterType, filterName, filterDisable, filterOrder, application_name);
            return filterInfo;
        } catch (Exception e) {
            // unable to retrieve data for this row, could be missing the uri column (which shouldn't happen)
            logger.warn("Unable to retrieve data from row => uri : " + filterName + "  revision: " + revision + "  row: " + row, e);
            return null;
        }
    }

    @Override
    public FilterInfo addFilter(String filtercode, String filter_type, String filter_name, String disableFilterPropertyName, String filter_order) {
        String filter_id = buildFilterID(filter_type, filter_name);
        FilterInfo latest = getLatestFilterInfoForFilter(filter_id);
        int revision = 1;
        if (latest != null) {
            revision = latest.getRevision() + 1;
        }
        // set attributes to be columns in the row
        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put("filter_name", filter_name);
        attributes.put("filter_type", filter_type);
        attributes.put("filter_id", filter_id);
        attributes.put("revision", (long) revision);
        attributes.put("active", false); // always false when inserting a new script
        attributes.put("canary", false); // always false when inserting a new script
        attributes.put("creation_date", Calendar.getInstance().getTime());
        // add each script as a separate column
        attributes.put("filter_code", filtercode.getBytes());
        attributes.put("filter_disable", disableFilterPropertyName);
        attributes.put("filter_order", filter_order);
        attributes.put("application_name", ZuulApplicationInfo.getApplicationName());

        cassandraGateway.upsert(filter_id + "_" + revision, attributes);
        List<String> filterIds = getFilterIdsIndex(FILTER_ID + ZuulApplicationInfo.getApplicationName());
        if (!filterIds.contains(filter_id)) {
            addFilterIdToIndex(FILTER_ID + ZuulApplicationInfo.getApplicationName(), filter_id);
        }
        addFilterIdToIndex(APPLICATION_SCRIPTS + ZuulApplicationInfo.getApplicationName(), filter_id + "_" + revision);
        addFilterIdToIndex(getScriptsForFilterIndexKey(filter_id), filter_id + "_" + revision);


        /*
        * now we will retrieve it and return it (I do this instead of building the object from what I have above for the following reasons ...
        * [1] I'm lazy
        * [2] It acts as a test to ensure that what was entered can be retrieved correctly and that we return exactly what ended up in Cassandra
        * [3] Cassandra doesn't have transactions, so the above logic could have had a race scenario and another thread/instance/client over-written us so this will return whatever is actually in Cassandra
        */
        return getFilterInfoForFilter(filter_id, revision);
    }

    public static String buildFilterID(String filter_type, String filter_name) {
        return new FilterId.Builder().filterType(filter_type)
                                     .filterName(filter_name)
                                     .build()
                                     .toString();
    }

    @Override
    public FilterInfo setCanaryFilter(String filter_id, int revision) {

        ArrayList<Integer> revisionsToDeactivate = new ArrayList<Integer>();

        FilterInfo filterInfo = getCanaryScriptForFilter(filter_id);

        if (filterInfo != null) {
            revisionsToDeactivate.add(filterInfo.getRevision());
            removeFilterIdFromIndex(ACTIVE_SCRIPTS + ZuulApplicationInfo.getApplicationName(), filter_id + "_" + filterInfo.getRevision());
            removeFilterIdFromIndex(CANARY_SCRIPTS + ZuulApplicationInfo.getApplicationName(), filter_id + "_" + filterInfo.getRevision());
        }

        /* activate the revision */
        Map<String, Object> attributesForActivation = new HashMap<String, Object>();
        attributesForActivation.put("canary", true);
        attributesForActivation.put("active", false);
        cassandraGateway.upsert(filter_id + "_" + revision, attributesForActivation);


        addFilterIdToIndex(CANARY_SCRIPTS + ZuulApplicationInfo.getApplicationName(), filter_id + "_" + revision);


        /* de-activate previously active revisions */
        // do this AFTER activating so we don't allow a period where no active scripts will respond
        for (int revisionToDeactivate : revisionsToDeactivate) {
            // do NOT deactivate if we just activated this (can occur if someone calls this twice in a row, cleaning up bad data, etc)
            if (revisionToDeactivate != revision) {
                Map<String, Object> attributesForDeactivation = new HashMap<String, Object>();
                attributesForDeactivation.put("canary", false);
                cassandraGateway.upsert(filter_id + "_" + revisionToDeactivate, attributesForDeactivation);
            }
        }
        setChanged();
        notifyObservers(new ZuulEvent("ZUUL_SCRIPT_CHANGE", "CANARY FILTER SET id = " + filter_id + "revision = " + revision));
        return getFilterInfoForFilter(filter_id, revision);
    }

    private void removeFilterIdFromIndex(String index, String filter_id) {
        List<String> filters = getFilterIdsIndex(index);
        if (filters.contains(filter_id)) {
            filters.remove(filter_id);
            String filterList = toFilterList(filters);
            cassandraGateway.updateFilterIndex(index, filterList);
        }
    }


    private String toFilterList(List<String> filterList) {
        String list = "";
        for (Iterator<String> iterator = filterList.iterator(); iterator.hasNext(); ) {
            String filter_info = iterator.next();
            list += filter_info;
            if (iterator.hasNext()) {
                list += "|";
            }
        }
        return list;

    }

    @Override
    public FilterInfo setFilterActive(String filter_id, int revision) throws Exception {

        FilterInfo filter = getFilterInfo(filter_id, revision);
        if (filter == null) throw new Exception("Filter not Found " + filter_id + "revision:" + revision);

        //make filters be canaried before they are activated
        if ("prod".equals(System.getenv("netflix.environment"))) {
            if (!filter.isCanary()) {
                throw new Exception("Filter must be canaried before activated " + filter_id + "revision:" + revision);
            }
        }
        ArrayList<Integer> revisionsToDeactivate = new ArrayList<Integer>();


        FilterInfo filterInfo = getActiveFilterInfoForFilter(filter_id);
        if (filterInfo != null) {
            revisionsToDeactivate.add(filterInfo.getRevision());
            removeFilterIdFromIndex(ACTIVE_SCRIPTS + ZuulApplicationInfo.getApplicationName(), filter_id + "_" + filterInfo.getRevision());
        }

        removeFilterIdFromIndex(CANARY_SCRIPTS + ZuulApplicationInfo.getApplicationName(), filter_id + "_" + revision);

        addFilterIdToIndex(ACTIVE_SCRIPTS + ZuulApplicationInfo.getApplicationName(), filter_id + "_" + revision);


        /* activate the revision */
        Map<String, Object> attributesForActivation = new HashMap<String, Object>();
        attributesForActivation.put("active", true);
        attributesForActivation.put("canary", false);
        cassandraGateway.upsert(filter_id + "_" + revision, attributesForActivation);

        /* de-activate previously active revisions */
        // do this AFTER activating so we don't allow a period where no active scripts will respond
        for (int revisionToDeactivate : revisionsToDeactivate) {
            // do NOT deactivate if we just activated this (can occur if someone calls this twice in a row, cleaning up bad data, etc)
            if (revisionToDeactivate != revision) {
                Map<String, Object> attributesForDeactivation = new HashMap<String, Object>();
                attributesForDeactivation.put("active", false);
                cassandraGateway.upsert(filter_id + "_" + revisionToDeactivate, attributesForDeactivation);
            }
        }
        setChanged();
        notifyObservers(new ZuulEvent("ZUUL_SCRIPT_CHANGE", "ACTIVATED NEW ZUUL FILTER id = " + filter_id + " revision = " + revision));
        return getFilterInfoForFilter(filter_id, revision);
    }

    @Override
    public FilterInfo deActivateFilter(String filter_id, int revision) throws Exception {

        FilterInfo filter = getFilterInfo(filter_id, revision);
        if (filter == null) throw new Exception("Filter not Found " + filter_id + "revision:" + revision);


        if (!filter.isCanary() && !filter.isActive()) {
            throw new Exception("Filter must be canary or active to deactivate" + filter_id + "revision:" + revision);
        }
        removeFilterIdFromIndex(ACTIVE_SCRIPTS + ZuulApplicationInfo.getApplicationName(), filter_id + "_" + filter.getRevision());
        removeFilterIdFromIndex(CANARY_SCRIPTS + ZuulApplicationInfo.getApplicationName(), filter_id + "_" + filter.getRevision());


        /* activate the revision */
        Map<String, Object> attributesForActivation = new HashMap<String, Object>();
        attributesForActivation.put("active", false);
        attributesForActivation.put("canary", false);
        cassandraGateway.upsert(filter_id + "_" + revision, attributesForActivation);
        setChanged();
        notifyObservers(new ZuulEvent("ZUUL_SCRIPT_CHANGE", "DEACTIVATED ZUUL FILTER id = " + filter_id + " revision = " + revision));

        return getFilterInfoForFilter(filter_id, revision);
    }
}
