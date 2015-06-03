package com.netflix.zuul.origins;

/**
 * User: michaels
 * Date: 2/24/15
 * Time: 5:35 PM
 */
public interface LoadBalancer
{
    public void init();
    public ServerInfo getNextServer();
}
