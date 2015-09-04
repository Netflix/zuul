package com.netflix.zuul.ribbon;

import com.netflix.client.http.HttpResponse;
import com.netflix.zuul.context.SessionCleaner;
import com.netflix.zuul.context.SessionContext;
import rx.Observable;

import javax.inject.Singleton;

/**
 * User: michaels@netflix.com
 * Date: 8/3/15
 * Time: 12:31 PM
 */
@Singleton
public class RibbonSessionCleaner implements SessionCleaner
{
    @Override
    public Observable<Void> cleanup(SessionContext context)
    {
        HttpResponse ribbonResp = (HttpResponse) context.get("_ribbonResp");
        if (ribbonResp != null) {
            ribbonResp.close();
        }
        return Observable.empty();
    }
}
