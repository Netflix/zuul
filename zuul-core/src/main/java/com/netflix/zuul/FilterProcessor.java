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

import io.netty.buffer.ByteBuf;
import io.reactivex.netty.protocol.http.server.HttpServerResponse;
import rx.Observable;

import java.util.List;

public class FilterProcessor {

    private final FilterStore filterStore;

    public FilterProcessor(FilterStore filterStore) {
        this.filterStore = filterStore;
    }

    public Observable<EgressResponse> applyAllFilters(IngressRequest ingressReq, HttpServerResponse<ByteBuf> nettyResp) {
        FiltersForRoute filtersForRoute = filterStore.getFilters(ingressReq);
        return applyPreFilters(ingressReq, filtersForRoute.getPreFilters()).flatMap(egressReq ->
                applyRoutingFilter(egressReq, filtersForRoute.getRouteFilter())).flatMap(ingressResp ->
                applyPostFilters(ingressResp, nettyResp, filtersForRoute.getPostFilters())).onErrorResumeNext(ex -> filtersForRoute.getErrorFilter().apply(ex));
    }

    private Observable<EgressRequest> applyPreFilters(IngressRequest ingressReq, List<PreFilter> preFilters) {
        System.out.println("IngressReq : " + ingressReq + ", preFilters : " + preFilters.size());

        return Observable.from(preFilters).reduce(Observable.just(EgressRequest.copiedFrom(ingressReq)), (egressReqObservable, preFilter) -> {
            return preFilter.shouldFilter(ingressReq).flatMap(shouldFilter -> {
                if (shouldFilter) {
                    return egressReqObservable.flatMap(egressReq -> preFilter.apply(ingressReq, egressReq).single());
                } else {
                    //System.out.println("Discarding preFilter with order : " + preFilter.getOrder());
                    return egressReqObservable;
                }
            });
        }).flatMap(o -> o);
    }

    private Observable<IngressResponse> applyRoutingFilter(EgressRequest egressReq, RouteFilter routeFilter) {
        System.out.println("EgressReq : " + egressReq);
        if (routeFilter == null) {
            return Observable.error(new ZuulException("You must define a RouteFilter."));
        } else {
            return routeFilter.apply(egressReq).single();
        }
    }

    private Observable<EgressResponse> applyPostFilters(IngressResponse ingressResp, HttpServerResponse<ByteBuf> nettyResp, List<PostFilter> postFilters) {
        System.out.println("IngressResp : " + ingressResp + ", nettyResp : " + nettyResp + ", postFilters : " + postFilters.size());

        Observable<EgressResponse> initialEgressRespObservable = Observable.just(EgressResponse.from(ingressResp, nettyResp));
        return Observable.from(postFilters).reduce(initialEgressRespObservable, (egressRespObservable, postFilter) ->
                postFilter.shouldFilter(ingressResp).flatMap(shouldFilter -> {
                    if (shouldFilter) {
                        return egressRespObservable.flatMap(egressResp -> postFilter.apply(ingressResp, egressResp).single());
                    } else {
                        //System.out.println("Discarding PostFilter with order : " + postFilter.getOrder());
                        return egressRespObservable;
                    }
                })).flatMap(o -> o);
    }
}
