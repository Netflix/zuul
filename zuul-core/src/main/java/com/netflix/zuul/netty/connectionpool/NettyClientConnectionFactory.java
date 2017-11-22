/*
 * Copyright 2018 Netflix, Inc.
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

package com.netflix.zuul.netty.connectionpool;

import com.netflix.zuul.netty.server.Server;
import com.netflix.zuul.passport.CurrentPassport;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoop;
import io.netty.channel.epoll.EpollSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;

import java.net.InetSocketAddress;

/**
 * Created by saroskar on 3/16/16.
 */
public class NettyClientConnectionFactory {

    private final ConnectionPoolConfig connPoolConfig;
    private final ChannelInitializer<? extends Channel> channelInitializer;

    NettyClientConnectionFactory(final ConnectionPoolConfig connPoolConfig,
                                 final ChannelInitializer<? extends Channel> channelInitializer) {
        this.connPoolConfig = connPoolConfig;
        this.channelInitializer = channelInitializer;
    }

    public ChannelFuture connect(final EventLoop eventLoop, String host, final int port, CurrentPassport passport) {

        Class socketChannelClass;
        if (Server.USE_EPOLL.get()) {
            socketChannelClass = EpollSocketChannel.class;
        } else {
            socketChannelClass = NioSocketChannel.class;
        }

        final Bootstrap bootstrap = new Bootstrap()
                .channel(socketChannelClass)
                .handler(channelInitializer)
                .group(eventLoop)
                .attr(CurrentPassport.CHANNEL_ATTR, passport)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connPoolConfig.getConnectTimeout())
                .option(ChannelOption.SO_KEEPALIVE, connPoolConfig.getTcpKeepAlive())
                .option(ChannelOption.TCP_NODELAY, connPoolConfig.getTcpNoDelay())
                .option(ChannelOption.SO_SNDBUF, connPoolConfig.getTcpSendBufferSize())
                .option(ChannelOption.SO_RCVBUF, connPoolConfig.getTcpReceiveBufferSize())
                .option(ChannelOption.WRITE_BUFFER_HIGH_WATER_MARK, connPoolConfig.getNettyWriteBufferHighWaterMark())
                .option(ChannelOption.WRITE_BUFFER_LOW_WATER_MARK, connPoolConfig.getNettyWriteBufferLowWaterMark())
                .option(ChannelOption.AUTO_READ, connPoolConfig.getNettyAutoRead())
                .remoteAddress(new InetSocketAddress(host, port));

        return bootstrap.connect();
    }

}
