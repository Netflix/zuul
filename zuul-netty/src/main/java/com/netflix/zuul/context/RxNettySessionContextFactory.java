package com.netflix.zuul.context;

import com.google.inject.Inject;
import com.netflix.zuul.rxnetty.RxNettyUtils;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.reactivex.netty.protocol.http.server.HttpServerRequest;
import io.reactivex.netty.protocol.http.server.HttpServerResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.subjects.PublishSubject;

import javax.annotation.Nullable;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.List;
import java.util.Map;

/**
 * User: michaels@netflix.com
 * Date: 2/25/15
 * Time: 4:03 PM
 */
public class RxNettySessionContextFactory implements SessionContextFactory<HttpServerRequest, HttpServerResponse>
{
    private static final Logger LOG = LoggerFactory.getLogger(RxNettySessionContextFactory.class);

    private SessionContextDecorator decorator;

    @Inject
    public RxNettySessionContextFactory(@Nullable SessionContextDecorator decorator) {
        this.decorator = decorator;
    }

    @Override
    public Observable<SessionContext> create(HttpServerRequest httpServerRequest)
    {
        // Get the client IP (ignore XFF headers at this point, as that can be app specific).
        String clientIp = getIpAddress(httpServerRequest.getNettyChannel());

        // TODO - How to get uri scheme from netty request?
        String scheme = "http";

        // Setup the req/resp message objects.
        HttpRequestMessage httpReqMsg = new HttpRequestMessage(
                httpServerRequest.getHttpVersion().text(),
                httpServerRequest.getHttpMethod().name().toLowerCase(),
                httpServerRequest.getUri(),
                copyQueryParams(httpServerRequest),
                copyHeaders(httpServerRequest),
                clientIp,
                scheme
        );
        HttpResponseMessage httpRespMsg = new HttpResponseMessage(500);

        // Create the new context object.
        SessionContext ctx = new SessionContext(httpReqMsg, httpRespMsg);
        ctx.getAttributes().set("_nettyHttpServerRequest", httpServerRequest);

        // Optionally decorate it.
        if (decorator != null) {
            ctx = decorator.decorate(ctx);
        }

        // Buffer the request body, and wrap in an Observable.
        return toObservable(ctx);
    }

    @Override
    public void write(SessionContext ctx, HttpServerResponse nativeResponse)
    {
        HttpResponseMessage zuulResp = ctx.getHttpResponse();

        // Set the response status code.
        nativeResponse.setStatus(HttpResponseStatus.valueOf(zuulResp.getStatus()));

        // Now set all of the response headers - note this is a multi-set in keeping with HTTP semantics
        for (Map.Entry<String, String> entry : zuulResp.getHeaders().entries()) {
            nativeResponse.getHeaders().add(entry.getKey(), entry.getValue());
        }

        // Write response body bytes.
        if (zuulResp.getBody() != null) {
            nativeResponse.write(Unpooled.wrappedBuffer(zuulResp.getBody()));
        }
    }

    private Observable<SessionContext> toObservable(SessionContext ctx)
    {
        PublishSubject<ByteBuf> cachedContent = PublishSubject.create();
        //UnicastDisposableCachingSubject<ByteBuf> cachedContent = UnicastDisposableCachingSubject.create();

        // Subscribe to the response-content observable (retaining the ByteBufS first).
        HttpServerRequest<ByteBuf> nettyServerRequest = (HttpServerRequest<ByteBuf>) ctx.getAttributes().get("_nettyHttpServerRequest");
        nettyServerRequest.getContent().map(ByteBuf::retain).subscribe(cachedContent);

        // Only apply the filters once the request body has been fully read and buffered.
        Observable<SessionContext> chain = cachedContent
                .reduce((bb1, bb2) -> {
                    // Buffer the request body into a single virtual ByteBuf.
                    // TODO - apply some max size to this.
                    return Unpooled.wrappedBuffer(bb1, bb2);

                })
                .map(bodyBuffer -> {
                    // Set the body on Request object.
                    byte[] body = RxNettyUtils.byteBufToBytes(bodyBuffer);
                    ctx.getRequest().setBody(body);

                    // Release the ByteBufS
                    if (bodyBuffer.refCnt() > 0) {
                        if (LOG.isDebugEnabled()) LOG.debug("Releasing the server-request ByteBuf.");
                        bodyBuffer.release();
                    }
                    return ctx;
                });

        return chain;
    }

    private Headers copyHeaders(HttpServerRequest httpServerRequest)
    {
        Headers headers = new Headers();
        for (Map.Entry<String, String> entry : httpServerRequest.getHeaders().entries()) {
            headers.add(entry.getKey(), entry.getValue());
        }
        return headers;
    }

    private HttpQueryParams copyQueryParams(HttpServerRequest httpServerRequest)
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
