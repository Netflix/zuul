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
import java.util.Objects;

public final class OriginName {
    /**
     * The NIWS client name of the origin.  This is typically used in metrics and for configuration of NIWS
     * {@link com.netflix.client.config.IClientConfig} objects.
     */
    private final String niwsClientName;

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
     * Indicates if the authority comes from a trusted source.
     */
    private final boolean authorityTrusted;

    public static OriginName fromVip(String vip) {
        return new OriginName(vip, vip, VipUtils.extractUntrustedAppNameFromVIP(vip), false);
    }

    private OriginName(String niwsClientName, String target, String authority, boolean authorityTrusted) {
        this.niwsClientName = Objects.requireNonNull(niwsClientName, "niwsClientName");
        this.target = Objects.requireNonNull(target, "target");
        this.authority = Objects.requireNonNull(authority, "authority");
        this.authorityTrusted = authorityTrusted;
    }

    /**
     * This is typically the VIP for the given Origin.
     */
    public String getTarget() {
        return target;
    }

    public String getNiwsClientName() {
        return niwsClientName;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof OriginName)) {
            return false;
        }
        OriginName that = (OriginName) o;
        return authorityTrusted == that.authorityTrusted
                && Objects.equals(niwsClientName, that.niwsClientName)
                && Objects.equals(target, that.target)
                && Objects.equals(authority, that.authority);
    }

    @Override
    public int hashCode() {
        return Objects.hash(niwsClientName, target, authority, authorityTrusted);
    }

    @Override
    public String toString() {
        return "OriginName{" +
                "niwsClientName='" + niwsClientName + '\'' +
                ", target='" + target + '\'' +
                ", authority='" + authority + '\'' +
                ", authorityTrusted=" + authorityTrusted +
                '}';
    }
}
