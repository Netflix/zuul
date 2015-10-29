package com.netflix.zuul.rxnetty;

import netflix.ocelli.Instance;
import rx.Observable;
import rx.functions.Func1;

import java.net.SocketAddress;

public interface HostSourceFactory extends Func1<String, Observable<Instance<SocketAddress>>> {
}
