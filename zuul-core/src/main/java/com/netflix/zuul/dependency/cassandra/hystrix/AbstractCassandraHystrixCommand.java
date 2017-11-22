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

import com.netflix.astyanax.model.ColumnFamily;
import com.netflix.astyanax.serializers.IntegerSerializer;
import com.netflix.astyanax.serializers.LongSerializer;
import com.netflix.astyanax.serializers.StringSerializer;
import com.netflix.hystrix.HystrixCommand;
import com.netflix.hystrix.HystrixCommandGroupKey;

/**
 * Abstract Hystrix wrapper for Astyanax Cassandra calls
 * @param <K>
 */
public abstract class AbstractCassandraHystrixCommand<K> extends HystrixCommand<K> {



    public AbstractCassandraHystrixCommand() {
        super(HystrixCommandGroupKey.Factory.asKey("Cassandra"));
	}


    /**
     * returns a ColumnFamily given a columnFamilyName
     * @param columnFamilyName
     * @param rowKey
     * @return
     */
    @SuppressWarnings("rawtypes")
    protected ColumnFamily getColumnFamilyViaColumnName(String columnFamilyName, Object rowKey) {
        return getColumnFamilyViaColumnName(columnFamilyName, rowKey.getClass());
    }

    /**
     * returns a ColumnFamily given a columnFamilyName
     * @param columnFamilyName
     * @param rowKeyClass
     * @return
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    protected ColumnFamily getColumnFamilyViaColumnName(String columnFamilyName, Class rowKeyClass) {
        if (rowKeyClass == String.class) {
            return new ColumnFamily(columnFamilyName, StringSerializer.get(), StringSerializer.get());
        } else if (rowKeyClass == Integer.class) {
            return new ColumnFamily(columnFamilyName, IntegerSerializer.get(), StringSerializer.get());
        } else if (rowKeyClass == Long.class) {
            return new ColumnFamily(columnFamilyName, LongSerializer.get(), StringSerializer.get());
        } else {
            throw new IllegalArgumentException("RowKeyType is not supported: " + rowKeyClass.getSimpleName() + ". String/Integer/Long are supported, or you can define the ColumnFamily yourself and use the other constructor.");
        }
    }

}
