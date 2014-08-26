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

import java.io.IOException;
import java.util.List;

import rx.Observable;

public class FilterProcessor<Request, Response> {

    private final FilterStore<Request, Response> filterStore;
    private final FilterStateFactory<Request> requestState; 
    private final FilterStateFactory<Response> responseState;

    public FilterProcessor(FilterStore<Request, Response> filterStore, FilterStateFactory<Request> requestState, FilterStateFactory<Response> responseState) {
        this.filterStore = filterStore;
        this.requestState = requestState;
        this.responseState = responseState;
    }

    public Observable<EgressResponse<Response>> applyAllFilters(IngressRequest ingressReq) {
        try {
            FiltersForRoute<Request, Response> filtersForRoute = filterStore.getFilters(ingressReq);
            return applyPreFilters(ingressReq, filtersForRoute.getPreFilters()).flatMap(egressReq ->
                    applyRoutingFilter(egressReq, filtersForRoute.getRouteFilter())).flatMap(ingressResp ->
                    applyPostFilters(ingressResp, filtersForRoute.getPostFilters())).onErrorResumeNext(
                    ex -> {
                        System.out.println("Received Exception : " + ex);
                        ex.printStackTrace();
                        return applyErrorFilter(ex, filtersForRoute.getErrorFilter());
                    });
        } catch (IOException ioe) {
            System.err.println("Couldn't load the filters");
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
        System.out.println("Executing post filters on : " + ingressResp);
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
}
