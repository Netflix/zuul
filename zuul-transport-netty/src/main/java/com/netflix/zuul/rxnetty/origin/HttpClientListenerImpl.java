package com.netflix.zuul.rxnetty.origin;

import netflix.ocelli.rxnetty.FailureListener;
import netflix.ocelli.rxnetty.protocol.http.WeightedHttpClientListener;

/**
 * An implementation
 */
public class HttpClientListenerImpl extends WeightedHttpClientListener {

    private final FailureListener failureListener;

    public HttpClientListenerImpl(FailureListener failureListener) {
        this.failureListener = failureListener;
    }

    @Override
    public int getWeight() {
        //TODO: Auto-generated
        return 1;
    }

}
