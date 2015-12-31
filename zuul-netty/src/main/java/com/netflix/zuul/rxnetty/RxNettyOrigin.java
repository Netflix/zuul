/**
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.zuul.rxnetty;

import com.netflix.zuul.context.SessionContext;
import com.netflix.zuul.message.Header;
import com.netflix.zuul.message.HeaderName;
import com.netflix.zuul.message.Headers;
import com.netflix.zuul.message.http.HttpHeaderNames;
import com.netflix.zuul.message.http.HttpRequestMessage;
import com.netflix.zuul.message.http.HttpResponseMessage;
import com.netflix.zuul.message.http.HttpResponseMessageImpl;
import com.netflix.zuul.origins.Origin;
import com.netflix.zuul.util.ProxyUtils;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.logging.LogLevel;
import io.reactivex.netty.client.ConnectionProvider;
import io.reactivex.netty.protocol.http.client.HttpClient;
import io.reactivex.netty.protocol.http.client.HttpClientRequest;
import netflix.ocelli.Instance;
import netflix.ocelli.rxnetty.protocol.http.HttpLoadBalancer;
import rx.Observable;

import java.net.SocketAddress;

/**
 * User: michaels@netflix.com
 * Date: 2/20/15
 * Time: 2:19 PM
 */
public class RxNettyOrigin implements Origin {

    private final String vip;
    private final HttpClient<ByteBuf, ByteBuf> client;
    private final String name;

    private RxNettyOrigin(String name, String vip, ConnectionProvider<ByteBuf, ByteBuf> loadBalancer,
                         HttpClientMetrics clientMetrics) {
        this.name = name;
        if (null == vip) {
            throw new IllegalArgumentException("VIP can not be null.");
        }
        this.vip = vip;
        client = HttpClient.newClient(loadBalancer)
                           .enableWireLogging(LogLevel.DEBUG);
        client.subscribe(clientMetrics);
    }

    private RxNettyOrigin(String name, String vip, HttpClient<ByteBuf, ByteBuf> client) {
        this.name = name;
        if (null == vip) {
            throw new IllegalArgumentException("VIP can not be null.");
        }
        this.vip = vip;
        this.client = client;
    }

    public String getVip() {
        return vip;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public Observable<HttpResponseMessage> request(HttpRequestMessage requestMsg) {
        final SessionContext context = requestMsg.getContext();

        // Add X-Forwarded headers if not already there.
        ProxyUtils.addXForwardedHeaders(requestMsg);

        HttpMethod method = toNettyHttpMethod(requestMsg.getMethod());
        Headers headers = requestMsg.getHeaders();
        HttpClientRequest<ByteBuf, ByteBuf> request = client.createRequest(method, requestMsg.getPathAndQuery());

        for (Header header : headers.entries()) {
            if (ProxyUtils.isValidRequestHeader(header.getName())) {
                request = request.addHeader(header.getKey(), header.getValue());
            }
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
                                                                   .forEach(headerVal -> {
                                                                       HeaderName hn = HttpHeaderNames.get(headerName);
                                                                       if (ProxyUtils.isValidResponseHeader(hn)) {
                                                                           respHeaders.add(headerName, headerVal);
                                                                       }
                                                                   }));
                          resp.setBodyStream(nettyResp.getContent());
                          return resp;
                      });
    }

    public static RxNettyOrigin newOrigin(String name, String vip, Observable<Instance<SocketAddress>> hostStream) {
        HttpClientMetrics clientMetrics = new HttpClientMetrics(name);
        ConnectionProvider<ByteBuf, ByteBuf> cp =
                HttpLoadBalancer.<ByteBuf, ByteBuf>choiceOfTwo(hostStream,
                                                               failureListener -> new HttpClientListenerImpl(failureListener,
                                                                                                       clientMetrics))
                                .toConnectionProvider();
        return new RxNettyOrigin(name, vip, cp, clientMetrics);
    }

    public static RxNettyOrigin newOrigin(String name, String vip,
                                          ConnectionProvider<ByteBuf, ByteBuf> connectionProvider,
                                          HttpClientMetrics clientMetrics) {
        return new RxNettyOrigin(name, vip, connectionProvider, clientMetrics);
    }

    public static RxNettyOrigin newOrigin(String name, String vip, HttpClient<ByteBuf, ByteBuf> client) {
        return new RxNettyOrigin(name, vip, client);
    }

    private HttpMethod toNettyHttpMethod(String method) {
        return HttpMethod.valueOf(method.toUpperCase());
    }

}
