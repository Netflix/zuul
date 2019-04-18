package com.netflix.zuul.netty.server;

import com.netflix.config.CachedDynamicIntProperty;

import javax.inject.Singleton;

@Singleton
public class ServerTimeout
{
    private static CachedDynamicIntProperty SERVER_CONN_IDLE_TIMEOUT =
            new CachedDynamicIntProperty("server.connection.idle.timeout", 65000);

    public int connectionIdleTimeout()
    {
        return SERVER_CONN_IDLE_TIMEOUT.get();
    }

    public int defaultRequestExpiryTimeout()
    {
        // Note this is the timeout for the inbound request to zuul, not for each outbound attempt.
        // It needs to align with the inbound connection idle timeout and/or the ELB idle timeout. So we
        // set it here to 1 sec less than that.

        int idleTimeout = connectionIdleTimeout();
        return idleTimeout > 1000 ? idleTimeout - 1000 : 1000;
    }
}
