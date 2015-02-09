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

import io.netty.buffer.ByteBuf;
import io.reactivex.netty.protocol.http.client.HttpClientRequest;
import io.reactivex.netty.protocol.http.server.HttpServerRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;

import java.util.Map;

/**
 * Class that represents a request we are making to the origin server.  Use the {@link EgressRequest#copiedFrom} method to
 * copy the relevant fields from an {@link IngressRequest} and then filters can be applied to a {@link EgressRequest} until
 * it is ready to create an actual HTTP request against the origin.
 * @param <T>
 */
public class EgressRequest<T> {
    private final static Logger logger = LoggerFactory.getLogger(EgressRequest.class);

    private HttpClientRequest<ByteBuf> httpClientRequest;
    private final HttpServerRequest<ByteBuf> httpServerRequest;
    private final Observable<ByteBuf> requestContent;
    private final T state;
    
    private EgressRequest(HttpClientRequest<ByteBuf> httpClientRequest, HttpServerRequest<ByteBuf> httpServerRequest, Observable<ByteBuf> requestContent, T state) {
        this.httpClientRequest = httpClientRequest;
        this.httpServerRequest = httpServerRequest;
        this.requestContent = requestContent;
        this.state = state;
    }

    /**
     * Copy over all references from an {@link IngressRequest} to create a fresh {@link EgressRequest}.
     * @param ingressReq initial {@link IngressRequest}
     * @param incomingContent stream of HTTP content to pass to origin
     * @param requestState encapsulation of state within the filter chain
     * @param <T> type of requestState
     * @return a fresh {@link EgressRequest} that filters may work with
     */
    public static <T> EgressRequest<T> copiedFrom(IngressRequest ingressReq, Observable<ByteBuf> incomingContent, T requestState) {
        HttpServerRequest<ByteBuf> nettyReq = ingressReq.getHttpServerRequest();
        HttpClientRequest<ByteBuf> clientReq = HttpClientRequest.create(nettyReq.getHttpMethod(), nettyReq.getUri());
        for (Map.Entry<String, String> entry: nettyReq.getHeaders().entries()) {
            if (logger.isDebugEnabled()) {
                logger.debug("Adding header to EgressRequest : " + entry.getKey() + " -> " + entry.getValue());
            }
            clientReq = clientReq.withHeader(entry.getKey(), entry.getValue());
        }
        clientReq = clientReq.withContentSource(incomingContent);
        return new EgressRequest<>(clientReq, ingressReq.getHttpServerRequest(), incomingContent, requestState);
    }

    public HttpClientRequest<ByteBuf> getHttpClientRequest() {
        return httpClientRequest;
    }

    public HttpServerRequest<ByteBuf> getHttpServerRequest() {
        return httpServerRequest;
    }

    public Observable<ByteBuf> getRequestContent() {
        return requestContent;
    }
    
    public T get() {
        return state;
    }
}
