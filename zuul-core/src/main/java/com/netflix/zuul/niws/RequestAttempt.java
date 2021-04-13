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

package com.netflix.zuul.niws;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Throwables;
import com.netflix.appinfo.AmazonInfo;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.client.config.IClientConfig;
import com.netflix.client.config.IClientConfigKey;
import com.netflix.zuul.discovery.DiscoveryResult;
import com.netflix.zuul.exception.OutboundException;
import com.netflix.zuul.discovery.SimpleMetaInfo;
import com.netflix.zuul.netty.connectionpool.OriginConnectException;
import io.netty.handler.timeout.ReadTimeoutException;

import javax.net.ssl.SSLHandshakeException;

/**
 * User: michaels@netflix.com
 * Date: 9/2/14
 * Time: 2:52 PM
 */
public class RequestAttempt
{
    private static final ObjectMapper JACKSON_MAPPER = new ObjectMapper();

    private int attempt;
    private int status;
    private long duration;
    private String cause;
    private String error;
    private String exceptionType;
    private String app;
    private String asg;
    private String instanceId;
    private String host;
    private int port;
    private String vip;
    private String region;
    private String availabilityZone;
    private long readTimeout;
    private int connectTimeout;
    private int maxRetries;

    public RequestAttempt(int attemptNumber, InstanceInfo server, String targetVip, String chosenWarmupLB, int status, String error, String exceptionType,
                          int readTimeout, int connectTimeout, int maxRetries)
    {
        if (attemptNumber < 1) {
            throw new IllegalArgumentException("Attempt number must be greater than 0! - " + attemptNumber);
        }
        this.attempt = attemptNumber;
        this.vip = targetVip;

        if (server != null) {
            this.app = server.getAppName().toLowerCase();
            this.asg = server.getASGName();
            this.instanceId = server.getInstanceId();
            this.host = server.getHostName();
            this.port = server.getPort();

            // If targetVip is null, then try to use the actual server's vip.
            if (targetVip == null) {
                this.vip = server.getVIPAddress();
            }

            if (server.getDataCenterInfo() instanceof AmazonInfo) {
                this.availabilityZone = ((AmazonInfo) server.getDataCenterInfo()).getMetadata().get("availability-zone");

                // HACK - get region by just removing the last char from zone.
                String az = getAvailabilityZone();
                if (az != null && az.length() > 0) {
                    this.region = az.substring(0, az.length() - 1);
                }
            }
        }
        
        this.status = status;
        this.error = error;
        this.exceptionType = exceptionType;
        this.readTimeout = readTimeout;
        this.connectTimeout = connectTimeout;
        this.maxRetries = maxRetries;
    }

    public RequestAttempt(final DiscoveryResult server, final IClientConfig clientConfig, int attemptNumber, int readTimeout) {
        this.status = -1;
        this.attempt = attemptNumber;
        this.readTimeout = readTimeout;

        if (server != null && server != DiscoveryResult.EMPTY) {
            this.host = server.getHost();
            this.port = server.getPort();
            this.availabilityZone = server.getZone();

            if (server.isDiscoveryEnabled()) {
                this.app = server.getAppName().toLowerCase();
                this.asg = server.getASGName();
                this.instanceId = server.getServerId();
                this.host = server.getHost();
                this.port = server.getPort();
                this.vip = server.getTarget();
                this.availabilityZone = server.getAvailabilityZone();

            }
            else {
                SimpleMetaInfo metaInfo = server.getMetaInfo();
                if (metaInfo != null) {
                    this.asg = metaInfo.getServerGroup();
                    this.vip = metaInfo.getServiceIdForDiscovery();
                    this.instanceId = metaInfo.getInstanceId();
                }
            }
            // HACK - get region by just removing the last char from zone.
            if (availabilityZone != null && availabilityZone.length() > 0) {
                region = availabilityZone.substring(0, availabilityZone.length() - 1);
            }
        }

        if (clientConfig != null) {
            this.connectTimeout = clientConfig.get(IClientConfigKey.Keys.ConnectTimeout);
        }
    }

    private RequestAttempt() {
    }

    public void complete(int responseStatus, long durationMs, Throwable exception)
    {
        if (responseStatus > -1)
            setStatus(responseStatus);

        this.duration = durationMs;

        if (exception != null)
            setException(exception);
    }

    public int getAttempt()
    {
        return attempt;
    }

    public String getVip()
    {
        return vip;
    }

    public int getStatus() {
        return this.status;
    }

    public long getDuration() {
        return this.duration;
    }

    public String getError() {
        return error;
    }

    public String getApp()
    {
        return app;
    }

    public String getAsg() {
        return asg;
    }

    public String getInstanceId() {
        return instanceId;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public String getRegion()
    {
        return region;
    }

    public String getAvailabilityZone() {
        return availabilityZone;
    }

    public String getExceptionType()
    {
        return exceptionType;
    }

    public long getReadTimeout()
    {
        return readTimeout;
    }

    public int getConnectTimeout()
    {
        return connectTimeout;
    }

    public int getMaxRetries()
    {
        return maxRetries;
    }

    public void setStatus(int status)
    {
        this.status = status;
    }

    public void setError(String error)
    {
        this.error = error;
    }

    public void setExceptionType(String exceptionType)
    {
        this.exceptionType = exceptionType;
    }

    public void setApp(String app)
    {
        this.app = app;
    }

    public void setAsg(String asg)
    {
        this.asg = asg;
    }

    public void setInstanceId(String instanceId)
    {
        this.instanceId = instanceId;
    }

    public void setHost(String host)
    {
        this.host = host;
    }

    public void setPort(int port)
    {
        this.port = port;
    }

    public void setVip(String vip)
    {
        this.vip = vip;
    }

    public void setRegion(String region)
    {
        this.region = region;
    }

    public void setAvailabilityZone(String availabilityZone)
    {
        this.availabilityZone = availabilityZone;
    }

    public void setReadTimeout(long readTimeout)
    {
        this.readTimeout = readTimeout;
    }

    public void setConnectTimeout(int connectTimeout)
    {
        this.connectTimeout = connectTimeout;
    }

    public void setException(Throwable t) {
        if (t != null) {
            if (t instanceof ReadTimeoutException) {
                error = "READ_TIMEOUT";
                exceptionType = t.getClass().getSimpleName();
            }
            else if (t instanceof OriginConnectException) {
                OriginConnectException oce = (OriginConnectException) t;
                if (oce.getErrorType() != null) {
                    error = oce.getErrorType().toString();
                }
                else {
                    error = "ORIGIN_CONNECT_ERROR";
                }

                final Throwable cause = t.getCause();
                if (cause != null) {
                    exceptionType = t.getCause().getClass().getSimpleName();
                }
                else {
                    exceptionType = t.getClass().getSimpleName();
                }
            }
            else if (t instanceof OutboundException) {
                OutboundException obe = (OutboundException) t;
                error = obe.getOutboundErrorType().toString();
                exceptionType = OutboundException.class.getSimpleName();
            }
            else if (t instanceof SSLHandshakeException) {
                error = t.getMessage();
                exceptionType = t.getClass().getSimpleName();
                cause = t.getCause().getMessage();
            }
            else {
                error = t.getMessage();
                exceptionType = t.getClass().getSimpleName();
                cause = Throwables.getStackTraceAsString(t);
            }
        }
    }

    public void setMaxRetries(int maxRetries)
    {
        this.maxRetries = maxRetries;
    }

    @Override
    public String toString()
    {
        try {
            return JACKSON_MAPPER.writeValueAsString(toJsonNode());
        }
        catch (JsonProcessingException e) {
            throw new RuntimeException("Error serializing RequestAttempt!", e);
        }
    }

    public ObjectNode toJsonNode()
    {
        ObjectNode root = JACKSON_MAPPER.createObjectNode();
        root.put("status", status);
        root.put("duration", duration);
        root.put("attempt", attempt);

        putNullableAttribute(root, "error", error);
        putNullableAttribute(root, "cause", cause);
        putNullableAttribute(root, "exceptionType", exceptionType);
        putNullableAttribute(root, "region", region);
        putNullableAttribute(root, "asg", asg);
        putNullableAttribute(root, "instanceId", instanceId);
        putNullableAttribute(root, "vip", vip);

        if (status < 1) {
            root.put("readTimeout", readTimeout);
            root.put("connectTimeout", connectTimeout);
        }

        return root;
    }

    private static ObjectNode putNullableAttribute(ObjectNode node, String name, String value)
    {
        if (value != null) {
            node.put(name, value);
        }
        return node;
    }
}
