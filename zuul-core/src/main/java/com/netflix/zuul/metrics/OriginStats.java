package com.netflix.zuul.metrics;

/**
 * User: michaels@netflix.com
 * Date: 3/20/15
 * Time: 5:55 PM
 */
public interface OriginStats
{
    public void completed(boolean success, long totalTimeMS);
}
