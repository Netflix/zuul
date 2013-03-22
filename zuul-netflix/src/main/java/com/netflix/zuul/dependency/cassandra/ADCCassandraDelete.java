package com.netflix.zuul.dependency.cassandra;


import com.netflix.astyanax.Keyspace;
import com.netflix.astyanax.MutationBatch;
import com.netflix.astyanax.connectionpool.exceptions.ConnectionException;
import com.netflix.astyanax.model.ColumnFamily;


/**
 * Deletes a row from Cassandra.
 */
public class ADCCassandraDelete<RowKeyType> extends AbstractCassandraAPIDependencyCommand<Void> {

    private final Keyspace keyspace;
    private final ColumnFamily<RowKeyType, String> columnFamily;
    private final RowKeyType rowKey;

    public ADCCassandraDelete(Keyspace keyspace, ColumnFamily<RowKeyType, String> columnFamily, RowKeyType rowKey) {
        this.keyspace = keyspace;
        this.columnFamily = columnFamily;
        this.rowKey = rowKey;
    }

    @SuppressWarnings("unchecked")
    public ADCCassandraDelete(Keyspace keyspace, String columnFamilyName, RowKeyType rowKey) {
        this.keyspace = keyspace;
        this.columnFamily = getColumnFamilyViaColumnName(columnFamilyName, rowKey);
        this.rowKey = rowKey;
    }

    @Override
    protected Void run() throws Exception {
        try {
            MutationBatch m = keyspace.prepareMutationBatch();
            m.withRow(columnFamily, rowKey).delete();
            m.execute();
            return null;
        } catch (ConnectionException e) {
            throw e;
        }
    }

}
