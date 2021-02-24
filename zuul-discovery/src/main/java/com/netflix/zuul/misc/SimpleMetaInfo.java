package com.netflix.zuul.misc;

import com.netflix.loadbalancer.Server.MetaInfo;

/**
 * @author Argha C
 * @since 2/24/21
 */
public class SimpleMetaInfo {

    private final MetaInfo metaInfo;

    public SimpleMetaInfo(MetaInfo metaInfo) {
        this.metaInfo = metaInfo;
    }

    public String getServerGroup() {
        return metaInfo.getServerGroup();
    }

    public String getServiceIdForDiscovery() {
        return metaInfo.getServiceIdForDiscovery();
    }

    public String getInstanceId() {
        return metaInfo.getInstanceId();
    }
}
