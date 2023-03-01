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

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import com.google.common.reflect.Reflection;
import com.netflix.appinfo.InstanceInfo.InstanceStatus;
import com.netflix.config.ConfigurationManager;
import com.netflix.discovery.EurekaClient;
import com.netflix.discovery.EurekaEventListener;
import com.netflix.discovery.StatusChangeEvent;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.DefaultEventLoopGroup;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.ChannelGroupFuture;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.channel.local.LocalAddress;
import io.netty.channel.local.LocalChannel;
import io.netty.channel.local.LocalServerChannel;
import io.netty.util.concurrent.EventExecutor;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
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

    //using LocalChannels instead of EmbeddedChannels to re-create threading behavior in an actual deployment
    private static LocalAddress LOCAL_ADDRESS;
    private static DefaultEventLoopGroup SERVER_EVENT_LOOP;
    private static DefaultEventLoopGroup CLIENT_EVENT_LOOP;
    private static ExecutorService EXECUTOR;

    @BeforeAll
    static void staticSetup() throws InterruptedException {
        LOCAL_ADDRESS = new LocalAddress(UUID.randomUUID().toString());

        CLIENT_EVENT_LOOP = new DefaultEventLoopGroup(4);
        SERVER_EVENT_LOOP = new DefaultEventLoopGroup(4);
        ServerBootstrap serverBootstrap = new ServerBootstrap().group(SERVER_EVENT_LOOP)
                                                               .localAddress(LOCAL_ADDRESS)
                                                               .channel(LocalServerChannel.class)
                                                               .childHandler(new ChannelInitializer<LocalChannel>() {
                                                                   @Override
                                                                   protected void initChannel(LocalChannel ch) {

                                                                   }
                                                               });

        serverBootstrap.bind().sync();
        EXECUTOR = Executors.newSingleThreadExecutor();
    }

    @AfterAll
    static void staticCleanup() {
        CLIENT_EVENT_LOOP.shutdownGracefully();
        SERVER_EVENT_LOOP.shutdownGracefully();
    }

    private ChannelGroup channels;
    private ClientConnectionsShutdown shutdown;
    private CountDownLatch latch;
    private AtomicReference<Boolean> awaitEscapeHatch;

    @BeforeEach
    void setup() {
        latch = new CountDownLatch(1);
        awaitEscapeHatch = new AtomicReference<>();

        channels = spy(new DefaultChannelGroup(SERVER_EVENT_LOOP.next()));
        doAnswer(invocation -> {
            ChannelGroupFuture future = (ChannelGroupFuture) invocation.callRealMethod();
            return testGroupFuture(future);
        }).when(channels).newCloseFuture();

        shutdown = new ClientConnectionsShutdown(channels, null, null);
    }

    @Test
    void discoveryShutdown() {
        String configName = "server.outofservice.connections.shutdown";
        AbstractConfiguration configuration = ConfigurationManager.getConfigInstance();

        try {
            configuration.setProperty(configName, "true");
            EurekaClient eureka = Mockito.mock(EurekaClient.class);
            EventExecutor executor = Mockito.mock(EventExecutor.class);

            ArgumentCaptor<EurekaEventListener> captor = ArgumentCaptor.forClass(
                    EurekaEventListener.class);
            shutdown = spy(new ClientConnectionsShutdown(channels, executor, eureka));
            verify(eureka).registerEventListener(captor.capture());
            doNothing().when(shutdown).gracefullyShutdownClientChannels();

            EurekaEventListener listener = captor.getValue();

            listener.onEvent(new StatusChangeEvent(InstanceStatus.UP, InstanceStatus.DOWN));
            verify(executor).schedule(ArgumentMatchers.isA(Runnable.class), anyLong(), eq(TimeUnit.MILLISECONDS));

            Mockito.reset(executor);
            listener.onEvent(new StatusChangeEvent(InstanceStatus.UP, InstanceStatus.OUT_OF_SERVICE));
            verify(executor).schedule(ArgumentMatchers.isA(Runnable.class), anyLong(), eq(TimeUnit.MILLISECONDS));

            Mockito.reset(executor);
            listener.onEvent(new StatusChangeEvent(InstanceStatus.STARTING, InstanceStatus.OUT_OF_SERVICE));
            verify(executor, never()).schedule(ArgumentMatchers.isA(Runnable.class), anyLong(), eq(TimeUnit.MILLISECONDS));
        } finally {
            configuration.setProperty(configName, "false");
        }
    }

    @Test
    void allConnectionsGracefullyClosed() throws Exception {
        createChannels(100);
        Future<?> gracefulShutdown = EXECUTOR.submit(() -> shutdown.gracefullyShutdownClientChannels());
        awaitNewCloseAwait();
        channels.forEach(Channel::close);
        gracefulShutdown.get(5, TimeUnit.SECONDS);
        assertTrue(channels.isEmpty());
    }

    @Test
    void connectionNeedsToBeForceClosed() throws Exception {
        createChannels(10);

        awaitEscapeHatch.set(false);
        Future<?> gracefulShutdown = EXECUTOR.submit(() -> shutdown.gracefullyShutdownClientChannels());
        awaitNewCloseAwait();

        gracefulShutdown.get(10, TimeUnit.SECONDS);
        assertTrue(channels.isEmpty(), "All channels in group should have been force closed");
    }

    private void createChannels(int numChannels) throws InterruptedException {
        ChannelInitializer<LocalChannel> initializer = new ChannelInitializer<LocalChannel>() {
            @Override
            protected void initChannel(LocalChannel ch) {

            }
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

    private void awaitNewCloseAwait() throws InterruptedException {
        if(!latch.await(5, TimeUnit.SECONDS)) {
            fail();
        }
    }

    /**
     * Creates a proxy that delegates calls to a proper {@link ChannelGroupFuture}. The method {@link ChannelGroupFuture#await(long, TimeUnit)}
     * has a synchronization aid and can have its behavior modified
     */
    private ChannelGroupFuture testGroupFuture(ChannelGroupFuture delegate) {
        return Reflection.newProxy(ChannelGroupFuture.class, (proxy, method, args) -> {
            if(method.getName().equals("await") && args.length == 2) {
                latch.countDown();
                if(awaitEscapeHatch.get() != null) {
                    return awaitEscapeHatch.get();
                }
            }
            return method.invoke(delegate, args);
        });
    }

}