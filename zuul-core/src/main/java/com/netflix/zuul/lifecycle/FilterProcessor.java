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

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.netflix.zuul.ZuulException;
import com.netflix.zuul.context.FilterStateFactory;
import com.netflix.zuul.filter.ErrorFilter;
import com.netflix.zuul.filter.PostFilter;
import com.netflix.zuul.filter.PreFilter;
import com.netflix.zuul.filter.RouteFilter;
import com.netflix.zuul.filterstore.FilterStore;
import com.netflix.zuul.metrics.ZuulMetrics;
import io.netty.buffer.ByteBuf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;

import java.io.IOException;
import java.util.List;

/**
 * This class is responsible for wiring together the entire filter chain.  A class which invokes this class must
 * convert the HTTP objects into Zuul domain objects prior to calling {@link #applyAllFilters}.
 *
 * The flow:
 * 1) Given an incoming {@link IngressRequest}, first copy the reference to the content.  RxNetty attempts to discard it ASAP,
 * so we need to capture this reference before we do any other work.  From the content and {@link IngressRequest},
 * create a {@link EgressRequest}.
 * 2) Apply all of the {@link PreFilter}s to the {@link EgressRequest}.  These filters are intended to provide whatever logic
 * is required to modify an HTTP request before the routing decision is made.
 * 3) Apply the single {@link RouteFilter} to the {@link EgressRequest}.  This is a mandatory filter that performs the
 * actual HTTP call to the origin.  It is also responsible for mapping the HTTP response object into a {@link IngressResponse}.
 * 4) Concert the {@link IngressResponse} into a {@link EgressResponse}.
 * 5) Apply all of the {@link PostFilter}s to the {@link EgressResponse}.  These filters are intended to provide any necessary logic
 * that might inspect/modify an HTTP response.  After all of these filters have been applied, the {@link EgressResponse} is
 * ready to be handed back so that the caling class can write it out into the actual HTTP response mechanism.
 * Error) At any point along the way, if a filter throws an error, the {@link ErrorFilter} is invoked.  An {@link ErrorFilter}
 * provides a whole {@link EgressResponse} and ends the entire chain.  If you desire more granular error-handling, you should wire that up
 * in a filter yourself.
 * @param <State>
 */
@Singleton
public class FilterProcessor<State> {

    private final Logger logger = LoggerFactory.getLogger(FilterProcessor.class);
    private static final Logger HTTP_DEBUG_LOGGER = LoggerFactory.getLogger("HTTP_DEBUG");

    private final FilterStore<State> filterStore;
    private final FilterStateFactory<State> stateFactory;

    @Inject
    public FilterProcessor(FilterStore<State> filterStore, FilterStateFactory<State> stateFactory) {
        this.filterStore = filterStore;
        this.stateFactory = stateFactory;
    }

    public Observable<EgressResponse<State>> applyAllFilters(IngressRequest ingressReq) {
        final long startTime = System.currentTimeMillis();
        try {
            FiltersForRoute<State> filtersForRoute = filterStore.getFilters(ingressReq);

            UnicastDisposableCachingSubject<ByteBuf> cachedContent = UnicastDisposableCachingSubject.create();
            /**
             * Why do we retain here?
             * After the onNext on the content returns, RxNetty releases the sent ByteBuf. This ByteBuf is kept out of
             * the scope of the onNext for consumption of the client in the route. The client when eventually writes
             * this ByteBuf over the wire expects the ByteBuf to be usable (i.e. ref count => 1). If this retain() call
             * is removed, the ref count will be 0 after the onNext on the content returns and hence it will be unusable
             * by the client in the route.
             */
            ingressReq.getHttpServerRequest().getContent().map(ByteBuf::retain).subscribe(cachedContent); // Caches data if arrived before client writes it out, else passes through


            EgressRequest<State> initialEgressReq = EgressRequest.copiedFrom(
                    ingressReq, cachedContent, stateFactory.create(ingressReq.getHttpServerRequest()));

            Observable<EgressRequest<State>> egressReq = applyPreFilters(initialEgressReq, filtersForRoute.getPreFilters());
            Observable<IngressResponse<State>> ingressResp = applyRouteFilter(egressReq, filtersForRoute.getRouteFilter(),
                                                                              cachedContent);
            Observable<EgressResponse<State>> initialEgressResp = ingressResp.map(EgressResponse::from);

            Observable<EgressResponse<State>> egressResp = applyPostFilters(initialEgressResp, filtersForRoute.getPostFilters());
            return egressResp.doOnCompleted(() ->
                ZuulMetrics.markSuccess(ingressReq, System.currentTimeMillis() - startTime)
            ).onErrorResumeNext(ex -> {
                long duration = System.currentTimeMillis() - startTime;
                ZuulMetrics.markError(ingressReq, duration);
                return applyErrorFilter(ex, ingressReq, filtersForRoute.getErrorFilter());
            }).doOnNext(httpResp ->
                ZuulMetrics.markStatus(httpResp.getStatus().code(), ingressReq, startTime)
            );
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

    private Observable<IngressResponse<State>> applyRouteFilter(final Observable<EgressRequest<State>> egressReq,
                                                                final RouteFilter<State> routeFilter,
                                                                final UnicastDisposableCachingSubject<ByteBuf> ingressContent) {
        return egressReq.flatMap(req -> {
            if (HTTP_DEBUG_LOGGER.isDebugEnabled()) {
                for (String headerName : req.getHttpClientRequest().getHeaders().names()) {
                    for (String headerValue : req.getHttpClientRequest().getHeaders().getAll(headerName)) {
                        HTTP_DEBUG_LOGGER.debug("Origin req header : " + headerName + " -> " + headerValue);
                    }
                }
            }
            return routeFilter.execute(req);
        }).single().
                doOnNext(resp -> {
                    if (HTTP_DEBUG_LOGGER.isDebugEnabled()) {
                        for (String headerName : resp.getHeaders().names()) {
                            for (String headerValue : resp.getHeaders().getAll(headerName)) {
                                HTTP_DEBUG_LOGGER.debug("Origin resp header : " + headerName + " -> " + headerValue);
                            }
                        }
                    }
                }).
                doOnTerminate(() -> ingressContent.dispose(byteBuf -> {
                    /**
                     * Why do we release here?
                     *
                     * All ByteBuf which were never consumed are disposed and sent here. This means that the
                     * client in the route never consumed this ByteBuf. Before sending this ByteBuf to the
                     * content subject, we do a retain (see above for reason) expecting the client in the route
                     * to release it when written over the wire. In this case, though, the client never consumed
                     * it and hence never released corresponding to the retain done by us.
                     */
                    if (byteBuf.refCnt() > 1) { // 1 refCount will be from the subject putting into the cache.
                        byteBuf.release();
                    }
                }));
    }

    private Observable<EgressResponse<State>> applyPostFilters(final Observable<EgressResponse<State>> initialEgressResp, final List<PostFilter<State>> postFilters) {
        Observable<EgressResponse<State>> resp = initialEgressResp;
        for (PostFilter<State> postFilter: postFilters) {
            resp = postFilter.execute(resp).single();
        }
        return resp;
    }

    private Observable<EgressResponse<State>> applyErrorFilter(Throwable ex, IngressRequest ingressReq, ErrorFilter<State> errorFilter) {
        if (errorFilter != null) {
            return errorFilter.execute(ex, ingressReq);
        } else return Observable.error(ex);
    }
}
