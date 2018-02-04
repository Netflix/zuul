/*
 * Copyright 2018 Netflix, Inc.
 *
 *      Licensed under the Apache License, Version 2.0 (the "License");
 *      you may not use this file except in compliance with the License.
 *      You may obtain a copy of the License at
 *
 *          http://www.apache.org/licenses/LICENSE-2.0
 *
 *      Unless required by applicable law or agreed to in writing, software
 *      distributed under the License is distributed on an "AS IS" BASIS,
 *      WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *      See the License for the specific language governing permissions and
 *      limitations under the License.
 */

package com.netflix.zuul.netty.filter;

import com.google.common.base.Strings;
import com.netflix.config.DynamicStringProperty;
import com.netflix.spectator.impl.Preconditions;
import com.netflix.zuul.FilterLoader;
import com.netflix.zuul.FilterUsageNotifier;
import com.netflix.zuul.context.SessionContext;
import com.netflix.zuul.filters.Endpoint;
import com.netflix.zuul.filters.FilterType;
import com.netflix.zuul.filters.ZuulFilter;
import com.netflix.zuul.filters.endpoint.ProxyEndpoint;
import com.netflix.zuul.message.ZuulMessage;
import com.netflix.zuul.message.http.HttpRequestMessage;
import com.netflix.zuul.message.http.HttpResponseMessage;
import com.netflix.zuul.message.http.HttpResponseMessageImpl;
import com.netflix.zuul.filters.endpoint.MissingEndpointHandlingFilter;
import com.netflix.zuul.filters.SyncZuulFilterAdapter;
import com.netflix.zuul.netty.server.MethodBinding;
import io.netty.handler.codec.http.HttpContent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.concurrent.ThreadSafe;

import static com.netflix.zuul.context.CommonContextKeys.ZUUL_ENDPOINT;


/**
 * This class is supposed to be thread safe and hence should not have any non final member variables
 * Created by saroskar on 5/18/17.
 */
@ThreadSafe
public class ZuulEndPointRunner extends BaseZuulFilterRunner<HttpRequestMessage, HttpResponseMessage> {

    private final FilterLoader filterLoader;

    private static Logger logger = LoggerFactory.getLogger(ZuulEndPointRunner.class);
    public static final String PROXY_ENDPOINT_FILTER_NAME = ProxyEndpoint.class.getCanonicalName();
    public static final DynamicStringProperty DEFAULT_ERROR_ENDPOINT = new DynamicStringProperty("zuul.filters.error.default", "endpoint.ErrorResponse");


    public ZuulEndPointRunner(FilterUsageNotifier usageNotifier, FilterLoader filterLoader,
                              FilterRunner<HttpResponseMessage, HttpResponseMessage> respFilters) {
        super(FilterType.ENDPOINT, usageNotifier, respFilters);
        this.filterLoader = filterLoader;
    }

    public static ZuulFilter<HttpRequestMessage, HttpResponseMessage> getEndpoint(final HttpRequestMessage zuulReq) {
        if (zuulReq != null) {
            return (ZuulFilter<HttpRequestMessage, HttpResponseMessage>) zuulReq.getContext().get(ZUUL_ENDPOINT);
        }
        return null;
    }

    public static void setEndpoint(HttpRequestMessage zuulReq, ZuulFilter<HttpRequestMessage, HttpResponseMessage> endpoint) {
        zuulReq.getContext().set(ZUUL_ENDPOINT, endpoint);
    }

    @Override
    public void filter(final HttpRequestMessage zuulReq) {
        if (zuulReq.getContext().isCancelled()) {
            zuulReq.disposeBufferedBody();
            logger.debug("Request was cancelled, UUID {}", zuulReq.getContext().getUUID());
            return;
        }

        final String endpointName = getEndPointName(zuulReq.getContext());
        try {
            Preconditions.checkNotNull(zuulReq, "input message");

            final ZuulFilter<HttpRequestMessage, HttpResponseMessage> endpoint = getEndpoint(endpointName, zuulReq);
            logger.debug("Got endpoint {}, UUID {}", endpoint.filterName(), zuulReq.getContext().getUUID());
            setEndpoint(zuulReq, endpoint);
            final HttpResponseMessage zuulResp = filter(endpoint, zuulReq);

            if ((zuulResp != null)&&(! (endpoint instanceof ProxyEndpoint))) {
                //EdgeProxyEndpoint calls invokeNextStage internally
                logger.debug("Endpoint calling invokeNextStage, UUID {}", zuulReq.getContext().getUUID());
                invokeNextStage(zuulResp);
            }
        }
        catch (Exception ex) {
            handleException(zuulReq, endpointName, ex);
        }
    }

    @Override
    protected void resume(final HttpResponseMessage zuulMesg) {
        if (zuulMesg.getContext().isCancelled()) {
            return;
        }
        invokeNextStage(zuulMesg);
    }

    @Override
    public void filter(final HttpRequestMessage zuulReq, final HttpContent chunk) {
        if (zuulReq.getContext().isCancelled()) {
            chunk.release();
            return;
        }

        String endpointName = "-";
        try {
            ZuulFilter<HttpRequestMessage, HttpResponseMessage> endpoint = Preconditions.checkNotNull(
                    getEndpoint(zuulReq), "endpoint");
            endpointName = endpoint.filterName();

            final HttpContent newChunk = endpoint.processContentChunk(zuulReq, chunk);
            if (newChunk != null) {
                //Endpoints do not directly forward content chunks to next stage in the filter chain.
                zuulReq.bufferBodyContents(newChunk);

                //deallocate original chunk if necessary
                if (newChunk != chunk) {
                    chunk.release();
                }

                if (isFilterAwaitingBody(zuulReq) && zuulReq.hasCompleteBody() && !(endpoint instanceof ProxyEndpoint)) {
                    //whole body has arrived, resume filter chain
                    invokeNextStage(filter(endpoint, zuulReq));
                }
            }
        }
        catch (Exception ex) {
            handleException(zuulReq, endpointName, ex);
        }
    }

    protected String getEndPointName(final SessionContext zuulCtx) {
        if (zuulCtx.shouldSendErrorResponse()) {
            zuulCtx.setShouldSendErrorResponse(false);
            zuulCtx.setErrorResponseSent(true);
            final String errEndPointName = zuulCtx.getErrorEndpoint();
            return (Strings.isNullOrEmpty(errEndPointName)) ? DEFAULT_ERROR_ENDPOINT.get() : errEndPointName;
        } else {
            return zuulCtx.getEndpoint();
        }
    }

    protected ZuulFilter<HttpRequestMessage, HttpResponseMessage> getEndpoint(final String endpointName,
                final HttpRequestMessage zuulRequest) {
        final SessionContext zuulCtx = zuulRequest.getContext();

        if (zuulCtx.getStaticResponse() != null) {
            return STATIC_RESPONSE_ENDPOINT;
        }

        if (endpointName == null) {
            return new MissingEndpointHandlingFilter("NO_ENDPOINT_NAME");
        }

        if (PROXY_ENDPOINT_FILTER_NAME.equals(endpointName)) {
            return newProxyEndpoint(zuulRequest);
        }

        final Endpoint<HttpRequestMessage, HttpResponseMessage> filter = getEndpointFilter(endpointName);
        if (filter == null) {
            return new MissingEndpointHandlingFilter(endpointName);
        }

        return filter;
    }

    /**
     * Override to inject your own proxy endpoint implementation
     *
     * @param zuulRequest - the request message
     * @return the proxy endpoint
     */
    protected ZuulFilter<HttpRequestMessage, HttpResponseMessage> newProxyEndpoint(HttpRequestMessage zuulRequest) {
        return new ProxyEndpoint(zuulRequest, getChannelHandlerContext(zuulRequest), getNextStage(), MethodBinding.NO_OP_BINDING);
    }

    protected <I extends ZuulMessage, O extends ZuulMessage> Endpoint<I, O> getEndpointFilter(String endpointName) {
        return (Endpoint<I, O>) filterLoader.getFilterByNameAndType(endpointName, FilterType.ENDPOINT);
    }

    final protected static ZuulFilter<HttpRequestMessage, HttpResponseMessage> STATIC_RESPONSE_ENDPOINT = new SyncZuulFilterAdapter<HttpRequestMessage, HttpResponseMessage>() {
        @Override
        public HttpResponseMessage apply(HttpRequestMessage request) {
            final HttpResponseMessage resp = request.getContext().getStaticResponse();
            resp.finishBufferedBodyIfIncomplete();
            return resp;
        }

        @Override
        public String filterName() {
            return "StaticResponseEndpoint";
        }

        @Override
        public HttpResponseMessage getDefaultOutput(HttpRequestMessage input) {
            return HttpResponseMessageImpl.defaultErrorResponse(input);
        }
    };

}
