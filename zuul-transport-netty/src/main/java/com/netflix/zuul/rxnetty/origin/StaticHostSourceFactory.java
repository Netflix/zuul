package com.netflix.zuul.rxnetty.origin;

import netflix.ocelli.Instance;
import rx.Observable;

import java.net.SocketAddress;

public class StaticHostSourceFactory implements HostSourceFactory {

    private final Observable<Instance<SocketAddress>> source;

    public StaticHostSourceFactory(SocketAddress host) {
        this.source = Observable.just(new Instance<SocketAddress>() {
            @Override
            public Observable<Void> getLifecycle() {
                return Observable.never();
            }

            @Override
            public SocketAddress getValue() {
                return host;
            }
        });
    }

    public StaticHostSourceFactory(SocketAddress... hosts) {
        this.source = Observable.from(hosts)
                                .map(host -> new Instance<SocketAddress>() {
                                    @Override
                                    public Observable<Void> getLifecycle() {
                                        return Observable.never();
                                    }

                                    @Override
                                    public SocketAddress getValue() {
                                        return host;
                                    }
                                });
    }

    @Override
    public Observable<Instance<SocketAddress>> call(String s) {
        return source;
    }
}
