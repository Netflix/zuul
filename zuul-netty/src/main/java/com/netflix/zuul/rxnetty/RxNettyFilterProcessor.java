package com.netflix.zuul.rxnetty;


import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.netflix.zuul.context.SessionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;

import java.io.IOException;

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
public class RxNettyFilterProcessor {

    private final Logger logger = LoggerFactory.getLogger(RxNettyFilterProcessor.class);

    private final FilterStore<SessionContext> filterStore;


    @Inject
    public RxNettyFilterProcessor(FilterStore<SessionContext> filterStore) {
        this.filterStore = filterStore;
    }


    public Observable<SessionContext> applyAllFilters(Observable<SessionContext> chain) {

        // Load the filters to be used.
        final FiltersForRoute<SessionContext> filtersForRoute;
        try {
            filtersForRoute = filterStore.getFilters();
        }
        catch (IOException e) {
            logger.error("Couldn't load the filters.", e);
            throw new RuntimeException("Error loading filters.", e);
        }

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

    private Observable<SessionContext> applyPreFilters(Observable<SessionContext> chain, final List<PreFilter<SessionContext>> preFilters) {
        for (PreFilter<SessionContext> preFilter: preFilters) {
            chain = preFilter.execute(chain).single();
        }
        return chain;
    }

    private Observable<SessionContext> applyRouteFilter(final Observable<SessionContext> chain,
                                                        final RouteFilter<SessionContext> routeFilter)
    {
        return chain.flatMap(ctx -> routeFilter.execute(ctx).single());
    }

    private Observable<SessionContext> applyPostFilters(Observable<SessionContext> chain, final List<PostFilter<SessionContext>> postFilters) {
        for (PostFilter<SessionContext> postFilter: postFilters) {
            chain = postFilter.execute(chain).single();
        }
        return chain;
    }

    private Observable<SessionContext> applyErrorFilter(Throwable ex, SessionContext ctx, ErrorFilter<SessionContext> errorFilter) {
        if (errorFilter != null) {
            return errorFilter.execute(ex, ctx);
        } else return Observable.error(ex);
    }
}
