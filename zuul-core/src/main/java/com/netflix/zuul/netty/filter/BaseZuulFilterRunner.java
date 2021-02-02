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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.netflix.zuul.ExecutionStatus.DISABLED;
import static com.netflix.zuul.ExecutionStatus.FAILED;
import static com.netflix.zuul.ExecutionStatus.SKIPPED;
import static com.netflix.zuul.ExecutionStatus.SUCCESS;
import static com.netflix.zuul.context.CommonContextKeys.NETTY_SERVER_CHANNEL_HANDLER_CONTEXT;
import static com.netflix.zuul.filters.FilterType.ENDPOINT;
import static com.netflix.zuul.filters.FilterType.INBOUND;
import static io.perfmark.PerfMark.attachTag;
import static io.perfmark.PerfMark.traceTask;

import com.netflix.config.CachedDynamicIntProperty;
import com.netflix.spectator.impl.Preconditions;
import com.netflix.zuul.ExecutionStatus;
import com.netflix.zuul.FilterUsageNotifier;
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
import com.netflix.zuul.netty.server.MethodBinding;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpContent;
import io.netty.util.concurrent.DefaultPromise;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.GenericFutureListener;
import io.netty.util.concurrent.Promise;
import io.perfmark.Link;
import io.perfmark.PerfMark;
import io.perfmark.TaskCloseable;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Subclasses of this class are supposed to be thread safe and hence should not have any non final member variables
 * Created by saroskar on 5/18/17.
 */
@ThreadSafe
public abstract class BaseZuulFilterRunner<I extends ZuulMessage, O extends ZuulMessage> implements FilterRunner<I, O> {

    private final FilterUsageNotifier usageNotifier;
    private final FilterRunner<O, ? extends ZuulMessage> nextStage;

    private final String RUNNING_FILTER_IDX_SESSION_CTX_KEY;
    private final String AWAITING_BODY_FLAG_SESSION_CTX_KEY;
    private static final Logger logger = LoggerFactory.getLogger(BaseZuulFilterRunner.class);

    private static final CachedDynamicIntProperty FILTER_EXCESSIVE_EXEC_TIME = new CachedDynamicIntProperty("zuul.filters.excessive.execTime", 500);
    private final Consumer<? super I> asyncDetach;
    private final Consumer<? super O> asyncAttach;

    /**
     * Use {@link #BaseZuulFilterRunner(FilterType, FilterUsageNotifier, FilterRunner, Consumer, Consumer)} instead.
     */
    @Deprecated
    protected BaseZuulFilterRunner(FilterType filterType, FilterUsageNotifier usageNotifier, FilterRunner<O, ?> nextStage) {
        this(filterType, usageNotifier, nextStage, in -> {}, out -> {});
    }

    protected BaseZuulFilterRunner(
            FilterType filterType, FilterUsageNotifier usageNotifier, FilterRunner<O, ?> nextStage,
            Consumer<? super I> asyncDetach, Consumer<? super O> asyncAttach) {
        this.usageNotifier = Preconditions.checkNotNull(usageNotifier, "filter usage notifier");
        this.nextStage = nextStage;
        this.RUNNING_FILTER_IDX_SESSION_CTX_KEY = filterType + "RunningFilterIndex";
        this.AWAITING_BODY_FLAG_SESSION_CTX_KEY = filterType + "IsAwaitingBody";
        this.asyncDetach = Objects.requireNonNull(asyncDetach, "asyncDetach");
        this.asyncAttach = Objects.requireNonNull(asyncAttach, "asyncAttach");
    }

    public static final ChannelHandlerContext getChannelHandlerContext(final ZuulMessage mesg) {
        return (ChannelHandlerContext) checkNotNull(mesg.getContext().get(NETTY_SERVER_CHANNEL_HANDLER_CONTEXT),
                "channel handler context");
    }

    public FilterRunner<O, ? extends ZuulMessage> getNextStage() {
        return nextStage;
    }

    protected final AtomicInteger initRunningFilterIndex(I zuulMesg) {
        final AtomicInteger idx = new AtomicInteger(0);
        zuulMesg.getContext().put(RUNNING_FILTER_IDX_SESSION_CTX_KEY, idx);
        return idx;
    }

    protected final AtomicInteger getRunningFilterIndex(I zuulMesg) {
        final SessionContext ctx = zuulMesg.getContext();
        return (AtomicInteger) Preconditions.checkNotNull(ctx.get(RUNNING_FILTER_IDX_SESSION_CTX_KEY), "runningFilterIndex");
    }

    protected final boolean isFilterAwaitingBody(I zuulMesg) {
        return zuulMesg.getContext().containsKey(AWAITING_BODY_FLAG_SESSION_CTX_KEY);
    }

    protected final void setFilterAwaitingBody(I zuulMesg, boolean flag) {
        if (flag) {
            zuulMesg.getContext().put(AWAITING_BODY_FLAG_SESSION_CTX_KEY, Boolean.TRUE);
        }
        else {
            zuulMesg.getContext().remove(AWAITING_BODY_FLAG_SESSION_CTX_KEY);
        }
    }

    protected final void invokeNextStage(final O zuulMesg, final HttpContent chunk) {
        if (nextStage != null) {
            try (TaskCloseable ignored =
                    traceTask(this, s -> s.getClass().getSimpleName() + ".invokeNextStageChunk")){
                addPerfMarkTags(zuulMesg);
                nextStage.filter(zuulMesg, chunk);
            }
        } else {
            //Next stage is Netty channel handler
            try (TaskCloseable ignored =
                    traceTask(this, s -> s.getClass().getSimpleName() + ".fireChannelReadChunk")) {
                addPerfMarkTags(zuulMesg);
                getChannelHandlerContext(zuulMesg).fireChannelRead(chunk);
            }
        }
    }

    protected final void invokeNextStage(final O zuulMesg) {
        if (nextStage != null) {
            try (TaskCloseable ignored =
                    traceTask(this, s -> s.getClass().getSimpleName() + ".invokeNextStage")) {
                addPerfMarkTags(zuulMesg);
                nextStage.filter(zuulMesg);
            }
        } else {
            //Next stage is Netty channel handler
            try (TaskCloseable ignored =
                    traceTask(this, s -> s.getClass().getSimpleName() + ".fireChannelRead")) {
                addPerfMarkTags(zuulMesg);
                getChannelHandlerContext(zuulMesg).fireChannelRead(zuulMesg);
            }
        }
    }

    protected final void addPerfMarkTags(ZuulMessage inMesg) {
        HttpRequestInfo req = null;
        if (inMesg instanceof HttpRequestInfo) {
            req = (HttpRequestInfo) inMesg;
        }
        if (inMesg instanceof HttpResponseMessage) {
            HttpResponseMessage msg = (HttpResponseMessage) inMesg;
            req = msg.getOutboundRequest();
            attachTag("statuscode", msg.getStatus());
        }
        if (req != null) {
            attachTag("path", req, HttpRequestInfo::getPath);
            attachTag("originalhost", req, HttpRequestInfo::getOriginalHost);
        }
        attachTag("uuid", inMesg, m -> m.getContext().getUUID());
    }

    protected final O filter(final ZuulFilter<I, O> filter, final I inMesg) {
        final long startTime = System.nanoTime();
        final ZuulMessage snapshot = inMesg.getContext().debugRouting() ? inMesg.clone() : null;
        boolean decrementConcurrency = false;
        try (TaskCloseable ignored = traceTask(filter, f -> f.filterName() + ".filter")) {
            addPerfMarkTags(inMesg);
            ExecutionStatus filterRunStatus = null;
            if (filter.filterType() == INBOUND && inMesg.getContext().shouldSendErrorResponse()) {
                // Pass request down the pipeline, all the way to error endpoint if error response needs to be generated
                filterRunStatus = SKIPPED;
            }

            ;
            try (TaskCloseable ignored2 = traceTask(filter, f -> f.filterName() + ".shouldSkipFilter")){
                if (shouldSkipFilter(inMesg, filter)) {
                    filterRunStatus = SKIPPED;
                }
            }

            if (filter.isDisabled()) {
                filterRunStatus = DISABLED;
            }

            if (filterRunStatus != null) {
                recordFilterCompletion(filterRunStatus, filter, startTime, inMesg, snapshot);
                return filter.getDefaultOutput(inMesg);
            }

            if (!isMessageBodyReadyForFilter(filter, inMesg)) {
                setFilterAwaitingBody(inMesg, true);
                logger.debug("Filter {} waiting for body, UUID {}", filter.filterName(), inMesg.getContext().getUUID());
                return null;  //wait for whole body to be buffered
            }
            setFilterAwaitingBody(inMesg, false);

            if (snapshot != null) {
                Debug.addRoutingDebug(inMesg.getContext(), "Filter " + filter.filterType().toString() + " " + filter.filterOrder() + " " + filter.filterName());
            }

            //run body contents accumulated so far through this filter
            inMesg.runBufferedBodyContentThroughFilter(filter);

            if (filter.getSyncType() == FilterSyncType.SYNC) {
                final SyncZuulFilter<I, O> syncFilter = (SyncZuulFilter) filter;
                final O outMesg;
                try (TaskCloseable ignored2 = traceTask(filter, f -> f.filterName() + ".apply")) {
                    addPerfMarkTags(inMesg);
                    outMesg = syncFilter.apply(inMesg);
                }
                recordFilterCompletion(SUCCESS, filter, startTime, inMesg, snapshot);
                return (outMesg != null) ? outMesg : filter.getDefaultOutput(inMesg);
            }

            Promise<O> promise;
            PerfMark.startTask(filter.filterName(), "filterAsync");
            try {
                EventExecutor exec = getChannelHandlerContext(inMesg).executor();
                promise = new TracingPromise<>(exec, filter.filterName());
                decrementConcurrency = true;
                filter.incrementConcurrency();
                filter.filterAsync(promise, inMesg);
            } finally {
                PerfMark.stopTask(filter.filterName(), "filterAsync");
            }

            // This is an optimization.  Some filters are async some of the time, but mostly sync (such as filters that
            // return cached results.
            if (promise.isSuccess()) {
                // we haven't added any listener yet, so it's safe to assume the filter methods.  We only handle
                // success here, and punt failures to the listener to simplify the implementation.
                decrementConcurrency = false;
                filter.decrementConcurrency();
                O outMesg = promise.getNow();
                recordFilterCompletion(SUCCESS, filter, startTime, inMesg, snapshot);
                return (outMesg != null) ? outMesg : filter.getDefaultOutput(inMesg);
            }
            asyncDetach.accept(inMesg);
            promise.addListener(new FilterChainResumer(inMesg, filter, snapshot, startTime));

            return null;  //wait for the async filter to finish
        } catch (Throwable t) {
            if (decrementConcurrency) {
                filter.decrementConcurrency();
            }
            final O outMesg = handleFilterException(inMesg, filter, t);
            outMesg.finishBufferedBodyIfIncomplete();
            recordFilterCompletion(FAILED, filter, startTime, inMesg, snapshot);
            return outMesg;
        }
    }

    /* This is typically set by a filter when wanting to reject a request and also reduce load on the server by
       not processing any more filterChain */
    protected final boolean shouldSkipFilter(final I inMesg, final ZuulFilter<I, O> filter) {
        if (filter.filterType() == ENDPOINT) {
            //Endpoints may not be skipped
            return false;
        }
        final SessionContext zuulCtx = inMesg.getContext();
        if ((zuulCtx.shouldStopFilterProcessing()) && (!filter.overrideStopFilterProcessing())) {
            return true;
        }
        if (zuulCtx.isCancelled()) {
            return true;
        }
        if (!filter.shouldFilter(inMesg)) {
            return true;
        }
        return false;
    }

    private boolean isMessageBodyReadyForFilter(final ZuulFilter<I, ?> filter, final I inMesg) {
        return ((!filter.needsBodyBuffered(inMesg)) || (inMesg.hasCompleteBody()));
    }

    protected O handleFilterException(final I inMesg, final ZuulFilter<I, O> filter, final Throwable ex) {
        inMesg.getContext().setError(ex);
        if (filter.filterType() == ENDPOINT) {
            inMesg.getContext().setShouldSendErrorResponse(true);
        }
        recordFilterError(inMesg, filter, ex);
        return filter.getDefaultOutput(inMesg);
    }

    protected void recordFilterError(final I inMesg, final ZuulFilter<I, O> filter, final Throwable t) {
        // Add a log statement for this exception.
        final String errorMsg = "Filter Exception: filter=" + filter.filterName() +
                ", request-info=" + inMesg.getInfoForLogging() + ", msg=" + String.valueOf(t.getMessage());
        if (t instanceof ZuulException && !((ZuulException) t).shouldLogAsError()) {
            logger.warn(errorMsg);
        }
        else {
            logger.error(errorMsg, t);
        }

        // Store this filter error for possible future use. But we still continue with next filter in the chain.
        final SessionContext zuulCtx = inMesg.getContext();
        zuulCtx.getFilterErrors().add(new FilterError(filter.filterName(), filter.filterType().toString(), t));
        if (zuulCtx.debugRouting()) {
            Debug.addRoutingDebug(zuulCtx, "Running Filter failed " + filter.filterName() + " type:" +
                    filter.filterType() + " order:" + filter.filterOrder() + " " + t.getMessage());
        }
    }

    protected void recordFilterCompletion(final ExecutionStatus status, final ZuulFilter<I, O> filter, long startTime,
                                          final ZuulMessage zuulMesg, final ZuulMessage startSnapshot) {

        final SessionContext zuulCtx = zuulMesg.getContext();
        final long execTimeNs = System.nanoTime() - startTime;
        final long execTimeMs = execTimeNs / 1_000_000L;
        if (execTimeMs >= FILTER_EXCESSIVE_EXEC_TIME.get()) {
            logger.warn("Filter {} took {} ms to complete! status = {}", filter.filterName(), execTimeMs, status.name());
        }

        // Record the execution summary in context.
        switch (status) {
            case FAILED:
                if (logger.isDebugEnabled()) {
                    zuulCtx.addFilterExecutionSummary(filter.filterName(), FAILED.name(), execTimeMs);
                }
                break;
            case SUCCESS:
                if (logger.isDebugEnabled()) {
                    zuulCtx.addFilterExecutionSummary(filter.filterName(), SUCCESS.name(), execTimeMs);
                }
                if (startSnapshot != null) {
                    //debugRouting == true
                    Debug.addRoutingDebug(zuulCtx, "Filter {" + filter.filterName() + " TYPE:" + filter.filterType().toString()
                            + " ORDER:" + filter.filterOrder() + "} Execution time = " + execTimeMs + "ms");
                    Debug.compareContextState(filter.filterName(), zuulCtx, startSnapshot.getContext());
                }
                break;
            default:
                break;
        }

        logger.debug("Filter {} completed with status {}, UUID {}", filter.filterName(), status.name(),
                zuulMesg.getContext().getUUID());
        // Notify configured listener.
        usageNotifier.notify(filter, status);
    }


    protected void handleException(final ZuulMessage zuulMesg, final String filterName, final Exception ex) {
        HttpRequestInfo zuulReq = null;
        if (zuulMesg instanceof HttpRequestMessage) {
            zuulReq = (HttpRequestMessage) zuulMesg;
        }
        else if (zuulMesg instanceof HttpResponseMessage) {
            zuulReq = ((HttpResponseMessage) zuulMesg).getInboundRequest();
        }
        final String path = (zuulReq != null) ? zuulReq.getPathAndQuery() : "-";
        final String method = (zuulReq != null) ? zuulReq.getMethod() : "-";
        final String errMesg = "Error with filter: " + filterName + ", path: " + path + ", method: " + method;
        logger.error(errMesg, ex);
        getChannelHandlerContext(zuulMesg).fireExceptionCaught(ex);
    }

    protected abstract void resume(O zuulMesg);

    protected MethodBinding<?> methodBinding(ZuulMessage zuulMesg) {
        return MethodBinding.NO_OP_BINDING;
    }

    protected void resumeInBindingContext(final O zuulMesg, final String filterName) {
        try {
            methodBinding(zuulMesg).bind(() -> resume(zuulMesg));
        }
        catch (Exception ex) {
            handleException(zuulMesg, filterName, ex);
        }
    }

    private final class FilterChainResumer implements FutureListener<O> {
        private final I inMsg;
        private final ZuulFilter<I, O> filter;
        @Nullable
        private ZuulMessage snapshot;
        private final long startTime;

        FilterChainResumer(I inMsg, ZuulFilter<I, O> filter, @Nullable ZuulMessage snapshot, long startTime) {
            this.inMsg = Objects.requireNonNull(inMsg, "inMsg");
            this.filter = Objects.requireNonNull(filter, "filter");
            this.snapshot = snapshot;
            this.startTime = startTime;
        }

        @Override
        public void operationComplete(Future<O> future) throws Exception {
            filter.decrementConcurrency();
            if (future.isSuccess()) {
                handleSuccess(future.getNow());
            } else {
                handleFailure(future.cause());
            }
        }

        private void handleSuccess(O outMsg) {
            try {
                recordFilterCompletion(SUCCESS, filter, startTime, inMsg, snapshot);
                if (outMsg == null) {
                    outMsg = filter.getDefaultOutput(inMsg);
                }
                asyncAttach.accept(outMsg);
                resumeInBindingContext(outMsg, filter.filterName());
            } catch (RuntimeException e) {
                handleException(inMsg, filter.filterName(), e);
            }

        }

        private void handleFailure(Throwable t) {
            try {
                recordFilterCompletion(FAILED, filter, startTime, inMsg, snapshot);
                final O outMsg = handleFilterException(inMsg, filter, t);
                asyncAttach.accept(outMsg);
                resumeInBindingContext(outMsg, filter.filterName());
            } catch (RuntimeException e) {
                handleException(inMsg, filter.filterName(), e);
            }
        }
    }

    private static final class TracingPromise<V> extends DefaultPromise<V>
            implements GenericFutureListener<TracingPromise<V>> {

        private final String filterName;
        private Link link;

        /**
         * This should be invoked while inside a PerfMark Task.
         */
        TracingPromise(EventExecutor exec, String filterName) {
            super(exec);
            this.filterName = filterName;
            this.link = PerfMark.linkOut();
            addListener(this);
        }

        @Override
        public boolean trySuccess(V result) {
            startDone("trySuccess");
            return super.trySuccess(result);
        }

        @Override
        public Promise<V> setFailure(Throwable cause) {
            startDone("setFailure");
            return super.setFailure(cause);
        }

        @Override
        public Promise<V> setSuccess(V result) {
            startDone("setSuccess");
            return super.setSuccess(result);
        }

        @Override
        public boolean tryFailure(Throwable cause) {
            startDone("tryFailure");
            return super.tryFailure(cause);
        }

        @Override
        public void operationComplete(TracingPromise<V> future) {
            PerfMark.startTask(filterName, "promiseComplete");
            PerfMark.linkIn(link);
            PerfMark.stopTask(filterName, "promiseComplete");
        }

        private void startDone(String name) {
            if (!executor().inEventLoop()) {
                PerfMark.startTask(filterName, name);
                PerfMark.linkIn(link);
                // Normally promises can be called from multiple places trying to set the result or cancel.
                // We avoid the risk by asking filters to not call the success/failure methods concurrently while
                // off the event loop.  Cancellation is expect to happen on the event loop so it would be unsafe
                // to trace it.  Instead, just accept less correct results and avoid the race.
                // This could be reasonably safe to allow the race with VarHandle's opaque mode.
                link = PerfMark.linkOut();
                PerfMark.stopTask(filterName, name);
            }
        }
    }
}
