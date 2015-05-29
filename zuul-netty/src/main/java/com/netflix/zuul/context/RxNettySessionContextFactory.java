package com.netflix.zuul.context;

import com.netflix.config.DynamicIntProperty;
import com.netflix.config.DynamicPropertyFactory;
import com.netflix.zuul.rxnetty.UnicastDisposableCachingSubject;
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
public class RxNettySessionContextFactory implements SessionContextFactory<HttpServerRequest, HttpServerResponse>
{
    private static final Logger LOG = LoggerFactory.getLogger(RxNettySessionContextFactory.class);

    private static final DynamicIntProperty MAX_REQ_BODY_SIZE_PROP = DynamicPropertyFactory.getInstance().getIntProperty(
            "zuul.request.body.max.size", 25 * 1000 * 1024);

    @Override
    public Observable<ZuulMessage> create(SessionContext context, HttpServerRequest httpServerRequest)
    {
        // Get the client IP (ignore XFF headers at this point, as that can be app specific).
        String clientIp = getIpAddress(httpServerRequest.getNettyChannel());

        // TODO - How to get uri scheme from the netty request?
        String scheme = "http";

        // Setup the req/resp message objects.
        HttpRequestMessage request = new HttpRequestMessage(
                context,
                httpServerRequest.getHttpVersion().text(),
                httpServerRequest.getHttpMethod().name().toLowerCase(),
                httpServerRequest.getUri(),
                copyQueryParams(httpServerRequest),
                copyHeaders(httpServerRequest),
                clientIp,
                scheme
        );

        return wrapBody(request, httpServerRequest);
    }

    @Override
    public Observable<ZuulMessage> write(ZuulMessage msg, HttpServerResponse nativeResponse)
    {
        HttpResponseMessage zuulResp = (HttpResponseMessage) msg;

        // Set the response status code.
        nativeResponse.setStatus(HttpResponseStatus.valueOf(zuulResp.getStatus()));

        // Now set all of the response headers - note this is a multi-set in keeping with HTTP semantics
        for (Map.Entry<String, String> entry : zuulResp.getHeaders().entries()) {
            nativeResponse.getHeaders().add(entry.getKey(), entry.getValue());
        }

        // Write response body stream as received.
        Observable<ZuulMessage> chain;
        Observable<ByteBuf> bodyStream = zuulResp.getBodyStream();
        if (bodyStream != null) {
            chain = bodyStream
                    .doOnNext(bb -> nativeResponse.writeBytesAndFlush(bb))
                    .ignoreElements()
                    .doOnCompleted(() -> nativeResponse.close())
                    .map(bb -> msg);
        }
        else {
            chain = Observable.just(msg);
        }
        return chain;
    }


    private Observable<ZuulMessage> wrapBody(HttpRequestMessage request, HttpServerRequest<ByteBuf> nettyServerRequest)
    {
        //PublishSubject<ByteBuf> cachedContent = PublishSubject.create();
        UnicastDisposableCachingSubject<ByteBuf> cachedContent = UnicastDisposableCachingSubject.create();

        // Subscribe to the response-content observable (retaining the ByteBufS first).
        nettyServerRequest.getContent().map(ByteBuf::retain).subscribe(cachedContent);

        request.setBodyStream(cachedContent);
        return Observable.just(request);
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
