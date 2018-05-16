package com.netflix.zuul.netty.server.push;

public interface PushUserAuth {

    boolean isSuccess();

    int statusCode();

    String getClientIdentity();

}
