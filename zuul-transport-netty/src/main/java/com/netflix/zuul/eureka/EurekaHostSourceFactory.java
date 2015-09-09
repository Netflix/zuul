package com.netflix.zuul.eureka;

import com.netflix.appinfo.InstanceInfo;
import com.netflix.zuul.rxnetty.origin.HostSourceFactory;
import netflix.ocelli.Instance;
import netflix.ocelli.eureka.EurekaInterestManager;
import rx.Observable;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

public class EurekaHostSourceFactory implements HostSourceFactory {

    private final EurekaInterestManager eurekaInterestManager;

    public EurekaHostSourceFactory(EurekaInterestManager eurekaInterestManager) {
        this.eurekaInterestManager = eurekaInterestManager;
    }

    @Override
    public Observable<Instance<SocketAddress>> call(String vip) {
        return eurekaInterestManager.newInterest()
                                    .forVip(vip)
                                    .asObservable()
                                    .map(instance -> {
                                        final InstanceInfo ii = instance.getValue();
                                        final SocketAddress addr = toSocketAddress(ii);
                                        return new Instance<SocketAddress>() {
                                            @Override
                                            public Observable<Void> getLifecycle() {
                                                return instance.getLifecycle();
                                            }

                                            @Override
                                            public SocketAddress getValue() {
                                                return addr;
                                            }
                                        };
                                    });
    }

    protected SocketAddress toSocketAddress(InstanceInfo instanceInfo) {
        return new InetSocketAddress(instanceInfo.getIPAddr(), instanceInfo.getPort());
    }
}
