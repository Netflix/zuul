/*
 * Copyright 2013 Netflix, Inc.
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

import com.netflix.config.DynamicBooleanProperty
import com.netflix.config.DynamicIntProperty
import com.netflix.config.DynamicPropertyFactory
import com.netflix.util.Pair
import com.netflix.zuul.ZuulFilter
import com.netflix.zuul.constants.ZuulConstants
import com.netflix.zuul.constants.ZuulHeaders
import com.netflix.zuul.context.Debug
import com.netflix.zuul.context.RequestContext
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.mockito.runners.MockitoJUnitRunner

import java.util.zip.GZIPInputStream
import javax.servlet.http.HttpServletResponse

class SendResponseFilter extends ZuulFilter {

    static DynamicBooleanProperty INCLUDE_DEBUG_HEADER =
        DynamicPropertyFactory.getInstance().getBooleanProperty(ZuulConstants.ZUUL_INCLUDE_DEBUG_HEADER, false);

    static DynamicIntProperty INITIAL_STREAM_BUFFER_SIZE =
        DynamicPropertyFactory.getInstance().getIntProperty(ZuulConstants.ZUUL_INITIAL_STREAM_BUFFER_SIZE, 1024);

    static DynamicBooleanProperty SET_CONTENT_LENGTH = DynamicPropertyFactory.getInstance().getBooleanProperty(ZuulConstants.ZUUL_SET_CONTENT_LENGTH, false);

    @Override
    String filterType() {
        return 'post'
    }

    @Override
    int filterOrder() {
        return 1000
    }

    boolean shouldFilter() {
        return !RequestContext.currentContext.getZuulResponseHeaders().isEmpty() ||
                RequestContext.currentContext.getResponseDataStream() != null ||
                RequestContext.currentContext.responseBody != null
    }

    Object run() {
        addResponseHeaders()
        writeResponse()
    }

    void writeResponse() {
        RequestContext context = RequestContext.currentContext

        // there is no body to send
        if (context.getResponseBody() == null && context.getResponseDataStream() == null) return;

        HttpServletResponse servletResponse = context.getResponse()
        servletResponse.setCharacterEncoding("UTF-8")

        OutputStream outStream = servletResponse.getOutputStream();
        InputStream is = null
        try {
            if (RequestContext.currentContext.responseBody != null) {
                String body = RequestContext.currentContext.responseBody
                writeResponse(new ByteArrayInputStream(body.bytes), outStream)
                return;
            }

            boolean isGzipRequested = false
            final String requestEncoding = context.getRequest().getHeader(ZuulHeaders.ACCEPT_ENCODING)
            if (requestEncoding != null && requestEncoding.equals("gzip"))
                isGzipRequested = true;

            is = context.getResponseDataStream();
            InputStream inputStream = is
            if (is != null) {
                if (context.sendZuulResponse()) {
                    // if origin response is gzipped, and client has not requested gzip, decompress stream
                    // before sending to client
                    // else, stream gzip directly to client
                    if (context.getResponseGZipped() && !isGzipRequested)
                        try {
                            inputStream = new GZIPInputStream(is);

                        } catch (java.util.zip.ZipException e) {
                            println("gzip expected but not received assuming unencoded response" + RequestContext.currentContext.getRequest().getRequestURL().toString())
                            inputStream = is
                        }
                    else if (context.getResponseGZipped() && isGzipRequested)
                        servletResponse.setHeader(ZuulHeaders.CONTENT_ENCODING, "gzip")
                    writeResponse(inputStream, outStream)
                }
            }

        } finally {
            try {
                is?.close();

                outStream.flush()
                outStream.close()
            } catch (IOException e) {

            }
        }
    }

    def writeResponse(InputStream zin, OutputStream out) {
        byte[] bytes = new byte[INITIAL_STREAM_BUFFER_SIZE.get()];
        int bytesRead = -1;
        while ((bytesRead = zin.read(bytes)) != -1) {
//            if (Debug.debugRequest() && !Debug.debugRequestHeadersOnly()) {
//                Debug.addRequestDebug("OUTBOUND: <  " + new String(bytes, 0, bytesRead));
//            }

            try {
                out.write(bytes, 0, bytesRead);
                out.flush();
            } catch (IOException e) {
                //ignore
                e.printStackTrace()
            }

            // doubles buffer size if previous read filled it
            if (bytesRead == bytes.length) {
                bytes = new byte[bytes.length * 2]
            }
        }
    }

    private void addResponseHeaders() {
        RequestContext context = RequestContext.getCurrentContext();
        HttpServletResponse servletResponse = context.getResponse();
        List<Pair<String, String>> zuulResponseHeaders = context.getZuulResponseHeaders();
        String debugHeader = ""

        List<String> rd

        rd = (List<String>) RequestContext.getCurrentContext().get("routingDebug");
        rd?.each {
            debugHeader += "[[[${it}]]]";
        }

        /*
        rd = (List<String>) RequestContext.getCurrentContext().get("requestDebug");
        rd?.each {
            debugHeader += "[[[REQUEST_DEBUG::${it}]]]";
        }
        */

        if (INCLUDE_DEBUG_HEADER.get()) servletResponse.addHeader("X-Zuul-Debug-Header", debugHeader)

        if (Debug.debugRequest()) {
            zuulResponseHeaders?.each { Pair<String, String> it ->
                servletResponse.addHeader(it.first(), it.second())
                Debug.addRequestDebug("OUTBOUND: <  " + it.first() + ":" + it.second())
            }
        } else {
            zuulResponseHeaders?.each { Pair<String, String> it ->
                servletResponse.addHeader(it.first(), it.second())
            }
        }

        RequestContext ctx = RequestContext.getCurrentContext()
        Integer contentLength = ctx.getOriginContentLength()

        // only inserts Content-Length if origin provides it and origin response is not gzipped
        if (SET_CONTENT_LENGTH.get()) {
            if (contentLength != null && !ctx.getResponseGZipped())
                servletResponse.setContentLength(contentLength)
        }
    }

}