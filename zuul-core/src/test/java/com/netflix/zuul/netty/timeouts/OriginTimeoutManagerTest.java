package com.netflix.zuul.netty.timeouts;

import static com.netflix.zuul.netty.timeouts.OriginTimeoutManager.MAX_OUTBOUND_READ_TIMEOUT_MS;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;

import com.netflix.client.config.CommonClientConfigKey;
import com.netflix.client.config.DefaultClientConfigImpl;
import com.netflix.client.config.IClientConfig;
import com.netflix.zuul.context.CommonContextKeys;
import com.netflix.zuul.context.SessionContext;
import com.netflix.zuul.message.http.HttpRequestMessage;
import com.netflix.zuul.origins.NettyOrigin;
import java.time.Duration;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

/**
 * Origin Timeout Manager Test
 *
 * @author Arthur Gonigberg
 * @since March 23, 2021
 */
@RunWith(MockitoJUnitRunner.class)
public class OriginTimeoutManagerTest {

    @Mock
    private NettyOrigin origin;
    @Mock
    private HttpRequestMessage request;

    private SessionContext context;
    private IClientConfig requestConfig;
    private IClientConfig originConfig;

    private OriginTimeoutManager originTimeoutManager;

    @Before
    public void before() {
        originTimeoutManager = new OriginTimeoutManager(origin);

        context = new SessionContext();
        when(request.getContext()).thenReturn(context);

        requestConfig = new DefaultClientConfigImpl();
        originConfig = new DefaultClientConfigImpl();

        context.put(CommonContextKeys.REST_CLIENT_CONFIG, requestConfig);
        when(origin.getClientConfig()).thenReturn(originConfig);
    }

    @Test
    public void computeReadTimeout_default() {
        Duration timeout = originTimeoutManager.computeReadTimeout(request, 1);

        assertEquals(MAX_OUTBOUND_READ_TIMEOUT_MS.get(), timeout.toMillis());
    }

    @Test
    public void computeReadTimeout_requestOnly() {
        requestConfig.set(CommonClientConfigKey.ReadTimeout, 1000);

        Duration timeout = originTimeoutManager.computeReadTimeout(request, 1);

        assertEquals(1000, timeout.toMillis());
    }

    @Test
    public void computeReadTimeout_originOnly() {
        originConfig.set(CommonClientConfigKey.ReadTimeout, 1000);

        Duration timeout = originTimeoutManager.computeReadTimeout(request, 1);

        assertEquals(1000, timeout.toMillis());
    }

    @Test
    public void computeReadTimeout_bolth_equal() {
        requestConfig.set(CommonClientConfigKey.ReadTimeout, 1000);
        originConfig.set(CommonClientConfigKey.ReadTimeout, 1000);

        Duration timeout = originTimeoutManager.computeReadTimeout(request, 1);

        assertEquals(1000, timeout.toMillis());
    }

    @Test
    public void computeReadTimeout_bolth_originLower() {
        requestConfig.set(CommonClientConfigKey.ReadTimeout, 1000);
        originConfig.set(CommonClientConfigKey.ReadTimeout, 100);

        Duration timeout = originTimeoutManager.computeReadTimeout(request, 1);

        assertEquals(100, timeout.toMillis());
    }

    @Test
    public void computeReadTimeout_bolth_requestLower() {
        requestConfig.set(CommonClientConfigKey.ReadTimeout, 1000);
        originConfig.set(CommonClientConfigKey.ReadTimeout, 1000);

        Duration timeout = originTimeoutManager.computeReadTimeout(request, 1);

        assertEquals(1000, timeout.toMillis());
    }

    @Test
    public void computeReadTimeout_bolth_enforceMax() {
        requestConfig.set(CommonClientConfigKey.ReadTimeout,
                (int) MAX_OUTBOUND_READ_TIMEOUT_MS.get() + 1000);
        originConfig.set(CommonClientConfigKey.ReadTimeout,
                (int) MAX_OUTBOUND_READ_TIMEOUT_MS.get() + 10000);

        Duration timeout = originTimeoutManager.computeReadTimeout(request, 1);

        assertEquals(MAX_OUTBOUND_READ_TIMEOUT_MS.get(), timeout.toMillis());
    }
}