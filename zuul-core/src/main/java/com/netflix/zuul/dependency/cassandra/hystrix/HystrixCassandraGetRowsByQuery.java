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
package com.netflix.zuul.dependency.cassandra.hystrix;


import com.netflix.astyanax.Keyspace;
import com.netflix.astyanax.connectionpool.exceptions.ConnectionException;
import com.netflix.astyanax.model.ColumnFamily;
import com.netflix.astyanax.model.CqlResult;
import com.netflix.astyanax.model.Rows;
import com.netflix.zuul.dependency.cassandra.CassandraCache;


/**
 * Fetches values in multiple rows from Cassandra using the CQL query language.
 * <p/>
 * See http://crlog.info/2011/06/13/cassandra-query-language-cql-v1-0-0-updated/
 */
public class HystrixCassandraGetRowsByQuery<RowKeyType> extends AbstractCassandraHystrixCommand<Rows<RowKeyType, String>> {

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
    public HystrixCassandraGetRowsByQuery(Keyspace keyspace, ColumnFamily<RowKeyType, String> columnFamily, String cql) {
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
    public HystrixCassandraGetRowsByQuery(Keyspace keyspace, String columnFamilyName, Class<?> columnFamilyKeyType, String cql) {
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
    public HystrixCassandraGetRowsByQuery<RowKeyType> setCache(CassandraCache<Rows<RowKeyType, String>> cache) {
        this.fallbackCache = cache;
        return this;
    }

    @Override
    protected Rows<RowKeyType, String> getFallback() {
        return (this.fallbackCache != null) ? this.fallbackCache.fetchQuery() : null;
    }

}
