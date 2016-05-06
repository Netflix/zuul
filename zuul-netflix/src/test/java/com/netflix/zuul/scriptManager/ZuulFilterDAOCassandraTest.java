package com.netflix.zuul.scriptManager;

import com.netflix.astyanax.model.Column;
import com.netflix.astyanax.model.ColumnList;
import com.netflix.astyanax.model.Row;
import com.netflix.astyanax.model.Rows;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.anyList;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ZuulFilterDAOCassandraTest {
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

        doReturn("name:type_1|name:type_2").when(dao).getFilterIdsRaw(anyString());

        String filter = "name:type";

        Calendar now = Calendar.getInstance();

            /* create mock response data */
        Row<String, String> row0 = mockRow();
        ColumnList<String> columnList0 = mockColumnList(row0);
        mockColumn(columnList0, "filter_id", filter);
        mockColumn(columnList0, "filter_name", "name");
        mockColumn(columnList0, "filter_type", "type");
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
        mockColumn(columnList1, "filter_type", "type");
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
        String filter = "name:type";

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
        mockColumn(columnList0, "filter_type", "type");
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

        doReturn("name:type_3").when(dao).getFilterIdsRaw(anyString());


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
        String filter = "name:type";

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
        mockColumn(columnList0, "filter_type", "type");
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

        doReturn("name:type_4").when(dao).getFilterIdsRaw(anyString());

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
        String filter = "name:type";

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
        mockColumn(columnList0, "filter_type", "type");
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
        doReturn("name:type_3").when(dao).getFilterIdsRaw(anyString());

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
        String filter = "null:name:type";

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
        doReturn("name:type").when(dao).getFilterIdsRaw(anyString());
        when(gateway.getByFilterIds(anyList())).thenReturn(response);


        FilterInfo filterInfo = dao.addFilter("code", "type", "name", "disable", "order");

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
        String filter = "name:type";

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
        mockColumn(columnList0, "filter_type", "type");
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
        doReturn("name:type").when(dao).getFilterIdsRaw(anyString());
        doReturn(response).when(testGateway).getByFilterIds(anyList());


        FilterInfo filterInfo = dao.addFilter("script body1", "type", "name", "disable", "order");

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
        String filter = "name:type";
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
        mockColumn(columnList0, "filter_type", "type");
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
        mockColumn(columnList1, "filter_type", "type");
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
        mockColumn(response2columnList1, "filter_type", "type");
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
        mockColumn(response3columnList1, "filter_type", "type");
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
        doReturn("name:type").when(dao).getFilterIdsRaw(anyString());
        when(gateway.getByFilterIds(anyList())).thenReturn(response3, response, response2);


        try {
            dao.setFilterActive(filter, 4);
        } catch (Exception ignored) {
        }

        InOrder inOrder = inOrder(gateway);

        // assert that the script was activated (and done first)
        Map<String, Object> attributesForActivation = new HashMap<>();
        attributesForActivation.put("active", true);
        attributesForActivation.put("canary", false);
        inOrder.verify(gateway, times(1)).upsert(filter + "_4", attributesForActivation);

        // assert that the previously active script was marked as inactive (after activation step)
        Map<String, Object> attributesForDeactivation = new HashMap<>();
        attributesForDeactivation.put("active", false);
        inOrder.verify(gateway).upsert(filter + "_3", attributesForDeactivation);


    }

    /**
     * Deal with the first of 2 upserts failing ... Cassandra isn't transactional, so we need to deal with this
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testSetScriptActiveHandlesPartialFailure() {
        String filter = "name:type";
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
        mockColumn(columnList0, "filter_type", "type");
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
        mockColumn(columnList1, "filter_type", "type");
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
        doReturn("name:type").when(dao).getFilterIdsRaw(anyString());
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
        String filter = "name:type";
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
        mockColumn(columnList0, "filter_type", "type");
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
        doReturn("name:type").when(dao).getFilterIdsRaw(anyString());
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
        Row<String, String> row0 = mock(Row.class);
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
        mockColumn(columnList0, "filter_type", "type");
        mockColumn(columnList0, "canary", false);

        when(response.getRowByIndex(0)).thenReturn(row0);

        when(response.isEmpty()).thenReturn(true, false); // true on the first request, false thereafter
        when(response.size()).thenReturn(1);
    }

}