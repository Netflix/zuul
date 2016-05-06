package com.netflix.zuul.scriptManager;

import com.netflix.astyanax.model.Rows;

import java.util.List;
import java.util.Map;

/**
 * Gateway to our usage of Cassandra so we encapsulate the calls and can stub these out for unit testing.
 */
public interface CassandraGateway {
    void upsert(String rowKey, Map<String, Object> attributes);

    void updateFilterIndex(String rowKey, String filter_ids);

    Rows<String, String> select(String cql);

    Rows<String, String> getByFilterIds(List<String> filterIds);
}

