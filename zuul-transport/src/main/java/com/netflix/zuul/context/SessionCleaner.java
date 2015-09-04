package com.netflix.zuul.context;

import rx.Observable;

/**
 * User: michaels@netflix.com
 * Date: 8/3/15
 * Time: 12:30 PM
 */
public interface SessionCleaner
{
    Observable<Void> cleanup(SessionContext context);
}
