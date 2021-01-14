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

package com.netflix.zuul.origins;

import static com.netflix.zuul.stats.status.ZuulStatusCategory.FAILURE_ORIGIN;
import static com.netflix.zuul.stats.status.ZuulStatusCategory.FAILURE_ORIGIN_THROTTLED;
import static com.netflix.zuul.stats.status.ZuulStatusCategory.SUCCESS;

import com.google.common.base.Strings;
import com.netflix.client.config.CommonClientConfigKey;
import com.netflix.client.config.DefaultClientConfigImpl;
import com.netflix.client.config.IClientConfig;
import com.netflix.config.CachedDynamicBooleanProperty;
import com.netflix.config.CachedDynamicIntProperty;
import com.netflix.loadbalancer.Server;
import com.netflix.loadbalancer.reactive.ExecutionContext;
import com.netflix.niws.loadbalancer.DiscoveryEnabledServer;
import com.netflix.spectator.api.Counter;
import com.netflix.spectator.api.Registry;
import com.netflix.zuul.context.CommonContextKeys;
import com.netflix.zuul.context.SessionContext;
import com.netflix.zuul.exception.ErrorType;
import com.netflix.zuul.message.http.HttpRequestMessage;
import com.netflix.zuul.message.http.HttpResponseMessage;
import com.netflix.zuul.netty.NettyRequestAttemptFactory;
import com.netflix.zuul.netty.SpectatorUtils;
import com.netflix.zuul.netty.connectionpool.ClientChannelManager;
import com.netflix.zuul.netty.connectionpool.DefaultClientChannelManager;
import com.netflix.zuul.netty.connectionpool.PooledConnection;
import com.netflix.zuul.niws.RequestAttempt;
import com.netflix.zuul.passport.CurrentPassport;
import com.netflix.zuul.stats.status.StatusCategory;
import com.netflix.zuul.stats.status.StatusCategoryUtils;
import io.netty.channel.EventLoop;
import io.netty.util.concurrent.Promise;
import java.net.InetAddress;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.Nullable;

/**
 * Netty Origin basic implementation that can be used for most apps, with the more complex methods having no-op
 * implementations.
 *
 * Author: Arthur Gonigberg
 * Date: December 01, 2017
 */
public class BasicNettyOrigin implements NettyOrigin {

    private final OriginName originName;
    private final Registry registry;
    private final IClientConfig config;
    private final ClientChannelManager clientChannelManager;
    private final NettyRequestAttemptFactory requestAttemptFactory;
    private final OriginStats stats = new OriginStats();

    private final AtomicInteger concurrentRequests;
    private final Counter rejectedRequests;
    private final CachedDynamicIntProperty concurrencyMax;
    private final CachedDynamicBooleanProperty concurrencyProtectionEnabled;

    public BasicNettyOrigin(OriginName originName, Registry registry) {
        this.originName = Objects.requireNonNull(originName, "originName");
        this.registry = registry;
        this.config = setupClientConfig(originName);
        this.clientChannelManager = new DefaultClientChannelManager(originName, config, registry);
        this.clientChannelManager.init();
        this.requestAttemptFactory = new NettyRequestAttemptFactory();

        String niwsClientName = getName().getNiwsClientName();
        this.concurrentRequests =
                SpectatorUtils.newGauge(
                        "zuul.origin.concurrent.requests", niwsClientName, new AtomicInteger(0));
        this.rejectedRequests =
                SpectatorUtils.newCounter("zuul.origin.rejected.requests", niwsClientName);
        this.concurrencyMax =
                new CachedDynamicIntProperty("zuul.origin." + niwsClientName + ".concurrency.max.requests", 200);
        this.concurrencyProtectionEnabled =
                new CachedDynamicBooleanProperty("zuul.origin." + niwsClientName + ".concurrency.protect.enabled", true);
    }

    protected IClientConfig setupClientConfig(OriginName originName) {
        // Get the NIWS properties for this Origin.
        IClientConfig niwsClientConfig =
                DefaultClientConfigImpl.getClientConfigWithDefaultValues(originName.getNiwsClientName());
        niwsClientConfig.set(CommonClientConfigKey.ClientClassName, originName.getNiwsClientName());
        niwsClientConfig.loadProperties(originName.getNiwsClientName());
        return niwsClientConfig;
    }

    @Override
    public OriginName getName() {
        return originName;
    }

    @Override
    public boolean isAvailable() {
        return clientChannelManager.isAvailable();
    }

    @Override
    public boolean isCold() {
        return clientChannelManager.isCold();
    }

    @Override
    public Promise<PooledConnection> connectToOrigin(
            HttpRequestMessage zuulReq, EventLoop eventLoop, int attemptNumber, CurrentPassport passport,
            AtomicReference<Server> chosenServer, AtomicReference<? super InetAddress> chosenHostAddr) {
        return clientChannelManager.acquire(eventLoop, null, passport, chosenServer, chosenHostAddr);
    }

    public int getMaxRetriesForRequest(SessionContext context) {
        return config.get(CommonClientConfigKey.MaxAutoRetriesNextServer, 0);
    }

    @Override
    public RequestAttempt newRequestAttempt(Server server, SessionContext zuulCtx, int attemptNum) {
        return new RequestAttempt(server, config, attemptNum, config.get(CommonClientConfigKey.ReadTimeout));
    }

    @Override
    public String getIpAddrFromServer(Server server) {
        if (server instanceof DiscoveryEnabledServer) {
            DiscoveryEnabledServer discoveryServer = (DiscoveryEnabledServer) server;
            if (discoveryServer.getInstanceInfo() != null) {
                String ip = discoveryServer.getInstanceInfo().getIPAddr();
                if (!Strings.isNullOrEmpty(ip)) {
                    return ip;
                }
            }
        }
        return null;
    }

    @Override
    public IClientConfig getClientConfig() {
        return config;
    }

    @Override
    public Registry getSpectatorRegistry() {
        return registry;
    }

    @Override
    public ExecutionContext<?> getExecutionContext(HttpRequestMessage zuulRequest) {
        ExecutionContext<?> execCtx = (ExecutionContext<?>) zuulRequest.getContext().get(CommonContextKeys.REST_EXECUTION_CONTEXT);
        if (execCtx == null) {
            IClientConfig overriddenClientConfig = (IClientConfig) zuulRequest.getContext().get(CommonContextKeys.REST_CLIENT_CONFIG);
            if (overriddenClientConfig == null) {
                overriddenClientConfig = new DefaultClientConfigImpl();
                zuulRequest.getContext().put(CommonContextKeys.REST_CLIENT_CONFIG, overriddenClientConfig);
            }

            final ExecutionContext<?> context = new ExecutionContext<>(zuulRequest, overriddenClientConfig, this.config, null);
            context.put("vip", getName().getTarget());
            context.put("clientName", getName().getNiwsClientName());

            zuulRequest.getContext().set(CommonContextKeys.REST_EXECUTION_CONTEXT, context);
            execCtx = context;
        }
        return execCtx;
    }

    @Override
    public void recordFinalError(HttpRequestMessage requestMsg, Throwable throwable) {
        if (throwable == null) {
            return;
        }

        final SessionContext zuulCtx = requestMsg.getContext();

        // Choose StatusCategory based on the ErrorType.
        final ErrorType et = requestAttemptFactory.mapNettyToOutboundErrorType(throwable);
        final StatusCategory nfs = et.getStatusCategory();
        zuulCtx.set(CommonContextKeys.STATUS_CATGEORY, nfs);
        zuulCtx.set(CommonContextKeys.ORIGIN_STATUS_CATEGORY, nfs);

        zuulCtx.setError(throwable);
    }

    @Override
    public void recordFinalResponse(HttpResponseMessage resp) {
        if (resp != null) {
            final SessionContext zuulCtx = resp.getContext();

            // Store the status code of final attempt response.
            int originStatusCode = resp.getStatus();
            zuulCtx.set(CommonContextKeys.ORIGIN_STATUS, originStatusCode);

            // Mark origin StatusCategory based on http status code.
            StatusCategory originNfs = SUCCESS;
            if (originStatusCode == 503) {
                originNfs = FAILURE_ORIGIN_THROTTLED;
            }
            else if (StatusCategoryUtils.isResponseHttpErrorStatus(originStatusCode)) {
                originNfs = FAILURE_ORIGIN;
            }
            zuulCtx.set(CommonContextKeys.ORIGIN_STATUS_CATEGORY, originNfs);
            // Choose the zuul StatusCategory based on the origin one...
            // ... but only if existing one has not already been set to a non-success value.
            StatusCategoryUtils.storeStatusCategoryIfNotAlreadyFailure(zuulCtx, originNfs);
        }
    }

    @Override
    public void preRequestChecks(HttpRequestMessage zuulRequest) {
        if (concurrencyProtectionEnabled.get() && concurrentRequests.get() > concurrencyMax.get()) {
            rejectedRequests.increment();
            throw new OriginConcurrencyExceededException(getName());
        }

        concurrentRequests.incrementAndGet();
    }

    @Override
    public void recordProxyRequestEnd() {
        concurrentRequests.decrementAndGet();
    }

    @Override
    public final OriginStats stats() {
        return stats;
    }

    /* Not required for basic operation */

    @Override
    public double getErrorPercentage() {
        return 0;
    }

    @Override
    public double getErrorAllPercentage() {
        return 0;
    }

    @Override
    public void onRequestExecutionStart(HttpRequestMessage zuulReq) {
    }

    @Override
    public void onRequestStartWithServer(HttpRequestMessage zuulReq, Server originServer, int attemptNum) {
    }

    @Override
    public void onRequestExceptionWithServer(HttpRequestMessage zuulReq, Server originServer, int attemptNum, Throwable t) {
    }

    @Override
    public void onRequestExecutionSuccess(HttpRequestMessage zuulReq, HttpResponseMessage zuulResp, Server originServer, int attemptNum) {
    }

    @Override
    public void onRequestExecutionFailed(HttpRequestMessage zuulReq, Server originServer, int attemptNum, Throwable t) {
    }

    @Override
    public void adjustRetryPolicyIfNeeded(HttpRequestMessage zuulRequest) {
    }

    @Override
    public void recordSuccessResponse() {
    }
}
