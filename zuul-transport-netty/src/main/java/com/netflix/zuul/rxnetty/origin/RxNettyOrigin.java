/**
 * Copyright 2015 Netflix, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package com.netflix.zuul.rxnetty.origin;

import com.netflix.zuul.context.SessionContext;
import com.netflix.zuul.message.Header;
import com.netflix.zuul.message.Headers;
import com.netflix.zuul.message.http.HttpRequestMessage;
import com.netflix.zuul.message.http.HttpResponseMessage;
import com.netflix.zuul.message.http.HttpResponseMessageImpl;
import com.netflix.zuul.origins.Origin;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.logging.LogLevel;
import io.reactivex.netty.protocol.http.client.HttpClient;
import io.reactivex.netty.protocol.http.client.HttpClientRequest;
import io.reactivex.netty.spectator.http.HttpClientListener;
import netflix.ocelli.Instance;
import netflix.ocelli.rxnetty.FailureListener;
import netflix.ocelli.rxnetty.protocol.http.HttpLoadBalancer;
import netflix.ocelli.rxnetty.protocol.http.WeightedHttpClientListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.functions.Func1;

import java.net.SocketAddress;
import java.nio.charset.Charset;

public class RxNettyOrigin implements Origin {

    private static final Logger logger = LoggerFactory.getLogger(RxNettyOrigin.class);

    private final String vip;
    private final HttpClient<ByteBuf, ByteBuf> client;

    public RxNettyOrigin(String vip, Observable<Instance<SocketAddress>> hostStream) {
        this(vip, hostStream, HttpClientListenerImpl::new);
    }

    public RxNettyOrigin(String vip, Observable<Instance<SocketAddress>> hostStream,
                         Func1<FailureListener, WeightedHttpClientListener> listenerFactory) {
        if (null == vip) {
            throw new IllegalArgumentException("VIP can not be null.");
        }
        this.vip = vip;
        HttpLoadBalancer<ByteBuf, ByteBuf> lb = HttpLoadBalancer.choiceOfTwo(hostStream, listenerFactory);
        client = HttpClient.newClient(lb.toConnectionProvider());
        client.subscribe(new HttpClientListener("origin-" + vip));
    }

    @Override
    public String getName() {
        return vip;
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public Observable<HttpResponseMessage> request(HttpRequestMessage requestMsg) {
        final SessionContext context = requestMsg.getContext();
        HttpMethod method = toNettyHttpMethod(requestMsg.getMethod());
        Headers headers = requestMsg.getHeaders();
        HttpClientRequest<ByteBuf, ByteBuf> request = client.createRequest(method, requestMsg.getPathAndQuery());

        for (Header header : headers.entries()) {
            request = request.addHeader(header.getKey(), header.getValue());
        }

        return request.writeContent(requestMsg.getBodyStream())
                      .map(nettyResp -> {
                          HttpResponseMessage resp = new HttpResponseMessageImpl(context, requestMsg,
                                                                                 nettyResp.getStatus().code());
                          Headers respHeaders = resp.getHeaders();
                          nettyResp.getHeaderNames()
                                   .stream()
                                   .forEach(headerName -> nettyResp.getAllHeaderValues(headerName)
                                                                   .stream()
                                                                   .forEach(headerVal ->
                                                                                    respHeaders.add(headerName,
                                                                                                    headerVal)));
                          resp.setBodyStream(nettyResp.getContent());
                          return resp;
                      })
                      .retryWhen(new RetryWhenNoServersAvailable());
    }

    private HttpMethod toNettyHttpMethod(String method) {
        return HttpMethod.valueOf(method.toUpperCase());
    }
}
