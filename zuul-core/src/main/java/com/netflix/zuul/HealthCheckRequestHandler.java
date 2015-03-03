package com.netflix.zuul;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.reactivex.netty.protocol.http.server.HttpServerRequest;
import io.reactivex.netty.protocol.http.server.HttpServerResponse;
import io.reactivex.netty.protocol.http.server.RequestHandler;
import rx.Observable;

/**
 * User: Mike Smith
 * Date: 3/3/15
 * Time: 12:42 PM
 */
public class HealthCheckRequestHandler implements RequestHandler<ByteBuf, ByteBuf>
{
    @Override
    public Observable<Void> handle(HttpServerRequest<ByteBuf> request, HttpServerResponse<ByteBuf> response)
    {
        response.getHeaders().set("Content-Type", "text/plain");
        if (StartupState.getInstance().isStarted()) {
            response.setStatus(HttpResponseStatus.OK);
            response.writeString("OK");
        }
        else {
            response.setStatus(HttpResponseStatus.SERVICE_UNAVAILABLE);
            response.writeString("NOT STARTED");
        }
        return response.close();
    }
}
