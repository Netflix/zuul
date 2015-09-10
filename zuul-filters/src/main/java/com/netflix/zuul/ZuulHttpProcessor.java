package com.netflix.zuul;

import com.netflix.zuul.context.SessionCleaner;
import com.netflix.zuul.context.SessionContext;
import com.netflix.zuul.context.SessionContextDecorator;
import com.netflix.zuul.context.SessionContextFactory;
import com.netflix.zuul.message.ZuulMessage;
import com.netflix.zuul.message.http.HttpResponseMessage;
import com.netflix.zuul.message.http.RequestCompleteHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.Single;

import javax.annotation.Nullable;
import javax.inject.Inject;

/**
 * The main processing class for Zuul.
 *
 * 1. Translates the inbound native request (ie. HttpServletRequest, or rxnetty HttpServerRequest) into a zuul HttpRequestMessage.
 * 2. Builds the filter chain and passes the request through it.
 * 3. Writes out the HttpResponseMessage to the native response object.
 *
 * User: michaels@netflix.com
 * Date: 8/3/15
 * Time: 11:38 AM
 */
public class ZuulHttpProcessor<I,O>
{
    private static final Logger LOG = LoggerFactory.getLogger(ZuulHttpProcessor.class);

    private FilterProcessor filterProcessor;
    private SessionContextFactory<I, O> contextFactory;
    private SessionContextDecorator decorator;
    private SessionCleaner sessionCleaner;
    private RequestCompleteHandler requestCompleteHandler;

    @Inject
    public ZuulHttpProcessor(FilterProcessor filterProcessor, SessionContextFactory<I, O> contextFactory,
                             @Nullable SessionContextDecorator decorator,
                             @Nullable RequestCompleteHandler requestCompleteHandler,
                             SessionCleaner sessionCleaner)
    {
        this.filterProcessor = filterProcessor;
        this.contextFactory = contextFactory;
        this.decorator = decorator;
        this.requestCompleteHandler = requestCompleteHandler;
        this.sessionCleaner = sessionCleaner;
    }

    public Observable<Void> process(final I nativeRequest, final O nativeResponse)
    {
        // Setup the context for this request.
        final SessionContext context;

        // Optionally decorate the context.
        if (decorator == null) {
            context = new SessionContext();
        } else {
            context = decorator.decorate(new SessionContext());
        }

        // Build a ZuulMessage from the netty request.
        final ZuulMessage request = contextFactory.create(context, nativeRequest, nativeResponse);

        // Start timing the request.
        request.getContext().getTimings().getRequest().start();

        // Create initial chain.
        Single<ZuulMessage> chain = Single.just(request);

        /*
         * Delegate all of the filter application logic to {@link FilterProcessor}.
         * This work is some combination of synchronous and asynchronous.
         */
        chain = filterProcessor.applyInboundFilters(chain);
        chain = filterProcessor.applyEndpointFilter(chain);
        chain = filterProcessor.applyOutboundFilters(chain);

        return chain.toObservable()
                    .flatMap(msg -> {
                        Observable<Void> toReturn = contextFactory.write(msg, nativeResponse);

                        if (null != requestCompleteHandler) {
                            toReturn = toReturn.concatWith(requestCompleteHandler.handle((HttpResponseMessage) msg));
                        }

                        return toReturn.finallyDo(() -> {
                            msg.getContext().getTimings().getRequest().end();
                        }).doOnError(throwable -> {
                            LOG.error("Error in writing response! request=" + request.getInfoForLogging(), throwable);
                        });

                    })
                    .doOnError(throwable -> {
                        LOG.error("Unexpected error in filter chain! request=" + request.getInfoForLogging(),
                                  throwable);
                    })
                    .ignoreElements()
                    .finallyDo(() -> {
                        // Cleanup any resources related to this request/response.
                        sessionCleaner.cleanup(context);
                    });
    }
}
