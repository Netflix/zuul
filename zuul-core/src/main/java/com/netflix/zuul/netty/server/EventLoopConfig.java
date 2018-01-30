package com.netflix.zuul.netty.server;

public interface EventLoopConfig
{
    int eventLoopCount();

    int acceptorCount();
}
