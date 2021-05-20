/*
 * Copyright 2020 Netflix, Inc.
 *
 *      Licensed under the Apache License, Version 2.0 (the "License");
 *      you may not use this file except in compliance with the License.
 *      You may obtain a copy of the License at
 *
 *          http://www.apache.org/licenses/LICENSE-2.0
 *
 *      Unless required by applicable law or agreed to in writing, software
 *      distributed under the License is distributed on an "AS IS" BASIS,
 *      WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *      See the License for the specific language governing permissions and
 *      limitations under the License.
 */

package com.netflix.zuul.origins;

import com.netflix.zuul.util.VipUtils;
import java.util.Locale;
import java.util.Objects;

public final class OriginName {
    /**
     * The NIWS client name of the origin.  This is typically used in metrics and for configuration of NIWS
     * {@link com.netflix.client.config.IClientConfig} objects.
     */
    private final String niwsClientName;

    /**
     * This should not be used in {@link #equals} or {@link #hashCode} as it is already covered by
     * {@link #niwsClientName}.
     */
    private final String metricId;

    /**
     * The target to connect to, used for name resolution.  This is typically the VIP.
     */
    private final String target;
    /**
     * The authority of this origin.  Usually this is the Application name of origin.  It is primarily
     * used for establishing a secure connection, as well as logging.
     */
    private final String authority;

    /**
     * @deprecated use {@link #fromVipAndApp(String, String)}
     */
    @Deprecated
    public static OriginName fromVip(String vip) {
        return fromVipAndApp(vip, VipUtils.extractUntrustedAppNameFromVIP(vip));
    }

    /**
     * @deprecated use {@link #fromVipAndApp(String, String, String)}
     */
    @Deprecated
    public static OriginName fromVip(String vip, String niwsClientName) {
        return fromVipAndApp(vip, VipUtils.extractUntrustedAppNameFromVIP(vip), niwsClientName);
    }

    public static OriginName fromVipAndApp(String vip, String appName) {
        return fromVipAndApp(vip, appName, vip);
    }

    public static OriginName fromVipAndApp(String vip, String appName, String niwsClientName) {
        return new OriginName(vip, appName, niwsClientName);
    }

    private OriginName(String target, String authority, String niwsClientName) {
        this.target = Objects.requireNonNull(target, "target");
        this.authority = Objects.requireNonNull(authority, "authority");
        this.niwsClientName = Objects.requireNonNull(niwsClientName, "niwsClientName");
        this.metricId = niwsClientName.toLowerCase(Locale.ROOT);
    }

    /**
     * This is typically the VIP for the given Origin.
     */
    public String getTarget() {
        return target;
    }

    /**
     * Returns the niwsClientName.   This is normally used for interaction with NIWS, and should be used without prior
     * knowledge that the value will be used in NIWS libraries.
     */
    public String getNiwsClientName() {
        return niwsClientName;
    }

    /**
     * Returns the identifier for this this metric name.  This may be different than any of the other
     * fields; currently it is equivalent to the lowercased {@link #getNiwsClientName()}.
     */
    public String getMetricId() {
        return metricId;
    }

    /**
     * Returns the Authority of this origin.   This is used for establishing secure connections.  May be absent
     * if the authority is not trusted.
     */
    public String getAuthority() {
        return authority;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof OriginName)) {
            return false;
        }
        OriginName that = (OriginName) o;
        return Objects.equals(niwsClientName, that.niwsClientName)
                && Objects.equals(target, that.target)
                && Objects.equals(authority, that.authority);
    }

    @Override
    public int hashCode() {
        return Objects.hash(niwsClientName, target, authority);
    }

    @Override
    public String toString() {
        return "OriginName{" +
                "niwsClientName='" + niwsClientName + '\'' +
                ", target='" + target + '\'' +
                ", authority='" + authority + '\'' +
                '}';
    }
}
