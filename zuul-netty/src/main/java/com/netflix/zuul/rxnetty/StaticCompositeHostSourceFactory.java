package com.netflix.zuul.rxnetty;

import io.reactivex.netty.client.Host;
import rx.Observable;

import java.util.Map;

public class StaticCompositeHostSourceFactory implements HostSourceFactory {

    private final Map<String, StaticHostSourceFactory> vipVsFactory;

    public StaticCompositeHostSourceFactory(Map<String, StaticHostSourceFactory> vipVsFactory) {
        this.vipVsFactory = vipVsFactory;
    }

    @Override
    public Observable<Host> call(String vip) {
        StaticHostSourceFactory f = vipVsFactory.get(vip);
        if (null == f) {
            return Observable.error(new IllegalArgumentException("VIP " + vip + " is not registered"));
        }
        return f.call(vip);
    }
}
