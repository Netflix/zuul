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

package com.netflix.zuul.netty.filter;

import com.netflix.config.CachedDynamicIntProperty;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Registry;
import com.netflix.spectator.impl.Preconditions;
import com.netflix.zuul.ExecutionStatus;
import com.netflix.zuul.FilterUsageNotifier;
import com.netflix.zuul.context.CommonContextKeys;
import com.netflix.zuul.context.Debug;
import com.netflix.zuul.context.SessionContext;
import com.netflix.zuul.exception.ZuulException;
import com.netflix.zuul.filters.FilterError;
import com.netflix.zuul.filters.FilterSyncType;
import com.netflix.zuul.filters.FilterType;
import com.netflix.zuul.filters.SyncZuulFilter;
import com.netflix.zuul.filters.ZuulFilter;
import com.netflix.zuul.message.ZuulMessage;
import com.netflix.zuul.message.http.HttpRequestInfo;
import com.netflix.zuul.message.http.HttpRequestMessage;
import com.netflix.zuul.message.http.HttpResponseMessage;
import com.netflix.zuul.netty.SpectatorUtils;
import com.netflix.zuul.netty.server.MethodBinding;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpContent;
import io.netty.util.concurrent.EventExecutor;
import io.perfmark.Link;
import io.perfmark.PerfMark;
import io.perfmark.TaskCloseable;
import jakarta.annotation.Nullable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.concurrent.ThreadSafe;
import lombok.Getter;
import lombok.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Subclasses of this class are supposed to be thread safe
 *
 * Created by saroskar on 5/18/17.
 */
@ThreadSafe
public abstract class BaseZuulFilterRunner<I extends ZuulMessage, O extends ZuulMessage> implements FilterRunner<I, O> {

    private final FilterUsageNotifier usageNotifier;

    @Getter
    private final FilterRunner<O, ? extends ZuulMessage> nextStage;

    private final String RUNNING_FILTER_IDX_SESSION_CTX_KEY;
    private final String AWAITING_BODY_FLAG_SESSION_CTX_KEY;
    private static final Logger logger = LoggerFactory.getLogger(BaseZuulFilterRunner.class);

    private static final CachedDynamicIntProperty FILTER_EXCESSIVE_EXEC_TIME =
            new CachedDynamicIntProperty("zuul.filters.excessive.execTime", 500);

    private final Registry registry;
    private final Id filterExcessiveTimerId;
    private final FilterConstraints filterConstraints;

    protected BaseZuulFilterRunner(
            FilterType filterType,
            FilterUsageNotifier usageNotifier,
            FilterRunner<O, ?> nextStage,
            FilterConstraints filterConstraints,
            Registry registry) {
        this.usageNotifier = Preconditions.checkNotNull(usageNotifier, "filter usage notifier");
        this.nextStage = nextStage;
        this.RUNNING_FILTER_IDX_SESSION_CTX_KEY = filterType + "RunningFilterIndex";
        this.AWAITING_BODY_FLAG_SESSION_CTX_KEY = filterType + "IsAwaitingBody";
        this.registry = registry;
        this.filterExcessiveTimerId = registry.createId("zuul.request.timing.filterExcessive");
        this.filterConstraints = filterConstraints;
    }

    @NonNull
    public static ChannelHandlerContext getChannelHandlerContext(ZuulMessage mesg) {
        return (ChannelHandlerContext) mesg.getContext().get(CommonContextKeys.NETTY_SERVER_CHANNEL_HANDLER_CONTEXT);
    }

    protected final AtomicInteger initRunningFilterIndex(I zuulMesg) {
        AtomicInteger idx = new AtomicInteger(0);
        zuulMesg.getContext().put(RUNNING_FILTER_IDX_SESSION_CTX_KEY, idx);
        return idx;
    }

    protected final AtomicInteger getRunningFilterIndex(I zuulMesg) {
        SessionContext ctx = zuulMesg.getContext();
        return (AtomicInteger)
                Preconditions.checkNotNull(ctx.get(RUNNING_FILTER_IDX_SESSION_CTX_KEY), "runningFilterIndex");
    }

    protected final boolean isFilterAwaitingBody(SessionContext context) {
        return context.containsKey(AWAITING_BODY_FLAG_SESSION_CTX_KEY);
    }

    protected final void setFilterAwaitingBody(I zuulMesg, boolean flag) {
        if (flag) {
            zuulMesg.getContext().put(AWAITING_BODY_FLAG_SESSION_CTX_KEY, Boolean.TRUE);
        } else {
            zuulMesg.getContext().remove(AWAITING_BODY_FLAG_SESSION_CTX_KEY);
        }
    }

    protected final void invokeNextStage(O zuulMesg, HttpContent chunk) {
        if (nextStage != null) {
            try (TaskCloseable ignored =
                    PerfMark.traceTask(this, s -> s.getClass().getSimpleName() + ".invokeNextStageChunk")) {
                addPerfMarkTags(zuulMesg);
                nextStage.filter(zuulMesg, chunk);
            }
        } else {
            // Next stage is Netty channel handler
            try (TaskCloseable ignored =
                    PerfMark.traceTask(this, s -> s.getClass().getSimpleName() + ".fireChannelReadChunk")) {
                addPerfMarkTags(zuulMesg);
                ChannelHandlerContext channelHandlerContext = getChannelHandlerContext(zuulMesg);
                if (!channelHandlerContext.channel().isActive()) {
                    zuulMesg.getContext().cancel();
                    zuulMesg.disposeBufferedBody();
                    SpectatorUtils.newCounter(
                                    "zuul.filterChain.chunk.hanging",
                                    zuulMesg.getClass().getSimpleName())
                            .increment();
                } else {
                    channelHandlerContext.fireChannelRead(chunk);
                }
            }
        }
    }

    protected final void invokeNextStage(O zuulMesg) {
        if (nextStage != null) {
            try (TaskCloseable ignored =
                    PerfMark.traceTask(this, s -> s.getClass().getSimpleName() + ".invokeNextStage")) {
                addPerfMarkTags(zuulMesg);
                nextStage.filter(zuulMesg);
            }
        } else {
            // Next stage is Netty channel handler
            try (TaskCloseable ignored =
                    PerfMark.traceTask(this, s -> s.getClass().getSimpleName() + ".fireChannelRead")) {
                addPerfMarkTags(zuulMesg);
                ChannelHandlerContext channelHandlerContext = getChannelHandlerContext(zuulMesg);
                if (!channelHandlerContext.channel().isActive()) {
                    zuulMesg.getContext().cancel();
                    zuulMesg.disposeBufferedBody();
                    SpectatorUtils.newCounter(
                                    "zuul.filterChain.message.hanging",
                                    zuulMesg.getClass().getSimpleName())
                            .increment();
                } else {
                    channelHandlerContext.fireChannelRead(zuulMesg);
                }
            }
        }
    }

    protected final void addPerfMarkTags(ZuulMessage inMesg) {
        HttpRequestInfo req = null;
        if (inMesg instanceof HttpRequestInfo) {
            req = (HttpRequestInfo) inMesg;
        }
        if (inMesg instanceof HttpResponseMessage msg) {

            req = msg.getOutboundRequest();
            PerfMark.attachTag("statuscode", msg.getStatus());
        }
        if (req != null) {
            PerfMark.attachTag("path", req, HttpRequestInfo::getPath);
            PerfMark.attachTag("originalhost", req, HttpRequestInfo::getOriginalHost);
        }
        PerfMark.attachTag("uuid", inMesg, m -> m.getContext().getUUID());
    }

    protected final FilterExecutionResult<O> executeFilter(ZuulFilter<I, O> filter, I inMesg) {
        long startTime = System.nanoTime();
        ZuulMessage snapshot = inMesg.getContext().debugRouting() ? inMesg.clone() : null;

        try (TaskCloseable ignored = PerfMark.traceTask(filter, f -> f.filterName() + ".filter")) {
            addPerfMarkTags(inMesg);

            ExecutionStatus executionStatus = checkFilterPreconditions(filter, inMesg);
            if (executionStatus != null) {
                recordFilterCompletion(executionStatus, filter, startTime, inMesg, snapshot);
                return FilterExecutionResult.completed(filter.getDefaultOutput(inMesg));
            }

            if (!isMessageBodyReadyForFilter(filter, inMesg)) {
                setFilterAwaitingBody(inMesg, true);
                logger.debug(
                        "Filter {} waiting for body, UUID {}",
                        filter.filterName(),
                        inMesg.getContext().getUUID());
                return FilterExecutionResult.pending();
            }
            setFilterAwaitingBody(inMesg, false);

            if (snapshot != null) {
                Debug.addRoutingDebug(
                        inMesg.getContext(),
                        "Filter " + filter.filterType().toString() + " " + filter.filterOrder() + " "
                                + filter.filterName());
            }

            // run body contents accumulated so far through this filter
            inMesg.runBufferedBodyContentThroughFilter(filter);

            if (filter.getSyncType() == FilterSyncType.SYNC) {
                return executeSyncFilter((SyncZuulFilter<I, O>) filter, inMesg, startTime, snapshot);
            }

            return executeAsyncFilter(filter, inMesg, startTime, snapshot);
        } catch (Throwable t) {
            O outMesg = handleFilterException(inMesg, filter, t);
            outMesg.finishBufferedBodyIfIncomplete();
            recordFilterCompletion(ExecutionStatus.FAILED, filter, startTime, inMesg, snapshot);
            return FilterExecutionResult.completed(outMesg);
        }
    }

    @Nullable
    private ExecutionStatus checkFilterPreconditions(ZuulFilter<I, O> filter, I inMesg) {
        if (filter.filterType() == FilterType.INBOUND && inMesg.getContext().shouldSendErrorResponse()) {
            // Pass request down the pipeline, all the way to error endpoint if error response needs to be generated
            return ExecutionStatus.SKIPPED;
        }

        try (TaskCloseable ignored = PerfMark.traceTask(filter, f -> f.filterName() + ".shouldSkipFilter")) {
            if (shouldSkipFilter(inMesg, filter)) {
                return ExecutionStatus.SKIPPED;
            }
        }

        if (filter.isDisabled()) {
            return ExecutionStatus.DISABLED;
        }

        return null;
    }

    /**
     * Execute a SyncZuulFilter apply on the current event loop thread.
     */
    private FilterExecutionResult<O> executeSyncFilter(
            SyncZuulFilter<I, O> filter, I inMesg, long startTime, ZuulMessage snapshot) {
        O outMesg;
        try (TaskCloseable ignored = PerfMark.traceTask(filter, f -> f.filterName() + ".apply")) {
            addPerfMarkTags(inMesg);
            outMesg = filter.apply(inMesg);
        }
        recordFilterCompletion(ExecutionStatus.SUCCESS, filter, startTime, inMesg, snapshot);
        return FilterExecutionResult.completed((outMesg != null) ? outMesg : filter.getDefaultOutput(inMesg));
    }

    /**
     * Execute a ZuulFilter's async apply, wiring up the completion callback to resume the filter chain.
     */
    private FilterExecutionResult<O> executeAsyncFilter(
            ZuulFilter<I, O> filter, I inMesg, long startTime, ZuulMessage snapshot) {
        filter.incrementConcurrency();
        try (TaskCloseable ignored = PerfMark.traceTask(filter, f -> f.filterName() + ".applyAsync")) {
            Link perfMarkLink = PerfMark.linkOut();
            CompletableFuture<O> future = filter.applyAsync(inMesg);
            EventExecutor eventExecutor = getChannelHandlerContext(inMesg).executor();
            future.whenComplete((result, error) -> executeOnEventLoop(
                    eventExecutor,
                    () -> onAsyncFilterComplete(filter, inMesg, result, error, startTime, snapshot, perfMarkLink)));
        } catch (Throwable t) {
            filter.decrementConcurrency();
            throw t;
        }
        return FilterExecutionResult.pending();
    }

    private void onAsyncFilterComplete(
            ZuulFilter<I, O> filter,
            I inMesg,
            @Nullable O result,
            @Nullable Throwable error,
            long startTime,
            ZuulMessage snapshot,
            Link perfMarkLink) {
        try (TaskCloseable ignored = PerfMark.traceTask(filter, f -> f.filterName() + ".asyncComplete")) {
            PerfMark.linkIn(perfMarkLink);
            filter.decrementConcurrency();
            O outMesg;
            if (error != null) {
                recordFilterCompletion(ExecutionStatus.FAILED, filter, startTime, inMesg, snapshot);
                outMesg = handleFilterException(inMesg, filter, error);
                outMesg.finishBufferedBodyIfIncomplete();
            } else {
                outMesg = (result != null) ? result : filter.getDefaultOutput(inMesg);
                recordFilterCompletion(ExecutionStatus.SUCCESS, filter, startTime, inMesg, snapshot);
            }
            resumeInBindingContext(outMesg, filter.filterName());
        } catch (Exception e) {
            handleException(inMesg, filter.filterName(), e);
        }
    }

    private static void executeOnEventLoop(@NonNull EventExecutor eventExecutor, @NonNull Runnable task) {
        if (eventExecutor.inEventLoop()) {
            task.run();
        } else {
            eventExecutor.execute(task);
        }
    }

    /**
     *  This is typically set by a filter when wanting to reject a request and also reduce load on the server by
     *  not processing anymore filterChain
     */
    protected final boolean shouldSkipFilter(I inMesg, ZuulFilter<I, O> filter) {
        if (filter.filterType() == FilterType.ENDPOINT) {
            // Endpoints may not be skipped
            return false;
        }
        SessionContext zuulCtx = inMesg.getContext();
        if (zuulCtx.shouldStopFilterProcessing() && !filter.overrideStopFilterProcessing()) {
            return true;
        }
        if (zuulCtx.isCancelled()) {
            return true;
        }

        if (filterConstraints.isConstrained(inMesg, filter)) {
            return true;
        }
        return !filter.shouldFilter(inMesg);
    }

    private boolean isMessageBodyReadyForFilter(ZuulFilter<I, O> filter, I inMesg) {
        return inMesg.hasCompleteBody() || !filter.needsBodyBuffered(inMesg);
    }

    protected O handleFilterException(I inMesg, ZuulFilter<I, O> filter, Throwable ex) {
        inMesg.getContext().setError(ex);
        if (filter.filterType() == FilterType.ENDPOINT) {
            inMesg.getContext().setShouldSendErrorResponse(true);
        }
        recordFilterError(inMesg, filter, ex);
        return filter.getDefaultOutput(inMesg);
    }

    protected void recordFilterError(I inMesg, ZuulFilter<I, O> filter, Throwable t) {
        // Add a log statement for this exception.
        String errorMsg = "Filter Exception: filter=" + filter.filterName() + ", request-info="
                + inMesg.getInfoForLogging() + ", msg=" + String.valueOf(t.getMessage());
        if (t instanceof ZuulException && !((ZuulException) t).shouldLogAsError()) {
            logger.warn(errorMsg);
        } else {
            logger.error(errorMsg, t);
        }

        // Store this filter error for possible future use. But we still continue with next filter in the chain.
        SessionContext zuulCtx = inMesg.getContext();
        zuulCtx.getFilterErrors()
                .add(new FilterError(filter.filterName(), filter.filterType().toString(), t));
        if (zuulCtx.debugRouting()) {
            Debug.addRoutingDebug(
                    zuulCtx,
                    "Running Filter failed " + filter.filterName() + " type:" + filter.filterType() + " order:"
                            + filter.filterOrder() + " " + t.getMessage());
        }
    }

    protected void recordFilterCompletion(
            ExecutionStatus status,
            ZuulFilter<I, O> filter,
            long startTime,
            ZuulMessage zuulMesg,
            ZuulMessage startSnapshot) {

        SessionContext zuulCtx = zuulMesg.getContext();
        long execTimeNs = System.nanoTime() - startTime;
        long execTimeMs = execTimeNs / 1_000_000L;
        if (execTimeMs >= FILTER_EXCESSIVE_EXEC_TIME.get()) {
            zuulCtx.setEventProperty("filter_execution_time_exceeded", true);
            registry.timer(filterExcessiveTimerId
                            .withTag("id", filter.filterName())
                            .withTag("status", status.name()))
                    .record(execTimeMs, TimeUnit.MILLISECONDS);
        }

        // Record the execution summary in context.
        switch (status) {
            case FAILED:
                if (logger.isDebugEnabled()) {
                    zuulCtx.addFilterExecutionSummary(filter.filterName(), ExecutionStatus.FAILED.name(), execTimeMs);
                }
                break;
            case SUCCESS:
                if (logger.isDebugEnabled()) {
                    zuulCtx.addFilterExecutionSummary(filter.filterName(), ExecutionStatus.SUCCESS.name(), execTimeMs);
                }
                if (startSnapshot != null) {
                    // debugRouting == true
                    Debug.addRoutingDebug(
                            zuulCtx,
                            "Filter {" + filter.filterName() + " TYPE:"
                                    + filter.filterType().toString() + " ORDER:" + filter.filterOrder()
                                    + "} Execution time = " + execTimeMs + "ms");
                    Debug.compareContextState(filter.filterName(), zuulCtx, startSnapshot.getContext());
                }
                break;
            default:
                break;
        }

        logger.debug(
                "Filter {} completed with status {}, UUID {}",
                filter.filterName(),
                status.name(),
                zuulMesg.getContext().getUUID());
        // Notify configured listener.
        usageNotifier.notify(filter, status);
    }

    protected void handleException(ZuulMessage zuulMesg, String filterName, Exception ex) {
        HttpRequestInfo zuulReq = null;
        if (zuulMesg instanceof HttpRequestMessage) {
            zuulReq = (HttpRequestMessage) zuulMesg;
        } else if (zuulMesg instanceof HttpResponseMessage) {
            zuulReq = ((HttpResponseMessage) zuulMesg).getInboundRequest();
        }
        String path = (zuulReq != null) ? zuulReq.getPathAndQuery() : "-";
        String method = (zuulReq != null) ? zuulReq.getMethod() : "-";
        String errMesg = "Error with filter: " + filterName + ", path: " + path + ", method: " + method;
        logger.error(errMesg, ex);
        getChannelHandlerContext(zuulMesg).fireExceptionCaught(ex);
    }

    protected abstract void resume(O zuulMesg);

    protected MethodBinding<?> methodBinding(ZuulMessage zuulMesg) {
        return MethodBinding.NO_OP_BINDING;
    }

    protected void resumeInBindingContext(O zuulMesg, String filterName) {
        try {
            methodBinding(zuulMesg).bind(() -> resume(zuulMesg));
        } catch (Exception ex) {
            handleException(zuulMesg, filterName, ex);
        }
    }

    /**
     * FilterExecutionResult indicates if the filter is still processing a request, such as waiting
     * on an async filter execution or for a full body to buffer, or has completed.
     */
    protected sealed interface FilterExecutionResult<O> {
        record Complete<O>(@Nullable O message) implements FilterExecutionResult<O> {}

        record Pending<O>() implements FilterExecutionResult<O> {}

        Pending<?> pending = new Pending<>();

        @SuppressWarnings("unchecked")
        static <O> FilterExecutionResult<O> pending() {
            return (FilterExecutionResult<O>) pending;
        }

        static <O> FilterExecutionResult<O> completed(O message) {
            return new Complete<>(message);
        }
    }
}
