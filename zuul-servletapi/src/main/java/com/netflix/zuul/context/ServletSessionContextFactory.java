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

import com.netflix.config.CachedDynamicBooleanProperty;
import com.netflix.zuul.bytebuf.ByteBufUtils;
import com.netflix.zuul.exception.ZuulException;
import com.netflix.zuul.message.Header;
import com.netflix.zuul.message.HeaderName;
import com.netflix.zuul.message.Headers;
import com.netflix.zuul.message.ZuulMessage;
import com.netflix.zuul.message.http.*;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.net.SocketTimeoutException;
import java.security.cert.X509Certificate;
import java.util.Enumeration;

/**
 * User: michaels@netflix.com
 * Date: 4/29/15
 * Time: 11:25 AM
 */
public class ServletSessionContextFactory implements SessionContextFactory<HttpServletRequest, HttpServletResponse>
{
    private static final Logger LOG = LoggerFactory.getLogger(ServletSessionContextFactory.class);
    private static final String JAVAX_SERVLET_REQUEST_X509_CERTIFICATE = "javax.servlet.request.X509Certificate";

    private static final CachedDynamicBooleanProperty SHOULD_ERROR_ON_SOCKET_READ_TIMEOUT = new CachedDynamicBooleanProperty(
            "zuul.ServletSessionContextFactory.errorOnSocketReadTimeout", false);

    @Override
    public ZuulMessage create(SessionContext context, HttpServletRequest servletRequest, HttpServletResponse response)
    {
        // Parse the headers.
        Headers reqHeaders = new Headers();
        Enumeration headerNames = servletRequest.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String name = (String) headerNames.nextElement();
            Enumeration values = servletRequest.getHeaders(name);
            HeaderName hn = HttpHeaderNames.get(name);
            while (values.hasMoreElements()) {
                String value = (String) values.nextElement();
                reqHeaders.add(hn, value);
            }
        }

        // Parse the url query parameters.
        HttpQueryParams queryParams = HttpQueryParams.parse(servletRequest.getQueryString());

        // Copy any applicable attributes from the ServletRequest.
        copyServletRequestAttributes(context, servletRequest);

        // Build the request object.
        HttpRequestMessage request = new HttpRequestMessageImpl(context, servletRequest.getProtocol(), servletRequest.getMethod(),
                servletRequest.getRequestURI(), queryParams, reqHeaders, servletRequest.getRemoteAddr(),
                servletRequest.getScheme(), servletRequest.getServerPort(), servletRequest.getServerName());

        // Store this original request info for future reference (ie. for metrics and access logging purposes).
        request.storeInboundRequest();

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
            bodyObs = bodyObs.onErrorReturn((e) -> {
                if (SocketTimeoutException.class.isAssignableFrom(e.getClass())) {

                    // This can happen if the request body is smaller than the size specified in the
                    // Content-Length header, and using tomcat APR connector.
                    LOG.error("SocketTimeoutException reading request body from inputstream. error="
                            + String.valueOf(e.getMessage()) + ", request-info: " + request.getInfoForLogging());

                    // Store the exception.
                    ZuulException ze = new ZuulException(e.getMessage(), e, "TIMEOUT_READING_REQ_BODY");
                    ze.setStatusCode(400);
                    request.getContext().setError(ze);

                    if (SHOULD_ERROR_ON_SOCKET_READ_TIMEOUT.get()) {
                        // Flag to respond to client with an error. As we don't want to attempt proxying if we failed to read the body.
                        request.getContext().setShouldSendErrorResponse(true);
                    }
                }
                else {
                    LOG.error("Error reading request body from inputstream. error="
                            + String.valueOf(e.getMessage()) + ", request-info: " + request.getInfoForLogging());
                }

                // Return an empty bytebuf.
                return Unpooled.EMPTY_BUFFER;
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

        copyServletRequestX509Attributes(context, servletRequest);
    }

    private void copyServletRequestX509Attributes(SessionContext context, HttpServletRequest servletRequest)
    {
        // Copy X509 request attribute into the context.
        X509Certificate[] certs = (X509Certificate[]) servletRequest.getAttribute(JAVAX_SERVLET_REQUEST_X509_CERTIFICATE);
        if (certs != null)
            context.set(JAVAX_SERVLET_REQUEST_X509_CERTIFICATE, certs);
    }

    @Override
    public Observable<ZuulMessage> write(ZuulMessage msg, HttpServletResponse servletResponse)
    {
        HttpResponseMessage response = (HttpResponseMessage) msg;

        // Status.
        servletResponse.setStatus(response.getStatus());

        // Headers.
        for (Header header : response.getHeaders().entries()) {
            servletResponse.addHeader(header.getKey(), header.getValue());
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
                        LOG.error("Error writing response to ServletOutputStream.", t);
                    })
                    .map(bb ->  msg);

            return writeBody;

        }
    }
}
