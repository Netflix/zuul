package com.netflix.zuul.rxnetty.origin;

import io.reactivex.netty.protocol.http.client.HttpClientRequest;

import java.util.NoSuchElementException;

/**
 * A retry function to be applied to an {@link HttpClientRequest} to retry when there are no servers available.
 */
public class RetryWhenNoServersAvailable {

    public static final RetryPolicy SINGLE_RETRY =
            RetryPolicy.createSingleRetry(throwable -> throwable instanceof NoSuchElementException);

    public static RetryPolicy create() {
        return SINGLE_RETRY;
    }
}

