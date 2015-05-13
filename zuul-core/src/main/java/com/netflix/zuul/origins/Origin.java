package com.netflix.zuul.origins;

import com.netflix.zuul.context.HttpRequestMessage;
import com.netflix.zuul.context.HttpResponseMessage;
import com.netflix.zuul.context.SessionContext;
import rx.Observable;

/**
 * User: michaels@netflix.com
 * Date: 5/11/15
 * Time: 3:14 PM
 */
public interface Origin {
    String getName();
    Observable<SessionContext> request(SessionContext ctx);
    Observable<HttpResponseMessage> request(HttpRequestMessage requestMsg);
}
