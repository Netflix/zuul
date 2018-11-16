package com.netflix.zuul.netty.server.push;

import com.google.common.base.Charsets;
import com.netflix.config.CachedDynamicIntProperty;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;

/**
 * Author: Susheel Aroskar
 * Date:
 */
public class PushConnection {

    private final PushProtocol pushProtocol;
    private final ChannelHandlerContext ctx;
    private String secureToken;

    //Token bucket implementation state.
    private double tkBktAllowance;
    private long tkBktLastCheckTime;
    public static final CachedDynamicIntProperty TOKEN_BUCKET_RATE = new CachedDynamicIntProperty("zuul.push.tokenBucket.rate", 3);
    public static final CachedDynamicIntProperty TOKEN_BUCKET_WINDOW = new CachedDynamicIntProperty("zuul.push.tokenBucket.window.millis", 2000);



    public PushConnection(PushProtocol pushProtocol, ChannelHandlerContext ctx) {
        this.pushProtocol = pushProtocol;
        this.ctx = ctx;
        tkBktAllowance = TOKEN_BUCKET_RATE.get();
        tkBktLastCheckTime = System.currentTimeMillis();
    }

    public String getSecureToken() {
        return secureToken;
    }

    public void setSecureToken(String secureToken) {
        this.secureToken = secureToken;
    }

    /**
     * Implementation of TokenBucket algorithm to do rate limiting: http://stackoverflow.com/a/668327
     * @return true if should be rate limited, false if it is OK to send the message
     */
    public synchronized boolean isRateLimited() {
        final double rate = TOKEN_BUCKET_RATE.get();
        final double window = TOKEN_BUCKET_WINDOW.get();
        final long current = System.currentTimeMillis();
        final double timePassed = current - tkBktLastCheckTime;

        tkBktLastCheckTime = current;
        tkBktAllowance = tkBktAllowance + timePassed * (rate / window);

        if (tkBktAllowance > rate) {
            tkBktAllowance = rate; //cap max to rate
        }

        if (tkBktAllowance < 1.0) {
            return true;
        }

        tkBktAllowance = tkBktAllowance - 1.0;
        return false;
    }

    public ChannelFuture sendPushMessage(ByteBuf mesg) {
        return pushProtocol.sendPushMessage(ctx, mesg);
    }

    public ChannelFuture sendPushMessage(String mesg) {
        return sendPushMessage(Unpooled.copiedBuffer(mesg, Charsets.UTF_8));
    }

    public ChannelFuture sendPing() {
        return pushProtocol.sendPing(ctx);
    }

}
