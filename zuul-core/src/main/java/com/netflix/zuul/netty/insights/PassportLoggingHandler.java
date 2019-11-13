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

package com.netflix.zuul.netty.insights;

import com.netflix.config.CachedDynamicLongProperty;
import com.netflix.spectator.api.Counter;
import com.netflix.spectator.api.Registry;
import com.netflix.zuul.context.SessionContext;
import com.netflix.zuul.message.http.HttpRequestMessage;
import com.netflix.zuul.message.http.HttpResponseMessage;
import com.netflix.zuul.netty.ChannelUtils;
import com.netflix.zuul.netty.server.ClientRequestReceiver;
import com.netflix.zuul.niws.RequestAttempts;
import com.netflix.zuul.passport.CurrentPassport;
import com.netflix.zuul.passport.PassportState;
import com.netflix.zuul.passport.StartAndEnd;
import com.netflix.zuul.stats.status.StatusCategoryUtils;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import com.netflix.netty.common.HttpLifecycleChannelHandler;
import com.netflix.netty.common.metrics.HttpMetricsChannelHandler;
import com.netflix.netty.common.metrics.ServerChannelMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * User: michaels@netflix.com
 * Date: 2/28/17
 * Time: 5:41 PM
 */
@ChannelHandler.Sharable
public class PassportLoggingHandler extends ChannelInboundHandlerAdapter
{
    private static final Logger LOG = LoggerFactory.getLogger(PassportLoggingHandler.class);

    private static final CachedDynamicLongProperty WARN_REQ_PROCESSING_TIME_NS = new CachedDynamicLongProperty("zuul.passport.log.request.time.threshold",
            1000 * 1000 * 1000); // 1000 ms
    private static final CachedDynamicLongProperty WARN_RESP_PROCESSING_TIME_NS = new CachedDynamicLongProperty("zuul.passport.log.response.time.threshold",
            1000 * 1000 * 1000); // 1000 ms

    private final Counter incompleteProxySessionCounter;

    public PassportLoggingHandler(Registry spectatorRegistry)
    {
        incompleteProxySessionCounter = spectatorRegistry.counter("server.http.session.incomplete");
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception
    {
        try {
            super.userEventTriggered(ctx, evt);
        }
        finally {
            if (evt instanceof HttpLifecycleChannelHandler.CompleteEvent) {
                try {
                    logPassport(ctx.channel());
                }
                catch(Exception e) {
                    LOG.error("Error logging passport info after request completed!", e);
                }
            }
        }
    }

    private void logPassport(Channel channel)
    {
        // Collect attributes.
        CurrentPassport passport = CurrentPassport.fromChannel(channel);
        HttpRequestMessage request = ClientRequestReceiver.getRequestFromChannel(channel);
        HttpResponseMessage response = ClientRequestReceiver.getResponseFromChannel(channel);
        SessionContext ctx = request == null ? null : request.getContext();

        String topLevelRequestId = getRequestId(channel, ctx);

        // Do some debug logging of the Passport.
        if (LOG.isDebugEnabled()) {
            LOG.debug("State after complete. "
                    + ", current-server-conns = " + ServerChannelMetrics.currentConnectionCountFromChannel(channel)
                    + ", current-http-reqs = " + HttpMetricsChannelHandler.getInflightRequestCountFromChannel(channel)
                    + ", status = " + (response == null ? getRequestId(channel, ctx) : response.getStatus())
                    + ", nfstatus = " + String.valueOf(StatusCategoryUtils.getStatusCategory(ctx))
                    + ", toplevelid = " + topLevelRequestId
                    + ", req = " + request.getInfoForLogging()
                    + ", passport = " + String.valueOf(passport));
        }

        // Some logging of session states if certain criteria match:
        if (LOG.isInfoEnabled()) {
            if (passport.wasProxyAttempt()) {

                if (passport.findStateBackwards(PassportState.OUT_RESP_LAST_CONTENT_SENDING) == null) {
                    incompleteProxySessionCounter.increment();
                    LOG.info("Incorrect final state! toplevelid = " + topLevelRequestId + ", " + ChannelUtils.channelInfoForLogging(channel));
                }
            }

            if (! passport.wasProxyAttempt()) {
                if (ctx != null && !isHealthcheckRequest(request)) {
                    // Why did we fail to attempt to proxy this request?
                    RequestAttempts attempts = RequestAttempts.getFromSessionContext(ctx);
                    LOG.debug("State after complete. "
                            + ", context-error = " + String.valueOf(ctx.getError())
                            + ", current-http-reqs = " + HttpMetricsChannelHandler.getInflightRequestCountFromChannel(channel)
                            + ", toplevelid = " + topLevelRequestId
                            + ", req = " + request.getInfoForLogging()
                            + ", attempts = " + String.valueOf(attempts)
                            + ", passport = " + String.valueOf(passport));
                }
            }

            StartAndEnd inReqToOutResp = passport.findFirstStartAndLastEndStates(PassportState.IN_REQ_HEADERS_RECEIVED, PassportState.OUT_REQ_LAST_CONTENT_SENT);
            if (passport.calculateTimeBetween(inReqToOutResp) > WARN_REQ_PROCESSING_TIME_NS.get()) {
                LOG.info("Request processing took longer than threshold! toplevelid = " + topLevelRequestId + ", "
                        + ChannelUtils.channelInfoForLogging(channel));
            }

            StartAndEnd inRespToOutResp = passport.findLastStartAndFirstEndStates(PassportState.IN_RESP_HEADERS_RECEIVED, PassportState.OUT_RESP_LAST_CONTENT_SENT);
            if (passport.calculateTimeBetween(inRespToOutResp)
                    > WARN_RESP_PROCESSING_TIME_NS.get()) {
                LOG.info("Response processing took longer than threshold! toplevelid = " + topLevelRequestId + ", " + ChannelUtils.channelInfoForLogging(channel));
            }
        }
    }

    protected boolean isHealthcheckRequest(HttpRequestMessage req) {
        return req.getPath().equals("/healthcheck");
    }

    protected String getRequestId(Channel channel, SessionContext ctx) {
        return ctx == null ? "-" : ctx.getUUID();
    }
}
