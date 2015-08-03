package com.netflix.zuul.rxnetty;

import com.netflix.zuul.context.SessionCleaner;
import com.netflix.zuul.context.SessionContext;
import io.reactivex.netty.protocol.http.client.HttpClientResponse;
import rx.Observable;

/**
 * User: michaels@netflix.com
 * Date: 8/3/15
 * Time: 12:39 PM
 */
public class RxNettySessionCleaner implements SessionCleaner
{
    @Override
    public Observable<Void> cleanup(SessionContext context)
    {
        HttpClientResponse rxnettyResp = (HttpClientResponse) context.get("_rxnettyResp");
        if (rxnettyResp != null) {
            // TODO - HOW TO RELEASE CONNECTION RESOURCES OF AN RXNETTY RESPONSE OBJECT?
        }
        return Observable.empty();
    }
}
