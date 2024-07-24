package com.netflix.zuul.netty.server.psk;

import io.netty.buffer.ByteBuf;

class TlsPskUtils {
    static byte[] readDirect(ByteBuf byteBufMsg) {
        int length = byteBufMsg.readableBytes();
        byte[] dest = new byte[length];
        byteBufMsg.readSlice(length).getBytes(0, dest);
        return dest;
    }
}
