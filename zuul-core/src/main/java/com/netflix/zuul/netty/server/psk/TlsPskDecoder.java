/*
 * Copyright 2024 Netflix, Inc.
 *
 *      Licensed under the Apache License, Version 2.0 (the "License");
 *      you may not use this file except in compliance with the License.
 *      You may obtain a copy of the License at
 *
 *          http://www.apache.org/licenses/LICENSE-2.0
 *
 *      Unless required by applicable law or agreed to in writing, software
 *      distributed under the License is distributed on an "AS IS" BASIS,
 *      WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *      See the License for the specific language governing permissions and
 *      limitations under the License.
 */

package com.netflix.zuul.netty.server.psk;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.ssl.SslHandshakeCompletionEvent;
import org.bouncycastle.tls.TlsFatalAlert;

import java.util.List;

public class TlsPskDecoder extends ByteToMessageDecoder {

    private final TlsPskServerProtocol tlsPskServerProtocol;

    public TlsPskDecoder(TlsPskServerProtocol tlsPskServerProtocol) {
        this.tlsPskServerProtocol = tlsPskServerProtocol;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        final byte[] bytesRead = in.hasArray() ? in.array() : TlsPskUtils.readDirect(in);
        try {
            tlsPskServerProtocol.offerInput(bytesRead);
        } catch (TlsFatalAlert tlsFatalAlert) {
            writeOutputIfAvailable(ctx);
            ctx.fireUserEventTriggered(new SslHandshakeCompletionEvent(tlsFatalAlert));
            ctx.close();
            return;
        }
        writeOutputIfAvailable(ctx);
        final int appDataAvailable = tlsPskServerProtocol.getAvailableInputBytes();
        if (appDataAvailable > 0) {
            byte[] appData = new byte[appDataAvailable];
            tlsPskServerProtocol.readInput(appData, 0, appDataAvailable);
            out.add(Unpooled.wrappedBuffer(appData));
        }
    }

    private void writeOutputIfAvailable(ChannelHandlerContext ctx) {
        final int availableOutputBytes = tlsPskServerProtocol.getAvailableOutputBytes();
        // output is available immediately (handshake not complete), pipe that back to the client right away
        if (availableOutputBytes != 0) {
            byte[] outputBytes = new byte[availableOutputBytes];
            tlsPskServerProtocol.readOutput(outputBytes, 0, availableOutputBytes);
            ctx.writeAndFlush(Unpooled.wrappedBuffer(outputBytes))
                    .addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
        }
    }
}
