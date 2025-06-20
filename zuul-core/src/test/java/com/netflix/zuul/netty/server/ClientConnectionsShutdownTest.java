/*
 * Copyright 2023 Netflix, Inc.
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

package com.netflix.zuul.netty.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import com.netflix.appinfo.InstanceInfo.InstanceStatus;
import com.netflix.config.ConfigurationManager;
import com.netflix.discovery.EurekaClient;
import com.netflix.discovery.EurekaEventListener;
import com.netflix.discovery.StatusChangeEvent;
import com.netflix.zuul.netty.server.ClientConnectionsShutdown.ShutdownType;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.DefaultEventLoop;
import io.netty.channel.DefaultEventLoopGroup;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.channel.local.LocalAddress;
import io.netty.channel.local.LocalChannel;
import io.netty.channel.local.LocalServerChannel;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Promise;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.apache.commons.configuration.AbstractConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

/**
 * @author Justin Guerra
 * @since 2/28/23
 */
class ClientConnectionsShutdownTest {

    // using LocalChannels instead of EmbeddedChannels to re-create threading behavior in an actual deployment
    private static LocalAddress LOCAL_ADDRESS;
    private static DefaultEventLoopGroup SERVER_EVENT_LOOP;
    private static DefaultEventLoopGroup CLIENT_EVENT_LOOP;
    private static DefaultEventLoop EVENT_LOOP;

    @BeforeAll
    static void staticSetup() throws InterruptedException {
        LOCAL_ADDRESS = new LocalAddress(UUID.randomUUID().toString());

        CLIENT_EVENT_LOOP = new DefaultEventLoopGroup(4);
        SERVER_EVENT_LOOP = new DefaultEventLoopGroup(4);
        ServerBootstrap serverBootstrap = new ServerBootstrap()
                .group(SERVER_EVENT_LOOP)
                .localAddress(LOCAL_ADDRESS)
                .channel(LocalServerChannel.class)
                .childHandler(new ChannelInitializer<LocalChannel>() {
                    @Override
                    protected void initChannel(LocalChannel ch) {}
                });

        serverBootstrap.bind().sync();
        EVENT_LOOP = new DefaultEventLoop(Executors.newSingleThreadExecutor());
    }

    @AfterAll
    static void staticCleanup() {
        CLIENT_EVENT_LOOP.shutdownGracefully();
        SERVER_EVENT_LOOP.shutdownGracefully();
        EVENT_LOOP.shutdownGracefully();
    }

    private ChannelGroup channels;
    private ClientConnectionsShutdown shutdown;

    @BeforeEach
    void setup() {
        channels = new DefaultChannelGroup(EVENT_LOOP);
        shutdown = new ClientConnectionsShutdown(channels, EVENT_LOOP, null);
    }

    @Test
    @SuppressWarnings("unchecked")
    void discoveryShutdown() {
        String configName = "server.outofservice.connections.shutdown";
        AbstractConfiguration configuration = ConfigurationManager.getConfigInstance();

        try {
            configuration.setProperty(configName, "true");
            EurekaClient eureka = Mockito.mock(EurekaClient.class);
            EventExecutor executor = Mockito.mock(EventExecutor.class);

            ArgumentCaptor<EurekaEventListener> captor = ArgumentCaptor.forClass(EurekaEventListener.class);
            shutdown = spy(new ClientConnectionsShutdown(channels, executor, eureka));
            verify(eureka).registerEventListener(captor.capture());
            doReturn(Mockito.mock(Promise.class)).when(shutdown).gracefullyShutdownClientChannels();

            EurekaEventListener listener = captor.getValue();

            listener.onEvent(new StatusChangeEvent(InstanceStatus.UP, InstanceStatus.DOWN));
            verify(executor).schedule(ArgumentMatchers.isA(Callable.class), anyLong(), eq(TimeUnit.MILLISECONDS));

            Mockito.reset(executor);
            listener.onEvent(new StatusChangeEvent(InstanceStatus.UP, InstanceStatus.OUT_OF_SERVICE));
            verify(executor).schedule(ArgumentMatchers.isA(Callable.class), anyLong(), eq(TimeUnit.MILLISECONDS));

            Mockito.reset(executor);
            listener.onEvent(new StatusChangeEvent(InstanceStatus.STARTING, InstanceStatus.OUT_OF_SERVICE));
            verify(executor, never())
                    .schedule(ArgumentMatchers.isA(Callable.class), anyLong(), eq(TimeUnit.MILLISECONDS));
        } finally {
            configuration.setProperty(configName, "false");
        }
    }

    @Test
    void allConnectionsGracefullyClosed() throws Exception {
        createChannels(100);
        Promise<Void> promise = shutdown.gracefullyShutdownClientChannels();
        Promise<Object> testPromise = EVENT_LOOP.newPromise();

        promise.addListener(future -> {
            if (future.isSuccess()) {
                testPromise.setSuccess(null);
            } else {
                testPromise.setFailure(future.cause());
            }
        });

        channels.forEach(Channel::close);
        testPromise.await(10, TimeUnit.SECONDS);
        assertTrue(channels.isEmpty());
    }

    @Test
    void connectionNeedsToBeForceClosed() throws Exception {
        String configName = "server.outofservice.close.timeout";
        AbstractConfiguration configuration = ConfigurationManager.getConfigInstance();

        try {
            configuration.setProperty(configName, "0");
            createChannels(10);
            shutdown.gracefullyShutdownClientChannels().await(10, TimeUnit.SECONDS);

            assertTrue(
                    channels.isEmpty(),
                    "All channels in group should have been force closed after the timeout was triggered");
        } finally {
            configuration.setProperty(configName, "30");
        }
    }

    @Test
    void connectionNeedsToBeForceClosedAndOneChannelThrowsAnException() throws Exception {
        String configName = "server.outofservice.close.timeout";
        AbstractConfiguration configuration = ConfigurationManager.getConfigInstance();

        try {
            configuration.setProperty(configName, "0");
            createChannels(5);
            ChannelFuture connect = new Bootstrap()
                    .group(CLIENT_EVENT_LOOP)
                    .channel(LocalChannel.class)
                    .handler(new ChannelInitializer<>() {
                        @Override
                        protected void initChannel(Channel ch) {
                            ch.pipeline().addLast(new ChannelOutboundHandlerAdapter() {
                                @Override
                                public void close(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
                                    throw new Exception();
                                }
                            });
                        }
                    })
                    .remoteAddress(LOCAL_ADDRESS)
                    .connect()
                    .sync();
            channels.add(connect.channel());

            boolean await = shutdown.gracefullyShutdownClientChannels().await(10, TimeUnit.SECONDS);
            assertTrue(await, "the promise should finish even if a channel failed to close");
            assertEquals(1, channels.size(), "all other channels should have been closed");
        } finally {
            configuration.setProperty(configName, "30");
        }
    }

    @Test
    void connectionsNotForceClosed() throws Exception {
        String configName = "server.outofservice.close.timeout";
        AbstractConfiguration configuration = ConfigurationManager.getConfigInstance();

        DefaultEventLoop eventLoop = spy(EVENT_LOOP);
        shutdown = new ClientConnectionsShutdown(channels, eventLoop, null);

        try {
            configuration.setProperty(configName, "0");
            createChannels(10);
            Promise<Void> promise = shutdown.gracefullyShutdownClientChannels(ShutdownType.OUT_OF_SERVICE);
            verify(eventLoop, never()).schedule(isA(Runnable.class), anyLong(), isA(TimeUnit.class));
            channels.forEach(Channel::close);

            promise.await(10, TimeUnit.SECONDS);
            assertTrue(channels.isEmpty(), "All channels in group should have been closed");
        } finally {
            configuration.setProperty(configName, "30");
        }
    }

    @Test
    public void shutdownTypeForwardedToFlag() throws InterruptedException {
        shutdown = spy(shutdown);
        doNothing().when(shutdown).flagChannelForClose(any(), any());
        createChannels(1);
        Channel channel = channels.iterator().next();
        for (ShutdownType type : ShutdownType.values()) {
            shutdown.gracefullyShutdownClientChannels(type);
            verify(shutdown).flagChannelForClose(channel, type);
        }

        channels.close().await(5, TimeUnit.SECONDS);
    }

    private void createChannels(int numChannels) throws InterruptedException {
        ChannelInitializer<LocalChannel> initializer = new ChannelInitializer<>() {
            @Override
            protected void initChannel(LocalChannel ch) {}
        };

        for (int i = 0; i < numChannels; ++i) {
            ChannelFuture connect = new Bootstrap()
                    .group(CLIENT_EVENT_LOOP)
                    .channel(LocalChannel.class)
                    .handler(initializer)
                    .remoteAddress(LOCAL_ADDRESS)
                    .connect()
                    .sync();

            channels.add(connect.channel());
        }
    }
}
