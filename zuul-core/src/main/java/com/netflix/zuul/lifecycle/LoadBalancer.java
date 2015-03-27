package com.netflix.zuul.lifecycle;

import io.reactivex.netty.client.RxClient;

/**
 * User: michaels
 * Date: 2/24/15
 * Time: 5:35 PM
 */
public interface LoadBalancer
{
    public void init();
    public RxClient.ServerInfo getNextServer();
}
