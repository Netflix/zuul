/**
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.zuul.context;

import com.netflix.zuul.bytebuf.ByteBufUtils;
import io.netty.buffer.ByteBuf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.net.SocketTimeoutException;
import java.util.Enumeration;
import java.util.Map;

/**
 * User: michaels@netflix.com
 * Date: 4/29/15
 * Time: 11:25 AM
 */
public class ServletSessionContextFactory implements SessionContextFactory<HttpServletRequest, HttpServletResponse>
{
    private static final Logger LOG = LoggerFactory.getLogger(ServletSessionContextFactory.class);

    @Override
    public ZuulMessage create(SessionContext context, HttpServletRequest servletRequest)
    {
        // Parse the headers.
        Headers reqHeaders = new Headers();
        Enumeration headerNames = servletRequest.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String name = (String) headerNames.nextElement();
            Enumeration values = servletRequest.getHeaders(name);
            while (values.hasMoreElements()) {
                String value = (String) values.nextElement();
                reqHeaders.add(name, value);
            }
        }

        // Parse the url query parameters.
        HttpQueryParams queryParams = HttpQueryParams.parse(servletRequest.getQueryString());

        // Copy any applicable attributes from the ServletRequest.
        copyServletRequestAttributes(context, servletRequest);

        // Build the request object.
        HttpRequestMessage request = new HttpRequestMessage(context, servletRequest.getProtocol(), servletRequest.getMethod(),
                servletRequest.getRequestURI(), queryParams, reqHeaders, servletRequest.getRemoteAddr(),
                servletRequest.getScheme(), servletRequest.getServerPort(), servletRequest.getServerName());

        // Store this original request info for future reference (ie. for metrics and access logging purposes).
        request.storeOriginalRequestInfo();

        // Get the inputstream of body.
        InputStream bodyInput;
        try {
            bodyInput = servletRequest.getInputStream();
        }
        catch (IOException e) {
            String errorMsg = "Error reading ServletInputStream.";
            LOG.error(errorMsg, e);
            throw new RuntimeException(errorMsg, e);
        }

        // Wrap the ServletInputStream(body) in an Observable.
        if (bodyInput != null) {
            Observable<ByteBuf> bodyObs = ByteBufUtils.fromInputStream(bodyInput);
            bodyObs = bodyObs.doOnError((e) -> {
                    if (SocketTimeoutException.class.isAssignableFrom(e.getClass())) {
                        // This can happen if the request body is smaller than the size specified in the
                        // Content-Length header, and using tomcat APR connector.
                        LOG.error("SocketTimeoutException reading request body from inputstream. error="
                                + String.valueOf(e.getMessage()) + ", request-info: " + request.getInfoForLogging());
                    }
                    else {
                        LOG.error("Error reading request body from inputstream. error="
                                + String.valueOf(e.getMessage()) + ", request-info: " + request.getInfoForLogging());
                    }
            });
            request.setBodyStream(bodyObs);
        }

        return request;
    }

    private void copyServletRequestAttributes(SessionContext context, HttpServletRequest servletRequest)
    {
        Enumeration attrNames = servletRequest.getAttributeNames();
        String zuulAttrPrefix = "_zuul:";
        while (attrNames.hasMoreElements()) {
            String attrName = (String) attrNames.nextElement();
            if (attrName.startsWith(zuulAttrPrefix)) {
                context.put(attrName.substring(zuulAttrPrefix.length()), servletRequest.getAttribute(attrName));
            }
        }
    }

    @Override
    public Observable<ZuulMessage> write(ZuulMessage msg, HttpServletResponse servletResponse)
    {
        HttpResponseMessage response = (HttpResponseMessage) msg;

        // Status.
        servletResponse.setStatus(response.getStatus());

        // Headers.
        for (Map.Entry<String, String> header : response.getHeaders().entries()) {
            servletResponse.setHeader(header.getKey(), header.getValue());
        }

        // Body.
        ServletOutputStream output;
        try {
            output = servletResponse.getOutputStream();
        } catch (IOException e) {
            throw new RuntimeException("Error getting ServletOutputStream", e);
        }

        if (msg.getBodyStream() == null) {
            return Observable.just(msg);
        }
        else {
            // Write out the response body stream of ByteBufS.
            Observable<ZuulMessage> writeBody = msg.getBodyStream()
                    .doOnNext((bb) -> {
                        try {
                            output.write(ByteBufUtils.toBytes(bb));
                        } catch (IOException e) {
                            LOG.error("Error writing response to ServletOutputStream.", e);
                        }
                    })
                    .doOnCompleted(() -> {
                        try {
                            output.flush();
                        } catch (IOException e) {
                            LOG.error("Error flushing response to ServletOutputStream.", e);
                        }
                    })
                    .doOnError(t -> {
                        LOG.error("Error writing repsonse to ServletOutputStream.", t);
                    })
                    .map(bb ->  msg);

            writeBody.subscribe();
            return writeBody;

        }
    }
}
