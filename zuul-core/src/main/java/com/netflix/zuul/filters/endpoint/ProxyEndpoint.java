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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import com.netflix.client.ClientException;
import com.netflix.client.config.IClientConfig;
import com.netflix.client.config.IClientConfigKey;
import com.netflix.config.CachedDynamicIntProperty;
import com.netflix.config.DynamicBooleanProperty;
import com.netflix.config.DynamicIntegerSetProperty;
import com.netflix.loadbalancer.Server;
import com.netflix.loadbalancer.reactive.ExecutionContext;
import com.netflix.spectator.api.Counter;
import com.netflix.zuul.context.CommonContextKeys;
import com.netflix.zuul.context.Debug;
import com.netflix.zuul.context.SessionContext;
import com.netflix.zuul.exception.ErrorType;
import com.netflix.zuul.exception.OutboundErrorType;
import com.netflix.zuul.exception.OutboundException;
import com.netflix.zuul.exception.ZuulException;
import com.netflix.zuul.filters.SyncZuulFilterAdapter;
import com.netflix.zuul.message.Header;
import com.netflix.zuul.message.HeaderName;
import com.netflix.zuul.message.Headers;
import com.netflix.zuul.message.ZuulMessage;
import com.netflix.zuul.message.http.HttpHeaderNames;
import com.netflix.zuul.message.http.HttpQueryParams;
import com.netflix.zuul.message.http.HttpRequestMessage;
import com.netflix.zuul.message.http.HttpResponseMessage;
import com.netflix.zuul.message.http.HttpResponseMessageImpl;
import com.netflix.zuul.netty.ChannelUtils;
import com.netflix.zuul.netty.NettyRequestAttemptFactory;
import com.netflix.zuul.netty.SpectatorUtils;
import com.netflix.zuul.netty.connectionpool.BasicRequestStat;
import com.netflix.zuul.netty.connectionpool.PooledConnection;
import com.netflix.zuul.netty.connectionpool.RequestStat;
import com.netflix.zuul.netty.filter.FilterRunner;
import com.netflix.zuul.netty.server.MethodBinding;
import com.netflix.zuul.netty.server.OriginResponseReceiver;
import com.netflix.zuul.niws.RequestAttempt;
import com.netflix.zuul.niws.RequestAttempts;
import com.netflix.zuul.origins.NettyOrigin;
import com.netflix.zuul.origins.Origin;
import com.netflix.zuul.origins.OriginManager;
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
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import io.netty.util.concurrent.Promise;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.concurrent.atomic.AtomicReference;

import static com.netflix.client.config.CommonClientConfigKey.ReadTimeout;
import static com.netflix.zuul.netty.server.ClientRequestReceiver.ATTR_ZUUL_RESP;
import static com.netflix.zuul.passport.PassportState.*;
import static com.netflix.zuul.stats.status.ZuulStatusCategory.*;

/**
 * Not thread safe! New instance of this class is created per HTTP/1.1 request proxied to the origin but NOT for each
 * attempt/retry. All the retry attempts for a given HTTP/1.1 request proxied share the same EdgeProxyEndpoint instance
 * Created by saroskar on 5/31/17.
 */
public class ProxyEndpoint extends SyncZuulFilterAdapter<HttpRequestMessage, HttpResponseMessage> implements GenericFutureListener<Future<PooledConnection>> {

    private final ChannelHandlerContext channelCtx;
    private final FilterRunner<HttpResponseMessage, ?> responseFilters;
    private final AtomicReference<Server> chosenServer;

    /* Individual request related state */
    private final HttpRequestMessage zuulRequest;
    private final SessionContext context;
    private final NettyOrigin origin;
    private final RequestAttempts requestAttempts;
    private final CurrentPassport passport;
    private final NettyRequestAttemptFactory requestAttemptFactory;

    private MethodBinding<?> methodBinding;
    private HttpResponseMessage zuulResponse;
    private boolean startedSendingResponseToClient;

    /* Individual retry related state */
    private volatile PooledConnection originConn;
    private volatile OriginResponseReceiver originResponseReceiver;
    private volatile int concurrentReqCount;
    private volatile boolean proxiedRequestWithoutBuffering;
    private int attemptNum;
    private RequestAttempt currentRequestAttempt;
    private RequestStat requestStat;
    private final byte[] sslRetryBodyCache;

    public static final Set<String> IDEMPOTENT_HTTP_METHODS = Sets.newHashSet("GET", "HEAD", "OPTIONS");
    private static final DynamicIntegerSetProperty RETRIABLE_STATUSES_FOR_IDEMPOTENT_METHODS = new DynamicIntegerSetProperty("zuul.retry.allowed.statuses.idempotent", "500");
    private static final DynamicBooleanProperty ENABLE_CACHING_SSL_BODIES = new DynamicBooleanProperty("zuul.cache.ssl.bodies", true);

    private static final CachedDynamicIntProperty MAX_OUTBOUND_READ_TIMEOUT = new CachedDynamicIntProperty("zuul.origin.readtimeout.max", 90 * 1000);

    private static final Set<HeaderName> REQUEST_HEADERS_TO_REMOVE = Sets.newHashSet(HttpHeaderNames.CONNECTION, HttpHeaderNames.KEEP_ALIVE);
    private static final Set<HeaderName> RESPONSE_HEADERS_TO_REMOVE = Sets.newHashSet(HttpHeaderNames.CONNECTION, HttpHeaderNames.KEEP_ALIVE);
    public static final String POOLED_ORIGIN_CONNECTION_KEY = "_origin_pooled_conn";
    private static final Logger LOG = LoggerFactory.getLogger(ProxyEndpoint.class);
    private static final Counter NO_RETRY_INCOMPLETE_BODY = SpectatorUtils.newCounter("zuul.no.retry","incomplete_body");
    private static final Counter NO_RETRY_RESP_STARTED = SpectatorUtils.newCounter("zuul.no.retry","resp_started");
    private final Counter populatedSslRetryBody;


    public ProxyEndpoint(final HttpRequestMessage inMesg, final ChannelHandlerContext ctx,
                         final FilterRunner<HttpResponseMessage, ?> filters, MethodBinding<?> methodBinding) {
        this(inMesg, ctx, filters, methodBinding, new NettyRequestAttemptFactory());
    }

    public ProxyEndpoint(final HttpRequestMessage inMesg, final ChannelHandlerContext ctx,
                         final FilterRunner<HttpResponseMessage, ?> filters, MethodBinding<?> methodBinding,
                         NettyRequestAttemptFactory requestAttemptFactory) {
        channelCtx = ctx;
        responseFilters = filters;
        zuulRequest = transformRequest(inMesg);
        context = zuulRequest.getContext();
        origin = getOrigin(zuulRequest);
        requestAttempts = RequestAttempts.getFromSessionContext(context);
        passport = CurrentPassport.fromSessionContext(context);
        chosenServer = new AtomicReference<>();

        this.sslRetryBodyCache = preCacheBodyForRetryingSslRequests();
        this.populatedSslRetryBody = SpectatorUtils.newCounter("zuul.populated.ssl.retry.body", origin == null ? "null" : origin.getVip());

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

    public HttpRequestMessage getZuulRequest() {
        return zuulRequest;
    }

    //Unlink OriginResponseReceiver from origin channel pipeline so that we no longer receive events
    private Channel unlinkFromOrigin() {
        if (originResponseReceiver != null) {
            originResponseReceiver.unlinkFromClientRequest();
            originResponseReceiver = null;
        }

        if (concurrentReqCount > 0) {
            origin.recordProxyRequestEnd();
            concurrentReqCount--;
        }

        Channel origCh = null;
        if (originConn != null) {
            origCh = originConn.getChannel();
            originConn = null;
        }
        return origCh;
    }

    public void finish(boolean error) {
        final Channel origCh = unlinkFromOrigin();

        while (concurrentReqCount > 0) {
            origin.recordProxyRequestEnd();
            concurrentReqCount--;
        }

        if (requestStat != null) {
            if (error) requestStat.generalError();
            requestStat.finishIfNotAlready();
        }

        if ((error) && (origCh != null)) {
            origCh.close();
        }
    }

    /* Zuul filter methods */
    @Override
    public String filterName() {
        return "ProxyEndpoint";
    }

    @Override
    public HttpResponseMessage apply(final HttpRequestMessage input) {
        // If no Origin has been selected, then just return a 404 static response.
        // handle any exception here
        try {

            if (origin == null) {
                handleNoOriginSelected();
                return null;
            }

            origin.getProxyTiming(zuulRequest).start();
            origin.onRequestExecutionStart(zuulRequest, 1);
            proxyRequestToOrigin();
            //Doesn't return origin response to caller, calls invokeNext() internally in response filter chain
            return null;
        } catch (Exception ex) {
            handleError(ex);
            return null;
        }
    }

    @Override
    public HttpContent processContentChunk(final ZuulMessage zuulReq, final HttpContent chunk) {
        if (originConn != null) {
            //Connected to origin, stream request body without buffering
            proxiedRequestWithoutBuffering = true;
            originConn.getChannel().writeAndFlush(chunk);
            return null;
        }

        //Not connected to origin yet, let caller buffer the request body
        return chunk;
    }

    @Override
    public HttpResponseMessage getDefaultOutput(final HttpRequestMessage input) {
        return null;
    }

    public void invokeNext(final HttpResponseMessage zuulResponse) {
        try {
            methodBinding.bind(() -> filterResponse(zuulResponse));
        } catch (Exception ex) {
            unlinkFromOrigin();
            LOG.error("Error in invokeNext resp", ex);
            channelCtx.fireExceptionCaught(ex);
        }
    }

    private void filterResponse(final HttpResponseMessage zuulResponse) {
        if (responseFilters != null) {
            responseFilters.filter(zuulResponse);
        } else {
            channelCtx.fireChannelRead(zuulResponse);
        }
    }

    public void invokeNext(final HttpContent chunk) {
        try {
            methodBinding.bind(() -> filterResponseChunk(chunk));
        } catch (Exception ex) {
            unlinkFromOrigin();
            LOG.error("Error in invokeNext content", ex);
            channelCtx.fireExceptionCaught(ex);
        }
    }

    private void filterResponseChunk(final HttpContent chunk) {
        if (chunk instanceof LastHttpContent) {
            unlinkFromOrigin();
            if (requestStat != null) {
                requestStat.finishIfNotAlready();
            }
        }

        if (responseFilters != null) {
            responseFilters.filter(zuulResponse, chunk);
        } else {
            channelCtx.fireChannelRead(chunk);
        }
    }

    private void logOriginRequestInfo() {
        final Map<String, Object> eventProps = context.getEventProperties();
        Map<Integer, String> attempToIpAddressMap = (Map) eventProps.get(CommonContextKeys.ZUUL_ORIGIN_ATTEMPT_IPADDR_MAP_KEY);
        if (attempToIpAddressMap == null) {
            attempToIpAddressMap = new HashMap<>();
        }
        String ipAddr = origin.getIpAddrFromServer(chosenServer.get());
        if (ipAddr != null) {
            attempToIpAddressMap.put(attemptNum, ipAddr);
            eventProps.put(CommonContextKeys.ZUUL_ORIGIN_ATTEMPT_IPADDR_MAP_KEY, attempToIpAddressMap);
        }

        eventProps.put(CommonContextKeys.ZUUL_ORIGIN_REQUEST_URI, zuulRequest.getPathAndQuery());
    }

    private void proxyRequestToOrigin() {
        Promise<PooledConnection> promise = null;
        try {
            attemptNum += 1;
            requestStat = createRequestStat();
            origin.preRequestChecks(zuulRequest);
            concurrentReqCount++;
            promise = origin.connectToOrigin(zuulRequest, channelCtx.channel().eventLoop(), attemptNum, passport, chosenServer);

            logOriginRequestInfo();
            currentRequestAttempt = origin.newRequestAttempt(chosenServer.get(), context, attemptNum);
            requestAttempts.add(currentRequestAttempt);
            passport.add(PassportState.ORIGIN_CONN_ACQUIRE_START);

            if (promise.isDone()) {
                operationComplete(promise);
            } else {
                promise.addListener(this);
            }
        }
        catch (Exception ex) {
            LOG.error("Error while connecting to origin, UUID {} " + context.getUUID(), ex);
            logOriginRequestInfo();
            if (promise != null && ! promise.isDone()) {
                promise.setFailure(ex);
            } else {
                errorFromOrigin(ex);
            }
        }
    }

    /**
     * Override to track your own request stats
     *
     * @return
     */
    protected RequestStat createRequestStat() {
        BasicRequestStat basicRequestStat = new BasicRequestStat(origin.getName());
        RequestStat.putInSessionContext(basicRequestStat, context);
        return basicRequestStat;
    }

    @Override
    public void operationComplete(final Future<PooledConnection> connectResult) {
        // MUST run this within bindingcontext because RequestExpiryProcessor (and probably other things) depends on ThreadVariables.
        try {
            methodBinding.bind(() -> {

                // Invoke the ribbon execution listeners (including RequestExpiry).
                final ExecutionContext<?> executionContext = origin.getExecutionContext(zuulRequest, attemptNum);
                IClientConfig requestConfig = executionContext.getRequestConfig();
                final Object previousOverriddenReadTimeout = requestConfig.getProperty(ReadTimeout, null);
                Integer readTimeout;
                try {
                    Server server = chosenServer.get();
                    if (requestStat != null)
                        requestStat.server(server);

                    readTimeout = getReadTimeout(requestConfig, attemptNum);
                    requestConfig.set(ReadTimeout, readTimeout);

                    origin.onRequestStartWithServer(zuulRequest, server, attemptNum);
                }
                catch (Throwable e) {
                    handleError(e);
                    return;
                }
                finally {
                    // Reset the timeout in overriddenConfig back to what it was before, otherwise it will take
                    // preference on subsequent retry attempts in RequestExpiryProcessor.
                    if (previousOverriddenReadTimeout == null) {
                        requestConfig.setProperty(ReadTimeout, null);
                    } else {
                        requestConfig.setProperty(ReadTimeout, previousOverriddenReadTimeout);
                    }
                }

                // Handle the connection.
                if (connectResult.isSuccess()) {
                    onOriginConnectSucceeded(connectResult.getNow(), readTimeout);
                } else {
                    onOriginConnectFailed(connectResult.cause());
                }
            });
        } catch (Throwable ex) {
            LOG.error("Uncaught error in operationComplete(). Closing the server channel now. {}"
                    , ChannelUtils.channelInfoForLogging(channelCtx.channel()), ex);

            unlinkFromOrigin();

            // Fire exception here to ensure that server channel gets closed, so clients don't hang.
            channelCtx.fireExceptionCaught(ex);
        }
    }

    private void onOriginConnectSucceeded(PooledConnection conn, int readTimeout) {
        passport.add(ORIGIN_CONN_ACQUIRE_END);

        if (context.isCancelled()) {
            conn.release();
        }
        else {
            // Set the read timeout (we only do this late because this timeout can be adjusted dynamically by the niws RequestExpiryExecutionListener
            // that is run as part of onRequestStartWithServer() above.
            // Add a ReadTimeoutHandler to the channel before we send a request on it.
            conn.startReadTimeoutHandler(readTimeout);

            // Also update the RequestAttempt to reflect the readTimeout chosen.
            currentRequestAttempt.setReadTimeout(readTimeout);

            // Start sending the request to origin now.
            writeClientRequestToOrigin(conn);
        }
    }

    protected Integer getReadTimeout(IClientConfig requestConfig, int attemptNum) {
        Integer originTimeout = parseReadTimeout(origin.getClientConfig().getProperty(IClientConfigKey.Keys.ReadTimeout, null));
        Integer requestTimeout = parseReadTimeout(requestConfig.getProperty(IClientConfigKey.Keys.ReadTimeout, null));

        if (originTimeout == null && requestTimeout == null) {
            return MAX_OUTBOUND_READ_TIMEOUT.get();
        }
        else if (originTimeout == null || requestTimeout == null) {
            return originTimeout == null ? requestTimeout : originTimeout;
        }
        else {
            // return the greater of two timeouts
            return originTimeout > requestTimeout ? originTimeout : requestTimeout;
        }
    }

    private Integer parseReadTimeout(Object p) {
        if (p instanceof String && StringUtils.isNotBlank((String)p)) {
            return Integer.valueOf((String)p);
        }
        else if (p instanceof Integer) {
            return (Integer) p;
        }
        else {
            return null;
        }
    }

    private void onOriginConnectFailed(Throwable cause) {
        passport.add(ORIGIN_CONN_ACQUIRE_FAILED);
        if (! context.isCancelled()) {
            errorFromOrigin(cause);
        }
    }

    private byte[] preCacheBodyForRetryingSslRequests() {
        // Netty SSL handler clears body ByteBufs, so we need to cache the body if we want to retry POSTs
        if (ENABLE_CACHING_SSL_BODIES.get() && origin != null &&
                // only cache requests if already buffered
                origin.getClientConfig().get(IClientConfigKey.Keys.IsSecure, false) && zuulRequest.hasCompleteBody()) {
            return zuulRequest.getBody();
        }
        return null;
    }

    private void repopulateRetryBody() {
        // if SSL origin request body is cached and has been cleared by Netty SslHandler, set it from cache
        // note: it's not null but is empty because the content chunks exist but the actual readable bytes are 0
        if (sslRetryBodyCache != null && attemptNum > 1 && zuulRequest.getBody() != null && zuulRequest.getBody().length == 0) {
            zuulRequest.setBody(sslRetryBodyCache);
            populatedSslRetryBody.increment();
        }
    }

    private void writeClientRequestToOrigin(final PooledConnection conn) {
        final Channel ch = conn.getChannel();
        passport.setOnChannel(ch);

        context.set("_origin_channel", ch);
        context.set(POOLED_ORIGIN_CONNECTION_KEY, conn);

        preWriteToOrigin(chosenServer.get(), context);

        final ChannelPipeline pipeline = ch.pipeline();
        originResponseReceiver = getOriginResponseReceiver();
        pipeline.addBefore("connectionPoolHandler", OriginResponseReceiver.CHANNEL_HANDLER_NAME, originResponseReceiver);

        // check if body needs to be repopulated for retry
        repopulateRetryBody();

        ch.write(zuulRequest);
        writeBufferedBodyContent(zuulRequest, ch);
        ch.flush();

        //Get ready to read origin's response
        ch.read();

        originConn = conn;
        channelCtx.read();
    }

    protected OriginResponseReceiver getOriginResponseReceiver() {
        return new OriginResponseReceiver(this);
    }

    protected void preWriteToOrigin(Server chosenServer, SessionContext context) {
        // override for custom metrics or processing
    }

    private static void writeBufferedBodyContent(final HttpRequestMessage zuulRequest, final Channel channel) {
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
        return (attemptNum <= maxAllowedRetries) &&
                isRemoteZuulRetriesBelowRetryLimit(maxAllowedRetries);
    }

    public void errorFromOrigin(final Throwable ex) {
        try {
            // Flag that there was an origin server related error for the loadbalancer to choose
            // whether to circuit-trip this server.
            if (originConn != null) {
                originConn.getServerStats().incrementSuccessiveConnectionFailureCount();
                originConn.getServerStats().addToFailureCount();
                originConn.flagShouldClose();
            }

            //detach from current origin
            final Channel originCh = unlinkFromOrigin();

            methodBinding.bind(() -> processErrorFromOrigin(ex, originCh));
        } catch (Exception e) {
            channelCtx.fireExceptionCaught(ex);
        }
    }

    private void processErrorFromOrigin(final Throwable ex, final Channel origCh) {
        try {
            final SessionContext zuulCtx = context;
            final ErrorType err = requestAttemptFactory.mapNettyToOutboundErrorType(ex);

            // Be cautious about how much we log about errors from origins, as it can have perf implications at high rps.
            if (zuulCtx.isInBrownoutMode()) {
                // Don't include the stacktrace or the channel info.
                LOG.warn(err.getStatusCategory().name() + ", origin = " + origin.getName() + ": " + String.valueOf(ex));
            } else {
                final String origChInfo = (origCh != null) ? ChannelUtils.channelInfoForLogging(origCh) : "";
                if (LOG.isInfoEnabled()) {
                    // Include the stacktrace.
                    LOG.warn(err.getStatusCategory().name() + ", origin = " + origin.getName() + ", origin channel info = " + origChInfo, ex);
                }
                else {
                    LOG.warn(err.getStatusCategory().name() + ", origin = " + origin.getName() + ", " + String.valueOf(ex) + ", origin channel info = " + origChInfo);
                }
            }

            // Update the NIWS stat.
            finishRequestStatWithErrorType(err);

            // Update RequestAttempt info.
            if (currentRequestAttempt != null) {
                currentRequestAttempt.complete(-1, requestStat.duration(), ex);
            }

            postErrorProcessing(ex, zuulCtx, err, chosenServer.get(), attemptNum);

            final ClientException niwsEx = new ClientException(ClientException.ErrorType.valueOf(err.getClientErrorType().name()));
            if (chosenServer.get() != null) {
                origin.onRequestExceptionWithServer(zuulRequest, chosenServer.get(), attemptNum, niwsEx);
            }

            if ((isBelowRetryLimit()) && (isRetryable(err))) {
                //retry request with different origin
                passport.add(ORIGIN_RETRY_START);
                proxyRequestToOrigin();
            } else {
                // Record the exception in context. An error filter should later run which can translate this into an
                // app-specific error response if needed.
                zuulCtx.setError(ex);
                zuulCtx.setShouldSendErrorResponse(true);

                StatusCategoryUtils.storeStatusCategoryIfNotAlreadyFailure(zuulCtx, err.getStatusCategory());
                origin.getProxyTiming(zuulRequest).end();
                origin.recordFinalError(zuulRequest, ex);
                origin.onRequestExecutionFailed(zuulRequest, chosenServer.get(), attemptNum - 1, niwsEx);

                //Send error response to client
                handleError(ex);
            }
        } catch (Exception e) {
            //Use original origin returned exception
            handleError(ex);
        }
    }

    protected void postErrorProcessing(Throwable ex, SessionContext zuulCtx, ErrorType err, Server chosenServer, int attemptNum) {
        // override for custom processing
    }

    private void finishRequestStatWithErrorType(ErrorType niwsErrorType)
    {
        if (requestStat != null) {

            if (! isBelowRetryLimit()) {
                requestStat.nextServerRetriesExceeded();
            } else {
                if (niwsErrorType != null) {
                    requestStat.failAndSetErrorCode(niwsErrorType.toString());
                }
            }

            requestStat.finishIfNotAlready();
        }
    }

    private void handleError(final Throwable cause) {
        final ZuulException ze = (cause instanceof  ZuulException) ?
                (ZuulException) cause : requestAttemptFactory.mapNettyToOutboundException(cause, context);
        LOG.debug("Proxy endpoint failed.", cause);
        if (! startedSendingResponseToClient) {
            startedSendingResponseToClient = true;
            zuulResponse = new HttpResponseMessageImpl(context, zuulRequest, ze.getStatusCode());
            zuulResponse.getHeaders().add("Connection", "close");   // TODO - why close the connection? maybe don't always want this to happen ...
            zuulResponse.finishBufferedBodyIfIncomplete();
            invokeNext(zuulResponse);
        } else {
            channelCtx.fireExceptionCaught(ze);
        }
    }

    private void handleNoOriginSelected() {
        StatusCategoryUtils.setStatusCategory(context, SUCCESS_LOCAL_NO_ROUTE);
        startedSendingResponseToClient = true;
        zuulResponse = new HttpResponseMessageImpl(context, zuulRequest, 404);
        zuulResponse.finishBufferedBodyIfIncomplete();
        invokeNext(zuulResponse);
    }

    protected boolean isRetryable(final ErrorType err) {
        if (err == OutboundErrorType.READ_TIMEOUT  || err == OutboundErrorType.RESET_CONNECTION || err == OutboundErrorType.CONNECT_ERROR) {
            return isRequestReplayable() ;
        }
        return false;
    }

    /**
     * Request is replayable on a different origin IFF
     *   A) we have not started to send response back to the client  AND
     *   B) we have not lost any of its body chunks
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

    public void responseFromOrigin(final HttpResponse originResponse) {
        try {
            methodBinding.bind(() -> processResponseFromOrigin(originResponse));
        } catch (Exception ex) {
            unlinkFromOrigin();
            LOG.error("Error in responseFromOrigin", ex);
            channelCtx.fireExceptionCaught(ex);
        }
    }

    private void processResponseFromOrigin(final HttpResponse originResponse) {
        if (originResponse.status().code() >= 500) {
            handleOriginNonSuccessResponse(originResponse, chosenServer.get());
        } else {
            handleOriginSuccessResponse(originResponse, chosenServer.get());
        }
    }

    protected void handleOriginSuccessResponse(final HttpResponse originResponse, Server chosenServer) {
        origin.recordSuccessResponse();
        if (originConn != null) {
            originConn.getServerStats().clearSuccessiveConnectionFailureCount();
        }
        final int respStatus = originResponse.status().code();
        long duration = 0;
        if (requestStat != null) {
            requestStat.updateWithHttpStatusCode(respStatus);
            requestStat.finishIfNotAlready();
            duration = requestStat.duration();
        }
        if (currentRequestAttempt != null) {
            currentRequestAttempt.complete(respStatus, duration, null);
        }
        // separate nfstatus for 404 so that we can notify origins
        final StatusCategory statusCategory = respStatus == 404 ? SUCCESS_NOT_FOUND : SUCCESS;
        zuulResponse = buildZuulHttpResponse(originResponse, statusCategory, context.getError());
        invokeNext(zuulResponse);
    }

    private HttpResponseMessage buildZuulHttpResponse(final HttpResponse httpResponse, final StatusCategory statusCategory, final Throwable ex) {
        startedSendingResponseToClient = true;

        // Translate the netty HttpResponse into a zuul HttpResponseMessage.
        final SessionContext zuulCtx = context;
        final int respStatus = httpResponse.status().code();
        final HttpResponseMessage zuulResponse = new HttpResponseMessageImpl(zuulCtx, zuulRequest, respStatus);

        final Headers respHeaders = zuulResponse.getHeaders();
        for (Map.Entry<String, String> entry : httpResponse.headers()) {
            respHeaders.add(entry.getKey(), entry.getValue());
        }

        // Try to decide if this response has a body or not based on the headers (as we won't yet have
        // received any of the content).
        // NOTE that we also later may override this if it is Chunked encoding, but we receive
        // a LastHttpContent without any prior HttpContent's.
        if (HttpUtils.hasChunkedTransferEncodingHeader(zuulResponse) || HttpUtils.hasNonZeroContentLengthHeader(zuulResponse)) {
            zuulResponse.setHasBody(true);
        }

        // Store this original response info for future reference (ie. for metrics and access logging purposes).
        zuulResponse.storeInboundResponse();
        channelCtx.attr(ATTR_ZUUL_RESP).set(zuulResponse);

        if (httpResponse instanceof DefaultFullHttpResponse) {
            final ByteBuf chunk = ((DefaultFullHttpResponse) httpResponse).content();
            zuulResponse.bufferBodyContents(new DefaultLastHttpContent(chunk));
        }

        // Invoke any Ribbon execution listeners.
        // Request was a success even if server may have responded with an error code 5XX, except for 503.
        if (originConn != null) {
            if (statusCategory == ZuulStatusCategory.FAILURE_ORIGIN_THROTTLED) {
                origin.onRequestExecutionFailed(zuulRequest, originConn.getServer(), attemptNum,
                        new ClientException(ClientException.ErrorType.SERVER_THROTTLED));
            }
            else {
                origin.onRequestExecutionSuccess(zuulRequest, zuulResponse, originConn.getServer(), attemptNum);
            }
        }

        // Collect some info about the received response.
        origin.recordFinalResponse(zuulResponse);
        origin.recordFinalError(zuulRequest, ex);
        origin.getProxyTiming(zuulRequest).end();
        zuulCtx.set(CommonContextKeys.STATUS_CATGEORY, statusCategory);
        zuulCtx.setError(ex);
        zuulCtx.put("origin_http_status", Integer.toString(respStatus));

        return transformResponse(zuulResponse);
    }

    private HttpResponseMessage transformResponse(HttpResponseMessage resp) {
        RESPONSE_HEADERS_TO_REMOVE.stream().forEach(s -> resp.getHeaders().remove(s));
        return resp;
    }

    protected void handleOriginNonSuccessResponse(final HttpResponse originResponse, Server chosenServer) {
        final int respStatus = originResponse.status().code();
        OutboundException obe;
        StatusCategory statusCategory;

        if (respStatus == 503) {
            //Treat 503 status from Origin similarly to connection failures, ie. we want to back off from this server
            statusCategory = FAILURE_ORIGIN_THROTTLED;
            obe = new OutboundException(OutboundErrorType.SERVICE_UNAVAILABLE, requestAttempts);
            if (originConn != null) {
                originConn.getServerStats().incrementSuccessiveConnectionFailureCount();
                originConn.getServerStats().addToFailureCount();
                originConn.flagShouldClose();
            }
            if (requestStat != null) {
                requestStat.updateWithHttpStatusCode(respStatus);
                requestStat.serviceUnavailable();
            }
        } else {
            statusCategory = FAILURE_ORIGIN;
            obe = new OutboundException(OutboundErrorType.ERROR_STATUS_RESPONSE, requestAttempts);
            if (requestStat != null) {
                requestStat.updateWithHttpStatusCode(respStatus);
                requestStat.generalError();
            }
        }
        obe.setStatusCode(respStatus);

        long duration = 0;
        if (requestStat != null) {
            requestStat.finishIfNotAlready();
            duration = requestStat.duration();
        }

        if (currentRequestAttempt != null) {
            currentRequestAttempt.complete(respStatus, duration, obe);
        }

        // If throttled by origin server, then we also need to invoke onRequestExceptionWithServer().
        if (statusCategory == FAILURE_ORIGIN_THROTTLED) {
            origin.onRequestExceptionWithServer(zuulRequest, chosenServer, attemptNum,
                    new ClientException(ClientException.ErrorType.SERVER_THROTTLED));
        }

        if ((isBelowRetryLimit()) && (isRetryable5xxResponse(zuulRequest, originResponse))) {
            LOG.debug("Retrying: status={}, attemptNum={}, maxRetries={}, startedSendingResponseToClient={}, hasCompleteBody={}, method={}",
                    respStatus, attemptNum, origin.getMaxRetriesForRequest(context),
                    startedSendingResponseToClient, zuulRequest.hasCompleteBody(), zuulRequest.getMethod());
            //detach from current origin.
            unlinkFromOrigin();
            //retry request with different origin
            passport.add(ORIGIN_RETRY_START);
            proxyRequestToOrigin();
        } else {
            SessionContext zuulCtx = context;
            LOG.info("Sending error to client: status={}, attemptNum={}, maxRetries={}, startedSendingResponseToClient={}, hasCompleteBody={}, method={}",
                    respStatus, attemptNum, origin.getMaxRetriesForRequest(zuulCtx),
                    startedSendingResponseToClient, zuulRequest.hasCompleteBody(), zuulRequest.getMethod());
            //This is a final response after all retries that will go to the client
            zuulResponse = buildZuulHttpResponse(originResponse, statusCategory, obe);
            invokeNext(zuulResponse);
        }
    }

    public boolean isRetryable5xxResponse(final HttpRequestMessage zuulRequest, HttpResponse originResponse) { // int retryNum, int maxRetries) {
        if (isRequestReplayable()) {
            int status = originResponse.status().code();
            if (status == 503 || originIndicatesRetryableInternalServerError(originResponse)) {
                return true;
            }
            // Retry if this is an idempotent http method AND status code was retriable for idempotent methods.
            else if (RETRIABLE_STATUSES_FOR_IDEMPOTENT_METHODS.get().contains(status) && IDEMPOTENT_HTTP_METHODS.contains(zuulRequest.getMethod().toUpperCase())) {
                return true;
            }
        }
        return false;
    }

    protected boolean originIndicatesRetryableInternalServerError(final HttpResponse response) {
        // override for custom origin headers for retry
        return false;
    }


    /* static utility methods */

    protected HttpRequestMessage transformRequest(HttpRequestMessage requestMsg) {
        requestMsg = massageRequestURI(requestMsg);

        final Headers headers = requestMsg.getHeaders();
        for (Header entry : headers.entries()) {
            String headerName = entry.getName().getNormalised();
            if (REQUEST_HEADERS_TO_REMOVE.contains(headerName)) {
                headers.remove(entry.getKey());
            }
        }

        addCustomRequestHeaders(headers);

        // Add X-Forwarded headers if not already there.
        ProxyUtils.addXForwardedHeaders(requestMsg);

        return requestMsg;
    }

    protected void addCustomRequestHeaders(Headers headers) {
        // override to add custom headers
    }

    private static HttpRequestMessage massageRequestURI(HttpRequestMessage request) {
        final SessionContext context = request.getContext();
        String modifiedPath;
        HttpQueryParams modifiedQueryParams = null;
        String uri = null;

        if (context.get("requestURI") != null) {
            uri = (String) context.get("requestURI");
        }

        // If another filter has specified an overrideURI, then use that instead of requested URI.
        final Object override = context.get("overrideURI");
        if(override != null ) {
            uri = override.toString();
        }

        if (null != uri) {
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
                    LOG.error("Error decoding url query param - " + paramString, e);
                }
            } else {
                modifiedPath = uri;
            }

            request.setPath(modifiedPath);
            if (null != modifiedQueryParams) {
                request.setQueryParams(modifiedQueryParams);
            }
        }

        return request;
    }

    /**
     * Get the implementing origin.
     *
     * Note: this method gets called in the constructor so if overloading it or any methods called within, you cannot
     * rely on your own constructor parameters.
     *
     * @param request
     * @return
     */
    protected NettyOrigin getOrigin(HttpRequestMessage request) {
        SessionContext context = request.getContext();
        OriginManager<NettyOrigin> originManager = (OriginManager<NettyOrigin>) context.get(CommonContextKeys.ORIGIN_MANAGER);
        if (Debug.debugRequest(context)) {

            ImmutableList.Builder<String> routingLogEntries = (ImmutableList.Builder<String>)context.get(CommonContextKeys.ROUTING_LOG);
            if(routingLogEntries != null) {
                for (String entry : routingLogEntries.build()) {
                    Debug.addRequestDebug(context, "RoutingLog: " + entry);
                }
            }
        }

        String primaryRoute = context.getRouteVIP();
        if (StringUtils.isEmpty(primaryRoute)) {
            // If no vip selected, leave origin null, then later the handleNoOriginSelected() method will be invoked.
            return null;
        }

        // make sure the restClientName will never be a raw VIP in cases where it's the fallback for another route assignment
        String restClientVIP = primaryRoute;
        boolean useFullName = context.getBoolean(CommonContextKeys.USE_FULL_VIP_NAME);
        String restClientName = useFullName ? restClientVIP : VipUtils.getVIPPrefix(restClientVIP);

        Pair<String, String> customVip = injectCustomVip(request);
        if (customVip != null) {
            restClientVIP = customVip.getLeft();
            restClientName = customVip.getRight();
        }

        NettyOrigin origin = null;

        if (restClientName != null) {
            // This is the normal flow - that a RoutingFilter has assigned a route
            origin = getOrCreateOrigin(originManager, restClientName, restClientVIP, request.reconstructURI(), useFullName, context);
        }

        verifyOrigin(context, request, restClientName, origin);

        // Update the routeVip on context to show the actual raw VIP from the clientConfig of the chosen Origin.
        if (origin != null) {
            context.set(CommonContextKeys.ACTUAL_VIP, origin.getClientConfig().get(IClientConfigKey.Keys.DeploymentContextBasedVipAddresses));
            context.set(CommonContextKeys.ORIGIN_VIP_SECURE, origin.getClientConfig().get(IClientConfigKey.Keys.IsSecure));
        }

        return origin;
    }

    /**
     * Inject your own custom VIP based on your own processing
     *
     * Note: this method gets called in the constructor so if overloading it or any methods called within, you cannot
     * rely on your own constructor parameters.
     *
     * @param request
     * @return
     */
    protected Pair<String, String> injectCustomVip(HttpRequestMessage request) {
        // override for custom vip injection
        return null;
    }

    private NettyOrigin getOrCreateOrigin(OriginManager<NettyOrigin> originManager, String name, String vip, String uri, boolean useFullVipName, SessionContext ctx) {
        NettyOrigin origin = originManager.getOrigin(name, vip, uri, ctx);
        if (origin == null) {
            // If no pre-registered and configured RestClient found for this VIP, then register one using default NIWS properties.
            LOG.warn("Attempting to register RestClient for client that has not been configured. restClientName={}, vip={}, uri={}", name, vip, uri);
            origin = originManager.createOrigin(name, vip, uri, useFullVipName, ctx);
        }
        return origin;
    }

    private void verifyOrigin(SessionContext context, HttpRequestMessage request, String restClientName, Origin primaryOrigin) {
        if (primaryOrigin == null) {
            // If no origin found then add specific error-cause metric tag, and throw an exception with 404 status.
            context.set(CommonContextKeys.STATUS_CATGEORY, SUCCESS_LOCAL_NO_ROUTE);
            String causeName = "RESTCLIENT_NOTFOUND";
            originNotFound(context, causeName);
            ZuulException ze = new ZuulException("No origin found for request. name=" + restClientName
                    + ", uri=" + request.reconstructURI(), causeName);
            ze.setStatusCode(404);
            throw ze;
        }
    }

    protected void originNotFound(SessionContext context, String causeName) {
        // override for metrics or custom processing
    }

}

