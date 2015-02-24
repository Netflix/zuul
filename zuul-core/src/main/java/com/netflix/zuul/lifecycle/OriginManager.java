package com.netflix.zuul.lifecycle;

import com.google.inject.Singleton;
import com.netflix.config.FastProperty;
import com.netflix.logging.ILog;
import com.netflix.logging.LogManager;

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
    private final static ILog LOG = LogManager.getLogger(OriginManager.class);
    private final FastProperty.StringProperty ORIGIN_VIPS = new FastProperty.StringProperty("zuul.origins", "");
    private final Map<String, Origin> origins = new ConcurrentHashMap<>();

    public void init()
    {
        String[] vips = ORIGIN_VIPS.get().split(",");
        for (String vip : vips) {
            if (vip != null) {
                vip = vip.trim();
                if (vip.length() > 0) {
                    LOG.info("Registering Origin for vip=" + vip);
                    this.origins.put(vip, new Origin(vip));
                }
            }
        }
    }

    public Origin getOrigin(String vip)
    {
        return origins.get(vip);
    }
}
