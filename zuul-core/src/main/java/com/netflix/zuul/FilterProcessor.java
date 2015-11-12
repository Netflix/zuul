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

import com.netflix.config.DynamicPropertyFactory;
import com.netflix.config.DynamicStringProperty;
import com.netflix.servo.monitor.DynamicCounter;
import com.netflix.zuul.context.Debug;
import com.netflix.zuul.context.SessionContext;
import com.netflix.zuul.exception.ZuulException;
import com.netflix.zuul.filters.FilterError;
import com.netflix.zuul.filters.ZuulFilter;
import com.netflix.zuul.message.ZuulMessage;
import com.netflix.zuul.message.http.HttpRequestMessage;
import com.netflix.zuul.message.http.HttpResponseMessage;
import com.netflix.zuul.message.http.HttpResponseMessageImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;

/**
 * This the the core class to execute filters.
 *
 * @author Mikey Cohen
 * @author Mike Smith
 */
@Singleton
public class FilterProcessor {

    protected static final Logger LOG = LoggerFactory.getLogger(FilterProcessor.class);

    /** The name of the error filter to use if none specified in the context. */
    protected static final DynamicStringProperty DEFAULT_ERROR_ENDPOINT = DynamicPropertyFactory.getInstance()
            .getStringProperty("zuul.filters.error.default", "endpoint.ErrorResponse");

    protected final FilterLoader filterLoader;
    protected final FilterUsageNotifier usageNotifier;

    @Inject
    public FilterProcessor(FilterLoader loader, FilterUsageNotifier usageNotifier)
    {
        filterLoader = loader;
        this.usageNotifier = usageNotifier;
    }

    public Observable<ZuulMessage> applyFilterChain(ZuulMessage msg)
    {
        // Create initial chain.
        Observable<ZuulMessage> chain = Observable.just(msg);

        chain = applyInboundFilters(chain);
        chain = applyEndpointFilter(chain);
        chain = applyOutboundFilters(chain);

        return chain;
    }

    public Observable<ZuulMessage> applyInboundFilters(Observable<ZuulMessage> chain)
    {
        chain = applyFilterPhase(chain, "in", false);

        chain = applyErrorEndpointIfNeeded(chain);

        return chain;
    }

    public Observable<ZuulMessage> applyErrorEndpointIfNeeded(Observable<ZuulMessage> chain)
    {
        return chain.flatMap(msg -> {

            // If flagged to, then apply the error endpoint filter.
            SessionContext context = msg.getContext();
            if (context.shouldSendErrorResponse()) {

                // Un-flag this as needing the error filter.
                context.setShouldSendErrorResponse(false);
                context.setErrorResponseSent(true);

                // Pull out the request object to use for building a new response if needed.
                HttpRequestMessage request;
                if (HttpResponseMessage.class.isAssignableFrom(msg.getClass())) {
                    request = ((HttpResponseMessage) msg).getOutboundRequest();
                } else {
                    request = (HttpRequestMessage) msg;
                }

                // Get the error endpoint filter to use.
                String endpointName = context.getErrorEndpoint();
                if (endpointName == null) {
                    endpointName = DEFAULT_ERROR_ENDPOINT.get();
                }
                ZuulFilter endpointFilter = filterLoader.getFilterByNameAndType(endpointName, "end");
                if (endpointFilter == null) {
                    // No error filter to use, so send a basic default response.
                    String errorStr = "No error filter found of chosen name! name=" + endpointName;
                    LOG.error("Errored but no error filter found, so sent default error response. " + errorStr, context.getError());

                    // Build the error response.
                    HttpResponseMessage response = defaultErrorResponse(request);

                    return Observable.just(response);
                }

                // Apply this endpoint.
                if (HttpResponseMessage.class.isAssignableFrom(msg.getClass())) {
                    // if msg is a response, then we need to get it's request to pass to the error filter.
                    HttpResponseMessage response = (HttpResponseMessage) msg;
                    return processAsyncFilter(response.getOutboundRequest(), endpointFilter, false);
                }
                else {
                    return processAsyncFilter(msg, endpointFilter, false);
                }
            }
            else {
                // Do nothing.
                return Observable.just(msg);
            }
        });
    }

    /**
     * Applies the selected Endpoint filter.
     *
     * If the input message is a request, then the returned message should be a response.
     *
     * @param chain
     * @return
     */
    public Observable<ZuulMessage> applyEndpointFilter(Observable<ZuulMessage> chain)
    {
        chain = chain.flatMap(msg -> {

            SessionContext context = msg.getContext();

            // If an error filter has already generated a response, then don't run the endpoint.
            if (context.errorResponseSent()) {
                // Therefore this msg is already a response, so just return that.
                return Observable.just(msg);
            }

            HttpRequestMessage request = (HttpRequestMessage) msg;

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
            ZuulFilter endpointFilter = filterLoader.getFilterByNameAndType(endpointName, "end");
            if (endpointFilter == null) {
                context.setShouldSendErrorResponse(true);
                context.setError(new ZuulException("No endpoint filter found of chosen name! name=" + endpointName));
                return Observable.just(defaultErrorResponse(request));
            }

            // Apply this endpoint.
            return processAsyncFilter(msg, endpointFilter, true);
        });

        // Apply the error filters AGAIN. This is for if there was an error during the endpoint phase.
        chain = applyErrorEndpointIfNeeded(chain);

        return chain;
    }

    private HttpResponseMessageImpl defaultErrorResponse(HttpRequestMessage request)
    {
        return new HttpResponseMessageImpl(request.getContext(), request, 500);
    }

    public Observable<ZuulMessage> applyOutboundFilters(Observable<ZuulMessage> chain)
    {
        // Apply POST filters.
        chain = applyFilterPhase(chain, "out", false);

        // And apply one last time to catch any errors from outbound filters.
        chain = applyErrorEndpointIfNeeded(chain);

        return chain;
    }

    protected Observable<ZuulMessage> applyFilterPhase(Observable<ZuulMessage> chain, String filterType, boolean shouldSendErrorResponse)
    {
        List<ZuulFilter> filters = filterLoader.getFiltersByType(filterType);
        for (ZuulFilter filter: filters) {
            chain = processFilterAsObservable(chain, filter, shouldSendErrorResponse).single();
        }
        return chain;
    }

    public Observable<ZuulMessage> processFilterAsObservable(Observable<ZuulMessage> input, ZuulFilter filter, boolean shouldSendErrorResponse)
    {
        return input.flatMap(msg -> processAsyncFilter(msg, filter, shouldSendErrorResponse));
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
        info.bDebug = msg.getContext().debugRouting();

        if (info.bDebug) {
            Debug.addRoutingDebug(msg.getContext(), "Filter " + filter.filterType() + " " + filter.filterOrder() + " " + filter.filterName());
            info.debugCopy = msg.clone();
        }

        // Apply this filter.
        Observable<ZuulMessage> resultObs;
        long ltime = System.currentTimeMillis();
        try {
            if (filter.isDisabled()) {
                resultObs = Observable.just(filter.getDefaultOutput(msg));
                info.status = ExecutionStatus.DISABLED;
            }
            else if (msg.getContext().shouldStopFilterProcessing()) {
                // This is typically set by a filter when wanting to reject a request, and also reduce load on the server by
                // not processing any more filters.
                resultObs = Observable.just(filter.getDefaultOutput(msg));
                info.status = ExecutionStatus.SKIPPED;
            }
            else {
                // Only apply the filter if both the shouldFilter() method AND the filter has a priority of
                // equal or above the requested.
                int requiredPriority = msg.getContext().getFilterPriorityToApply();
                if (isFilterPriority(filter, requiredPriority) && filter.shouldFilter(msg)) {
                    resultObs = filter.applyAsync(msg).single();
                } else {
                    resultObs = Observable.just(filter.getDefaultOutput(msg));
                    info.status = ExecutionStatus.SKIPPED;
                }
            }
        }
        catch (Exception e) {
            msg.getContext().setError(e);
            if (shouldSendErrorResponse) msg.getContext().setShouldSendErrorResponse(true);
            resultObs = Observable.just(filter.getDefaultOutput(msg));
            info.status = ExecutionStatus.FAILED;
            recordFilterError(filter, msg, e);
        }

        // Handle errors from the filter. Don't break out of the filter chain - instead just record info about the error
        // in context, and continue.
        resultObs = resultObs.onErrorReturn((e) -> {
            msg.getContext().setError(e);
            if (shouldSendErrorResponse) msg.getContext().setShouldSendErrorResponse(true);
            info.status = ExecutionStatus.FAILED;
            recordFilterError(filter, msg, e);
            return filter.getDefaultOutput(msg);
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
            if (info.status == null) {
                info.status = ExecutionStatus.SUCCESS;
            }
            info.execTime = System.currentTimeMillis() - ltime;
            recordFilterCompletion(msg1, filter, info);
        });

        return resultObs;
    }

    protected boolean isFilterPriority(ZuulFilter filter, int requiredPriority)
    {
        return filter.getPriority() >= requiredPriority;
    }

    protected void recordFilterCompletion(ZuulMessage msg, ZuulFilter filter, FilterExecInfo info)
    {
        // Record the execution summary in context.
        switch (info.status) {
            case FAILED:
                msg.getContext().addFilterExecutionSummary(filter.filterName(), ExecutionStatus.FAILED.name(), info.execTime);
                break;
            case SUCCESS:
                msg.getContext().addFilterExecutionSummary(filter.filterName(), ExecutionStatus.SUCCESS.name(), info.execTime);
                if (info.bDebug) {
                    Debug.addRoutingDebug(msg.getContext(), "Filter {" + filter.filterName() + " TYPE:" + filter.filterType()
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
        msg.getContext().getFilterErrors().add(new FilterError(filter.filterName(), filter.filterType(), e));

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
            DynamicCounter.increment(METRIC_PREFIX + filter.getClass().getSimpleName(), "status", status.name(), "filtertype", filter.filterType());
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
