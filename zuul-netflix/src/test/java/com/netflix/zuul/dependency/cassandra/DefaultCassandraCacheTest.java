package com.netflix.zuul.dependency.cassandra;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static com.netflix.zuul.dependency.cassandra.DefaultCassandraCache.buildKey;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;


public class DefaultCassandraCacheTest {
    private static final String DOMAIN = "test-domain";

    private static final String QUERY1 = "test-query1";

    private static final String QUERY2 = "test-query2";

    @Mock
    HashMap<String, String> response1;

    @Mock
    HashMap<String, String> response2;

    @Mock
    ConcurrentMap<String, HashMap<String, String>> cacheMap;

    DefaultCassandraCache<HashMap<String, String>> cache;

    @Before
    public void before() {
        MockitoAnnotations.initMocks(this);

        cacheMap = new ConcurrentHashMap<>();
        cache = new DefaultCassandraCache<>(cacheMap);
        cache = spy(cache);
    }

    @Test
    public void storesItemIfCacheIsNotFull() {
        when(cache.getMaxCacheSize()).thenReturn(5);

        final String cacheKey1 = buildKey(DOMAIN, QUERY1);
        final String cacheKey2 = buildKey(DOMAIN, QUERY2);

        cache.storeQuery(response1, DOMAIN, QUERY1);
        cache.storeQuery(response2, DOMAIN, QUERY2);

        assertSame(response1, cache.fetchQuery(DOMAIN, QUERY1));
        assertSame(response1, cacheMap.get(cacheKey1));

        assertSame(response2, cache.fetchQuery(DOMAIN, QUERY2));
        assertSame(response2, cacheMap.get(cacheKey2));
    }

    @Test
    public void removesItemIfCacheIsFull() {
        when(cache.getMaxCacheSize()).thenReturn(1);

        final String cacheKey1 = buildKey(DOMAIN, QUERY1);
        final String cacheKey2 = buildKey(DOMAIN, QUERY2);

        cache.storeQuery(response1, DOMAIN, QUERY1);
        assertSame(response1, cache.fetchQuery(DOMAIN, QUERY1));
        assertSame(response1, cacheMap.get(cacheKey1));
        assertEquals(1, cacheMap.size());

        cache.storeQuery(response2, DOMAIN, QUERY2);
        assertSame(response2, cache.fetchQuery(DOMAIN, QUERY2));
        assertSame(response2, cacheMap.get(cacheKey2));
        assertEquals(1, cacheMap.size());
    }
}