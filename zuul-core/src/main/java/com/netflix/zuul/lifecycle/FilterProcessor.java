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
            EgressRequest<State> initialEgressReq = EgressRequest.copiedFrom(ingressReq, stateFactory.create());
            Observable<EgressRequest<State>> egressReq = applyPreFilters(initialEgressReq, filtersForRoute.getPreFilters());
            Observable<IngressResponse<State>> ingressResp = applyRouteFilter(egressReq, filtersForRoute.getRouteFilter());
            Observable<EgressResponse<State>> initialEgressResp = ingressResp.map(EgressResponse::from);
            Observable<EgressResponse<State>> egressResp = applyPostFilters(initialEgressResp, filtersForRoute.getPostFilters());
            return egressResp.doOnCompleted(() ->
                ZuulMetrics.markSuccess(ingressReq, System.currentTimeMillis() - startTime)
            ).onErrorResumeNext(ex -> {
                ZuulMetrics.markError(ingressReq, System.currentTimeMillis() - startTime);
                return applyErrorFilter(ex, ingressReq, filtersForRoute.getErrorFilter());
            }).doOnNext(httpResp -> recordHttpStatusCode(ingressReq, httpResp.getStatus().code(), startTime));
        } catch (IOException ex) {
            logger.error("Couldn't load the filters");
            return Observable.error(new ZuulException("Could not load filters", 500));
        }
    }

    private Observable<EgressRequest<State>> applyPreFilters(final EgressRequest<State> initialEgressReq, final List<PreFilter<State>> preFilters) {
        Observable<EgressRequest<State>> req = Observable.just(initialEgressReq);
        for (PreFilter<State> preFilter: preFilters) {
            req = preFilter.execute(req).single();
        }
        return req;
    }

    private Observable<IngressResponse<State>> applyRouteFilter(final Observable<EgressRequest<State>> egressReq, final RouteFilter<State> routeFilter) {
        return egressReq.flatMap(routeFilter::execute).single();
    }

    private Observable<EgressResponse<State>> applyPostFilters(final Observable<EgressResponse<State>> initialEgressResp, final List<PostFilter<State>> postFilters) {
        Observable<EgressResponse<State>> resp = initialEgressResp;
        for (PostFilter<State> postFilter: postFilters) {
            resp = postFilter.execute(resp).single();
        }
        return resp;
    }

    private Observable<EgressResponse<State>> applyErrorFilter(Throwable ex, IngressRequest ingressReq, ErrorFilter<State> errorFilter) {
        return errorFilter.execute(ex, ingressReq);
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
