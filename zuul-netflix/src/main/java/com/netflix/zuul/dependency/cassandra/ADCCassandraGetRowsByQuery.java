package com.netflix.zuul.dependency.cassandra;


import com.netflix.astyanax.Keyspace;
import com.netflix.astyanax.connectionpool.exceptions.ConnectionException;
import com.netflix.astyanax.model.ColumnFamily;
import com.netflix.astyanax.model.CqlResult;
import com.netflix.astyanax.model.Rows;


/**
 * Fetches values in multiple rows from Cassandra using the CQL query language.
 * <p/>
 * See http://crlog.info/2011/06/13/cassandra-query-language-cql-v1-0-0-updated/
 */
public class ADCCassandraGetRowsByQuery<RowKeyType> extends AbstractCassandraAPIDependencyCommand<Rows<RowKeyType, String>> {

    private final Keyspace keyspace;
    private final ColumnFamily<RowKeyType, String> columnFamily;
    private final String cql;

    private CassandraCache<Rows<RowKeyType, String>> fallbackCache = null;

    /**
     * Get rows specified by their row keys.
     *
     * @param keyspace
     * @param columnFamily
     * @param cql
     */
    public ADCCassandraGetRowsByQuery(Keyspace keyspace, ColumnFamily<RowKeyType, String> columnFamily, String cql) {
        this.keyspace = keyspace;
        this.columnFamily = columnFamily;
        this.cql = cql;
    }

    /**
     * Get rows specified by their row keys.
     *
     * @param keyspace
     * @param columnFamilyName
     * @param cql
     */
    @SuppressWarnings("unchecked")
    public ADCCassandraGetRowsByQuery(Keyspace keyspace, String columnFamilyName, Class<?> columnFamilyKeyType, String cql) {
        this.keyspace = keyspace;
        this.columnFamily = getColumnFamilyViaColumnName(columnFamilyName, columnFamilyKeyType);
        this.cql = cql;
    }

    @Override
    protected Rows<RowKeyType, String> run() throws Exception {
        try {
            CqlResult<RowKeyType, String> cqlresult = keyspace.prepareQuery(columnFamily).withCql(cql).execute().getResult();
            Rows<RowKeyType, String> result = cqlresult.getRows();
            if (fallbackCache != null) {
                try {
                    /* store the response in the cache for fallback if we have a cache */
                    fallbackCache.storeQuery(result, keyspace.toString(), columnFamily.getName(), cql);
                } catch (Exception e) {
                    // don't blow up on cache population since this is non-essential
                }
            }
            return result;
        } catch (ConnectionException e) {
            throw e;
        }
    }

    /**
     * Optionally define a cache to retrieve values from for fallback if the query fails.
     * <p/>
     * Idiomatic usage: new ADCCassandraSelect(args).setCache(cache).execute()
     *
     * @param cache
     * @return
     */
    public ADCCassandraGetRowsByQuery<RowKeyType> setCache(CassandraCache<Rows<RowKeyType, String>> cache) {
        this.fallbackCache = cache;
        return this;
    }

    @Override
    protected Rows<RowKeyType, String> getFallback() {
        return (this.fallbackCache != null) ? this.fallbackCache.fetchQuery() : null;
    }

}
