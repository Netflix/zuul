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
package com.netflix.zuul;

import rx.Observable;

import java.util.List;

public class FilterProcessor {

    private final FilterStore filterStore;

    public FilterProcessor(FilterStore filterStore) {
        this.filterStore = filterStore;
    }

    public Observable<EgressResponse> applyAllFilters(IngressRequest ingressReq) {
        FiltersForRoute filtersForRoute = filterStore.getFilters(ingressReq);
        return applyPreFilters(ingressReq, filtersForRoute.getPreFilters()).flatMap(egressReq ->
                applyRoutingFilters(egressReq, filtersForRoute.getRouteFilters())).flatMap(ingressResp ->
                applyPostFilters(ingressResp, filtersForRoute.getPostFilters()));
    }

    private Observable<EgressRequest> applyPreFilters(IngressRequest ingressReq, List<PreFilter> preFilters) {
        Observable<EgressRequest> observableReq = Observable.empty();
        if (preFilters != null && preFilters.size() != 0) {
            for (PreFilter filter: preFilters) {
                observableReq = observableReq.flatMap(egressReq -> filter.shouldFilter(ingressReq).flatMap(shouldFilter -> {
                    if (shouldFilter) {
                        return filter.apply(ingressReq, egressReq);
                    } else {
                        return Observable.just(egressReq);
                    }
                }));
            }
        }
        return observableReq;
    }

    //at the moment, this chains all n route filters and only returns the last
    //is there a use case for any sort of intelligent combining (amb?, flatMap?)
    private Observable<IngressResponse> applyRoutingFilters(EgressRequest egressReq, List<RouteFilter> routeFilters) {
        Observable<IngressResponse> observableResp = Observable.empty();
        if (routeFilters != null && routeFilters.size() != 0) {
            for (RouteFilter filter: routeFilters) {
                observableResp = observableResp.flatMap(ingressResp -> filter.shouldFilter(egressReq).flatMap(shouldFilter -> {
                    if (shouldFilter) {
                        return filter.apply(egressReq, ingressResp);
                    } else {
                        return Observable.just(ingressResp);
                    }
                }));
            }
        }
        return observableResp;
    }

    private Observable<EgressResponse> applyPostFilters(IngressResponse ingressResp, List<PostFilter> postFilters) {
        Observable<EgressResponse> observableResp = Observable.empty();
        if (postFilters != null && postFilters.size() != 0) {
            for (PostFilter filter: postFilters) {
                observableResp = observableResp.flatMap(egressResp -> filter.shouldFilter(ingressResp).flatMap(shouldFilter -> {
                    if (shouldFilter) {
                        return filter.apply(ingressResp, egressResp);
                    } else {
                        return Observable.just(egressResp);
                    }
                }));
            }
        }
        return observableResp;
    }
}
