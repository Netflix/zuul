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

import java.io.IOException;
import java.util.List;

import rx.Observable;
import rx.functions.Func1;


/**
 * TODO: It would be better to use OP.single(), rather than Observable.error(ERROR).startWith(OP).take(1)
 * However, doing this around a Netty operation results in not being able to get at the ByteBuf.  We should look at
 * RxNetty to see if we can issue the onCompleted() before the resource release()
 */
public class FilterProcessor<Request, Response> {

    private final FilterStore<Request, Response> filterStore;
    private final FilterStateFactory<Request> requestState; 
    private final FilterStateFactory<Response> responseState;

    public FilterProcessor(FilterStore<Request, Response> filterStore, FilterStateFactory<Request> requestState, FilterStateFactory<Response> responseState) {
        this.filterStore = filterStore;
        this.requestState = requestState;
        this.responseState = responseState;
    }

    public Observable<EgressResponse<Response>> applyAllFilters(IngressRequest ingressReq, HttpServerResponse<ByteBuf> nettyResp) {
        try {
            FiltersForRoute<Request, Response> filtersForRoute = filterStore.getFilters(ingressReq);
            return applyPreFilters(ingressReq, filtersForRoute.getPreFilters()).flatMap(egressReq ->
                    applyRoutingFilter(egressReq, filtersForRoute.getRouteFilter())).flatMap(ingressResp ->
                    applyPostFilters(ingressResp, nettyResp, filtersForRoute.getPostFilters())).onErrorResumeNext(
                    ex -> filtersForRoute.getErrorFilter().handleError(ex));
        } catch (IOException ioe) {
            System.err.println("Couldn't load the filters");
            return Observable.error(new ZuulException("Could not load filters"));
        }
    }

    private Observable<EgressRequest<Request>> applyPreFilters(IngressRequest ingressReq, List<PreFilter<Request>> preFilters) {
        Observable<EgressRequest<Request>> initialEgressReqObservable = Observable.just(EgressRequest.copiedFrom(ingressReq, requestState.create()));
        return Observable.from(preFilters).reduce(initialEgressReqObservable, (egressReqObservable, preFilter) -> {
            return oneOrError(preFilter.execute(egressReqObservable), "Empty pre filter");
        }).flatMap(o -> o);
    }

    private Observable<IngressResponse> applyRoutingFilter(EgressRequest<Request> egressReq, RouteFilter<Request> routeFilter) {
        if (routeFilter == null) {
            return Observable.<IngressResponse>error(new ZuulException("You must define a RouteFilter."));
        } else {
            return oneOrError(routeFilter.execute(egressReq), "Empty route filter");
        }
    }

    private Observable<EgressResponse<Response>> applyPostFilters(IngressResponse ingressResp, HttpServerResponse<ByteBuf> nettyResp, List<PostFilter<Response>> postFilters) {
        Observable<EgressResponse<Response>> initialEgressRespObservable = Observable.just(EgressResponse.from(ingressResp, nettyResp, responseState.create()));
        return Observable.from(postFilters).reduce(initialEgressRespObservable, (egressRespObservable, postFilter) -> {
            return oneOrError(postFilter.execute(egressRespObservable), "Empty post filter");
        }).flatMap(o -> o);
    }

    private <T> Observable<T> oneOrError(Observable<T> in, String errorMsg) {
        return Observable.<T>error(new ZuulException(errorMsg)).startWith(in).take(1);
    }
}
