package com.netflix.zuul.lifecycle;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.netflix.config.DynamicStringMapProperty;
import com.netflix.zuul.metrics.OriginStats;
import com.netflix.zuul.metrics.OriginStatsFactory;
import io.reactivex.netty.metrics.MetricEventsListenerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
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
    private final DynamicStringMapProperty ORIGINS = new DynamicStringMapProperty("zuul.origins", "");

    private final Map<String, Origin> origins = new ConcurrentHashMap<>();

    private final LoadBalancerFactory loadBalancerFactory;
    private final OriginStatsFactory originStatsFactory;
    private final MetricEventsListenerFactory metricEventsListenerFactory;

    @Inject
    public OriginManager(LoadBalancerFactory loadBalancerFactory, OriginStatsFactory originStatsFactory,
                         MetricEventsListenerFactory metricEventsListenerFactory)
    {
        if (loadBalancerFactory == null) {
            throw new IllegalArgumentException("OriginManager.loadBalancerFactory is null.");
        }
        this.loadBalancerFactory = loadBalancerFactory;
        this.originStatsFactory = originStatsFactory;
        this.metricEventsListenerFactory = metricEventsListenerFactory;
    }

    // TODO - @WarmUp
    @PostConstruct
    public void initialize()
    {
        try {
            Map<String, String> originMappings = ORIGINS.getMap();
            for (String originName : originMappings.keySet())
            {
                String vip = originMappings.get(originName);
                if (vip != null) {
                    vip = vip.trim();
                    if (vip.length() > 0) {
                        LOG.info("Registering Origin for vip=" + vip);
                        try {
                            LoadBalancer lb = loadBalancerFactory.create(vip);

                            OriginStats originStats = null;
                            if (originStatsFactory != null) {
                                originStats = originStatsFactory.create(originName);
                            }

                            this.origins.put(originName, new Origin(originName, vip, lb, originStats, metricEventsListenerFactory));
                        }
                        catch (Exception e) {
                            // TODO - resolve why this is failing on first attempts at startup.
                            LOG.error("Error creating loadbalancer for vip=" + vip, e);
                        }
                    }
                }
            }
        }
        catch(Exception e) {
            String msg = "Error initialising OriginManager. zuul.origins property=" + String.valueOf(ORIGINS.get());
            LOG.error(msg, e);
            throw new IllegalStateException(msg, e);
        }
    }

    public Origin getOrigin(String vip)
    {
        return origins.get(vip);
    }
}
