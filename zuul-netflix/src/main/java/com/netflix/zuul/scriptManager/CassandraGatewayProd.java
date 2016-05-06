package com.netflix.zuul.scriptManager;

import com.netflix.astyanax.AstyanaxContext;
import com.netflix.astyanax.Keyspace;
import com.netflix.astyanax.model.Rows;
import com.netflix.zuul.dependency.cassandra.hystrix.HystrixCassandraGetRowsByKeys;
import com.netflix.zuul.dependency.cassandra.hystrix.HystrixCassandraGetRowsByQuery;
import com.netflix.zuul.dependency.cassandra.hystrix.HystrixCassandraPut;
import net.jcip.annotations.ThreadSafe;

import java.util.HashMap;
import java.util.List;
import java.util.Map;


@ThreadSafe
public class CassandraGatewayProd implements CassandraGateway {
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
