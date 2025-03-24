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

package com.netflix.zuul.filters.endpoint;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import com.google.errorprone.annotations.ForOverride;
import com.netflix.client.ClientException;
import com.netflix.client.config.IClientConfigKey;
import com.netflix.config.DynamicIntegerSetProperty;
import com.netflix.netty.common.ByteBufUtil;
import com.netflix.spectator.api.Counter;
import com.netflix.zuul.Filter;
import com.netflix.zuul.context.CommonContextKeys;
import com.netflix.zuul.context.Debug;
import com.netflix.zuul.context.SessionContext;
import com.netflix.zuul.discovery.DiscoveryResult;
import com.netflix.zuul.exception.ErrorType;
import com.netflix.zuul.exception.OutboundErrorType;
import com.netflix.zuul.exception.OutboundException;
import com.netflix.zuul.exception.RequestExpiredException;
import com.netflix.zuul.exception.ZuulException;
import com.netflix.zuul.filters.FilterType;
import com.netflix.zuul.filters.SyncZuulFilterAdapter;
import com.netflix.zuul.message.HeaderName;
import com.netflix.zuul.message.Headers;
import com.netflix.zuul.message.ZuulMessage;
import com.netflix.zuul.message.http.HttpHeaderNames;
import com.netflix.zuul.message.http.HttpQueryParams;
import com.netflix.zuul.message.http.HttpRequestInfo;
import com.netflix.zuul.message.http.HttpRequestMessage;
import com.netflix.zuul.message.http.HttpResponseMessage;
import com.netflix.zuul.message.http.HttpResponseMessageImpl;
import com.netflix.zuul.netty.ChannelUtils;
import com.netflix.zuul.netty.NettyRequestAttemptFactory;
import com.netflix.zuul.netty.SpectatorUtils;
import com.netflix.zuul.netty.connectionpool.BasicRequestStat;
import com.netflix.zuul.netty.connectionpool.ClientTimeoutHandler;
import com.netflix.zuul.netty.connectionpool.DefaultOriginChannelInitializer;
import com.netflix.zuul.netty.connectionpool.PooledConnection;
import com.netflix.zuul.netty.connectionpool.RequestStat;
import com.netflix.zuul.netty.filter.FilterRunner;
import com.netflix.zuul.netty.server.ClientRequestReceiver;
import com.netflix.zuul.netty.server.MethodBinding;
import com.netflix.zuul.netty.server.OriginResponseReceiver;
import com.netflix.zuul.netty.timeouts.OriginTimeoutManager;
import com.netflix.zuul.niws.RequestAttempt;
import com.netflix.zuul.niws.RequestAttempts;
import com.netflix.zuul.origins.NettyOrigin;
import com.netflix.zuul.origins.Origin;
import com.netflix.zuul.origins.OriginManager;
import com.netflix.zuul.origins.OriginName;
import com.netflix.zuul.passport.CurrentPassport;
import com.netflix.zuul.passport.PassportState;
import com.netflix.zuul.stats.status.StatusCategory;
import com.netflix.zuul.stats.status.StatusCategoryUtils;
import com.netflix.zuul.stats.status.ZuulStatusCategory;
import com.netflix.zuul.util.HttpUtils;
import com.netflix.zuul.util.ProxyUtils;
import com.netflix.zuul.util.VipUtils;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultLastHttpContent;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import io.netty.util.concurrent.Promise;
import io.perfmark.PerfMark;
import io.perfmark.TaskCloseable;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.URLDecoder;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Not thread safe! New instance of this class is created per HTTP/1.1 request proxied to the origin but NOT for each
 * attempt/retry. All the retry attempts for a given HTTP/1.1 request proxied share the same EdgeProxyEndpoint instance
 * Created by saroskar on 5/31/17.
 */
@Filter(order = 0, type = FilterType.ENDPOINT)
public class ProxyEndpoint extends SyncZuulFilterAdapter<HttpRequestMessage, HttpResponseMessage>
        implements GenericFutureListener<Future<PooledConnection>> {

    private static final String ZUUL_ORIGIN_ATTEMPT_IPADDR_MAP_KEY = "_zuul_origin_attempt_ipaddr_map";
    private static final String ZUUL_ORIGIN_REQUEST_URI = "_zuul_origin_request_uri";

    private final ChannelHandlerContext channelCtx;
    private final FilterRunner<HttpResponseMessage, ?> responseFilters;
    protected final AtomicReference<DiscoveryResult> chosenServer;
    protected final AtomicReference<InetAddress> chosenHostAddr;

    /* Individual request related state */
    protected final HttpRequestMessage zuulRequest;
    protected final SessionContext context;

    @Nullable protected final NettyOrigin origin;

    protected final RequestAttempts requestAttempts;
    protected final CurrentPassport passport;
    protected final NettyRequestAttemptFactory requestAttemptFactory;
    protected final OriginTimeoutManager originTimeoutManager;

    protected MethodBinding<?> methodBinding;
    protected HttpResponseMessage zuulResponse;
    protected boolean startedSendingResponseToClient;
    protected Duration timeLeftForAttempt;

    /* Individual retry related state */
    private volatile PooledConnection originConn;
    private volatile OriginResponseReceiver originResponseReceiver;
    private AtomicInteger concurrentReqCount;
    private volatile boolean proxiedRequestWithoutBuffering;
    protected int attemptNum;
    protected RequestAttempt currentRequestAttempt;
    protected List<RequestStat> requestStats = new ArrayList<>();
    protected RequestStat currentRequestStat;

    public static final Set<String> IDEMPOTENT_HTTP_METHODS = Sets.newHashSet("GET", "HEAD", "OPTIONS");
    private static final DynamicIntegerSetProperty RETRIABLE_STATUSES_FOR_IDEMPOTENT_METHODS =
            new DynamicIntegerSetProperty("zuul.retry.allowed.statuses.idempotent", "500");

    /**
     * Indicates how long Zuul should remember throttle events for an origin.  As of this writing, throttling is used
     * to decide to cache request bodies.
     */
    private static final Set<HeaderName> REQUEST_HEADERS_TO_REMOVE =
            Sets.newHashSet(HttpHeaderNames.CONNECTION, HttpHeaderNames.KEEP_ALIVE);

    private static final Set<HeaderName> RESPONSE_HEADERS_TO_REMOVE =
            Sets.newHashSet(HttpHeaderNames.CONNECTION, HttpHeaderNames.KEEP_ALIVE);
    public static final String POOLED_ORIGIN_CONNECTION_KEY = "_origin_pooled_conn";
    private static final Logger logger = LoggerFactory.getLogger(ProxyEndpoint.class);
    private static final Counter NO_RETRY_INCOMPLETE_BODY =
            SpectatorUtils.newCounter("zuul.no.retry", "incomplete_body");
    private static final Counter NO_RETRY_RESP_STARTED = SpectatorUtils.newCounter("zuul.no.retry", "resp_started");

    public ProxyEndpoint(
            HttpRequestMessage inMesg,
            ChannelHandlerContext ctx,
            FilterRunner<HttpResponseMessage, ?> filters,
            MethodBinding<?> methodBinding) {
        this(inMesg, ctx, filters, methodBinding, new NettyRequestAttemptFactory());
    }

    public ProxyEndpoint(
            HttpRequestMessage inMesg,
            ChannelHandlerContext ctx,
            FilterRunner<HttpResponseMessage, ?> filters,
            MethodBinding<?> methodBinding,
            NettyRequestAttemptFactory requestAttemptFactory) {
        channelCtx = ctx;
        responseFilters = filters;
        zuulRequest = transformRequest(inMesg);
        context = zuulRequest.getContext();
        origin = getOrigin(zuulRequest);
        originTimeoutManager = getTimeoutManager(origin);
        requestAttempts = RequestAttempts.getFromSessionContext(context);
        passport = CurrentPassport.fromSessionContext(context);
        chosenServer = new AtomicReference<>(DiscoveryResult.EMPTY);
        chosenHostAddr = new AtomicReference<>();
        concurrentReqCount = new AtomicInteger();

        this.methodBinding = methodBinding;
        this.requestAttemptFactory = requestAttemptFactory;
    }

    public int getAttemptNum() {
        return attemptNum;
    }

    public RequestAttempts getRequestAttempts() {
        return requestAttempts;
    }

    protected RequestAttempt getCurrentRequestAttempt() {
        return currentRequestAttempt;
    }

    public CurrentPassport getPassport() {
        return passport;
    }

    public NettyOrigin getOrigin() {
        return origin;
    }

    /**
     * Get the implementing origin.
     * <p>
     * Note: this method gets called in the constructor so if overloading it or any methods called within, you cannot
     * rely on your own constructor parameters.
     */
    @Nullable protected NettyOrigin getOrigin(HttpRequestMessage request) {
        SessionContext context = request.getContext();
        OriginManager<NettyOrigin> originManager =
                (OriginManager<NettyOrigin>) context.get(CommonContextKeys.ORIGIN_MANAGER);
        if (Debug.debugRequest(context)) {

            ImmutableList.Builder<String> routingLogEntries = context.get(CommonContextKeys.ROUTING_LOG);
            if (routingLogEntries != null) {
                for (String entry : routingLogEntries.build()) {
                    Debug.addRequestDebug(context, "RoutingLog: " + entry);
                }
            }
        }

        String primaryRoute = context.getRouteVIP();
        if (Strings.isNullOrEmpty(primaryRoute)) {
            // If no vip selected, leave origin null, then later the handleNoOriginSelected() method will be invoked.
            return null;
        }

        NettyOrigin origin = null;
        // allow implementors to override the origin with custom injection logic
        OriginName overrideOriginName = injectCustomOriginName(request);
        if (overrideOriginName != null) {
            // Use the custom vip instead if one has been provided.
            origin = getOrCreateOrigin(originManager, overrideOriginName, request.reconstructURI(), context);
        } else {
            // This is the normal flow - that a RoutingFilter has assigned a route
            OriginName originName = getOriginName(context);
            origin = getOrCreateOrigin(originManager, originName, request.reconstructURI(), context);
        }

        verifyOrigin(context, request, origin);

        // Update the routeVip on context to show the actual raw VIP from the clientConfig of the chosen Origin.
        if (origin != null) {
            context.set(
                    CommonContextKeys.ACTUAL_VIP,
                    origin.getClientConfig().get(IClientConfigKey.Keys.DeploymentContextBasedVipAddresses));
            context.set(
                    CommonContextKeys.ORIGIN_VIP_SECURE,
                    origin.getClientConfig().get(IClientConfigKey.Keys.IsSecure));
        }

        return origin;
    }

    public HttpRequestMessage getZuulRequest() {
        return zuulRequest;
    }

    // Unlink OriginResponseReceiver from origin channel pipeline so that we no longer receive events
    private Channel unlinkFromOrigin() {
        if (originResponseReceiver != null) {
            originResponseReceiver.unlinkFromClientRequest();
            originResponseReceiver = null;
        }

        if (concurrentReqCount.get() > 0) {
            origin.recordProxyRequestEnd();
            concurrentReqCount.incrementAndGet();
        }

        Channel origCh = null;
        if (originConn != null) {
            origCh = originConn.getChannel();
            originConn = null;
        }
        return origCh;
    }

    private void releasePartialResponse(HttpResponse partialResponse) {
        if (partialResponse != null && ReferenceCountUtil.refCnt(partialResponse) > 0) {
            ReferenceCountUtil.safeRelease(partialResponse);
        }
    }

    public void finish(boolean error) {
        Channel origCh = unlinkFromOrigin();

        while (concurrentReqCount.get() > 0) {
            origin.recordProxyRequestEnd();
            concurrentReqCount.decrementAndGet();
        }

        if (currentRequestStat != null) {
            if (error) {
                currentRequestStat.generalError();
            }
        }

        // Publish each of the request stats (ie. one for each attempt).
        if (!requestStats.isEmpty()) {
            int indexFinal = requestStats.size() - 1;
            for (int i = 0; i < requestStats.size(); i++) {
                RequestStat stat = requestStats.get(i);

                // Tag the final and non-final attempts.
                stat.finalAttempt(i == indexFinal);

                stat.finishIfNotAlready();
            }
        }

        if (error && (origCh != null)) {
            origCh.close();
        }
    }

    /* Zuul filter methods */
    @Override
    public String filterName() {
        return "ProxyEndpoint";
    }

    @Override
    public HttpResponseMessage apply(HttpRequestMessage input) {
        // If no Origin has been selected, then just return a 404 static response.
        // handle any exception here
        try {

            if (origin == null) {
                handleNoOriginSelected();
                return null;
            }

            origin.onRequestExecutionStart(zuulRequest);
            proxyRequestToOrigin();

            // Doesn't return origin response to caller, calls invokeNext() internally in response filter chain
            return null;
        } catch (Exception ex) {
            handleError(ex);
            return null;
        }
    }

    @Override
    public HttpContent processContentChunk(ZuulMessage zuulReq, HttpContent chunk) {
        if (originConn != null) {
            // Connected to origin, stream request body without buffering
            proxiedRequestWithoutBuffering = true;
            ByteBufUtil.touch(chunk, "ProxyEndpoint writing chunk to origin, request: ", zuulReq);
            originConn.getChannel().writeAndFlush(chunk);
            return null;
        }

        // Not connected to origin yet, let caller buffer the request body
        ByteBufUtil.touch(chunk, "ProxyEndpoint buffering chunk to origin, request: ", zuulReq);
        return chunk;
    }

    @Override
    public HttpResponseMessage getDefaultOutput(HttpRequestMessage input) {
        return null;
    }

    public void invokeNext(HttpResponseMessage zuulResponse) {
        try {
            methodBinding.bind(() -> filterResponse(zuulResponse));
        } catch (Exception ex) {
            unlinkFromOrigin();
            logger.error("Error in invokeNext resp", ex);
            channelCtx.fireExceptionCaught(ex);
        }
    }

    public void invokeNext(HttpContent chunk) {
        try {
            ByteBufUtil.touch(chunk, "ProxyEndpoint received chunk from origin, request: ", zuulRequest);
            methodBinding.bind(() -> filterResponseChunk(chunk));
        } catch (Exception ex) {
            ByteBufUtil.touch(chunk, "ProxyEndpoint exception processing chunk from origin, request: ", zuulRequest);
            unlinkFromOrigin();
            logger.error("Error in invokeNext content", ex);
            channelCtx.fireExceptionCaught(ex);
        }
    }

    private void filterResponse(HttpResponseMessage zuulResponse) {
        if (responseFilters != null) {
            responseFilters.filter(zuulResponse);
        } else {
            channelCtx.fireChannelRead(zuulResponse);
        }
    }

    private void filterResponseChunk(HttpContent chunk) {
        if (context.isCancelled() || !channelCtx.channel().isActive()) {
            SpectatorUtils.newCounter(
                            "zuul.origin.strayChunk",
                            origin == null ? "none" : origin.getName().getMetricId())
                    .increment();
            unlinkFromOrigin();
            ReferenceCountUtil.safeRelease(chunk);
            return;
        }

        if (chunk instanceof LastHttpContent) {
            unlinkFromOrigin();
        }

        if (responseFilters != null) {
            responseFilters.filter(zuulResponse, chunk);
        } else {
            channelCtx.fireChannelRead(chunk);
        }
    }

    private void storeAndLogOriginRequestInfo() {
        Map<String, Object> eventProps = context.getEventProperties();
        // These two maps appear to be almost the same but are slightly different.   Also, the types in the map don't
        // match exactly what needs to happen, so this is more of a To-Do.  ZUUL_ORIGIN_ATTEMPT_IPADDR_MAP_KEY is
        // supposed to be the mapping of IP addresses of the server.  This is (AFAICT) only used for logging.   It is
        // an IP address semantically, but a String here.   The two should be swapped.
        // ZUUL_ORIGIN_CHOSEN_HOST_ADDR_MAP_KEY is almost always an IP address, but may some times be a hostname in
        // case the discovery info is not an IP.
        Map<Integer, String> attemptToIpAddressMap =
                (Map<Integer, String>) eventProps.get(ZUUL_ORIGIN_ATTEMPT_IPADDR_MAP_KEY);
        Map<Integer, InetAddress> attemptToChosenHostMap = (Map<Integer, InetAddress>)
                eventProps.get(CommonContextKeys.ZUUL_ORIGIN_CHOSEN_HOST_ADDR_MAP_KEY.name());
        if (attemptToIpAddressMap == null) {
            attemptToIpAddressMap = new HashMap<>();
        }
        if (attemptToChosenHostMap == null) {
            attemptToChosenHostMap = new HashMap<>();
        }

        // the chosen server can be null in the case of a timeout exception that skips acquiring a new origin connection
        String ipAddr = origin.getIpAddrFromServer(chosenServer.get());
        if (ipAddr != null) {
            attemptToIpAddressMap.put(attemptNum, ipAddr);
            eventProps.put(ZUUL_ORIGIN_ATTEMPT_IPADDR_MAP_KEY, attemptToIpAddressMap);
        }

        if (chosenHostAddr.get() != null) {
            attemptToChosenHostMap.put(attemptNum, chosenHostAddr.get());
            eventProps.put(CommonContextKeys.ZUUL_ORIGIN_CHOSEN_HOST_ADDR_MAP_KEY.name(), attemptToChosenHostMap);
            context.put(CommonContextKeys.ZUUL_ORIGIN_CHOSEN_HOST_ADDR_MAP_KEY, attemptToChosenHostMap);
        }

        eventProps.put(ZUUL_ORIGIN_REQUEST_URI, zuulRequest.getPathAndQuery());
    }

    protected void updateOriginRpsTrackers(NettyOrigin origin, int attempt) {
        // override
    }

    private void proxyRequestToOrigin() {
        Promise<PooledConnection> promise = null;
        try {
            attemptNum += 1;

            /*
             * Before connecting to the origin, we need to compute how much time we have left for this attempt. This
             * method is also intended to validate deadline and timeouts boundaries for the request as a whole and could
             * throw an exception, skipping the logic below.
             */
            timeLeftForAttempt = originTimeoutManager.computeReadTimeout(zuulRequest, attemptNum);

            currentRequestStat = createRequestStat();
            origin.preRequestChecks(zuulRequest);
            concurrentReqCount.incrementAndGet();

            // update RPS trackers
            updateOriginRpsTrackers(origin, attemptNum);

            // We pass this AtomicReference<Server> here and the origin impl will assign the chosen server to it.
            promise = origin.connectToOrigin(
                    zuulRequest, channelCtx.channel().eventLoop(), attemptNum, passport, chosenServer, chosenHostAddr);

            storeAndLogOriginRequestInfo();
            currentRequestAttempt =
                    origin.newRequestAttempt(chosenServer.get(), chosenHostAddr.get(), context, attemptNum);
            requestAttempts.add(currentRequestAttempt);
            passport.add(PassportState.ORIGIN_CONN_ACQUIRE_START);

            if (promise.isDone()) {
                operationComplete(promise);
            } else {
                promise.addListener(this);
            }
        } catch (Exception ex) {
            if (ex instanceof RequestExpiredException) {
                logger.debug("Request deadline expired while connecting to origin, UUID {}", context.getUUID(), ex);
            } else {
                logger.error("Error while connecting to origin, UUID {}", context.getUUID(), ex);
            }
            storeAndLogOriginRequestInfo();
            if (promise != null && !promise.isDone()) {
                promise.setFailure(ex);
            } else {
                errorFromOrigin(ex);
            }
        }
    }

    /**
     * Override to track your own request stats.
     */
    protected RequestStat createRequestStat() {
        BasicRequestStat basicRequestStat = new BasicRequestStat();
        requestStats.add(basicRequestStat);
        RequestStat.putInSessionContext(basicRequestStat, context);
        return basicRequestStat;
    }

    @Override
    public void operationComplete(Future<PooledConnection> connectResult) {
        // MUST run this within bindingcontext to support ThreadVariables.
        try {
            methodBinding.bind(() -> {
                DiscoveryResult server = chosenServer.get();

                /* TODO(argha-c): This reliance on mutable update of the `chosenServer` must be improved.
                 * @see DiscoveryResult.EMPTY indicates that the loadbalancer found no available servers.
                 */
                if (!Objects.equals(server, DiscoveryResult.EMPTY)) {
                    if (currentRequestStat != null) {
                        currentRequestStat.server(server);
                    }

                    origin.onRequestStartWithServer(zuulRequest, server, attemptNum);
                }

                // Handle the connection establishment result.
                if (connectResult.isSuccess()) {
                    onOriginConnectSucceeded(connectResult.getNow(), timeLeftForAttempt);
                } else {
                    onOriginConnectFailed(connectResult.cause());
                }
            });
        } catch (Throwable ex) {
            logger.error(
                    "Uncaught error in operationComplete(). Closing the server channel now. {}",
                    ChannelUtils.channelInfoForLogging(channelCtx.channel()),
                    ex);

            unlinkFromOrigin();

            // Fire exception here to ensure that server channel gets closed, so clients don't hang.
            channelCtx.fireExceptionCaught(ex);
        }
    }

    private void onOriginConnectSucceeded(PooledConnection conn, Duration readTimeout) {
        passport.add(PassportState.ORIGIN_CONN_ACQUIRE_END);

        if (context.isCancelled()) {
            logger.info("Client cancelled after successful origin connect: {}", conn.getChannel());

            // conn isn't actually busy so we can put it in the pool
            conn.setConnectionState(PooledConnection.ConnectionState.WRITE_READY);
            conn.release();
        } else {
            // Update the RequestAttempt to reflect the readTimeout chosen.
            currentRequestAttempt.setReadTimeout(readTimeout.toMillis());

            // Start sending the request to origin now.
            writeClientRequestToOrigin(conn, readTimeout);
        }
    }

    private void onOriginConnectFailed(Throwable cause) {
        passport.add(PassportState.ORIGIN_CONN_ACQUIRE_FAILED);
        if (!context.isCancelled()) {
            errorFromOrigin(cause);
        }
    }

    private void writeClientRequestToOrigin(PooledConnection conn, Duration readTimeout) {
        Channel ch = conn.getChannel();
        passport.setOnChannel(ch);

        // set read timeout on origin channel
        ch.attr(ClientTimeoutHandler.ORIGIN_RESPONSE_READ_TIMEOUT).set(readTimeout);

        context.put(CommonContextKeys.ORIGIN_CHANNEL, ch);
        context.set(POOLED_ORIGIN_CONNECTION_KEY, conn);

        preWriteToOrigin(chosenServer.get(), zuulRequest);

        ChannelPipeline pipeline = ch.pipeline();
        originResponseReceiver = getOriginResponseReceiver();
        pipeline.addBefore(
                DefaultOriginChannelInitializer.CONNECTION_POOL_HANDLER,
                OriginResponseReceiver.CHANNEL_HANDLER_NAME,
                originResponseReceiver);

        ch.write(zuulRequest);
        writeBufferedBodyContent(zuulRequest, ch);
        ch.flush();

        // Get ready to read origin's response
        syncClientAndOriginChannels(channelCtx.channel(), ch);
        ch.read();

        originConn = conn;
        channelCtx.read();
    }

    protected void syncClientAndOriginChannels(Channel clientChannel, Channel originChannel) {
        // Add override for custom syncing between client and origin channels.
    }

    protected OriginResponseReceiver getOriginResponseReceiver() {
        return new OriginResponseReceiver(this);
    }

    protected void preWriteToOrigin(DiscoveryResult chosenServer, HttpRequestMessage zuulRequest) {
        // override for custom metrics or processing
    }

    private static void writeBufferedBodyContent(HttpRequestMessage zuulRequest, Channel channel) {
        zuulRequest.getBodyContents().forEach((chunk) -> {
            channel.write(chunk.retain());
        });
    }

    protected boolean isRemoteZuulRetriesBelowRetryLimit(int maxAllowedRetries) {
        // override for custom header checking..
        return true;
    }

    protected boolean isBelowRetryLimit() {
        int maxAllowedRetries = origin.getMaxRetriesForRequest(context);
        return (attemptNum <= maxAllowedRetries) && isRemoteZuulRetriesBelowRetryLimit(maxAllowedRetries);
    }

    public void errorFromOrigin(Throwable ex) {
        try {
            // Flag that there was an origin server related error for the loadbalancer to choose
            // whether to circuit-trip this server.
            if (originConn != null) {
                // NOTE: if originConn is null, then these stats will have been incremented within
                // PerServerConnectionPool
                // so don't need to be here.
                originConn.getServer().incrementSuccessiveConnectionFailureCount();
                originConn.getServer().addToFailureCount();

                originConn.flagShouldClose();
            }

            // detach from current origin
            Channel originCh = unlinkFromOrigin();

            methodBinding.bind(() -> processErrorFromOrigin(ex, originCh));
        } catch (Exception e) {
            channelCtx.fireExceptionCaught(ex);
        }
    }

    private void processErrorFromOrigin(Throwable ex, Channel origCh) {
        try {
            SessionContext zuulCtx = context;
            ErrorType err = requestAttemptFactory.mapNettyToOutboundErrorType(ex);

            // Be cautious about how much we log about errors from origins, as it can have perf implications at high
            // rps.
            if (zuulCtx.isInBrownoutMode()) {
                // Don't include the stacktrace or the channel info.
                logger.warn(
                        "{}, origin = {}: {}", err.getStatusCategory().name(), origin.getName(), String.valueOf(ex));
            } else {
                String origChInfo = (origCh != null) ? ChannelUtils.channelInfoForLogging(origCh) : "";
                if (logger.isInfoEnabled()) {
                    // Include the stacktrace.
                    logger.warn(
                            "{}, origin = {}, origin channel info = {}",
                            err.getStatusCategory().name(),
                            origin.getName(),
                            origChInfo,
                            ex);
                } else {
                    logger.warn(
                            "{}, origin = {}, {}, origin channel info = {}",
                            err.getStatusCategory().name(),
                            origin.getName(),
                            String.valueOf(ex),
                            origChInfo);
                }
            }

            // Update the NIWS stat.
            if (currentRequestStat != null) {
                currentRequestStat.failAndSetErrorCode(err);
            }

            // Update RequestAttempt info.
            if (currentRequestAttempt != null) {
                currentRequestAttempt.complete(-1, currentRequestStat.duration(), ex);
            }

            postErrorProcessing(ex, zuulCtx, err, chosenServer.get(), attemptNum);

            ClientException niwsEx = new ClientException(
                    ClientException.ErrorType.valueOf(err.getClientErrorType().name()));
            if (!Objects.equals(chosenServer.get(), DiscoveryResult.EMPTY)) {
                origin.onRequestExceptionWithServer(zuulRequest, chosenServer.get(), attemptNum, niwsEx);
            }

            boolean retryable = isRetryable(err);
            if (retryable) {
                origin.adjustRetryPolicyIfNeeded(zuulRequest);
            }

            if (retryable && isBelowRetryLimit()) {
                // retry request with different origin
                passport.add(PassportState.ORIGIN_RETRY_START);
                proxyRequestToOrigin();
            } else {
                // Record the exception in context. An error filter should later run which can translate this into an
                // app-specific error response if needed.
                zuulCtx.setError(ex);
                zuulCtx.setShouldSendErrorResponse(true);

                StatusCategoryUtils.storeStatusCategoryIfNotAlreadyFailure(zuulCtx, err.getStatusCategory());
                origin.recordFinalError(zuulRequest, ex);
                origin.onRequestExecutionFailed(zuulRequest, chosenServer.get(), attemptNum - 1, niwsEx);

                // Send error response to client
                handleError(ex);
            }
        } catch (Exception e) {
            // Use original origin returned exception
            handleError(ex);
        }
    }

    protected void postErrorProcessing(
            Throwable ex, SessionContext zuulCtx, ErrorType err, DiscoveryResult chosenServer, int attemptNum) {
        // override for custom processing
    }

    private void handleError(Throwable cause) {
        ZuulException ze = (cause instanceof ZuulException)
                ? (ZuulException) cause
                : requestAttemptFactory.mapNettyToOutboundException(cause, context);
        logger.debug("Proxy endpoint failed.", cause);
        if (!startedSendingResponseToClient) {
            startedSendingResponseToClient = true;
            zuulResponse = new HttpResponseMessageImpl(context, zuulRequest, ze.getStatusCode());
            zuulResponse
                    .getHeaders()
                    .add(
                            "Connection",
                            "close"); // TODO - why close the connection? maybe don't always want this to happen ...
            zuulResponse.finishBufferedBodyIfIncomplete();
            invokeNext(zuulResponse);
        } else {
            channelCtx.fireExceptionCaught(ze);
        }
    }

    private void handleNoOriginSelected() {
        StatusCategoryUtils.setStatusCategory(context, ZuulStatusCategory.SUCCESS_LOCAL_NO_ROUTE);
        startedSendingResponseToClient = true;
        zuulResponse = new HttpResponseMessageImpl(context, zuulRequest, 404);
        zuulResponse.finishBufferedBodyIfIncomplete();
        invokeNext(zuulResponse);
    }

    protected boolean isRetryable(ErrorType err) {
        if ((err == OutboundErrorType.RESET_CONNECTION)
                || (err == OutboundErrorType.CONNECT_ERROR)
                || (err == OutboundErrorType.READ_TIMEOUT
                        && IDEMPOTENT_HTTP_METHODS.contains(
                                zuulRequest.getMethod().toUpperCase(Locale.ROOT)))) {
            return isRequestReplayable();
        }
        return false;
    }

    /**
     * Request is replayable on a different origin IFF
     * A) we have not started to send response back to the client  AND
     * B) we have not lost any of its body chunks
     */
    protected boolean isRequestReplayable() {
        if (startedSendingResponseToClient) {
            NO_RETRY_RESP_STARTED.increment();
            return false;
        }
        if (proxiedRequestWithoutBuffering) {
            NO_RETRY_INCOMPLETE_BODY.increment();
            return false;
        }
        return true;
    }

    public void responseFromOrigin(HttpResponse originResponse) {
        try (TaskCloseable ignore = PerfMark.traceTask("ProxyEndpoint.responseFromOrigin")) {
            PerfMark.attachTag("uuid", zuulRequest, r -> r.getContext().getUUID());
            PerfMark.attachTag("path", zuulRequest, HttpRequestInfo::getPath);
            ByteBufUtil.touch(originResponse, "ProxyEndpoint handling response from origin, request: ", zuulRequest);
            methodBinding.bind(() -> processResponseFromOrigin(originResponse));
        } catch (Exception ex) {
            unlinkFromOrigin();
            releasePartialResponse(originResponse);
            logger.error("Error in responseFromOrigin", ex);
            channelCtx.fireExceptionCaught(ex);
        }
    }

    private void processResponseFromOrigin(HttpResponse originResponse) {
        if (originResponse.status().code() >= 500) {
            handleOriginNonSuccessResponse(originResponse, chosenServer.get());
        } else {
            handleOriginSuccessResponse(originResponse, chosenServer.get());
        }
    }

    protected void handleOriginSuccessResponse(HttpResponse originResponse, DiscoveryResult chosenServer) {
        origin.recordSuccessResponse();
        if (originConn != null) {
            originConn.getServer().clearSuccessiveConnectionFailureCount();
        }
        int respStatus = originResponse.status().code();
        long duration = 0;
        if (currentRequestStat != null) {
            currentRequestStat.updateWithHttpStatusCode(respStatus);
            duration = currentRequestStat.duration();
        }
        if (currentRequestAttempt != null) {
            currentRequestAttempt.complete(respStatus, duration, null);
        }
        // separate nfstatus for 404 so that we can notify origins
        ByteBufUtil.touch(originResponse, "ProxyEndpoint handling successful response, request: ", zuulRequest);
        StatusCategory statusCategory =
                respStatus == 404 ? ZuulStatusCategory.SUCCESS_NOT_FOUND : ZuulStatusCategory.SUCCESS;
        zuulResponse = buildZuulHttpResponse(originResponse, statusCategory, context.getError());
        invokeNext(zuulResponse);
    }

    private HttpResponseMessage buildZuulHttpResponse(
            HttpResponse httpResponse, StatusCategory statusCategory, Throwable ex) {
        startedSendingResponseToClient = true;

        // Translate the netty HttpResponse into a zuul HttpResponseMessage.
        SessionContext zuulCtx = context;
        int respStatus = httpResponse.status().code();
        HttpResponseMessage zuulResponse = new HttpResponseMessageImpl(zuulCtx, zuulRequest, respStatus);

        Headers respHeaders = zuulResponse.getHeaders();
        for (Map.Entry<String, String> entry : httpResponse.headers()) {
            respHeaders.add(entry.getKey(), entry.getValue());
        }

        // Try to decide if this response has a body or not based on the headers (as we won't yet have
        // received any of the content).
        // NOTE that we also later may override this if it is Chunked encoding, but we receive
        // a LastHttpContent without any prior HttpContent's.
        if (HttpUtils.hasChunkedTransferEncodingHeader(zuulResponse)
                || HttpUtils.hasNonZeroContentLengthHeader(zuulResponse)) {
            zuulResponse.setHasBody(true);
        }

        // Store this original response info for future reference (ie. for metrics and access logging purposes).
        zuulResponse.storeInboundResponse();
        channelCtx.channel().attr(ClientRequestReceiver.ATTR_ZUUL_RESP).set(zuulResponse);

        if (httpResponse instanceof DefaultFullHttpResponse) {
            ByteBufUtil.touch(
                    httpResponse, "ProxyEndpoint converting Netty response to Zuul response, request: ", zuulRequest);
            ByteBuf chunk = ((DefaultFullHttpResponse) httpResponse).content();
            zuulResponse.bufferBodyContents(new DefaultLastHttpContent(chunk));
        }

        // Invoke any Ribbon execution listeners.
        // Request was a success even if server may have responded with an error code 5XX.
        if (originConn != null) {
            origin.onRequestExecutionSuccess(zuulRequest, zuulResponse, originConn.getServer(), attemptNum);
        }

        // Collect some info about the received response.
        origin.recordFinalResponse(zuulResponse);
        origin.recordFinalError(zuulRequest, ex);
        StatusCategoryUtils.setStatusCategory(zuulCtx, statusCategory);
        zuulCtx.setError(ex);
        zuulCtx.put("origin_http_status", Integer.toString(respStatus));

        return transformResponse(zuulResponse);
    }

    private HttpResponseMessage transformResponse(HttpResponseMessage resp) {
        RESPONSE_HEADERS_TO_REMOVE.stream().forEach(s -> resp.getHeaders().remove(s));
        return resp;
    }

    protected void handleOriginNonSuccessResponse(HttpResponse originResponse, DiscoveryResult chosenServer) {
        int respStatus = originResponse.status().code();
        OutboundException obe;
        StatusCategory statusCategory;
        ClientException.ErrorType niwsErrorType;

        if (respStatus == 503) {
            statusCategory = ZuulStatusCategory.FAILURE_ORIGIN_THROTTLED;
            niwsErrorType = ClientException.ErrorType.SERVER_THROTTLED;
            obe = new OutboundException(OutboundErrorType.SERVICE_UNAVAILABLE, requestAttempts);
            if (currentRequestStat != null) {
                currentRequestStat.updateWithHttpStatusCode(respStatus);
                currentRequestStat.serviceUnavailable();
            }
        } else {
            statusCategory = ZuulStatusCategory.FAILURE_ORIGIN;
            niwsErrorType = ClientException.ErrorType.GENERAL;
            obe = new OutboundException(OutboundErrorType.ERROR_STATUS_RESPONSE, requestAttempts);
            if (currentRequestStat != null) {
                currentRequestStat.updateWithHttpStatusCode(respStatus);
                currentRequestStat.generalError();
            }
        }
        obe.setStatusCode(respStatus);

        long duration = 0;
        if (currentRequestStat != null) {
            duration = currentRequestStat.duration();
        }

        if (currentRequestAttempt != null) {
            currentRequestAttempt.complete(respStatus, duration, obe);
        }

        // Flag this error with the ExecutionListener.
        origin.onRequestExceptionWithServer(zuulRequest, chosenServer, attemptNum, new ClientException(niwsErrorType));

        boolean retryable5xxResponse = isRetryable5xxResponse(zuulRequest, originResponse);
        if (retryable5xxResponse) {
            origin.originRetryPolicyAdjustmentIfNeeded(zuulRequest, originResponse);
            origin.adjustRetryPolicyIfNeeded(zuulRequest);
        }

        if (retryable5xxResponse && isBelowRetryLimit()) {
            logger.debug(
                    "Retrying: status={}, attemptNum={}, maxRetries={}, startedSendingResponseToClient={},"
                            + " hasCompleteBody={}, method={}",
                    respStatus,
                    attemptNum,
                    origin.getMaxRetriesForRequest(context),
                    startedSendingResponseToClient,
                    zuulRequest.hasCompleteBody(),
                    zuulRequest.getMethod());
            // detach from current origin.
            ByteBufUtil.touch(originResponse, "ProxyEndpoint handling non-success retry, request: ", zuulRequest);
            unlinkFromOrigin();
            releasePartialResponse(originResponse);

            // ensure body reader indexes are reset so retry is able to access the body buffer
            // otherwise when the body is read by netty (in writeBufferedBodyContent) the body will appear empty
            zuulRequest.resetBodyReader();

            // retry request with different origin
            passport.add(PassportState.ORIGIN_RETRY_START);
            proxyRequestToOrigin();
        } else {
            SessionContext zuulCtx = context;
            logger.info(
                    "Sending error to client: status={}, attemptNum={}, maxRetries={},"
                            + " startedSendingResponseToClient={}, hasCompleteBody={}, method={}",
                    respStatus,
                    attemptNum,
                    origin.getMaxRetriesForRequest(zuulCtx),
                    startedSendingResponseToClient,
                    zuulRequest.hasCompleteBody(),
                    zuulRequest.getMethod());
            // This is a final response after all retries that will go to the client
            ByteBufUtil.touch(originResponse, "ProxyEndpoint handling non-success response, request: ", zuulRequest);
            zuulResponse = buildZuulHttpResponse(originResponse, statusCategory, obe);
            invokeNext(zuulResponse);
        }
    }

    public boolean isRetryable5xxResponse(
            HttpRequestMessage zuulRequest, HttpResponse originResponse) { // int retryNum, int maxRetries) {
        if (isRequestReplayable()) {
            int status = originResponse.status().code();
            if (status == 503 || originIndicatesRetryableInternalServerError(originResponse)) {
                return true;
            }
            // Retry if this is an idempotent http method AND status code was retriable for idempotent methods.
            else if (RETRIABLE_STATUSES_FOR_IDEMPOTENT_METHODS.get().contains(status)
                    && IDEMPOTENT_HTTP_METHODS.contains(zuulRequest.getMethod().toUpperCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    protected boolean originIndicatesRetryableInternalServerError(HttpResponse response) {
        // override for custom origin headers for retry
        return false;
    }

    /* static utility methods */

    protected HttpRequestMessage transformRequest(HttpRequestMessage requestMsg) {
        HttpRequestMessage massagedRequest = massageRequestURI(requestMsg);

        Headers headers = massagedRequest.getHeaders();
        REQUEST_HEADERS_TO_REMOVE.forEach(headerName -> headers.remove(headerName.getName()));

        addCustomRequestHeaders(headers);

        // Add X-Forwarded headers if not already there.
        ProxyUtils.addXForwardedHeaders(massagedRequest);

        return massagedRequest;
    }

    protected void addCustomRequestHeaders(Headers headers) {
        // override to add custom headers
    }

    private static HttpRequestMessage massageRequestURI(HttpRequestMessage request) {
        SessionContext context = request.getContext();
        String modifiedPath;
        HttpQueryParams modifiedQueryParams = null;
        String uri = null;

        if (context.get("requestURI") != null) {
            uri = (String) context.get("requestURI");
        }

        // If another filter has specified an overrideURI, then use that instead of requested URI.
        Object override = context.get("overrideURI");
        if (override != null) {
            uri = override.toString();
        }

        if (uri != null) {
            int index = uri.indexOf('?');
            if (index != -1) {
                // Strip the query string off of the URI.
                String paramString = uri.substring(index + 1);
                modifiedPath = uri.substring(0, index);

                try {
                    paramString = URLDecoder.decode(paramString, "UTF-8");
                    modifiedQueryParams = new HttpQueryParams();
                    StringTokenizer stk = new StringTokenizer(paramString, "&");
                    while (stk.hasMoreTokens()) {
                        String token = stk.nextToken();
                        int idx = token.indexOf("=");
                        if (idx != -1) {
                            String key = token.substring(0, idx);
                            String val = token.substring(idx + 1);
                            modifiedQueryParams.add(key, val);
                        }
                    }
                } catch (UnsupportedEncodingException e) {
                    logger.error("Error decoding url query param - {}", paramString, e);
                }
            } else {
                modifiedPath = uri;
            }

            request.setPath(modifiedPath);
            if (modifiedQueryParams != null) {
                request.setQueryParams(modifiedQueryParams);
            }
        }

        return request;
    }

    @Nonnull
    protected OriginName getOriginName(SessionContext context) {
        String clientName = getClientName(context);
        return OriginName.fromVip(context.getRouteVIP(), clientName);
    }

    @Nonnull
    protected String getClientName(SessionContext context) {
        // make sure the restClientName will never be a raw VIP in cases where it's the fallback for another route
        // assignment
        String restClientVIP = context.getRouteVIP();
        boolean useFullName = context.getBoolean(CommonContextKeys.USE_FULL_VIP_NAME);
        return useFullName ? restClientVIP : VipUtils.getVIPPrefix(restClientVIP);
    }

    /**
     * Inject your own custom VIP based on your own processing
     * <p>
     * Note: this method gets called in the constructor so if overloading it or any methods called within, you cannot
     * rely on your own constructor parameters.
     *
     * @return {@code null} if unused.
     */
    @Nullable protected OriginName injectCustomOriginName(HttpRequestMessage request) {
        // override for custom vip injection
        return null;
    }

    private NettyOrigin getOrCreateOrigin(
            OriginManager<NettyOrigin> originManager, OriginName originName, String uri, SessionContext ctx) {
        NettyOrigin origin = originManager.getOrigin(originName, uri, ctx);
        if (origin == null) {
            // If no pre-registered and configured RestClient found for this VIP, then register one using default NIWS
            // properties.
            logger.warn(
                    "Attempting to register RestClient for client that has not been configured. originName={}, uri={}",
                    originName,
                    uri);
            origin = originManager.createOrigin(originName, uri, ctx);
        }
        return origin;
    }

    private void verifyOrigin(SessionContext context, HttpRequestMessage request, Origin primaryOrigin) {
        if (primaryOrigin == null) {
            String vip = context.getRouteVIP();
            // If no origin found then add specific error-cause metric tag, and throw an exception with 404 status.
            StatusCategoryUtils.setStatusCategory(
                    context,
                    ZuulStatusCategory.SUCCESS_LOCAL_NO_ROUTE,
                    "Unable to find an origin client matching `" + vip + "` to handle request");
            String causeName = "RESTCLIENT_NOTFOUND";
            originNotFound(context, causeName);
            ZuulException ze = new ZuulException(
                    "No origin found for request. name=" + vip + ", uri=" + request.reconstructURI(), causeName);
            ze.setStatusCode(404);
            throw ze;
        }
    }

    @ForOverride
    protected void originNotFound(SessionContext context, String causeName) {
        // override for metrics or custom processing
    }

    @ForOverride
    protected OriginTimeoutManager getTimeoutManager(NettyOrigin origin) {
        return new OriginTimeoutManager(origin);
    }
}
