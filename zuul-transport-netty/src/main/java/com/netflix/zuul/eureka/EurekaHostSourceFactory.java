package com.netflix.zuul.eureka;

import com.netflix.appinfo.InstanceInfo;
import com.netflix.zuul.rxnetty.origin.HostSourceFactory;
import netflix.ocelli.Instance;
import netflix.ocelli.eureka.EurekaInterestManager;
import netflix.ocelli.eureka.EurekaInterestManager.InterestDsl;
import rx.Observable;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

public class EurekaHostSourceFactory implements HostSourceFactory {

    private final EurekaInterestManager eurekaInterestManager;
    private final String region;

    public EurekaHostSourceFactory(EurekaInterestManager eurekaInterestManager) {
        this.eurekaInterestManager = eurekaInterestManager;
        this.region = null;
    }

    public EurekaHostSourceFactory(EurekaInterestManager eurekaInterestManager, String region) {
        if (null == eurekaInterestManager) {
            throw new NullPointerException("Interest manager can not be null.");
        }
        if (null == region) {
            throw new NullPointerException("Region can not be null.");
        }
        this.eurekaInterestManager = eurekaInterestManager;
        this.region = region;
    }

    @Override
    public Observable<Instance<SocketAddress>> call(String vip) {
        return getInterestDsl().forVip(vip)
                               .asObservable()
                               .filter(ii ->  ii != null)
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

    protected InterestDsl getInterestDsl() {
        if (null == region) {
            return eurekaInterestManager.newInterest();
        } else {
            return eurekaInterestManager.newInterest().forRegion(region);
        }
    }

    protected SocketAddress toSocketAddress(InstanceInfo instanceInfo) {
        return new InetSocketAddress(instanceInfo.getIPAddr(), instanceInfo.getPort());
    }
}
