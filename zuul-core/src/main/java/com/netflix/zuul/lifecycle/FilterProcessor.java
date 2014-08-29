/**
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.zuul.lifecycle;

import com.netflix.zuul.ZuulException;
import com.netflix.zuul.filter.ErrorFilter;
import com.netflix.zuul.filter.PostFilter;
import com.netflix.zuul.filter.PreFilter;
import com.netflix.zuul.filter.RouteFilter;
import com.netflix.zuul.filterstore.FilterStore;
import com.netflix.zuul.metrics.ZuulMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;

import java.io.IOException;
import java.util.List;

public class FilterProcessor<Request, Response> {

    private final Logger logger = LoggerFactory.getLogger(FilterProcessor.class);

    private final FilterStore<Request, Response> filterStore;
    private final FilterStateFactory<Request> requestState; 
    private final FilterStateFactory<Response> responseState;

    public FilterProcessor(FilterStore<Request, Response> filterStore, FilterStateFactory<Request> requestState, FilterStateFactory<Response> responseState) {
        this.filterStore = filterStore;
        this.requestState = requestState;
        this.responseState = responseState;
    }

    public Observable<EgressResponse<Response>> applyAllFilters(IngressRequest ingressReq) {
        final long startTime = System.currentTimeMillis();
        try {
            FiltersForRoute<Request, Response> filtersForRoute = filterStore.getFilters(ingressReq);
            return applyPreFilters(ingressReq, filtersForRoute.getPreFilters()).flatMap(egressReq ->
                    applyRoutingFilter(egressReq, filtersForRoute.getRouteFilter())).flatMap(ingressResp ->
                    applyPostFilters(ingressResp, filtersForRoute.getPostFilters())).doOnCompleted(() ->
                        ZuulMetrics.markSuccess(ingressReq, System.currentTimeMillis() - startTime)
                    ).onErrorResumeNext(
                    ex -> {
                        ZuulMetrics.markError(ingressReq, System.currentTimeMillis() - startTime);
                        return applyErrorFilter(ex, filtersForRoute.getErrorFilter());
                    }).doOnNext(egressResp -> recordHttpStatusCode(ingressReq, egressResp.getStatus().code(), startTime));
        } catch (IOException ioe) {
            logger.error("Couldn't load the filters");
            return Observable.error(new ZuulException("Could not load filters"));
        }
    }

    private Observable<EgressRequest<Request>> applyPreFilters(IngressRequest ingressReq, List<PreFilter<Request>> preFilters) {
        Observable<EgressRequest<Request>> initialEgressReqObservable = Observable.just(EgressRequest.copiedFrom(ingressReq, requestState.create()));
        return Observable.from(preFilters).reduce(initialEgressReqObservable, (egressReqObservable, preFilter) -> {
            return preFilter.execute(egressReqObservable);
        }).flatMap(o -> o);
    }

    private Observable<IngressResponse> applyRoutingFilter(EgressRequest<Request> egressReq, RouteFilter<Request> routeFilter) {
        if (routeFilter == null) {
            return Observable.<IngressResponse>error(new ZuulException("You must define a RouteFilter."));
        } else {
            return routeFilter.execute(egressReq);
        }
    }

    private Observable<EgressResponse<Response>> applyPostFilters(IngressResponse ingressResp, List<PostFilter<Response>> postFilters) {
        Observable<EgressResponse<Response>> initialEgressRespObservable = Observable.just(EgressResponse.from(ingressResp, responseState.create()));
        return Observable.from(postFilters).reduce(initialEgressRespObservable, (egressRespObservable, postFilter) -> {
            return postFilter.execute(egressRespObservable);
        }).flatMap(o -> o);
    }

    private Observable<EgressResponse<Response>> applyErrorFilter(Throwable ex, ErrorFilter<Response> errorFilter) {
        if (errorFilter == null) {
            return Observable.<EgressResponse<Response>>error(new ZuulException("Unhandled exception: " + ex.getMessage()));
        } else {
            return errorFilter.execute(ex);
        }
    }

    private static void recordHttpStatusCode(IngressRequest ingressReq, int statusCode, long startTime) {
        if (statusCode >= 100 && statusCode < 200) {
            ZuulMetrics.mark1xx(ingressReq, System.currentTimeMillis() - startTime);
        } else if (statusCode >= 200 && statusCode < 300) {
            ZuulMetrics.mark2xx(ingressReq, System.currentTimeMillis() - startTime);
        } else if (statusCode >= 300 && statusCode < 400) {
            ZuulMetrics.mark3xx(ingressReq, System.currentTimeMillis() - startTime);
        } else if (statusCode >= 400 && statusCode < 500) {
            ZuulMetrics.mark4xx(ingressReq, System.currentTimeMillis() - startTime);
        } else if (statusCode >= 500 && statusCode < 600) {
            ZuulMetrics.mark5xx(ingressReq, System.currentTimeMillis() - startTime);
        }
    }
}
