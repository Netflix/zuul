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

import com.netflix.astyanax.AstyanaxContext;
import com.netflix.astyanax.Keyspace;
import com.netflix.astyanax.model.Column;
import com.netflix.astyanax.model.ColumnList;
import com.netflix.astyanax.model.Row;
import com.netflix.astyanax.model.Rows;
import com.netflix.zuul.ZuulApplicationInfo;
import com.netflix.zuul.dependency.cassandra.hystrix.HystrixCassandraGetRowsByKeys;
import com.netflix.zuul.dependency.cassandra.hystrix.HystrixCassandraGetRowsByQuery;
import com.netflix.zuul.dependency.cassandra.hystrix.HystrixCassandraPut;
import com.netflix.zuul.event.ZuulEvent;
import com.netflix.zuul.filters.FilterType;
import net.jcip.annotations.ThreadSafe;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static org.junit.Assert.*;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.*;

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


    private ZuulFilterDAOCassandra(CassandraGateway cassandraGateway) {
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
            FilterType filterType = FilterType.valueOf(columns.getColumnByName("filter_type").getStringValue());
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
    public FilterInfo addFilter(String filtercode, FilterType filter_type, String filter_name, String disableFilterPropertyName, String filter_order) {
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

    public static String buildFilterID(FilterType filter_type, String filter_name) {
        return FilterInfo.buildFilterID(ZuulApplicationInfo.getApplicationName(), filter_type, filter_name);

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

    /**
     * Gateway to our usage of Cassandra so we encapsulate the calls and can stub these out for unit testing.
     */
    private static interface CassandraGateway {
        public void upsert(String rowKey, Map<String, Object> attributes);

        public void updateFilterIndex(String rowKey, String filter_ids);

        public Rows<String, String> select(String cql);

        public Rows<String, String> getByFilterIds(List<String> filterIds);

    }

    @ThreadSafe
    private static class CassandraGatewayProd implements CassandraGateway {
        private static final String COLUMN_FAMILY = "zuul_filters";

        private final Keyspace keyspace;


        public CassandraGatewayProd(AstyanaxContext<Keyspace> context) {
            keyspace = context.getClient();
        }

        public CassandraGatewayProd(Keyspace keyspace) {
            this.keyspace = keyspace;
        }

        public void updateFilterIndex(String rowKey, String filter_ids) {

            HashMap<String, Object> attributes = new HashMap<String, Object>();
            attributes.put("index_name", rowKey);
            attributes.put("filter_ids", filter_ids);
            new HystrixCassandraPut<String>(keyspace, "zuul_filter_indices", rowKey, attributes).execute();
        }

        /**
         * Performs an insert/update for a row in Cassandra.
         *
         * @param rowKey
         * @param attributes
         */
        public void upsert(String rowKey, Map<String, Object> attributes) {
            new HystrixCassandraPut<String>(keyspace, COLUMN_FAMILY, rowKey, attributes).execute();
        }

        /**
         * Performs a CQL query and returns result.
         *
         * @param cql
         * @return
         */
        public Rows<String, String> select(String cql) {
            return new HystrixCassandraGetRowsByQuery<String>(keyspace, COLUMN_FAMILY, String.class, cql).execute();
        }

        public Rows<String, String> getByFilterIds(List<String> filterIds) {
            String[] list = new String[filterIds.size()];
            for (int i = 0; i < filterIds.size(); i++) {
                list[i] = filterIds.get(i);
            }
            return new HystrixCassandraGetRowsByKeys<String>(keyspace, COLUMN_FAMILY, list).execute();
        }
    }


    public static class UnitTest {
        @Mock
        CassandraGateway gateway;
        @Mock
        Rows<String, String> response;

        @Before
        public void before() {
            MockitoAnnotations.initMocks(this);
        }

        /**
         * We don't want a NULL when there are no Filters, instead we want an empty list.
         */
        @Test
        public void testGetAllFiltersReturnsEmptyListInsteadOfNullWhenNoFilters() {
            ZuulFilterDAO dao = new ZuulFilterDAOCassandra(gateway);
            // setup empty response
            when(gateway.select(anyString())).thenReturn(response);
            when(response.isEmpty()).thenReturn(true);

            List<String> list = dao.getAllFilterIDs();
            assertNotNull(list);
            assertEquals(0, list.size());
        }

        @SuppressWarnings("unchecked")
        @Test
        public void testGetFilterIdsRawIndex() {
            ZuulFilterDAO dao = new ZuulFilterDAOCassandra(gateway);

            // setup empty response
            String fids = "filter1|filter2|filter3";


            /* create mock response data */
            Row<String, String> row0 = mockRow();
            ColumnList<String> columnList0 = mockColumnList(row0);
            mockColumn(columnList0, "filter_ids", fids);
            // when(response.getRowByIndex(0)).thenReturn(row0);


            Iterator<Row<String, String>> iterator = (Iterator<Row<String, String>>) mock(Iterator.class);
            when(response.iterator()).thenReturn(iterator);
            when(iterator.hasNext()).thenReturn(true, false); // 1 row
            when(iterator.next()).thenReturn(row0, (Row) null);

            when(gateway.select(anyString())).thenReturn(response);
            when(response.isEmpty()).thenReturn(false);
            when(response.size()).thenReturn(1);

            /* exercise the method we're testing */
            String list = dao.getFilterIdsRaw("index");

            /* validate responses */
            assertEquals(fids, list);

        }


        @SuppressWarnings("unchecked")
        @Test
        public void testGetFilterIdsIndex() {
            ZuulFilterDAO dao = new ZuulFilterDAOCassandra(gateway);

            // setup empty response
            String fids = "filter1|filter2|filter3";


            /* create mock response data */
            Row<String, String> row0 = mockRow();
            ColumnList<String> columnList0 = mockColumnList(row0);
            mockColumn(columnList0, "filter_ids", fids);
            // when(response.getRowByIndex(0)).thenReturn(row0);


            Iterator<Row<String, String>> iterator = (Iterator<Row<String, String>>) mock(Iterator.class);
            when(response.iterator()).thenReturn(iterator);
            when(iterator.hasNext()).thenReturn(true, false); // 1 row
            when(iterator.next()).thenReturn(row0, (Row) null);

            when(gateway.select(anyString())).thenReturn(response);
            when(response.isEmpty()).thenReturn(false);
            when(response.size()).thenReturn(1);

            /* exercise the method we're testing */
            List<String> list = dao.getFilterIdsIndex("index");

            /* validate responses */
            assertEquals(list.size(), 3);
            assertEquals(list.get(0), "filter1");
            assertEquals(list.get(1), "filter2");
            assertEquals(list.get(2), "filter3");


        }


        /**
         * We can't unit test the CQL query or how Cassandra will behave, that will have to be manually confirmed.
         * We can however test that whatever rows are returned do end up in the List<EndpointURI> that we expect.
         */
        @SuppressWarnings("unchecked")
        @Test
        public void testGetAllEndpointsReturnsResults() {
            ZuulFilterDAO dao = new ZuulFilterDAOCassandra(gateway);

            // setup empty response
            String fids = "filter1|filter2";


            /* create mock response data */
            Row<String, String> row0 = mockRow();
            ColumnList<String> columnList0 = mockColumnList(row0);
            mockColumn(columnList0, "filter_ids", fids);
            // when(response.getRowByIndex(0)).thenReturn(row0);


            Iterator<Row<String, String>> iterator = (Iterator<Row<String, String>>) mock(Iterator.class);
            when(response.iterator()).thenReturn(iterator);
            when(iterator.hasNext()).thenReturn(true, false); // 1 row
            when(iterator.next()).thenReturn(row0, (Row) null);

            when(gateway.select(anyString())).thenReturn(response);
            when(response.isEmpty()).thenReturn(false);
            when(response.size()).thenReturn(1);

            /* exercise the method we're testing */
            List<String> list = dao.getAllFilterIDs();

            /* validate responses */
            assertEquals("filter1", list.get(0));
            assertEquals("filter2", list.get(1));
        }

        /**
         * We don't want a NULL when an filter is not found, instead we want an empty list.
         */
        @Test
        public void testGetScriptForEndpointReturnsEmptyListInsteadOfNullWhenEndpointNotFound() {
            ZuulFilterDAO dao = new ZuulFilterDAOCassandra(gateway);

            // setup empty response
            when(gateway.select(anyString())).thenReturn(response);
            when(response.isEmpty()).thenReturn(true);

            List<FilterInfo> list = dao.getZuulFiltersForFilterId("/unknown/Filter");
            assertNotNull(list);
            assertEquals(0, list.size());
        }

        /**
         * We can't unit test the CQL query or how Cassandra will behave, that will have to be manually confirmed.
         * We can however test that whatever rows are returned do end up in the List<FilterInfo> that we expect.
         */
        @SuppressWarnings("unchecked")
        @Test
        public void testGetScriptsForFilterReturnsResults() {
            ZuulFilterDAO dao = new ZuulFilterDAOCassandra(gateway);
            dao = spy(dao);

            doReturn("name:in_1|name:in_2").when(dao).getFilterIdsRaw(anyString());

            String filter = "name:in";

            Calendar now = Calendar.getInstance();

            /* create mock response data */
            Row<String, String> row0 = mockRow();
            ColumnList<String> columnList0 = mockColumnList(row0);
            mockColumn(columnList0, "filter_id", filter);
            mockColumn(columnList0, "filter_name", "name");
            mockColumn(columnList0, "filter_type", "INBOUND");
            mockColumn(columnList0, "revision", 1L);
            mockColumn(columnList0, "active", true);
            mockColumn(columnList0, "canary", false);
            mockColumn(columnList0, "creation_date", now.getTime());
            mockColumn(columnList0, "filter_code", "script body 1".getBytes());
            mockColumn(columnList0, "application_name", "app_name");

            // this row does NOT have a name

            Row<String, String> row1 = mockRow();
            ColumnList<String> columnList1 = mockColumnList(row1);
            mockColumn(columnList1, "filter_id", filter);
            mockColumn(columnList1, "revision", 2L);
            mockColumn(columnList1, "filter_name", "name");
            mockColumn(columnList1, "filter_type", "INBOUND");
            mockColumn(columnList1, "active", false);
            mockColumn(columnList1, "canary", false);
            mockColumn(columnList1, "creation_date", now.getTime());
            mockColumn(columnList1, "filter_code", "script body 2a".getBytes());
            mockColumn(columnList1, "application_name", "app_name");

            // this row has names for the scripts

            Iterator<Row<String, String>> iterator = (Iterator<Row<String, String>>) mock(Iterator.class);
            when(response.iterator()).thenReturn(iterator);
            when(iterator.hasNext()).thenReturn(true, true, false); // 2 rows
            when(iterator.next()).thenReturn(row0, row1, null);


            when(response.isEmpty()).thenReturn(false);
            when(response.size()).thenReturn(2);

            when(gateway.getByFilterIds(anyList())).thenReturn(response);

            /* exercise the method we're testing */
            List<FilterInfo> list = dao.getZuulFiltersForFilterId(filter);

            /* validate responses */
            assertEquals(filter, list.get(0).getFilterID());
            assertEquals(1, list.get(0).getRevision());
            assertEquals(true, list.get(0).isActive());
            assertEquals(now.getTime(), list.get(0).getCreationDate());
            // assert using scriptsForExecution
            assertEquals(filter, list.get(1).getFilterID());
            assertEquals(2, list.get(1).getRevision());
            assertEquals(false, list.get(1).isActive());

            // assert using scriptsForExecution
            // now assert using filenames to lookup
            assertEquals("script body 2a", list.get(1).getFilterCode());

        }

        @Test
        public void testGetScriptForEndpointAndRevisionReturnsNullWhenNotFound() {
            ZuulFilterDAO dao = new ZuulFilterDAOCassandra(gateway);

            // setup empty response
            when(gateway.select(anyString())).thenReturn(response);
            when(response.isEmpty()).thenReturn(true);

            FilterInfo filterInfo = dao.getFilterInfoForFilter("/unknown/filter", 2);
            assertNull(filterInfo);
        }

        /**
         * We can't unit test the CQL query or how Cassandra will behave, that will have to be manually confirmed.
         * We can however test that whatever rows are returned do end up as an FilterInfo that we expect.
         */
        @Test
        public void testGetScriptForEndpointAndRevision() {
            ZuulFilterDAO dao = new ZuulFilterDAOCassandra(gateway);
            String filter = "name:in";

            Calendar now = Calendar.getInstance();

            /* create mock response data */
            Row<String, String> row0 = mockRow();
            ColumnList<String> columnList0 = mockColumnList(row0);
            mockColumn(columnList0, "filter_id", filter);
            mockColumn(columnList0, "revision", 3L);
            mockColumn(columnList0, "active", true);
            mockColumn(columnList0, "creation_date", now.getTime());
            mockColumn(columnList0, "filter_code", "script body 1".getBytes());
            mockColumn(columnList0, "filter_name", "name");
            mockColumn(columnList0, "filter_type", "INBOUND");
            mockColumn(columnList0, "canary", false);
            mockColumn(columnList0, "application_name", "app_name");

            Iterator<Row<String, String>> iterator = (Iterator<Row<String, String>>) mock(Iterator.class);
            when(response.iterator()).thenReturn(iterator);
            when(iterator.hasNext()).thenReturn(true, false); // 2 rows
            when(iterator.next()).thenReturn(row0, (Row) null);


            when(response.getRowByIndex(0)).thenReturn(row0);

            when(gateway.getByFilterIds(anyList())).thenReturn(response);
            when(response.isEmpty()).thenReturn(false);
            when(response.size()).thenReturn(1);
            dao = spy(dao);

            doReturn("name:in_3").when(dao).getFilterIdsRaw(anyString());


            /* exercise the method we're testing */
            FilterInfo filterInfo = dao.getFilterInfoForFilter(filter, 3);

            /* validate responses */
            assertEquals(filter, filterInfo.getFilterID());
            assertEquals(3, filterInfo.getRevision());
            assertEquals(true, filterInfo.isActive());
            assertEquals(now.getTime(), filterInfo.getCreationDate());
            assertEquals("script body 1", filterInfo.getFilterCode());
        }

        @Test
        public void testGetScriptForLatestEndpointReturnsNullWhenNotFound() {
            ZuulFilterDAO dao = new ZuulFilterDAOCassandra(gateway);

            // setup empty response
            when(gateway.select(anyString())).thenReturn(response);
            when(response.isEmpty()).thenReturn(true);

            FilterInfo filterInfo = dao.getLatestFilterInfoForFilter("/unknown/filter");
            assertNull(filterInfo);
        }

        /**
         * We can't unit test the CQL query or how Cassandra will behave, that will have to be manually confirmed.
         * We can however test that whatever rows are returned do end up as an FilterInfo that we expect.
         */
        @SuppressWarnings("unchecked")
        @Test
        public void testGetScriptForLatestEndpoint() {
            ZuulFilterDAO dao = new ZuulFilterDAOCassandra(gateway);
            String filter = "name:in";

            Calendar now = Calendar.getInstance();

            /* create mock response data */
            Row<String, String> row0 = mockRow();
            ColumnList<String> columnList0 = mockColumnList(row0);
            mockColumn(columnList0, "filter_id", filter);
            mockColumn(columnList0, "revision", 4L);
            mockColumn(columnList0, "active", false);
            mockColumn(columnList0, "creation_date", now.getTime());
            mockColumn(columnList0, "filter_code", "script body 1".getBytes());
            mockColumn(columnList0, "filter_name", "name");
            mockColumn(columnList0, "filter_type", "INBOUND");
            mockColumn(columnList0, "canary", false);
            mockColumn(columnList0, "application_name", "app_name");


            when(response.getRowByIndex(0)).thenReturn(row0);

            Iterator<Row<String, String>> iterator = (Iterator<Row<String, String>>) mock(Iterator.class);
            when(response.iterator()).thenReturn(iterator);
            when(iterator.hasNext()).thenReturn(true, false); // 1 row
            when(iterator.next()).thenReturn(row0);

            when(response.isEmpty()).thenReturn(false);
            when(response.size()).thenReturn(1);
            when(gateway.getByFilterIds(anyList())).thenReturn(response);

            dao = spy(dao);

            doReturn("name:in_4").when(dao).getFilterIdsRaw(anyString());

            /* exercise the method we're testing */
            FilterInfo filterInfo = dao.getLatestFilterInfoForFilter(filter);

            /* validate responses */
            assertEquals(filter, filterInfo.getFilterID());
            assertEquals(4, filterInfo.getRevision());
            assertEquals(false, filterInfo.isActive());
            assertEquals(now.getTime(), filterInfo.getCreationDate());
            assertEquals("script body 1", filterInfo.getFilterCode());
        }

        /**
         * We can't unit test the CQL query or how Cassandra will behave, that will have to be manually confirmed.
         * We can however test that whatever rows are returned do end up as an FilterInfo that we expect.
         */
        @Test
        public void testGetActiveScriptForEndpoint() {
            ZuulFilterDAO dao = new ZuulFilterDAOCassandra(gateway);
            String filter = "name:in";

            Calendar now = Calendar.getInstance();

            /* create mock response data */
            Row<String, String> row0 = mockRow();
            ColumnList<String> columnList0 = mockColumnList(row0);
            mockColumn(columnList0, "filter_id", filter);
            mockColumn(columnList0, "revision", 3L);
            mockColumn(columnList0, "active", true);
            mockColumn(columnList0, "creation_date", now.getTime());
            mockColumn(columnList0, "filter_code", "script body 1".getBytes());
            mockColumn(columnList0, "filter_name", "name");
            mockColumn(columnList0, "filter_type", "INBOUND");
            mockColumn(columnList0, "canary", false);
            mockColumn(columnList0, "application_name", "app_name");


            when(response.getRowByIndex(0)).thenReturn(row0);

            when(response.isEmpty()).thenReturn(false);
            when(response.size()).thenReturn(1);

            when(gateway.getByFilterIds(anyList())).thenReturn(response);

            Iterator<Row<String, String>> iterator = (Iterator<Row<String, String>>) mock(Iterator.class);
            when(response.iterator()).thenReturn(iterator);
            when(iterator.hasNext()).thenReturn(true, false); // 1 row
            when(iterator.next()).thenReturn(row0);

            dao = spy(dao);
            doReturn("name:in_3").when(dao).getFilterIdsRaw(anyString());

            /* exercise the method we're testing */
            FilterInfo filterInfo = dao.getActiveFilterInfoForFilter(filter);

            /* validate responses */
            assertEquals(filter, filterInfo.getFilterID());
            assertEquals(3, filterInfo.getRevision());
            assertEquals(true, filterInfo.isActive());
            assertEquals(now.getTime(), filterInfo.getCreationDate());
            assertEquals("script body 1", filterInfo.getFilterCode());
        }

        /**
         * Test that the correct data is sent to Cassandra:
         * <p/>
         * - rowKey is URI+revision
         * - new upload should be isActive==false
         * - script data should be as a byte[]
         */
        @Test
        public void testAddScriptForNewEndpointUsingArray() {
            String filter = "null:name:in";

            mockGetScriptForEndpoint(filter);

            final StringBuilder upsertedRowKey = new StringBuilder();
            final Map<String, Object> upsertedAttributes = new HashMap<String, Object>();
            /* mock gateway so we can capture what is upserted */
            CassandraGateway testGateway = new CassandraGateway() {
                @Override
                public void upsert(String rowKey, Map<String, Object> attributes) {
                    upsertedRowKey.delete(0, upsertedRowKey.length());
                    upsertedRowKey.append(rowKey);
                    upsertedAttributes.clear();
                    upsertedAttributes.putAll(attributes);
                }

                @Override
                public void updateFilterIndex(String rowKey, String filter_ids) {

                }

                @Override
                public Rows<String, String> select(String cql) {
                    return response;
                }

                @Override
                public Rows<String, String> getByFilterIds(List<String> filterIds) {
                    return null;
                }

            };

            /* exercise the method we're testing */
            ZuulFilterDAO dao = new ZuulFilterDAOCassandra(testGateway);
            dao = spy(dao);
            doReturn("name:in").when(dao).getFilterIdsRaw(anyString());
            when(gateway.getByFilterIds(anyList())).thenReturn(response);


            FilterInfo filterInfo = dao.addFilter("code", FilterType.INBOUND, "name", "disable", "order");

            /* validate that Cassandra receives the correct attributes */
            assertEquals(filter + "_1", upsertedRowKey.toString());
            assertEquals(filter, upsertedAttributes.get("filter_id"));
            assertEquals(1L, upsertedAttributes.get("revision"));
            assertEquals(false, upsertedAttributes.get("active"));
            assertTrue(upsertedAttributes.get("creation_date") instanceof Date);
            assertTrue(Arrays.equals("code".getBytes(), (byte[]) upsertedAttributes.get("filter_code")));

        }


        /**
         * Test that adding a script to an existing filter increases the revision number
         */
        @SuppressWarnings("unchecked")
        @Test
        public void testAddScriptForExistingEndpoint() {
            String filter = "name:in";

            /* mock data so that the getScriptForEndpoint at the end of the method will work */
            Calendar now = Calendar.getInstance();
            Row<String, String> row0 = mockRow();
            ColumnList<String> columnList0 = mockColumnList(row0);
            mockColumn(columnList0, "filter_id", filter);
            mockColumn(columnList0, "revision", 1L);
            mockColumn(columnList0, "active", false);
            mockColumn(columnList0, "creation_date", now.getTime());
            mockColumn(columnList0, "filter_code", "script body 1".getBytes());
            mockColumn(columnList0, "filter_name", "name");
            mockColumn(columnList0, "filter_type", "INBOUND");
            mockColumn(columnList0, "canary", false);
            mockColumn(columnList0, "application_name", "app_name");


            when(response.getRowByIndex(0)).thenReturn(row0);

            Iterator<Row<String, String>> iterator = (Iterator<Row<String, String>>) mock(Iterator.class);
            when(response.iterator()).thenReturn(iterator);
            when(iterator.hasNext()).thenReturn(true, false); // 1 row
            when(iterator.next()).thenReturn(row0);

            when(response.isEmpty()).thenReturn(false);
            when(response.size()).thenReturn(1);

            final StringBuilder upsertedRowKey = new StringBuilder();
            final Map<String, Object> upsertedAttributes = new HashMap<String, Object>();
            /* mock gateway so we can capture what is upserted */
            CassandraGateway testGateway = new CassandraGateway() {
                @Override
                public void upsert(String rowKey, Map<String, Object> attributes) {
                    upsertedRowKey.delete(0, upsertedRowKey.length());
                    upsertedRowKey.append(rowKey);
                    upsertedAttributes.clear();
                    upsertedAttributes.putAll(attributes);
                }

                @Override
                public void updateFilterIndex(String rowKey, String filter_ids) {

                }

                @Override
                public Rows<String, String> select(String cql) {
                    return response;
                }

                @Override
                public Rows<String, String> getByFilterIds(List<String> filterIds) {
                    return null;
                }

            };
            testGateway = spy(testGateway);
            /* exercise the method we're testing */
            ZuulFilterDAO dao = new ZuulFilterDAOCassandra(testGateway);

            dao = spy(dao);
            doReturn("name:in").when(dao).getFilterIdsRaw(anyString());
            doReturn(response).when(testGateway).getByFilterIds(anyList());


            FilterInfo filterInfo = dao.addFilter("script body1", FilterType.INBOUND, "name", "disable", "order");

            /* validate that revision is 2 since the previous revision is 1 (defined in mock above) */
            assertEquals("null:" + filter + "_2", upsertedRowKey.toString());
            assertEquals(2L, upsertedAttributes.get("revision"));
        }

        /**
         * Test that setting a revision active also deactivates all other revisions.
         */
        @SuppressWarnings("unchecked")
        @Test
        public void testSetScriptActive() {
            String filter = "name:in";
            Calendar now = Calendar.getInstance();

            /* define currently active script */
            Row<String, String> row0 = mockRow();
            ColumnList<String> columnList0 = mockColumnList(row0);
            mockColumn(columnList0, "filter_id", filter);
            mockColumn(columnList0, "revision", 3L);
            mockColumn(columnList0, "active", true);
            mockColumn(columnList0, "creation_date", now.getTime());
            mockColumn(columnList0, "filter_code", "script body 1".getBytes());
            mockColumn(columnList0, "filter_name", "name");
            mockColumn(columnList0, "filter_type", "INBOUND");
            mockColumn(columnList0, "canary", false);
            mockColumn(columnList0, "application_name", "app_name");


            when(response.getRowByIndex(0)).thenReturn(row0);

            /* define latest inactive script */
            Row<String, String> row1 = mockRow();
            ColumnList<String> columnList1 = mockColumnList(row1);
            mockColumn(columnList1, "filter_id", filter);
            mockColumn(columnList1, "revision", 4L);
            mockColumn(columnList1, "active", false);
            mockColumn(columnList1, "creation_date", now.getTime());
            mockColumn(columnList1, "filter_code", "script body 1".getBytes());
            mockColumn(columnList1, "filter_name", "name");
            mockColumn(columnList1, "filter_type", "INBOUND");
            mockColumn(columnList1, "canary", true);
            mockColumn(columnList1, "application_name", "app_name");


            when(response.getRowByIndex(1)).thenReturn(row1);

            when(response.isEmpty()).thenReturn(false);
            when(response.size()).thenReturn(2);

            Iterator<Row<String, String>> iterator = (Iterator<Row<String, String>>) mock(Iterator.class);
            when(response.iterator()).thenReturn(iterator);
            when(iterator.hasNext()).thenReturn(true, true, false); // 2 row
            when(iterator.next()).thenReturn(row0, row1);

            /* define a 2nd response that is returned as the FilterInfo from the method */
            Rows<String, String> response2 = mock(Rows.class);
            Row<String, String> response2row0 = mockRow();
            ColumnList<String> response2columnList1 = mockColumnList(response2row0);
            mockColumn(response2columnList1, "filter_id", filter);
            mockColumn(response2columnList1, "revision", 4L);
            mockColumn(response2columnList1, "active", false);
            mockColumn(response2columnList1, "creation_date", now.getTime());
            mockColumn(response2columnList1, "filter_code", "script body 1".getBytes());
            mockColumn(response2columnList1, "filter_name", "name");
            mockColumn(response2columnList1, "filter_type", "INBOUND");
            mockColumn(response2columnList1, "canary", true);
            mockColumn(response2columnList1, "application_name", "app_name");


            when(response2.getRowByIndex(0)).thenReturn(response2row0);

            when(response2.isEmpty()).thenReturn(false);
            when(response2.size()).thenReturn(1);


            Rows<String, String> response3 = mock(Rows.class);

            /* define a 2nd response that is returned as the FilterInfo from the method */

            Row<String, String> response3row0 = mockRow();
            ColumnList<String> response3columnList1 = mockColumnList(response3row0);
            mockColumn(response3columnList1, "filter_id", filter);
            mockColumn(response3columnList1, "revision", 4L);
            mockColumn(response3columnList1, "active", false);
            mockColumn(response3columnList1, "creation_date", now.getTime());
            mockColumn(response3columnList1, "filter_code", "script body 1".getBytes());
            mockColumn(response3columnList1, "filter_name", "name");
            mockColumn(response3columnList1, "filter_type", "INBOUND");
            mockColumn(response3columnList1, "canary", true);
            mockColumn(response3columnList1, "application_name", "app_name");


            when(response3.getRowByIndex(0)).thenReturn(response3row0);

            when(response3.isEmpty()).thenReturn(false);
            when(response3.size()).thenReturn(1);

            Iterator<Row<String, String>> iterator1 = (Iterator<Row<String, String>>) mock(Iterator.class);
            when(response3.iterator()).thenReturn(iterator1);
            when(iterator1.hasNext()).thenReturn(true, false); // 1 row
            when(iterator1.next()).thenReturn(response3row0);


            /* exercise the method we're testing */
            ZuulFilterDAOCassandra dao = new ZuulFilterDAOCassandra(gateway);

            dao = spy(dao);
            doReturn("name:in").when(dao).getFilterIdsRaw(anyString());
            when(gateway.getByFilterIds(anyList())).thenReturn(response3, response, response2);


            FilterInfo filterInfo = null;
            try {
                filterInfo = dao.setFilterActive(filter, 4);
            } catch (Exception e) {
                e.printStackTrace();
            }

            InOrder inOrder = inOrder(gateway);

            // assert that the script was activated (and done first)
            Map<String, Object> attributesForActivation = new HashMap<String, Object>();
            attributesForActivation.put("active", true);
            attributesForActivation.put("canary", false);
            inOrder.verify(gateway, times(1)).upsert(filter + "_4", attributesForActivation);

            // assert that the previously active script was marked as inactive (after activation step)
            Map<String, Object> attributesForDeactivation = new HashMap<String, Object>();
            attributesForDeactivation.put("active", false);
            inOrder.verify(gateway).upsert(filter + "_3", attributesForDeactivation);


        }

        /**
         * Deal with the first of 2 upserts failing ... Cassandra isn't transactional, so we need to deal with this
         */
        @SuppressWarnings("unchecked")
        @Test
        public void testSetScriptActiveHandlesPartialFailure() {
            String filter = "name:in";
            Calendar now = Calendar.getInstance();

            /* define currently active script */
            Row<String, String> row0 = mockRow();
            ColumnList<String> columnList0 = mockColumnList(row0);
            mockColumn(columnList0, "filter_id", filter);
            mockColumn(columnList0, "revision", 3L);
            mockColumn(columnList0, "active", true);
            mockColumn(columnList0, "creation_date", now.getTime());
            mockColumn(columnList0, "filter_code", "script body 1".getBytes());
            mockColumn(columnList0, "filter_name", "name");
            mockColumn(columnList0, "filter_type", "INBOUND");
            mockColumn(columnList0, "canary", false);
            mockColumn(columnList0, "application_name", "app_name");


            when(response.getRowByIndex(0)).thenReturn(row0);

            /* define latest inactive script */
            Row<String, String> row1 = mockRow();
            ColumnList<String> columnList1 = mockColumnList(row1);
            mockColumn(columnList1, "filter_id", filter);
            mockColumn(columnList1, "revision", 4L);
            mockColumn(columnList1, "active", false);
            mockColumn(columnList1, "creation_date", now.getTime());
            mockColumn(columnList1, "filter_code", "script body 1".getBytes());
            mockColumn(columnList1, "filter_name", "name");
            mockColumn(columnList1, "filter_type", "INBOUND");
            mockColumn(columnList1, "canary", false);
            mockColumn(columnList0, "application_name", "app_name");


            when(response.getRowByIndex(1)).thenReturn(row1);

            Iterator<Row<String, String>> iterator = (Iterator<Row<String, String>>) mock(Iterator.class);
            when(response.iterator()).thenReturn(iterator);
            when(iterator.hasNext()).thenReturn(true, true, false); // 2 row
            when(iterator.next()).thenReturn(row0, row1);


            when(response.isEmpty()).thenReturn(false);
            when(response.size()).thenReturn(2);

            Map<String, Object> attributesForActivation = new HashMap<String, Object>();
            attributesForActivation.put("active", true);
            attributesForActivation.put("canary", false);
            doThrow(new RuntimeException()).when(gateway).upsert(filter + "_4", attributesForActivation);

            /* exercise the method we're testing */
            ZuulFilterDAO dao = new ZuulFilterDAOCassandra(gateway);

            dao = spy(dao);
            doReturn("name:in").when(dao).getFilterIdsRaw(anyString());
            when(gateway.getByFilterIds(anyList())).thenReturn(response);

            try {
                dao.setFilterActive(filter, 4);
            } catch (Exception e) {
                // expected
            }

            // verify the inactivation step never occurs
            Map<String, Object> attributesForDeactivation = new HashMap<String, Object>();
            attributesForDeactivation.put("active", false);
            verify(gateway, times(0)).upsert(filter + "_3", attributesForDeactivation);
        }

        /**
         * If 8 is active, and the user tries to activate it again, don't de-activate it in the post-activation cleanup.
         */
        @SuppressWarnings("unchecked")
        @Test
        public void testSetScriptActiveDoesNotDeactivateWhatWasJustActivated() {
            String filter = "name:in";
            Calendar now = Calendar.getInstance();

            /* define currently active script */
            Row<String, String> row0 = mockRow();
            ColumnList<String> columnList0 = mockColumnList(row0);
            mockColumn(columnList0, "filter_id", filter);
            mockColumn(columnList0, "revision", 3L);
            mockColumn(columnList0, "active", true);
            mockColumn(columnList0, "creation_date", now.getTime());
            mockColumn(columnList0, "filter_code", "script body 1".getBytes());
            mockColumn(columnList0, "filter_name", "name");
            mockColumn(columnList0, "filter_type", "INBOUND");
            mockColumn(columnList0, "canary", true);
            mockColumn(columnList0, "application_name", "app_name");


            when(response.getRowByIndex(0)).thenReturn(row0);

            Iterator<Row<String, String>> iterator = (Iterator<Row<String, String>>) mock(Iterator.class);
            when(response.iterator()).thenReturn(iterator);
            when(iterator.hasNext()).thenReturn(true, false); // 1 row
            when(iterator.next()).thenReturn(row0);

            when(response.isEmpty()).thenReturn(false);
            when(response.size()).thenReturn(1);

            /* exercise the method we're testing */
            ZuulFilterDAO dao = new ZuulFilterDAOCassandra(gateway);
            dao = spy(dao);
            doReturn("name:in").when(dao).getFilterIdsRaw(anyString());
            when(gateway.getByFilterIds(anyList())).thenReturn(response);

            try {
                dao.setFilterActive(filter, 3); // activate the filter that's already active
            } catch (Exception e) {
                e.printStackTrace();
            }

            InOrder inOrder = inOrder(gateway);

            // assert that the script was activated (and done first)
            Map<String, Object> attributesForActivation = new HashMap<String, Object>();
            attributesForActivation.put("active", true);
            attributesForActivation.put("canary", false);
            inOrder.verify(gateway, times(1)).upsert(filter + "_3", attributesForActivation);

            // ensure we do NOT deactivate it since this is the same revision
            Map<String, Object> attributesForDeactivation = new HashMap<String, Object>();
            attributesForDeactivation.put("active", false);
            inOrder.verify(gateway, times(0)).upsert(filter + "_3", attributesForDeactivation);
        }

        /**
         * Utility for creating Cassandra responses
         *
         * @param columnList
         * @param columnName
         * @param value
         * @return
         */
        @SuppressWarnings("unchecked")
        private static Column<String> mockColumn(ColumnList<String> columnList, String columnName, Object value) {
            /* create column with return value */
            Column<String> column = mock(Column.class);
            if (value instanceof String) {
                when(column.getStringValue()).thenReturn((String) value);
            } else if (value instanceof byte[]) {
                when(column.getByteArrayValue()).thenReturn((byte[]) value);
            } else if (value instanceof Date) {
                when(column.getDateValue()).thenReturn((Date) value);
            } else if (value instanceof Integer) {
                when(column.getIntegerValue()).thenReturn((Integer) value);
            } else if (value instanceof Long) {
                when(column.getLongValue()).thenReturn((Long) value);
            } else if (value instanceof Boolean) {
                when(column.getBooleanValue()).thenReturn((Boolean) value);
            } else {
                throw new RuntimeException("unsupported type, add another else above");
            }
            /* assign column to columnList for given columnName */
            when(columnList.getColumnByName(columnName)).thenReturn(column);
            return column;
        }

        /**
         * Utility for creating Cassandra responses
         *
         * @param row
         * @return
         */
        @SuppressWarnings("unchecked")
        private static ColumnList<String> mockColumnList(Row<String, String> row) {
            ColumnList<String> columnList = mock(ColumnList.class);
            when(row.getColumns()).thenReturn(columnList);
            return columnList;
        }

        /**
         * Utility for creating Cassandra responses
         *
         * @return
         */
        @SuppressWarnings("unchecked")
        private static Row<String, String> mockRow() {
            Row<String, String> row0 = mock(Row.class, "row0");
            return row0;
        }

        private void mockGetScriptForEndpoint(String filter) {
            mockGetScriptForEndpoint(response, filter);
        }

        private static void mockGetScriptForEndpoint(Rows<String, String> response, String filter) {
            /* mock data so that the getScriptForEndpoint at the end of the method will work */
            Calendar now = Calendar.getInstance();
            Row<String, String> row0 = mockRow();
            ColumnList<String> columnList0 = mockColumnList(row0);
            mockColumn(columnList0, "filter_id", filter);
            mockColumn(columnList0, "revision", 1L);
            mockColumn(columnList0, "active", false);
            mockColumn(columnList0, "creation_date", now.getTime());
            mockColumn(columnList0, "filter_code", "System.out.println(\"hello world\")".getBytes()); // what we put here doesn't matter
            mockColumn(columnList0, "filter_name", "name");
            mockColumn(columnList0, "filter_type", "INBOUND");
            mockColumn(columnList0, "canary", false);

            when(response.getRowByIndex(0)).thenReturn(row0);

            when(response.isEmpty()).thenReturn(true, false); // true on the first request, false thereafter
            when(response.size()).thenReturn(1);
        }
    }
}
