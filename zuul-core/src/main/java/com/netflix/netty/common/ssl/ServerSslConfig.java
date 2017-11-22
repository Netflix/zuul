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

import com.netflix.config.DynamicLongProperty;
import io.netty.handler.ssl.ClientAuth;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import java.io.File;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.List;

/**
 * User: michaels@netflix.com
 * Date: 8/16/16
 * Time: 2:40 PM
 */
public class ServerSslConfig
{
    private static final DynamicLongProperty DEFAULT_SESSION_TIMEOUT =
            new DynamicLongProperty("server.ssl.session.timeout", (18 * 60));  // 18 hours

    private static final String[] DEFAULT_CIPHERS;
    static {
        try {
            SSLContext context = SSLContext.getDefault();
            SSLSocketFactory sf = context.getSocketFactory();
            DEFAULT_CIPHERS = sf.getSupportedCipherSuites();
        }
        catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    private final String[] protocols;
    private final List<String> ciphers;
    private final File certChainFile;
    private final File keyFile;
    
    private final ClientAuth clientAuth;
    private final File clientAuthTrustStoreFile;
    private final String clientAuthTrustStorePassword;
    private final File clientAuthTrustStorePasswordFile;
    
    private final boolean decryptKeyUsingMetatron;
    private final byte[] metatronPolicy;

    private final long sessionTimeout;
    private final boolean sessionTicketsEnabled;

    public ServerSslConfig(String[] protocols, String[] ciphers, File certChainFile, File keyFile)
    {
        this(protocols, ciphers, certChainFile, keyFile, null, ClientAuth.NONE, null, (File) null, false);
    }

    public ServerSslConfig(String[] protocols, String[] ciphers, File certChainFile, File keyFile, byte[] metatronPolicy, ClientAuth clientAuth)
    {
        this(protocols, ciphers, certChainFile, keyFile, metatronPolicy, clientAuth, null, (File) null, true);
    }

    public ServerSslConfig(String[] protocols, String[] ciphers, File certChainFile, File keyFile,
                           byte[] metatronPolicy,
                           ClientAuth clientAuth, File clientAuthTrustStoreFile, File clientAuthTrustStorePasswordFile, 
                           boolean sessionTicketsEnabled)
    {
        this.protocols = protocols;
        this.ciphers = ciphers != null ? Arrays.asList(ciphers) : null;
        this.certChainFile = certChainFile;
        this.keyFile = keyFile;
        this.decryptKeyUsingMetatron = (metatronPolicy != null);
        this.metatronPolicy = metatronPolicy;
        this.clientAuth = clientAuth;
        this.clientAuthTrustStoreFile = clientAuthTrustStoreFile;
        this.clientAuthTrustStorePassword = null;
        this.clientAuthTrustStorePasswordFile = clientAuthTrustStorePasswordFile;
        this.sessionTimeout = DEFAULT_SESSION_TIMEOUT.get();
        this.sessionTicketsEnabled = sessionTicketsEnabled;
    }
    
    public ServerSslConfig(String[] protocols, String[] ciphers, File certChainFile, File keyFile, 
                           byte[] metatronPolicy, 
                           ClientAuth clientAuth, File clientAuthTrustStoreFile, String clientAuthTrustStorePassword, 
        boolean sessionTicketsEnabled)
    {
        this.protocols = protocols;
        this.ciphers = Arrays.asList(ciphers);
        this.certChainFile = certChainFile;
        this.keyFile = keyFile;
        this.decryptKeyUsingMetatron = (metatronPolicy != null);
        this.metatronPolicy = metatronPolicy;
        this.clientAuth = clientAuth;
        this.clientAuthTrustStoreFile = clientAuthTrustStoreFile;
        this.clientAuthTrustStorePassword = clientAuthTrustStorePassword;
        this.clientAuthTrustStorePasswordFile = null;
        this.sessionTimeout = DEFAULT_SESSION_TIMEOUT.get();
        this.sessionTicketsEnabled = sessionTicketsEnabled;
    }
    
    public static String[] getDefaultCiphers()
    {
        return DEFAULT_CIPHERS;
    }

    public static ServerSslConfig withDefaultCiphers(File certChainFile, File keyFile, String ... protocols)
    {
        return new ServerSslConfig(protocols, getDefaultCiphers(), certChainFile, keyFile);
    }

    public String[] getProtocols()
    {
        return protocols;
    }

    public List<String> getCiphers()
    {
        return ciphers;
    }

    public File getCertChainFile()
    {
        return certChainFile;
    }

    public File getKeyFile()
    {
        return keyFile;
    }

    public boolean shouldDecryptKeyUsingMetatron()
    {
        return decryptKeyUsingMetatron;
    }

    public byte[] getMetatronPolicyFile()
    {
        return metatronPolicy;
    }

    public ClientAuth getClientAuth()
    {
        return clientAuth;
    }

    public File getClientAuthTrustStoreFile()
    {
        return clientAuthTrustStoreFile;
    }

    public String getClientAuthTrustStorePassword()
    {
        return clientAuthTrustStorePassword;
    }

    public File getClientAuthTrustStorePasswordFile()
    {
        return clientAuthTrustStorePasswordFile;
    }

    public long getSessionTimeout()
    {
        return sessionTimeout;
    }

    public boolean sessionTicketsEnabled()
    {
        return sessionTicketsEnabled;
    }

    @Override
    public String toString()
    {
        return "ServerSslConfig{" +
                "protocols=" + Arrays.toString(protocols) +
                ", ciphers=" + ciphers +
                ", certChainFile=" + certChainFile +
                ", keyFile=" + keyFile +
                ", clientAuth=" + clientAuth +
                ", clientAuthTrustStoreFile=" + clientAuthTrustStoreFile +
                ", decryptKeyUsingMetatron=" + decryptKeyUsingMetatron +
                ", sessionTimeout=" + sessionTimeout +
                ", sessionTicketsEnabled=" + sessionTicketsEnabled +
                '}';
    }
}
