package com.netflix.zuul.netty.push;

import com.google.common.base.Charsets;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Maintains client key to web socket or SSE channel mapping.
 *
 * Created by saroskar on 9/26/16.
 */
@Singleton
public class PushConnectionRegistry {

    private final ConcurrentMap<String, PushConnection> clientPushConnectionMap;

    @Inject
    private PushConnectionRegistry() {
        clientPushConnectionMap = new ConcurrentHashMap<>(1024 * 8);
    }

    public static String buildKey(final String cid, final String esn) {
        return cid + "^" + esn;
    }

    public PushConnection get(final String cid, final String esn) {
        return clientPushConnectionMap.get(buildKey(cid, esn));
    }

    public void put(final String cid, final String esn, final PushConnection pushConnection) {
        clientPushConnectionMap.put(buildKey(cid, esn), pushConnection);
    }

    public PushConnection remove(final String cid, final String esn) {
        final PushConnection pc = clientPushConnectionMap.remove(buildKey(cid, esn));
        return pc;
    }

    public int size() {
        return clientPushConnectionMap.size();
    }

    public static class PushConnection {
        private final PushProtocol pushProtocol;
        private final ChannelHandlerContext ctx;

        public PushConnection(PushProtocol pushProtocol, ChannelHandlerContext ctx) {
            this.pushProtocol = pushProtocol;
            this.ctx = ctx;
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

}
