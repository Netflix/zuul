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
import io.netty.buffer.ByteBuf;
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


            EgressRequest<State> initialEgressReq = EgressRequest.copiedFrom(ingressReq, cachedContent,
                                                                             stateFactory.create());
            Observable<EgressRequest<State>> egressReq = applyPreFilters(initialEgressReq, filtersForRoute.getPreFilters());
            Observable<IngressResponse<State>> ingressResp = applyRouteFilter(egressReq, filtersForRoute.getRouteFilter(),
                                                                              cachedContent);
            Observable<EgressResponse<State>> initialEgressResp = ingressResp.map(EgressResponse::from);
            Observable<EgressResponse<State>> egressResp = applyPostFilters(initialEgressResp, filtersForRoute.getPostFilters());
            return egressResp.doOnCompleted(() ->
                ZuulMetrics.markSuccess(ingressReq, System.currentTimeMillis() - startTime)
            ).onErrorResumeNext(ex -> {
                long duration = System.currentTimeMillis() - startTime;
                logger.error("*** Unhandled Zuul Error : " + ingressReq.getHttpServerRequest().getUri() + " : " + duration + " ms : " + ex.getMessage());
                ex.printStackTrace();
                ZuulMetrics.markError(ingressReq, duration);
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

    private Observable<IngressResponse<State>> applyRouteFilter(final Observable<EgressRequest<State>> egressReq,
                                                                final RouteFilter<State> routeFilter,
                                                                final UnicastDisposableCachingSubject<ByteBuf> ingressContent) {
        return egressReq.flatMap(routeFilter::execute).single()
                        .doOnTerminate(() -> ingressContent.dispose(byteBuf -> {
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
