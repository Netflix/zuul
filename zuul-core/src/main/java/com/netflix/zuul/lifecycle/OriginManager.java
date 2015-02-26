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

    private final LoadBalancerFactory loadBalancerFactory;

    @Inject
    public OriginManager(LoadBalancerFactory loadBalancerFactory)
    {
        if (loadBalancerFactory == null) {
            throw new IllegalArgumentException("OriginManager.loadBalancerFactory is null.");
        }
        this.loadBalancerFactory = loadBalancerFactory;

        try {
            String[] vips = ORIGIN_VIPS.get().split(",");
            for (String vip : vips) {
                if (vip != null) {
                    vip = vip.trim();
                    if (vip.length() > 0) {
                        LOG.info("Registering Origin for vip=" + vip);
                        try {
                            LoadBalancer lb = loadBalancerFactory.create(vip);
                            this.origins.put(vip, new Origin(vip, lb));
                        } catch (Exception e) {
                            // TODO - resolve why this is failing on first attempts at startup.
                            LOG.error("Error creating loadbalancer for vip=" + vip, e);
                        }
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
