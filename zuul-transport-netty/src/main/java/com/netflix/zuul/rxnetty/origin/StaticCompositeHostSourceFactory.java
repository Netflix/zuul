package com.netflix.zuul.rxnetty.origin;

import netflix.ocelli.Instance;
import rx.Observable;

import java.net.SocketAddress;
import java.util.Map;

public class StaticCompositeHostSourceFactory implements HostSourceFactory {

    private final Map<String, StaticHostSourceFactory> vipVsFactory;

    public StaticCompositeHostSourceFactory(Map<String, StaticHostSourceFactory> vipVsFactory) {
        this.vipVsFactory = vipVsFactory;
    }

    @Override
    public Observable<Instance<SocketAddress>> call(String vip) {
        StaticHostSourceFactory f = vipVsFactory.get(vip);
        if (null == f) {
            return Observable.error(new IllegalArgumentException("VIP " + vip + " is not registered"));
        }
        return f.call(vip);
    }
}
