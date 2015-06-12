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
package com.netflix.zuul.dependency.cassandra;

import com.netflix.astyanax.AstyanaxContext;
import com.netflix.astyanax.Keyspace;
import com.netflix.astyanax.connectionpool.NodeDiscoveryType;
import com.netflix.astyanax.connectionpool.impl.ConnectionPoolConfigurationImpl;
import com.netflix.astyanax.connectionpool.impl.CountingConnectionPoolMonitor;
import com.netflix.astyanax.impl.AstyanaxConfigurationImpl;
import com.netflix.astyanax.thrift.ThriftFamilyFactory;
import com.netflix.config.ConfigurationManager;
import com.netflix.config.DynamicPropertyFactory;
import org.apache.commons.configuration.AbstractConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.netflix.zuul.constants.ZuulConstants.DEFAULT_NFASTYANAX_FAILOVERWAITTIME;
import static com.netflix.zuul.constants.ZuulConstants.DEFAULT_NFASTYANAX_MAXCONNSPERHOST;
import static com.netflix.zuul.constants.ZuulConstants.DEFAULT_NFASTYANAX_MAXFAILOVERCOUNT;
import static com.netflix.zuul.constants.ZuulConstants.DEFAULT_NFASTYANAX_MAXTIMEOUTWHENEXHAUSTED;
import static com.netflix.zuul.constants.ZuulConstants.DEFAULT_NFASTYANAX_READCONSISTENCY;
import static com.netflix.zuul.constants.ZuulConstants.DEFAULT_NFASTYANAX_SOCKETTIMEOUT;
import static com.netflix.zuul.constants.ZuulConstants.DEFAULT_NFASTYANAX_WRITECONSISTENCY;
import static com.netflix.zuul.constants.ZuulConstants.ZUUL_CASSANDRA_CSQL_VERSION;
import static com.netflix.zuul.constants.ZuulConstants.ZUUL_CASSANDRA_HOST;
import static com.netflix.zuul.constants.ZuulConstants.ZUUL_CASSANDRA_KEYSPACE;
import static com.netflix.zuul.constants.ZuulConstants.ZUUL_CASSANDRA_MAXCONNECTIONSPERHOST;
import static com.netflix.zuul.constants.ZuulConstants.ZUUL_CASSANDRA_PORT;
import static com.netflix.zuul.constants.ZuulConstants.ZUUL_CASSANDRA_TARGET_VERSION;

/**
 * Cassandra helper class that configures Astyanax and gets the keyspace that
 * is hosting the Zuul filters
 *
 * @author Raju Uppalapati
 */
public class CassandraHelper {
    
    private static Logger LOG = LoggerFactory.getLogger(CassandraHelper.class);
    private static Keyspace zuulCassKeyspace;

    private CassandraHelper() {}

    public static CassandraHelper getInstance() {
        return CassandraHelperSingletonHolder.instance;
    }
    
    private static class CassandraHelperSingletonHolder {
        static final CassandraHelper instance = new CassandraHelper();
    }
    
    /**
     * @return the Keyspace for the Zuul Cassandra cluster which stores filters
     * @throws Exception
     */
    public Keyspace getZuulCassKeyspace() throws Exception {
        if (zuulCassKeyspace != null) return zuulCassKeyspace;
        try {
            setAstynaxConfiguration(ConfigurationManager.getConfigInstance());

            AstyanaxContext<Keyspace> context = new AstyanaxContext.Builder()
                    .forKeyspace(DynamicPropertyFactory.getInstance().getStringProperty(ZUUL_CASSANDRA_KEYSPACE, "zuul_scripts").get())
                    .withAstyanaxConfiguration(new AstyanaxConfigurationImpl()
                            .setDiscoveryType(NodeDiscoveryType.RING_DESCRIBE)
                            .setTargetCassandraVersion(DynamicPropertyFactory.getInstance().getStringProperty(ZUUL_CASSANDRA_TARGET_VERSION, "2.0.9").get())
                            .setCqlVersion(DynamicPropertyFactory.getInstance().getStringProperty(ZUUL_CASSANDRA_CSQL_VERSION, "3.0.0").get())
                    )
                    .withConnectionPoolConfiguration(new ConnectionPoolConfigurationImpl("cass_connection_pool")
                                    .setPort(DynamicPropertyFactory.getInstance().getIntProperty(ZUUL_CASSANDRA_PORT, 7102).get())
                                    .setMaxConnsPerHost(DynamicPropertyFactory.getInstance().getIntProperty(ZUUL_CASSANDRA_MAXCONNECTIONSPERHOST, 1).get())
                                    .setSeeds(DynamicPropertyFactory.getInstance().getStringProperty(ZUUL_CASSANDRA_HOST, "").get() + ":" +
                                                    DynamicPropertyFactory.getInstance().getIntProperty(ZUUL_CASSANDRA_PORT, 7102).get()
                                    )
                    )
                    .withConnectionPoolMonitor(new CountingConnectionPoolMonitor())
                    .buildKeyspace(ThriftFamilyFactory.getInstance());

            context.start();
            zuulCassKeyspace = context.getClient();
            return zuulCassKeyspace;
        } catch (Exception e) {
            LOG.error("Exception occurred when initializing Cassandra keyspace: " + e);
            throw e;
        }
    }
    
    private void setAstynaxConfiguration(AbstractConfiguration configuration) {
        configuration.setProperty(DEFAULT_NFASTYANAX_READCONSISTENCY, DynamicPropertyFactory.getInstance().getStringProperty(DEFAULT_NFASTYANAX_READCONSISTENCY, "CL_ONE").get());
        configuration.setProperty(DEFAULT_NFASTYANAX_WRITECONSISTENCY, DynamicPropertyFactory.getInstance().getStringProperty("zuul.cassandra.default.nfastyanax.writeConsistency", "CL_ONE").get());
        configuration.setProperty(DEFAULT_NFASTYANAX_SOCKETTIMEOUT, DynamicPropertyFactory.getInstance().getStringProperty("zuul.cassandra.default.nfastyanax.socketTimeout", "2000").get());
        configuration.setProperty(DEFAULT_NFASTYANAX_MAXCONNSPERHOST, DynamicPropertyFactory.getInstance().getStringProperty("zuul.cassandra.default.nfastyanax.maxConnsPerHost", "3").get());
        configuration.setProperty(DEFAULT_NFASTYANAX_MAXTIMEOUTWHENEXHAUSTED, DynamicPropertyFactory.getInstance().getStringProperty("zuul.cassandra.default.nfastyanax.maxTimeoutWhenExhausted", "2000").get());
        configuration.setProperty(DEFAULT_NFASTYANAX_MAXFAILOVERCOUNT, DynamicPropertyFactory.getInstance().getStringProperty("zuul.cassandra.default.nfastyanax.maxFailoverCount", "1").get());
        configuration.setProperty(DEFAULT_NFASTYANAX_FAILOVERWAITTIME, DynamicPropertyFactory.getInstance().getStringProperty("zuul.cassandra.default.nfastyanax.failoverWaitTime", "0").get());
    }
}
