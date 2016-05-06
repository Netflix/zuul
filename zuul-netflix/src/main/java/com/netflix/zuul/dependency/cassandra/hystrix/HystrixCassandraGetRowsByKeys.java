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
package com.netflix.zuul.dependency.cassandra.hystrix;


import com.netflix.astyanax.Keyspace;
import com.netflix.astyanax.connectionpool.exceptions.ConnectionException;
import com.netflix.astyanax.model.ColumnFamily;
import com.netflix.astyanax.model.Rows;
import com.netflix.astyanax.query.RowSliceQuery;
import com.netflix.zuul.dependency.cassandra.CassandraCache;

import java.util.Arrays;

/**
 * Fetches values in multiple rows from Cassandra using row keys.
 * <p/>
 * Retrieves all columns by default or can be filtered with the <code>withColumns</code> method.
 */
public class HystrixCassandraGetRowsByKeys<RowKeyType> extends AbstractCassandraHystrixCommand<Rows<RowKeyType, String>> {

    private final Keyspace keyspace;
    private final ColumnFamily<RowKeyType, String> columnFamily;
    private final RowKeyType[] rowKeys;
    private final RowKeyType startKey;
    private final RowKeyType endKey;
    private final String startToken;
    private final String endToken;
    private final int maxRows;

    private String[] columns;

    private CassandraCache<Rows<RowKeyType, String>> fallbackCache = null;

    /**
     * Get rows specified by their row keys.
     *
     * @param keyspace
     * @param columnFamily
     * @param rowKeys
     */
    public HystrixCassandraGetRowsByKeys(Keyspace keyspace, ColumnFamily<RowKeyType, String> columnFamily, RowKeyType... rowKeys) {
        this.keyspace = keyspace;
        this.columnFamily = columnFamily;
        this.rowKeys = rowKeys;
        this.startKey = null;
        this.endKey = null;
        this.startToken = null;
        this.endToken = null;
        this.maxRows = -1;
    }

    /**
     * Get a range of rows.
     *
     * @param keyspace
     * @param columnFamily
     * @param startKey
     * @param endKey
     * @param startToken
     * @param endToken
     * @param maxRows
     */
    public HystrixCassandraGetRowsByKeys(Keyspace keyspace, ColumnFamily<RowKeyType, String> columnFamily, RowKeyType startKey, RowKeyType endKey, String startToken, String endToken, int maxRows) {
        this.keyspace = keyspace;
        this.columnFamily = columnFamily;
        this.rowKeys = null;
        this.startKey = startKey;
        this.endKey = endKey;
        this.startToken = startToken;
        this.endToken = endToken;
        this.maxRows = maxRows;
    }

    /**
     * Get rows specified by their row keys.
     *
     * @param keyspace
     * @param columnFamilyName
     * @param rowKeys
     */
    @SuppressWarnings("unchecked")
    public HystrixCassandraGetRowsByKeys(Keyspace keyspace, String columnFamilyName, RowKeyType... rowKeys) {
        this.keyspace = keyspace;
        this.columnFamily = getColumnFamilyViaColumnName(columnFamilyName, rowKeys[0]);
        this.rowKeys = rowKeys;
        this.startKey = null;
        this.endKey = null;
        this.startToken = null;
        this.endToken = null;
        this.maxRows = -1;
    }

    /**
     * Get a range of rows.
     *
     * @param keyspace
     * @param columnFamilyName
     * @param startKey
     * @param endKey
     * @param startToken
     * @param endToken
     * @param maxRows
     */
    @SuppressWarnings("unchecked")
    public HystrixCassandraGetRowsByKeys(Keyspace keyspace, String columnFamilyName, RowKeyType startKey, RowKeyType endKey, String startToken, String endToken, int maxRows) {
        this.keyspace = keyspace;
        this.columnFamily = getColumnFamilyViaColumnName(columnFamilyName, startKey);
        this.rowKeys = null;
        this.startKey = startKey;
        this.endKey = endKey;
        this.startToken = startToken;
        this.endToken = endToken;
        this.maxRows = maxRows;
    }

    @Override
    protected Rows<RowKeyType, String> run() throws Exception {
        try {
            RowSliceQuery<RowKeyType, String> rowQuery = null;
            if (rowKeys != null) {
                rowQuery = keyspace.prepareQuery(columnFamily).getKeySlice(rowKeys);
            } else {
                rowQuery = keyspace.prepareQuery(columnFamily).getKeyRange(startKey, endKey, startToken, endToken, maxRows);
            }

            /* apply column slice if we have one */
            if (columns != null) {
                rowQuery = rowQuery.withColumnSlice(columns);
            }
            Rows<RowKeyType, String> result = rowQuery.execute().getResult();
            if (fallbackCache != null) {
                try {
                    /* store the response in the cache for fallback if we have a cache */
                    if (columns != null) {
                        fallbackCache.storeQuery(result, keyspace.toString(), columnFamily.getName(), Arrays.toString(rowKeys), String.valueOf(startKey), String.valueOf(endKey), startToken, endToken, String.valueOf(maxRows), Arrays.toString(columns));
                    } else {
                        fallbackCache.storeQuery(result, keyspace.toString(), columnFamily.getName(), Arrays.toString(rowKeys), String.valueOf(startKey), String.valueOf(endKey), startToken, endToken, String.valueOf(maxRows));
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
     * Example usage: new HystrixCassandraGetRow(args).withColumns("column1", "column2").execute()
     *
     * @param columns
     * @return
     */
    public HystrixCassandraGetRowsByKeys<RowKeyType> withColumns(String... columns) {
        this.columns = columns;
        return this;
    }

    /**
     * Optionally define a cache to retrieve values from for fallback if the query fails.
     * <p/>
     * Idiomatic usage: new HystrixCassandraSelect(args).setCache(cache).execute()
     *
     * @param cache
     * @return
     */
    public HystrixCassandraGetRowsByKeys<RowKeyType> setCache(CassandraCache<Rows<RowKeyType, String>> cache) {
        this.fallbackCache = cache;
        return this;
    }

    @Override
    protected Rows<RowKeyType, String> getFallback() {
        return this.fallbackCache.fetchQuery();
    }

}
