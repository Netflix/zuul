package com.netflix.zuul.lifecycle;

import com.netflix.zuul.ZuulException;
import com.netflix.zuul.context.RequestContext;
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

    public static byte[] byteBufToBytes(ByteBuf bb) throws ZuulException
    {
        // Set the body on Request object.
        try {
            int size = bb.readableBytes();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            bb.getBytes(0, baos, size);
            return baos.toByteArray();
        }
        catch (Exception e) {
            throw new ZuulException("Error buffering message body!", e, 500);
        }
    }

    public static void clientResponseToZuulResponse(HttpClientResponse<ByteBuf> resp, RequestContext ctx)
    {
        // Copy the response headers and info into the RequestContext for use by post filters.
        HttpResponseMessage zuulResp = (HttpResponseMessage) ctx.getResponse();
        if (resp.getStatus() != null) {
            zuulResp.setStatus(resp.getStatus().code());
        }
        Headers zuulRespHeaders = zuulResp.getHeaders();
        for (Map.Entry<String, String> entry : resp.getHeaders().entries()) {
            zuulRespHeaders.add(entry.getKey(), entry.getValue());
        }

        ctx.getAttributes().put("origin_http_status", Integer.toString(resp.getStatus().code()));
    }

    public static HttpClientRequest<ByteBuf> createHttpClientRequest(RequestContext ctx)
    {
        HttpRequestMessage zuulReq = (HttpRequestMessage) ctx.getRequest();

        HttpClientRequest<ByteBuf> clientReq = HttpClientRequest.create(HttpMethod.valueOf(zuulReq.getMethod()), zuulReq.getUri());

        for (Map.Entry<String, String> entry : zuulReq.getHeaders().entries()) {
            clientReq = clientReq.withHeader(entry.getKey(), entry.getValue());
        }

        clientReq = clientReq.withContent(zuulReq.getBody());

        return clientReq;
    }

    public static Observable<RequestContext> bufferHttpClientResponse(
            Observable<HttpClientResponse<ByteBuf>> clientResp, RequestContext ctx)
    {
        return clientResp.flatMap(resp -> {

            RxNettyUtils.clientResponseToZuulResponse(resp, ctx);

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
                        ctx.getResponse().setBody(body);
                        return ctx;
                    });
        });
    }
}
