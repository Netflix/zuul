package com.netflix.zuul.netty.server;

public class ServerTimeout
{
    private final int connectionIdleTimeout;

    public ServerTimeout(int connectionIdleTimeout)
    {
        this.connectionIdleTimeout = connectionIdleTimeout;
    }

    public int connectionIdleTimeout()
    {
        return connectionIdleTimeout;
    }

    public int defaultRequestExpiryTimeout()
    {
        // Note this is the timeout for the inbound request to zuul, not for each outbound attempt.
        // It needs to align with the inbound connection idle timeout and/or the ELB idle timeout. So we
        // set it here to 1 sec less than that.
        return connectionIdleTimeout > 1000 ? connectionIdleTimeout - 1000 : 1000;
    }
}
