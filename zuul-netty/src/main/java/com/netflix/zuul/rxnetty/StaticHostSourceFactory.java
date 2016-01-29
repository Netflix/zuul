package com.netflix.zuul.rxnetty;

import io.reactivex.netty.client.Host;
import rx.Observable;

import java.net.SocketAddress;

public class StaticHostSourceFactory implements HostSourceFactory {

    private final Observable<Host> source;

    public StaticHostSourceFactory(SocketAddress host) {
        this.source = Observable.just(new Host(host));
    }

    @Override
    public Observable<Host> call(String s) {
        return source;
    }
}
