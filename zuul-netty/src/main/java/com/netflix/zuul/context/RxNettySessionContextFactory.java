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
package com.netflix.zuul.context;

import com.netflix.zuul.message.Header;
import com.netflix.zuul.message.HeaderName;
import com.netflix.zuul.message.Headers;
import com.netflix.zuul.message.ZuulMessage;
import com.netflix.zuul.message.http.*;
import com.netflix.zuul.rx.UnicastDisposableCachingSubject;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.reactivex.netty.protocol.http.server.HttpServerRequest;
import io.reactivex.netty.protocol.http.server.HttpServerResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.List;
import java.util.Map;

/**
 * User: michaels@netflix.com
 * Date: 2/25/15
 * Time: 4:03 PM
 */
public class RxNettySessionContextFactory
        implements SessionContextFactory<HttpServerRequest<ByteBuf>, HttpServerResponse<ByteBuf>> {

    private final String scheme;

    public RxNettySessionContextFactory() {
        this("http");
    }

    public RxNettySessionContextFactory(String scheme) {
        this.scheme = scheme;
    }

    @Override
    public ZuulMessage create(SessionContext context, HttpServerRequest<ByteBuf> req, HttpServerResponse<ByteBuf> resp)
    {
        // Get the client IP (ignore XFF headers at this point, as that can be app specific).
        String clientIp = getIpAddress(resp.unsafeNettyChannel());

        // This is the only way I found to get the port of the request with netty...
        int port = ((InetSocketAddress) resp.unsafeNettyChannel().localAddress()).getPort();
        String serverName = ((InetSocketAddress) resp.unsafeNettyChannel().localAddress()).getHostString();

        // Setup the req/resp message objects.
        HttpRequestMessage request = new HttpRequestMessageImpl(
                context,
                req.getHttpVersion().text(),
                req.getHttpMethod().name().toLowerCase(),
                req.getUri(),
                copyQueryParams(req),
                copyHeaders(req),
                clientIp,
                scheme,
                port,
                serverName
        );

        // Store this original request info for future reference (ie. for metrics and access logging purposes).
        request.storeInboundRequest();

        return wrapBody(request, req);
    }

    @Override
    public Observable<ZuulMessage> write(ZuulMessage msg, HttpServerResponse<ByteBuf> nativeResponse)
    {
        HttpResponseMessage zuulResp = (HttpResponseMessage) msg;

        // Set the response status code.
        nativeResponse = nativeResponse.setStatus(HttpResponseStatus.valueOf(zuulResp.getStatus()));

        // Now set all of the response headers - note this is a multi-set in keeping with HTTP semantics
        for (Header entry : zuulResp.getHeaders().entries()) {
            nativeResponse = nativeResponse.addHeader(entry.getKey(), entry.getValue());
        }

        return (zuulResp.hasBody() ? nativeResponse.write(zuulResp.getBodyStream()) : nativeResponse)
                .cast(ZuulMessage.class)
                .concatWith(Observable.just(msg));
    }


    private ZuulMessage wrapBody(HttpRequestMessage request, HttpServerRequest<ByteBuf> nettyServerRequest) {
        request.setBodyStream(nettyServerRequest.getContent());
        return request;
    }

    private Headers copyHeaders(HttpServerRequest<ByteBuf> req)
    {
        Headers headers = new Headers();

        req.getHeaderNames()
           .stream()
           .forEach(name -> {
               req.getAllHeaderValues(name)
                  .stream()
                  .forEach(value -> headers.add(name, value));
           });

        return headers;
    }

    private HttpQueryParams copyQueryParams(HttpServerRequest<ByteBuf> httpServerRequest)
    {
        HttpQueryParams queryParams = new HttpQueryParams();
        Map<String, List<String>> serverQueryParams = httpServerRequest.getQueryParameters();
        for (String key : serverQueryParams.keySet()) {
            for (String value : serverQueryParams.get(key)) {
                queryParams.add(key, value);
            }
        }
        return queryParams;
    }

    private static String getIpAddress(Channel channel) {
        if (null == channel) {
            return "";
        }

        SocketAddress localSocketAddress = channel.localAddress();
        if (null != localSocketAddress && InetSocketAddress.class.isAssignableFrom(localSocketAddress.getClass())) {
            InetSocketAddress inetSocketAddress = (InetSocketAddress) localSocketAddress;
            if (inetSocketAddress.getAddress() != null) {
                return inetSocketAddress.getAddress().getHostAddress();
            }
        }

        SocketAddress remoteSocketAddr = channel.remoteAddress();
        if (null != remoteSocketAddr && InetSocketAddress.class.isAssignableFrom(remoteSocketAddr.getClass())) {
            InetSocketAddress inetSocketAddress = (InetSocketAddress) remoteSocketAddr;
            if (inetSocketAddress.getAddress() != null) {
                return inetSocketAddress.getAddress().getHostAddress();
            }
        }

        return null;
    }
}
