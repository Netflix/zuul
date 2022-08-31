package com.netflix.zuul.netty.server.push;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
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
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

/**
 * @author Justin Guerra
 * @since 8/31/22
 */
public class PushRegistrationHandlerTest {

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

    @BeforeClass
    public static void classSetup() {
        EXECUTOR = Executors.newSingleThreadExecutor();
    }

    @AfterClass
    public static void classCleanup() {
        MoreExecutors.shutdownAndAwaitTermination(EXECUTOR, 5, TimeUnit.SECONDS);
    }

    @Before
    public void setup() {
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
    public void closeIfNotAuthenticated() throws Exception {
        doHandshakeComplete();

        Runnable scheduledTask = scheduledCaptor.getValue();
        scheduledTask.run();

        validateConnectionClosed(1000, "Server closed connection");
    }

    @Test
    public void authFailed() throws Exception {
        doHandshakeComplete();
        handler.userEventTriggered(context, new TestAuth(false));
        validateConnectionClosed(1008, "Auth failed");
    }

    @Test
    public void authSuccess() throws Exception {
        doHandshakeComplete();
        authenticateChannel();
    }

    @Test
    public void requestClientToCloseInactiveConnection() throws Exception {
        doHandshakeComplete();
        Mockito.reset(eventLoopSpy);
        authenticateChannel();
        verify(eventLoopSpy).schedule(scheduledCaptor.capture(), anyLong(), eq(TimeUnit.SECONDS));
        Runnable requestClientToClose = scheduledCaptor.getValue();

        requestClientToClose.run();
        validateConnectionClosed(1000, "Server closed connection");
    }

    @Test
    public void requestClientToClose() throws Exception {
        doHandshakeComplete();
        Mockito.reset(eventLoopSpy);
        authenticateChannel();
        verify(eventLoopSpy).schedule(scheduledCaptor.capture(), anyLong(), eq(TimeUnit.SECONDS));
        Runnable requestClientToClose = scheduledCaptor.getValue();

        int taskListSize = handler.getScheduledFutures().size();
        doReturn(true).when(channel).isActive();
        requestClientToClose.run();
        assertEquals(taskListSize + 1, handler.getScheduledFutures().size());
        Object capture = writeCaptor.getValue();
        assertTrue(capture instanceof TextWebSocketFrame);
        TextWebSocketFrame frame = (TextWebSocketFrame) capture;
        assertEquals("_CLOSE_", frame.text());
    }

    @Test
    public void channelInactiveCancelsTasks() throws Exception {
        doHandshakeComplete();
        TestAuth testAuth = new TestAuth(true);
        authenticateChannel();

        List<ScheduledFuture<?>> copyOfFutures = new ArrayList<>(handler.getScheduledFutures());

        handler.channelInactive(context);
        assertNull(registry.get(testAuth.getClientIdentity()));
        assertTrue(handler.getScheduledFutures().isEmpty());
        copyOfFutures.forEach(f -> assertTrue(f.isCancelled()));
        verify(context).close();
    }

    private void doHandshakeComplete() throws Exception {
        handler.userEventTriggered(context, PushProtocol.WEBSOCKET.getHandshakeCompleteEvent());
        assertNotNull(handler.getPushConnection());
        verify(eventLoopSpy).schedule(scheduledCaptor.capture(), anyLong(), eq(TimeUnit.SECONDS));
    }

    private void authenticateChannel() throws Exception {
        handler.userEventTriggered(context, successfulAuth);
        assertNotNull(registry.get(successfulAuth.getClientIdentity()));
        assertEquals(2, handler.getScheduledFutures().size());
        verify(pipelineMock).remove(PushAuthHandler.NAME);
    }

    private void validateConnectionClosed(int expected, String messaged) {
        Object capture = writeCaptor.getValue();
        assertTrue(capture instanceof CloseWebSocketFrame);
        CloseWebSocketFrame closeFrame = (CloseWebSocketFrame) capture;
        assertEquals(expected, closeFrame.statusCode());
        assertEquals(messaged, closeFrame.reasonText());
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