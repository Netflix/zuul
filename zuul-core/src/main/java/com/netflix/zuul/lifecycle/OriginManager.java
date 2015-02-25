package com.netflix.zuul.lifecycle;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.netflix.config.DynamicPropertyFactory;
import com.netflix.config.DynamicStringProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * User: michaels@netflix.com
 * Date: 2/20/15
 * Time: 3:04 PM
 */
@Singleton
public class OriginManager
{
    private final static Logger LOG = LoggerFactory.getLogger(OriginManager.class);
    private final DynamicStringProperty ORIGIN_VIPS = DynamicPropertyFactory.getInstance().getStringProperty("zuul.origins", "");
    private final Map<String, Origin> origins = new ConcurrentHashMap<>();

    @Inject
    private LoadBalancerFactory loadBalancerFactory;

    public OriginManager()
    {
        try {
            String[] vips = ORIGIN_VIPS.get().split(",");
            for (String vip : vips) {
                if (vip != null) {
                    vip = vip.trim();
                    if (vip.length() > 0) {
                        LOG.info("Registering Origin for vip=" + vip);
                        LoadBalancer lb = loadBalancerFactory.create(vip);
                        this.origins.put(vip, new Origin(vip, lb));
                    }
                }
            }
        }
        catch(Exception e) {
            String msg = "Error initialising OriginManager. origin.vips property=" + String.valueOf(ORIGIN_VIPS.get());
            LOG.error(msg, e);
            throw new IllegalStateException(msg, e);
        }
    }

    public Origin getOrigin(String vip)
    {
        return origins.get(vip);
    }
}
