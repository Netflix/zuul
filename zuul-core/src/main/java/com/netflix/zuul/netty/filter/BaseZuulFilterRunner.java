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
import io.perfmark.Link;
import io.perfmark.TaskCloseable;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observer;
import rx.functions.Action0;
import rx.functions.Action1;
import rx.schedulers.Schedulers;

import javax.annotation.concurrent.ThreadSafe;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.netflix.zuul.ExecutionStatus.DISABLED;
import static com.netflix.zuul.ExecutionStatus.FAILED;
import static com.netflix.zuul.ExecutionStatus.SKIPPED;
import static com.netflix.zuul.ExecutionStatus.SUCCESS;
import static com.netflix.zuul.context.CommonContextKeys.NETTY_SERVER_CHANNEL_HANDLER_CONTEXT;
import static com.netflix.zuul.filters.FilterType.ENDPOINT;
import static com.netflix.zuul.filters.FilterType.INBOUND;
import static io.perfmark.PerfMark.attachTag;
import static io.perfmark.PerfMark.linkIn;
import static io.perfmark.PerfMark.linkOut;
import static io.perfmark.PerfMark.traceTask;

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


    protected BaseZuulFilterRunner(FilterType filterType, FilterUsageNotifier usageNotifier, FilterRunner<O, ?> nextStage) {
        this.usageNotifier = Preconditions.checkNotNull(usageNotifier, "filter usage notifier");
        this.nextStage = nextStage;
        this.RUNNING_FILTER_IDX_SESSION_CTX_KEY = filterType + "RunningFilterIndex";
        this.AWAITING_BODY_FLAG_SESSION_CTX_KEY = filterType + "IsAwaitingBody";
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
        FilterChainResumer resumer = null;

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

            // async filter
            try (TaskCloseable ignored2 = traceTask(filter, f -> f.filterName() + ".applyAsync")){
                final Link nettyToSchedulerLink = linkOut();
                filter.incrementConcurrency();
                resumer = new FilterChainResumer(inMesg, filter, snapshot, startTime);
                filter.applyAsync(inMesg)
                        .doOnSubscribe(() -> {
                            try (TaskCloseable ignored3 =
                                    traceTask(filter, f -> f.filterName() + ".onSubscribeAsync")) {
                                linkIn(nettyToSchedulerLink);
                            }
                        })
                        .doOnNext(resumer.onNextStarted(nettyToSchedulerLink))
                        .doOnError(resumer.onErrorStarted(nettyToSchedulerLink))
                        .doOnCompleted(resumer.onCompletedStarted(nettyToSchedulerLink))
                        .observeOn(Schedulers.from(getChannelHandlerContext(inMesg).executor()))
                        .doOnUnsubscribe(resumer::decrementConcurrency)
                        .subscribe(resumer);
            }

            return null;  //wait for the async filter to finish
        }
        catch (Throwable t) {
            if (resumer != null) {
                resumer.decrementConcurrency();
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


    private boolean isMessageBodyReadyForFilter(final ZuulFilter filter, final I inMesg) {
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

    private final class FilterChainResumer implements Observer<O> {
        private final I inMesg;
        private final ZuulFilter<I, O> filter;
        private final long startTime;
        private ZuulMessage snapshot;
        private AtomicBoolean concurrencyDecremented;

        private final AtomicReference<Link> onNextLinkOut = new AtomicReference<>();
        private final AtomicReference<Link> onErrorLinkOut = new AtomicReference<>();
        private final AtomicReference<Link> onCompletedLinkOut = new AtomicReference<>();

        public FilterChainResumer(
                I inMesg, ZuulFilter<I, O> filter, ZuulMessage snapshot, long startTime) {
            this.inMesg = Preconditions.checkNotNull(inMesg, "input message");
            this.filter = Preconditions.checkNotNull(filter, "filter");
            this.snapshot = snapshot;
            this.startTime = startTime;
            this.concurrencyDecremented = new AtomicBoolean(false);
        }

        void decrementConcurrency() {
            if (concurrencyDecremented.compareAndSet(false, true)) {
                filter.decrementConcurrency();
            }
        }

        @Override
        public void onNext(O outMesg) {
            try (TaskCloseable ignored = traceTask(filter, f -> f.filterName() + ".onNextAsync")) {
                linkIn(onNextLinkOut.get());
                addPerfMarkTags(inMesg);
                recordFilterCompletion(SUCCESS, filter, startTime, inMesg, snapshot);
                if (outMesg == null) {
                    outMesg = filter.getDefaultOutput(inMesg);
                }
                resumeInBindingContext(outMesg, filter.filterName());
            }
            catch (Exception e) {
                decrementConcurrency();
                handleException(inMesg, filter.filterName(), e);
            }
        }

        @Override
        public void onError(Throwable ex) {
            try (TaskCloseable ignored = traceTask(filter, f -> f.filterName() + ".onErrorAsync")) {
                linkIn(onErrorLinkOut.get());
                decrementConcurrency();
                recordFilterCompletion(FAILED, filter, startTime, inMesg, snapshot);
                final O outMesg = handleFilterException(inMesg, filter, ex);
                resumeInBindingContext(outMesg, filter.filterName());
            }
            catch (Exception e) {
                handleException(inMesg, filter.filterName(), e);
            }
        }

        @Override
        public void onCompleted() {
            try (TaskCloseable ignored = traceTask(filter, f -> f.filterName() + ".onCompletedAsync")) {
                linkIn(onCompletedLinkOut.get());
                decrementConcurrency();
            }
        }

        private Action1<O> onNextStarted(Link onNextLinkIn) {
            return o -> {
                try (TaskCloseable ignored = traceTask(filter, f -> f.filterName() + ".onNext")) {
                    linkIn(onNextLinkIn);
                    onNextLinkOut.compareAndSet(null, linkOut());
                }
            };
        }

        private Action1<Throwable> onErrorStarted(Link onErrorLinkIn) {
            return t -> {
                try (TaskCloseable ignored = traceTask(filter, f -> f.filterName() + ".onError")) {
                    linkIn(onErrorLinkIn);
                    onErrorLinkOut.compareAndSet(null, linkOut());
                }
            };
        }

        private Action0 onCompletedStarted(Link onCompletedLinkIn) {
            return () -> {
                try (TaskCloseable ignored = traceTask(filter, f -> f.filterName() + ".onCompleted")) {
                    linkIn(onCompletedLinkIn);
                    onCompletedLinkOut.compareAndSet(null, linkOut());
                }
            };
        }
    }

}
