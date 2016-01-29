package com.netflix.zuul.rxnetty;

import io.reactivex.netty.client.Host;
import rx.Observable;
import rx.functions.Func1;

public interface HostSourceFactory extends Func1<String, Observable<Host>> {
}
