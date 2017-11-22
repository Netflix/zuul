/*
 * Copyright 2018 Netflix, Inc.
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

package com.netflix.netty.common.ssl;

import io.netty.handler.ssl.ClientAuth;

import javax.security.cert.X509Certificate;
import java.security.cert.Certificate;

/**
 * User: michaels@netflix.com
 * Date: 3/29/16
 * Time: 11:06 AM
 */
public class SslHandshakeInfo
{
    private final String protocol;
    private final String cipherSuite;
    private final ClientAuth clientAuthRequirement;
    private final Certificate serverCertificate;
    private final X509Certificate clientCertificate;
    private final boolean isOfIntermediary;

    public SslHandshakeInfo(boolean isOfIntermediary, String protocol, String cipherSuite, Certificate serverCertificate)
    {
        this.protocol = protocol;
        this.cipherSuite = cipherSuite;
        this.isOfIntermediary = isOfIntermediary;
        this.serverCertificate = serverCertificate;
        this.clientAuthRequirement = ClientAuth.NONE;
        this.clientCertificate = null;
    }

    public SslHandshakeInfo(boolean isOfIntermediary, String protocol, String cipherSuite, ClientAuth clientAuthRequirement,
                            Certificate serverCertificate, X509Certificate clientCertificate)
    {
        this.protocol = protocol;
        this.cipherSuite = cipherSuite;
        this.clientAuthRequirement = clientAuthRequirement;
        this.serverCertificate = serverCertificate;
        this.clientCertificate = clientCertificate;
        this.isOfIntermediary = isOfIntermediary;
    }

    public boolean isOfIntermediary()
    {
        return isOfIntermediary;
    }

    public String getProtocol()
    {
        return protocol;
    }

    public String getCipherSuite()
    {
        return cipherSuite;
    }

    public ClientAuth getClientAuthRequirement()
    {
        return clientAuthRequirement;
    }

    public Certificate getServerCertificate()
    {
        return serverCertificate;
    }

    public X509Certificate getClientCertificate()
    {
        return clientCertificate;
    }

    @Override
    public String toString()
    {
        return "SslHandshakeInfo{" +
                "protocol='" + protocol + '\'' +
                ", cipherSuite='" + cipherSuite + '\'' +
                ", clientAuthRequirement=" + clientAuthRequirement +
                ", serverCertificate=" + serverCertificate +
                ", clientCertificate=" + clientCertificate +
                ", isOfIntermediary=" + isOfIntermediary +
                '}';
    }
}
