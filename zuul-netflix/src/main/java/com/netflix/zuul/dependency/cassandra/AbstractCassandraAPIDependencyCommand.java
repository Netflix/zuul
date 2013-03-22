package com.netflix.zuul.dependency.cassandra;

import com.netflix.astyanax.model.ColumnFamily;
import com.netflix.astyanax.serializers.IntegerSerializer;
import com.netflix.astyanax.serializers.LongSerializer;
import com.netflix.astyanax.serializers.StringSerializer;
import com.netflix.hystrix.HystrixCommand;
import com.netflix.hystrix.HystrixCommandGroupKey;

public abstract class AbstractCassandraAPIDependencyCommand<K> extends HystrixCommand<K> {



    public AbstractCassandraAPIDependencyCommand() {
        super(HystrixCommandGroupKey.Factory.asKey("Cassandra"));
	}


    @SuppressWarnings("rawtypes")
    protected ColumnFamily getColumnFamilyViaColumnName(String columnFamilyName, Object rowKey) {
        return getColumnFamilyViaColumnName(columnFamilyName, rowKey.getClass());
    }

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
