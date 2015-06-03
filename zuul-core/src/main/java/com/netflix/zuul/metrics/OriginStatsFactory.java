package com.netflix.zuul.metrics;

/**
 * User: michaels@netflix.com
 * Date: 3/20/15
 * Time: 6:14 PM
 */
public interface OriginStatsFactory
{
    public OriginStats create(String name);
}
