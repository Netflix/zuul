package com.netflix.zuul.rxnetty;

import com.netflix.zuul.context.Headers;
import com.netflix.zuul.context.HttpRequestMessage;
import com.netflix.zuul.context.HttpResponseMessage;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.HttpMethod;
import io.reactivex.netty.protocol.http.client.HttpClientRequest;
import io.reactivex.netty.protocol.http.client.HttpClientResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.subjects.PublishSubject;

import java.io.ByteArrayOutputStream;
import java.util.Map;

/**
 * User: Mike Smith
 * Date: 3/31/15
 * Time: 3:27 PM
 */
public class RxNettyUtils
{
    private static final Logger LOG = LoggerFactory.getLogger(RxNettyUtils.class);

    public static byte[] byteBufToBytes(ByteBuf bb)
    {
        // Set the body on Request object.
        try {
            int size = bb.readableBytes();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            bb.getBytes(0, baos, size);
            return baos.toByteArray();
        }
        catch (Exception e) {
            throw new RuntimeException("Error buffering message body!", e);
        }
    }

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

        if (zuulReq.getBody() != null) {
            clientReq = clientReq.withContent(zuulReq.getBody());
        }

        return clientReq;
    }

    public static Observable<HttpResponseMessage> bufferHttpClientResponse(HttpRequestMessage zuulReq,
                                                                           Observable<HttpClientResponse<ByteBuf>> clientResp)
    {
        return clientResp.flatMap(resp -> {

            HttpResponseMessage zuulResp = RxNettyUtils.clientResponseToZuulResponse(zuulReq, resp);

            PublishSubject<ByteBuf> cachedContent = PublishSubject.create();
            //UnicastDisposableCachingSubject<ByteBuf> cachedContent = UnicastDisposableCachingSubject.create();

            // Only apply the filters once the request body has been fully read and buffered.
            Observable<ByteBuf> content = resp.getContent();

            // Subscribe to the response-content observable (retaining the ByteBufS first).
            content.map(ByteBuf::retain).subscribe(cachedContent);

            // use reduce() to create a virtual ByteBuf to buffer all of the response body before continuing.
            return cachedContent
                    .reduce((bb1, bb2) -> {
                        // Buffer the request body into a single virtual ByteBuf.
                        // TODO - apply some max size to this.
                        return Unpooled.wrappedBuffer(bb1, bb2);

                    }).map(bb -> {
                        // Set the body on Response object.
                        byte[] body = RxNettyUtils.byteBufToBytes(bb);
                        zuulResp.setBody(body);
                        return zuulResp;
                    });
        });
    }
}