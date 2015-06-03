package com.netflix.zuul.origins;

/**
 * User: michaels
 * Date: 2/24/15
 * Time: 5:42 PM
 */
public interface LoadBalancerFactory
{
    public LoadBalancer create(String vip);
}
