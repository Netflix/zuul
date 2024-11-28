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

package com.netflix.netty.common.throttle;
public class ThrottlingContextData{
    private io.netty.channel.ChannelHandlerContext ctx;

    public io.netty.channel.ChannelHandlerContext getCtx(){
        return ctx;
    }

    public void setCtx(io.netty.channel.ChannelHandlerContext ctx){
        this.ctx=ctx;
    }

    private com.netflix.zuul.stats.status.StatusCategory nfStatus;

    public com.netflix.zuul.stats.status.StatusCategory getNfStatus(){
        return nfStatus;
    }

    public void setNfStatus(com.netflix.zuul.stats.status.StatusCategory nfStatus){
        this.nfStatus=nfStatus;
    }

    private java.lang.String reason;

    public java.lang.String getReason(){
        return reason;
    }

    public void setReason(java.lang.String reason){
        this.reason=reason;
    }

    private io.netty.handler.codec.http.HttpRequest request;

    public io.netty.handler.codec.http.HttpRequest getRequest(){
        return request;
    }

    public void setRequest(io.netty.handler.codec.http.HttpRequest request){
        this.request=request;
    }

    private java.lang.Integer injectedLatencyMillis;

    public java.lang.Integer getInjectedLatencyMillis(){
        return injectedLatencyMillis;
    }

    public void setInjectedLatencyMillis(java.lang.Integer injectedLatencyMillis){
        this.injectedLatencyMillis=injectedLatencyMillis;
    }

    public ThrottlingContextData(io.netty.channel.ChannelHandlerContext ctx,com.netflix.zuul.stats.status.StatusCategory nfStatus,java.lang.String reason,io.netty.handler.codec.http.HttpRequest request,java.lang.Integer injectedLatencyMillis){
        this.ctx=ctx;
        this.nfStatus=nfStatus;
        this.reason=reason;
        this.request=request;
        this.injectedLatencyMillis=injectedLatencyMillis;
    }
}

