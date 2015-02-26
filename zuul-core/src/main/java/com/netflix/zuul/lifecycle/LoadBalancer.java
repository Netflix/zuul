package com.netflix.zuul.lifecycle;

import io.netty.buffer.ByteBuf;
import io.reactivex.netty.protocol.http.client.HttpClient;

/**
 * User: michaels
 * Date: 2/24/15
 * Time: 5:35 PM
 */
public interface LoadBalancer
{
    public void init();
    public HttpClient<ByteBuf, ByteBuf> getNextServer();
}
