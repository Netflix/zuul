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
import com.netflix.zuul.context.RequestContext;
import com.netflix.zuul.filter.ErrorFilter;
import com.netflix.zuul.filter.PostFilter;
import com.netflix.zuul.filter.PreFilter;
import com.netflix.zuul.filter.RouteFilter;
import com.netflix.zuul.filterstore.FilterStore;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.subjects.PublishSubject;

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
 */
@Singleton
public class FilterProcessor {

    private final Logger logger = LoggerFactory.getLogger(FilterProcessor.class);

    private final FilterStore<RequestContext> filterStore;


    @Inject
    public FilterProcessor(FilterStore<RequestContext> filterStore) {
        this.filterStore = filterStore;
    }

    /**
     * TODO - Why is the filter chain constructed for each request? Why not only once?
     *
     */
    public Observable<RequestContext> applyAllFilters(RequestContext ctx) {

        // Load the filters to be used.
        final FiltersForRoute<RequestContext> filtersForRoute;
        try {
            filtersForRoute = filterStore.getFilters();
        }
        catch (IOException e) {
            logger.error("Couldn't load the filters.", e);
            throw new RuntimeException("Error loading filters.", e);
        }

        PublishSubject<ByteBuf> cachedContent = PublishSubject.create();
        //UnicastDisposableCachingSubject<ByteBuf> cachedContent = UnicastDisposableCachingSubject.create();

        // Subscribe to the response-content observable (retaining the ByteBufS first).
        ctx.internal_getHttpServerRequest().getContent().map(ByteBuf::retain).subscribe(cachedContent);

        // Only apply the filters once the request body has been fully read and buffered.
        Observable<RequestContext> chain = cachedContent
                .reduce((bb1, bb2) -> {
                    // Buffer the request body into a single virtual ByteBuf.
                    // TODO - apply some max size to this.
                    return Unpooled.wrappedBuffer(bb1, bb2);

                })
                .map(bodyBuffer -> {
                    // Set the body on Request object.
                    byte[] body = RxNettyUtils.byteBufToBytes(bodyBuffer);
                    ctx.getRequest().setBody(body);

                    // Release the ByteBufS
                    if (bodyBuffer.refCnt() > 0) {
                        if (logger.isDebugEnabled()) logger.debug("Releasing the server-request ByteBuf.");
                        bodyBuffer.release();
                    }
                    return ctx;
                });

        // Apply PRE filters.
        chain = applyPreFilters(chain, filtersForRoute.getPreFilters());

        // Route/Proxy.
        chain = applyRouteFilter(chain, filtersForRoute.getRouteFilter());

        // Apply POST filters.
        chain = applyPostFilters(chain, filtersForRoute.getPostFilters());

        // Apply error filter if an error during processing.
        chain.onErrorResumeNext(ex -> {
            return applyErrorFilter(ex, ctx, filtersForRoute.getErrorFilter());
        });

        return chain;
    }

    private Observable<RequestContext> applyPreFilters(Observable<RequestContext> chain, final List<PreFilter<RequestContext>> preFilters) {
        for (PreFilter<RequestContext> preFilter: preFilters) {
            chain = preFilter.execute(chain).single();
        }
        return chain;
    }

    private Observable<RequestContext> applyRouteFilter(final Observable<RequestContext> chain,
                                                                final RouteFilter<RequestContext> routeFilter)
    {
        return chain.flatMap(ctx -> routeFilter.execute(ctx).single());
    }

    private Observable<RequestContext> applyPostFilters(Observable<RequestContext> chain, final List<PostFilter<RequestContext>> postFilters) {
        for (PostFilter<RequestContext> postFilter: postFilters) {
            chain = postFilter.execute(chain).single();
        }
        return chain;
    }

    private Observable<RequestContext> applyErrorFilter(Throwable ex, RequestContext ctx, ErrorFilter<RequestContext> errorFilter) {
        if (errorFilter != null) {
            return errorFilter.execute(ex, ctx);
        } else return Observable.error(ex);
    }
}
