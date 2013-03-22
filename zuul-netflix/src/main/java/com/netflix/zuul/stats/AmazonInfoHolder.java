package com.netflix.zuul.stats;

import com.netflix.appinfo.AmazonInfo;

/**
 * Builds and caches an <code>AmazonInfo</code> instance in memory.
 *
 * @author mhawthorne
 */
public class AmazonInfoHolder {

    private static final AmazonInfo INFO = AmazonInfo.Builder.newBuilder().autoBuild("netflix.appinfo");

    public static final AmazonInfo getInfo() {
        return INFO;
    }

    private AmazonInfoHolder() {}

}