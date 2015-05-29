package com.netflix.zuul.rxnetty;

import com.netflix.zuul.context.Headers;
import com.netflix.zuul.context.HttpRequestMessage;
import com.netflix.zuul.context.HttpResponseMessage;
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
        HttpResponseMessage zuulResp = new HttpResponseMessage(zuulRequest.getContext(), zuulRequest, 500);

        // Copy the response headers and info into the RequestContext for use by post filters.
        if (resp.getStatus() != null) {
            zuulResp.setStatus(resp.getStatus().code());
        }
        Headers zuulRespHeaders = zuulResp.getHeaders();
        for (Map.Entry<String, String> entry : resp.getHeaders().entries()) {
            zuulRespHeaders.add(entry.getKey(), entry.getValue());
        }

        return zuulResp;
    }

    public static HttpClientRequest<ByteBuf> createHttpClientRequest(HttpRequestMessage zuulReq)
    {
        HttpClientRequest<ByteBuf> clientReq = HttpClientRequest.create(HttpMethod.valueOf(zuulReq.getMethod().toUpperCase()), zuulReq.getPathAndQuery());

        for (Map.Entry<String, String> entry : zuulReq.getHeaders().entries()) {
            clientReq = clientReq.withHeader(entry.getKey(), entry.getValue());
        }

        if (zuulReq.getBodyStream() != null) {
            clientReq = clientReq.withContentSource(zuulReq.getBodyStream());
        }

        return clientReq;
    }


}