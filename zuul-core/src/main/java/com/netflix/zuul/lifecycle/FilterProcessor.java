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

public class FilterProcessor<State> {

    private final Logger logger = LoggerFactory.getLogger(FilterProcessor.class);

    private final FilterStore<State> filterStore;
    private final FilterStateFactory<State> stateFactory;

    public FilterProcessor(FilterStore<State> filterStore, FilterStateFactory<State> stateFactory) {
        this.filterStore = filterStore;
        this.stateFactory = stateFactory;
    }

    public Observable<EgressResponse<State>> applyAllFilters(IngressRequest ingressReq) {
        final long startTime = System.currentTimeMillis();
        try {
            FiltersForRoute<State> filtersForRoute = filterStore.getFilters(ingressReq);
            return applyPreFilters(ingressReq, filtersForRoute.getPreFilters()).flatMap(egressReq ->
                    applyRoutingFilter(egressReq, filtersForRoute.getRouteFilter())).flatMap(ingressResp ->
                    applyPostFilters(ingressResp, filtersForRoute.getPostFilters())).doOnCompleted(() ->
                        ZuulMetrics.markSuccess(ingressReq, System.currentTimeMillis() - startTime)
                    ).onErrorResumeNext(ex -> {
                        ZuulMetrics.markError(ingressReq, System.currentTimeMillis() - startTime);
                        return applyErrorFilter(ex, ingressReq, filtersForRoute.getErrorFilter());
                    }).doOnNext(egressResp -> recordHttpStatusCode(ingressReq, egressResp.getStatus().code(), startTime));
        } catch (IOException ioe) {
            logger.error("Couldn't load the filters");
            return Observable.error(new ZuulException("Could not load filters"));
        }
    }

    private Observable<EgressRequest<State>> applyPreFilters(IngressRequest ingressReq, List<PreFilter<State>> preFilters) {
        Observable<EgressRequest<State>> initialEgressReqObservable = Observable.just(EgressRequest.copiedFrom(ingressReq, stateFactory.create()));
        return Observable.from(preFilters).reduce(initialEgressReqObservable, (egressReqObservable, preFilter) -> {
            return preFilter.execute(egressReqObservable);
        }).flatMap(o -> o);
    }

    private Observable<IngressResponse> applyRoutingFilter(EgressRequest<State> egressReq, RouteFilter<State> routeFilter) {
        if (routeFilter == null) {
            return Observable.<IngressResponse>error(new ZuulException("You must define a RouteFilter."));
        } else {
            return routeFilter.execute(egressReq);
        }
    }

    private Observable<EgressResponse<State>> applyPostFilters(IngressResponse<State> ingressResp, List<PostFilter<State>> postFilters) {
        Observable<EgressResponse<State>> initialEgressRespObservable = Observable.just(EgressResponse.from(ingressResp));
        return Observable.from(postFilters).reduce(initialEgressRespObservable, (egressRespObservable, postFilter) -> {
            return postFilter.execute(egressRespObservable);
        }).flatMap(o -> o);
    }

    private Observable<EgressResponse<State>> applyErrorFilter(Throwable ex, IngressRequest ingressReq, ErrorFilter<State> errorFilter) {
        if (errorFilter == null) {
            return Observable.<EgressResponse<State>>error(new ZuulException("Unhandled exception: " + ex.getMessage(), ex));
        } else {
            return errorFilter.execute(ex, ingressReq);
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
