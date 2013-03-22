package com.netflix.zuul.dependency.cassandra;

/**
 * Operations for caching SimpleDB results for use with fallbacks.
 */
public interface CassandraCache<K> {

    K fetchQuery(String... keys);

    void storeQuery(K response, String... keys);

}
