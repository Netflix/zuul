/*
 * Copyright 2022 Netflix, Inc.
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
package com.netflix.zuul.netty.server.push;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import com.google.common.util.concurrent.MoreExecutors;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.DefaultEventLoop;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.util.concurrent.ScheduledFuture;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

/**
 * @author Justin Guerra
 * @since 8/31/22
 */
class PushRegistrationHandlerTest {

    private static ExecutorService EXECUTOR;

    @Captor
    private ArgumentCaptor<Runnable> scheduledCaptor;

    @Captor
    private ArgumentCaptor<Object> writeCaptor;

    @Mock
    private ChannelHandlerContext context;

    @Mock
    private ChannelFuture channelFuture;

    @Mock
    private ChannelPipeline pipelineMock;

    @Mock
    private Channel channel;

    private PushConnectionRegistry registry;
    private PushRegistrationHandler handler;
    private DefaultEventLoop eventLoopSpy;
    private TestAuth successfulAuth;

    @BeforeAll
    static void classSetup() {
        EXECUTOR = Executors.newSingleThreadExecutor();
    }

    @AfterAll
    static void classCleanup() {
        MoreExecutors.shutdownAndAwaitTermination(EXECUTOR, 5, TimeUnit.SECONDS);
    }

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        registry = new PushConnectionRegistry();
        handler = new PushRegistrationHandler(registry, PushProtocol.WEBSOCKET);
        successfulAuth = new TestAuth(true);

        eventLoopSpy = spy(new DefaultEventLoop(EXECUTOR));
        doReturn(eventLoopSpy).when(context).executor();
        doReturn(channelFuture).when(context).writeAndFlush(writeCaptor.capture());
        doReturn(pipelineMock).when(context).pipeline();
        doReturn(channel).when(context).channel();
    }

    @Test
    void closeIfNotAuthenticated() throws Exception {
        doHandshakeComplete();

        Runnable scheduledTask = scheduledCaptor.getValue();
        scheduledTask.run();

        validateConnectionClosed(1000, "Server closed connection");
    }

    @Test
    void authFailed() throws Exception {
        doHandshakeComplete();
        handler.userEventTriggered(context, new TestAuth(false));
        validateConnectionClosed(1008, "Auth failed");
    }

    @Test
    void authSuccess() throws Exception {
        doHandshakeComplete();
        authenticateChannel();
    }

    @Test
    void requestClientToCloseInactiveConnection() throws Exception {
        doHandshakeComplete();
        Mockito.reset(eventLoopSpy);
        authenticateChannel();
        verify(eventLoopSpy).schedule(scheduledCaptor.capture(), anyLong(), eq(TimeUnit.SECONDS));
        Runnable requestClientToClose = scheduledCaptor.getValue();

        requestClientToClose.run();
        validateConnectionClosed(1000, "Server closed connection");
    }

    @Test
    void requestClientToClose() throws Exception {
        doHandshakeComplete();
        Mockito.reset(eventLoopSpy);
        authenticateChannel();
        verify(eventLoopSpy).schedule(scheduledCaptor.capture(), anyLong(), eq(TimeUnit.SECONDS));
        Runnable requestClientToClose = scheduledCaptor.getValue();

        int taskListSize = handler.getScheduledFutures().size();
        doReturn(true).when(channel).isActive();
        requestClientToClose.run();
        assertThat(handler.getScheduledFutures().size()).isEqualTo(taskListSize + 1);
        Object capture = writeCaptor.getValue();
        assertThat(capture instanceof TextWebSocketFrame).isTrue();
        TextWebSocketFrame frame = (TextWebSocketFrame) capture;
        assertThat(frame.text()).isEqualTo("_CLOSE_");
    }

    @Test
    void channelInactiveCancelsTasks() throws Exception {
        doHandshakeComplete();
        TestAuth testAuth = new TestAuth(true);
        authenticateChannel();

        List<ScheduledFuture<?>> copyOfFutures = new ArrayList<>(handler.getScheduledFutures());

        handler.channelInactive(context);
        assertThat(registry.get(testAuth.getClientIdentity())).isNull();
        assertThat(handler.getScheduledFutures().isEmpty()).isTrue();
        copyOfFutures.forEach(f -> assertThat(f.isCancelled()).isTrue());
        verify(context).close();
    }

    private void doHandshakeComplete() throws Exception {
        handler.userEventTriggered(context, PushProtocol.WEBSOCKET.getHandshakeCompleteEvent());
        assertThat(handler.getPushConnection()).isNotNull();
        verify(eventLoopSpy).schedule(scheduledCaptor.capture(), anyLong(), eq(TimeUnit.SECONDS));
    }

    private void authenticateChannel() throws Exception {
        handler.userEventTriggered(context, successfulAuth);
        assertThat(registry.get(successfulAuth.getClientIdentity())).isNotNull();
        assertThat(handler.getScheduledFutures().size()).isEqualTo(2);
        verify(pipelineMock).remove(PushAuthHandler.NAME);
    }

    private void validateConnectionClosed(int expected, String messaged) {
        Object capture = writeCaptor.getValue();
        assertThat(capture instanceof CloseWebSocketFrame).isTrue();
        CloseWebSocketFrame closeFrame = (CloseWebSocketFrame) capture;
        assertThat(closeFrame.statusCode()).isEqualTo(expected);
        assertThat(closeFrame.reasonText()).isEqualTo(messaged);
        verify(channelFuture).addListener(ChannelFutureListener.CLOSE);
    }

    private static class TestAuth implements PushUserAuth {

        private final boolean success;

        public TestAuth(boolean success) {
            this.success = success;
        }

        @Override
        public boolean isSuccess() {
            return success;
        }

        @Override
        public int statusCode() {
            return 0;
        }

        @Override
        public String getClientIdentity() {
            return "whatever";
        }
    }
}
