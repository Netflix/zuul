/*
 *
 *  Copyright 2013-2015 Netflix, Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package com.netflix.zuul;

import com.netflix.config.DynamicStringProperty;
import com.netflix.servo.monitor.DynamicCounter;
import com.netflix.zuul.context.Debug;
import com.netflix.zuul.context.SessionContext;
import com.netflix.zuul.exception.ZuulException;
import com.netflix.zuul.filters.*;
import com.netflix.zuul.message.ZuulMessage;
import com.netflix.zuul.message.http.HttpRequestMessage;
import com.netflix.zuul.message.http.HttpResponseMessage;
import com.netflix.zuul.message.http.HttpResponseMessageImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.Iterator;

import static com.netflix.zuul.ExecutionStatus.*;

/**
 * This the the core class to execute filters.
 *
 * @author Mikey Cohen
 * @author Mike Smith
 */
@Singleton
public class FilterProcessorImpl implements FilterProcessor
{

    protected static final Logger LOG = LoggerFactory.getLogger(FilterProcessorImpl.class);

    /** The name of the error filter to use if none specified in the context. */
    protected static final DynamicStringProperty DEFAULT_ERROR_ENDPOINT = new DynamicStringProperty("zuul.filters.error.default", "endpoint.ErrorResponse");

    protected final FilterLoader filterLoader;
    protected final FilterUsageNotifier usageNotifier;

    @Inject
    public FilterProcessorImpl(FilterLoader loader, FilterUsageNotifier usageNotifier)
    {
        filterLoader = loader;
        this.usageNotifier = usageNotifier;
    }

    @Override
    public Observable<ZuulMessage> applyFilterChain(ZuulMessage msg)
    {
        Observable<ZuulMessage> chain = Observable.just(msg);

        chain = applyInboundFilters(msg, chain);
        chain = applyEndpointFilter(msg, chain);
        chain = applyOutboundFilters(msg, chain);

        return chain;
    }

    protected Observable<ZuulMessage> applyInboundFilters(final ZuulMessage message, final Observable<ZuulMessage> chain) {
        return applyFilters(chain, FilterType.INBOUND);
    }

    protected Observable<ZuulMessage> applyOutboundFilters(final ZuulMessage message, final Observable<ZuulMessage> chain) {
        return applyFilters(chain, FilterType.OUTBOUND);
    }

    private Observable<ZuulMessage> applyFilters(final Observable<ZuulMessage> chain, final FilterType type) {
        return chain.flatMap(msg -> {
            Iterator<ZuulFilter> filterIterator = filterLoader.getFiltersByType(type).iterator();
            return chainFilters(msg, filterIterator, type);
        });
    }


    protected Observable<ZuulMessage> chainFilters(ZuulMessage msg, final Iterator<ZuulFilter> filterChainIterator, FilterType phase)
    {
        if (! filterChainIterator.hasNext()) {
            // TODO - isn't there a better way of doing this without having to wrap in an Observable again?
            return Observable.just(msg);
        }

        // If an error filter has already generated a response, and this is the inbound phase, then
        // don't run any more filters in this phase.
        SessionContext context = msg.getContext();
        if (phase == FilterType.INBOUND && context.errorResponseSent()) {
            // Therefore this msg is already a response, so just return that.
            return Observable.just(msg);
        }

        HttpRequestMessage request = getRequestMessage(msg);

        // Check if error endpoint is needed.
        if (context.shouldSendErrorResponse()) {
            return applyErrorEndpoint(msg, request, filterChainIterator, phase);
        }

        // Get the next filter to apply from the chain.
        ZuulFilter filter = filterChainIterator.next();

        if (filter.getSyncType() == FilterSyncType.SYNC) {
            // Apply this filter.
            msg = processSyncFilter(msg, (SyncZuulFilter) filter, false);

            // Recurse to the next filter in chain.
            return chainFilters(msg, filterChainIterator, phase);
        }
        else {
            // TODO - find a way of avoiding wrapping with observable unless shouldFilter/ignore/etc have passed.
            // Apply this async filter.
            return processAsyncFilter(msg, filter, false)
                    .flatMap(msg2 -> {
                        // Recurse to the next filter in chain.
                        return chainFilters(msg2, filterChainIterator, phase);
                    });
        }
    }

    private Observable<ZuulMessage> applyErrorEndpoint(ZuulMessage msg, HttpRequestMessage request,
                                                       Iterator<ZuulFilter> filterChainIterator, FilterType phase)
    {
        // Get the error endpoint filter to use.
        ZuulFilter errorFilter = getErrorEndpoint(msg);
        if (errorFilter == null) {
            // No error filter to use, so send a basic default response.
            flagErrorSent(msg);
            return Observable.just(defaultErrorResponse(request));
        }

        if (errorFilter.getSyncType() == FilterSyncType.SYNC) {
            // After running the error filter reset the error flags on context.
            // This is to stop failures in error filters from turning into a recursive stack overflow.
            flagErrorSent(msg);
            msg = processSyncFilter(request, (SyncZuulFilter) errorFilter, false);

            // Recurse to the next filter in chain.
            return chainFilters(msg, filterChainIterator, phase);
        }
        else {
            // After running the error filter reset the error flags on context.
            // This is to stop failures in error filters from turning into a recursive stack overflow.
            flagErrorSent(msg);
            return processAsyncFilter(request, errorFilter, false)
                    .flatMap(msg2 -> {
                        // Recurse to the next filter in chain.
                        return chainFilters(msg2, filterChainIterator, phase);
                    });
        }
    }

    protected void flagErrorSent(ZuulMessage msg)
    {
        msg.getContext().setShouldSendErrorResponse(false);
        msg.getContext().setErrorResponseSent(true);
    }

    protected ZuulFilter getErrorEndpoint(ZuulMessage msg)
    {
        SessionContext context = msg.getContext();

        String endpointName = context.getErrorEndpoint();
        if (endpointName == null) {
            endpointName = DEFAULT_ERROR_ENDPOINT.get();
        }

        ZuulFilter errorEndpoint = filterLoader.getFilterByNameAndType(endpointName, FilterType.ENDPOINT);
        if (errorEndpoint == null) {
            String errorStr = "No error filter found of chosen name! name=" + endpointName;
            LOG.error("Errored but no error filter found, so sent default error response. " + errorStr, context.getError());
        }

        return errorEndpoint;
    }

    protected boolean isAResponseMessage(ZuulMessage msg)
    {
        return HttpResponseMessage.class.isAssignableFrom(msg.getClass());
    }

    protected boolean isEndpointFilter(ZuulFilter filter)
    {
        return Endpoint.class.isAssignableFrom(filter.getClass());
    }

    /**
     * Either get the request for this response (if msg is a response), or just return the msg if it is a request.
     *
     * @param msg
     * @return
     */
    protected HttpRequestMessage getRequestMessage(ZuulMessage msg)
    {
        if (HttpResponseMessage.class.isAssignableFrom(msg.getClass())) {
            return ((HttpResponseMessage) msg).getOutboundRequest();
        } else {
            return (HttpRequestMessage) msg;
        }
    }

    /**
     * Applies the selected Endpoint filter.
     *
     * If the input message is a request, then the returned message should be a response.
     *
     * @param chain
     * @return
     */
    public Observable<ZuulMessage> applyEndpointFilter(final ZuulMessage message, final Observable<ZuulMessage> chain)
    {
        return chain.flatMap(msg -> {

            SessionContext context = msg.getContext();

            // If an error filter has already generated a response, then don't run the endpoint.
            if (context.errorResponseSent()) {
                // Therefore this msg is already a response, so just return that.
                return Observable.just(msg);
            }

            HttpRequestMessage request = (HttpRequestMessage) msg;

            // Apply error filter instead if needed.
            if (context.shouldSendErrorResponse()) {
                // Pass an empty filterchain to applyErrorEndpoint, as no other filters to run after this one
                // in the endpoint phase.
                Iterator<ZuulFilter> filterChainIterator = new ArrayList<ZuulFilter>().iterator();
                return applyErrorEndpoint(msg, request, filterChainIterator, FilterType.ENDPOINT);
            }

            // If a static response has been set on the SessionContext, then just return that without attempting
            // to run any endpoint filter.
            HttpResponseMessage staticResponse = context.getStaticResponse();
            if (staticResponse != null) {
                return Observable.just(staticResponse);
            }

            // Get the previously chosen endpoint filter to use.
            String endpointName = context.getEndpoint();
            if (endpointName == null) {
                context.setShouldSendErrorResponse(true);
                context.setError(new ZuulException("No endpoint filter chosen!"));
                return Observable.just(defaultErrorResponse(request));
            }
            ZuulFilter endpointFilter = filterLoader.getFilterByNameAndType(endpointName,FilterType.ENDPOINT);
            if (endpointFilter == null) {
                context.setShouldSendErrorResponse(true);
                context.setError(new ZuulException("No endpoint filter found of chosen name! name=" + endpointName));
                return Observable.just(defaultErrorResponse(request));
            }

            // Apply this endpoint.
            return processAsyncFilter(msg, endpointFilter, true);
        });
    }

    private HttpResponseMessageImpl defaultErrorResponse(HttpRequestMessage request)
    {
        return new HttpResponseMessageImpl(request.getContext(), request, 500);
    }

    /**
     * Processes an individual ZuulFilter. This method adds Debug information. Any uncaught Throwables from filters
     * are caught by this method and stored for future insight on the SessionContext.getFilterErrors().
     *
     * @param msg ZuulMessage
     * @param filter IZuulFilter
     * @param shouldSendErrorResponse boolean
     * @return the return value for that filter
     */
    public Observable<ZuulMessage> processAsyncFilter(ZuulMessage msg, ZuulFilter filter, boolean shouldSendErrorResponse)
    {
        final FilterExecInfo info = new FilterExecInfo();

        handleDebug(filter, msg, info);

        // Apply this filter.
        Observable<ZuulMessage> resultObs;
        long ltime = System.currentTimeMillis();
        try {
            if (filter.isDisabled()) {
                resultObs = Observable.just(filter.getDefaultOutput(msg));
                info.status = ExecutionStatus.DISABLED;
            }
            else if (shouldSkipFilter(filter, msg)) {
                // Skip this filter.
                // This is typically set by a filter when wanting to reject a request, and also reduce load on the server by
                // not processing any more filters.
                resultObs = Observable.just(filter.getDefaultOutput(msg));
                info.status = ExecutionStatus.SKIPPED;
            }
            else {
                // Only apply the filter if the shouldFilter() method is true.
                ZuulMessage input = chooseFilterInput(filter, msg);
                if (filter.shouldFilter(input)) {
                    resultObs = filter.applyAsync(input).single();
                } else {
                    resultObs = Observable.just(filter.getDefaultOutput(msg));
                    info.status = ExecutionStatus.SKIPPED;
                }
            }
        }
        catch (Throwable e) {
            msg.getContext().setError(e);
            if (shouldSendErrorResponse) msg.getContext().setShouldSendErrorResponse(true);
            resultObs = Observable.just(filter.getDefaultOutput(msg));
            info.status = ExecutionStatus.FAILED;
            recordFilterError(filter, msg, e);
        }

        // Handle errors from the filter. Don't break out of the filter chain - instead just record info about the error
        // in context, and continue.
        resultObs = resultObs.onErrorReturn((e) -> {
            return handleFilterException(filter, msg, shouldSendErrorResponse, info, e);
        });

        // If no resultContext returned from filter, then use the original context.
        resultObs = resultObs.map((msg2) -> {
            ZuulMessage newMsg;
            if (msg2 == null) {
                newMsg = filter.getDefaultOutput(msg);
            }
            else {
                newMsg = msg2;
            }
            return newMsg;
        });

        // Record info when filter processing completes.
        resultObs = resultObs.doOnNext((msg1) -> {
            recordFilterCompletion(msg1, filter, info, ltime);
        });

        return resultObs;
    }


    /**
     * TODO - find a way to remove some of the duplication between this method and processAsyncFilter().
     *
     * @param msg
     * @param filter
     * @return
     */
    public ZuulMessage processSyncFilter(ZuulMessage msg, SyncZuulFilter filter, boolean shouldSendErrorResponse)
    {
        final FilterExecInfo info = new FilterExecInfo();

        handleDebug(filter, msg, info);

        // Apply this filter.
        ZuulMessage result;
        long ltime = System.currentTimeMillis();
        try {
            if (filter.isDisabled()) {
                result = filter.getDefaultOutput(msg);
                info.status = DISABLED;
            }
            else if (shouldSkipFilter(filter, msg)) {
                // Skip this filter.
                // This is typically set by a filter when wanting to reject a request, and also reduce load on the server by
                // not processing any more filters.
                result = filter.getDefaultOutput(msg);
                info.status = SKIPPED;
            }
            else {
                // Only apply the filter if the shouldFilter() is true.
                ZuulMessage input = chooseFilterInput(filter, msg);
                if (filter.shouldFilter(input)) {
                    result = filter.apply(input);

                    // If no result returned from filter, then use the original input.
                    if (result == null) {
                        result = filter.getDefaultOutput(msg);
                    }
                }
                else {
                    result = filter.getDefaultOutput(msg);
                    info.status = SKIPPED;
                }
            }
        }
        catch (Throwable e) {
            result = handleFilterException(filter, msg, shouldSendErrorResponse, info, e);
        }

        // Record info when filter processing completes.
        recordFilterCompletion(result, filter, info, ltime);

        return result;
    }

    protected void handleDebug(ZuulFilter filter, ZuulMessage msg, FilterExecInfo info)
    {
        info.bDebug = msg.getContext().debugRouting();
        if (info.bDebug) {
            Debug.addRoutingDebug(msg.getContext(), "Filter " + filter.filterType().toString() + " " + filter.filterOrder() + " " + filter.filterName());
            info.debugCopy = msg.clone();
        }
    }

    protected boolean shouldSkipFilter(ZuulFilter filter, ZuulMessage msg)
    {
        return msg.getContext().shouldStopFilterProcessing()
                && ! filter.overrideStopFilterProcessing()
                && ! isEndpointFilter(filter);
    }

    protected ZuulMessage handleFilterException(ZuulFilter filter, ZuulMessage msg, boolean shouldSendErrorResponse, FilterExecInfo info, Throwable e)
    {
        msg.getContext().setError(e);
        if (shouldSendErrorResponse) msg.getContext().setShouldSendErrorResponse(true);
        info.status = FAILED;
        recordFilterError(filter, msg, e);
        return filter.getDefaultOutput(msg);
    }

    protected ZuulMessage chooseFilterInput(ZuulFilter filter, ZuulMessage msg)
    {
        switch (filter.filterType()) {
        case INBOUND:
            if (isAResponseMessage(msg))
                return getRequestMessage(msg);
            else
                return msg;

        case OUTBOUND:
            if (isAResponseMessage(msg))
                return msg;
            else
                throw new IllegalArgumentException("Invalid input message type for outbound filter! " +
                        "filter=" + String.valueOf(filter) + " msg-type=" + msg.getClass() + ", msg=" + msg.getInfoForLogging());

        case ENDPOINT:
            if (isAResponseMessage(msg))
                return getRequestMessage(msg);
            else
                return msg;

        default:
            throw new IllegalArgumentException("Unknown filterType! filter=" + String.valueOf(filter));
        }
    }

    protected void recordFilterCompletion(ZuulMessage msg, ZuulFilter filter, FilterExecInfo info, long startTime)
    {
        if (info.status == null) {
            info.status = ExecutionStatus.SUCCESS;
        }
        info.execTime = System.currentTimeMillis() - startTime;

        // Record the execution summary in context.
        switch (info.status) {
            case FAILED:
                msg.getContext().addFilterExecutionSummary(filter.filterName(), ExecutionStatus.FAILED.name(), info.execTime);
                break;
            case SUCCESS:
                msg.getContext().addFilterExecutionSummary(filter.filterName(), ExecutionStatus.SUCCESS.name(), info.execTime);
                if (info.bDebug) {
                    Debug.addRoutingDebug(msg.getContext(), "Filter {" + filter.filterName() + " TYPE:" + filter.filterType().toString()
                            + " ORDER:" + filter.filterOrder() + "} Execution time = " + info.execTime + "ms");
                    Debug.compareContextState(filter.filterName(), msg.getContext(), info.debugCopy.getContext());
                }
                break;
            default:
                break;
        }

        // Notify configured listener.
        usageNotifier.notify(filter, info.status);
    }

    /**
     * Log the throwable, and also store info about it in the SessionContext for future reference.
     *
     * @param filter
     * @param msg
     * @param e
     */
    protected void recordFilterError(ZuulFilter filter, ZuulMessage msg, Throwable e)
    {
        // Add a log statement for this exception.
        String errorMsg = "Filter Exception: filter=" + String.valueOf(filter) + ", request-info=" + msg.getInfoForLogging() + ", msg=" + String.valueOf(e.getMessage());
        if (e instanceof ZuulException && ! ((ZuulException)e).shouldLogAsError()) {
            LOG.warn(errorMsg);
        } else {
            LOG.error(errorMsg, e);
        }

        // Store this filter error for possible future use. But we still continue with next filter in the chain.
        msg.getContext().getFilterErrors().add(new FilterError(filter.filterName(), filter.filterType().toString(), e));

        boolean bDebug = msg.getContext().debugRouting();
        if (bDebug) {
            Debug.addRoutingDebug(msg.getContext(), "Running Filter failed " + filter.filterName() + " type:" + filter.filterType() + " order:" + filter.filterOrder() + " " + e.getMessage());
        }
    }

    /**
     * Publishes a counter metric for each filter on each use.
     */
    public static class BasicFilterUsageNotifier implements FilterUsageNotifier {
        private static final String METRIC_PREFIX = "zuul.filter-";

        @Override
        public void notify(ZuulFilter filter, ExecutionStatus status) {
            DynamicCounter.increment(METRIC_PREFIX + filter.getClass().getSimpleName(), "status", status.name(), "filtertype", filter.filterType().toString());
        }
    }

    static class FilterExecInfo
    {
        ExecutionStatus status;
        boolean bDebug = false;
        long execTime;
        ZuulMessage debugCopy = null;
    }
}
