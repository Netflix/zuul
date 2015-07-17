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

import com.netflix.zuul.message.Headers;
import com.netflix.zuul.message.http.HttpRequestMessage;
import com.netflix.zuul.message.http.HttpResponseMessage;
import com.netflix.zuul.message.http.HttpResponseMessageImpl;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.HttpMethod;
import io.reactivex.netty.protocol.http.client.HttpClientRequest;
import io.reactivex.netty.protocol.http.client.HttpClientResponse;

import java.util.Map;

/**
 * User: Mike Smith
 * Date: 3/31/15
 * Time: 3:27 PM
 */
public class RxNettyUtils
{
    public static HttpResponseMessage clientResponseToZuulResponse(HttpRequestMessage zuulRequest, HttpClientResponse<ByteBuf> resp)
    {
        HttpResponseMessage zuulResp = new HttpResponseMessageImpl(zuulRequest.getContext(), zuulRequest, 500);

        // Copy the response headers and info into the RequestContext for use by post filters.
        if (resp.getStatus() != null) {
            zuulResp.setStatus(resp.getStatus().code());
        }
        Headers zuulRespHeaders = zuulResp.getHeaders();
        for (Map.Entry<String, String> entry : resp.getHeaders().entries()) {
            // TODO - should we be filtering headers here like we do when using Ribbon?
            zuulRespHeaders.add(entry.getKey(), entry.getValue());
        }

        // Store this original response info for future reference (ie. for metrics and access logging purposes).
        zuulResp.storeInboundResponse();

        return zuulResp;
    }

    public static HttpClientRequest<ByteBuf> createHttpClientRequest(HttpRequestMessage zuulReq)
    {
        HttpClientRequest<ByteBuf> clientReq = HttpClientRequest.create(HttpMethod.valueOf(zuulReq.getMethod().toUpperCase()), zuulReq.getPathAndQuery());

        for (Map.Entry<String, String> entry : zuulReq.getHeaders().entries()) {
            // TODO - should we be filtering headers here like we do when using Ribbon?
            clientReq = clientReq.withHeader(entry.getKey(), entry.getValue());
        }

        if (zuulReq.getBodyStream() != null) {
            clientReq = clientReq.withContentSource(zuulReq.getBodyStream());
        }

        return clientReq;
    }


}