package com.netflix.zuul.sample.push;

import com.netflix.zuul.netty.server.push.PushUserAuth;

/**
 * Author: Susheel Aroskar
 * Date: 5/16/18
 */
public class SamplePushUserAuth implements PushUserAuth {

    private String customerId;
    private int statusCode;

    private SamplePushUserAuth(String customerId, int statusCode) {
        this.customerId = customerId;
        this.statusCode = statusCode;
    }

    // Successful auth
    public SamplePushUserAuth(String customerId) {
        this(customerId, 200);
    }

    // Failed auth
    public SamplePushUserAuth(int statusCode) {
        this("", statusCode);
    }

    @Override
    public boolean isSuccess() {
        return statusCode == 200;
    }

    @Override
    public int statusCode() {
        return statusCode;
    }

    @Override
    public String getClientIdentity() {
        return customerId;
    }
}
