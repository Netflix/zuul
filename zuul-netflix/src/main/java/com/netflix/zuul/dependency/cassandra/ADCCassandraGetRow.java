package com.netflix.zuul.dependency.cassandra;


import com.netflix.astyanax.Keyspace;
import com.netflix.astyanax.connectionpool.exceptions.ConnectionException;
import com.netflix.astyanax.model.ColumnFamily;
import com.netflix.astyanax.model.ColumnList;
import com.netflix.astyanax.query.RowQuery;


import java.util.Arrays;

/**
 * Fetches values in a row from Cassandra.
 * <p/>
 * Retrieves all columns by default or can be filtered with the <code>withColumns</code> method.
 */
public class ADCCassandraGetRow<RowKeyType> extends AbstractCassandraAPIDependencyCommand<ColumnList<String>> {

    private final Keyspace keyspace;
    private final ColumnFamily<RowKeyType, String> columnFamily;
    private final RowKeyType rowKey;
    private String[] columns;

    private CassandraCache<ColumnList<String>> fallbackCache = null;

    public ADCCassandraGetRow(Keyspace keyspace, ColumnFamily<RowKeyType, String> columnFamily, RowKeyType rowKey) {
        this.keyspace = keyspace;
        this.columnFamily = columnFamily;
        this.rowKey = rowKey;
    }

    @SuppressWarnings("unchecked")
    public ADCCassandraGetRow(Keyspace keyspace, String columnFamilyName, RowKeyType rowKey) {
        this.keyspace = keyspace;
        this.columnFamily = getColumnFamilyViaColumnName(columnFamilyName, rowKey);
        this.rowKey = rowKey;
    }

    @Override
    protected ColumnList<String> run() throws Exception {
        try {
            RowQuery<RowKeyType, String> rowQuery = keyspace.prepareQuery(columnFamily).getKey(rowKey);
            /* apply column slice if we have one */
            if (columns != null) {
                rowQuery = rowQuery.withColumnSlice(columns);
            }
            ColumnList<String> result = rowQuery.execute().getResult();
            if (fallbackCache != null) {
                try {
                    /* store the response in the cache for fallback if we have a cache */
                    if (columns != null) {
                        fallbackCache.storeQuery(result, keyspace.toString(), columnFamily.getName(), rowKey.toString(), Arrays.toString(columns));
                    } else {
                        fallbackCache.storeQuery(result, keyspace.toString(), columnFamily.getName(), rowKey.toString());
                    }
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
     * Restrict the response to only these columns.
     * <p/>
     * Example usage: new ADCCassandraGetRow(args).withColumns("column1", "column2").execute()
     *
     * @param columns
     * @return
     */
    public ADCCassandraGetRow<RowKeyType> withColumns(String... columns) {
        this.columns = columns;
        return this;
    }

    /**
     * Optionally define a cache to retrieve values from for fallback if the query fails.
     * <p/>
     * Idiomatic usage: new ADCCassandraSelect(args).setCache(cache).execute()
     *
     * @param cache
     * @return
     */
    public ADCCassandraGetRow<RowKeyType> setCache(CassandraCache<ColumnList<String>> cache) {
        this.fallbackCache = cache;
        return this;
    }

    @Override
    protected ColumnList<String> getFallback() {
        return this.fallbackCache.fetchQuery();
    }

}
