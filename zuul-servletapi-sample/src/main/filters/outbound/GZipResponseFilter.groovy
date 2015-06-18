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
package outbound

import com.netflix.zuul.context.Headers
import com.netflix.zuul.context.HttpRequestMessage
import com.netflix.zuul.context.HttpResponseMessage
import com.netflix.zuul.context.SessionContext
import com.netflix.zuul.filters.http.HttpOutboundSyncFilter
import com.netflix.zuul.util.HttpUtils
import org.apache.commons.io.IOUtils
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.runners.MockitoJUnitRunner
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

import static junit.framework.Assert.assertEquals
import static junit.framework.Assert.assertNull

class GZipResponseFilter extends HttpOutboundSyncFilter
{
    private static final Logger LOG = LoggerFactory.getLogger(GZipResponseFilter.class);

    @Override
    int filterOrder() {
        return 5
    }

    @Override
    boolean shouldFilter(HttpResponseMessage response) {
        return true
    }

    @Override
    HttpResponseMessage apply(HttpResponseMessage response)
    {
        HttpRequestMessage request = response.getRequest()

        byte[] body = response.bufferBody().toBlocking().first()

        // there is no body to send
        if (body == null) return response;

        boolean isGzipRequested = HttpUtils.acceptsGzip(request.getHeaders())
        boolean isResponseGzipped = HttpUtils.isGzipped(response.getHeaders())

        // If origin response is gzipped, and client has not requested gzip, decompress stream
        // before sending to client.
        if (isResponseGzipped && !isGzipRequested) {
            try {
                byte[] unGzippedBody = IOUtils.toByteArray(new GZIPInputStream(new ByteArrayInputStream(body)))
                response.setBody(unGzippedBody)
                response.getHeaders().remove("Content-Encoding")

            } catch (java.util.zip.ZipException e) {
                LOG.error("Gzip expected but not received assuming unencoded response. So sending body as-is.")
            }
        }
        // If origin response is not gzipped, but client accepts gzip, then compress the body.
        else if (isGzipRequested && !isResponseGzipped) {
            try {
                byte[] gzippedBody = gzip(body)
                response.setBody(gzippedBody)
                response.getHeaders().set("Content-Encoding", "gzip")

            } catch (java.util.zip.ZipException e) {
                LOG.error("Error gzipping response body. So just sending as-is.")
            }
        }

        return response
    }

    private byte[] gzip(byte[] data)
    {
        ByteArrayOutputStream baos = new ByteArrayOutputStream()
        GZIPOutputStream gzOut = new GZIPOutputStream(baos)
        gzOut.write(data)
        gzOut.close()

        return baos.toByteArray()
    }

    @RunWith(MockitoJUnitRunner.class)
    public static class TestUnit {

        GZipResponseFilter filter
        SessionContext ctx
        HttpResponseMessage response
        Headers reqHeaders

        @Mock
        HttpRequestMessage request

        @Before
        public void setup() {
            filter = new GZipResponseFilter()
            ctx = new SessionContext()
            Mockito.when(request.getContext()).thenReturn(ctx)
            response = new HttpResponseMessage(ctx, request, 99)

            reqHeaders = new Headers()
            Mockito.when(request.getHeaders()).thenReturn(reqHeaders)
        }

        @Test
        public void prepareResponseBody_NeedsGZipping()
        {
            reqHeaders.set("Accept-Encoding", "gzip")

            byte[] originBody = "blah".bytes
            response.setBody(originBody)

            filter.apply(response)

            // Check body is a gzipped version of the origin body.
            byte[] unzippedBytes = new GZIPInputStream(new ByteArrayInputStream(response.getBody())).bytes
            String bodyStr = new String(unzippedBytes, "UTF-8")
            assertEquals("blah", bodyStr)
            assertEquals("gzip", response.getHeaders().getFirst("Content-Encoding"))
        }

        @Test
        public void prepareResponseBody_NeedsUnGZipping() {
            reqHeaders.set("Accept-Encoding", "identity")
            response.getHeaders().set("Content-Encoding", "gzip")

            byte[] originBody = gzip("blah")
            response.setBody(originBody)

            filter.apply(response)

            // Check returned body is same as origin body.
            String bodyStr = new String(response.getBody(), "UTF-8")
            assertEquals("blah", bodyStr)
            assertNull(response.getHeaders().getFirst("Content-Encoding"))
        }

        private byte[] gzip(String data)
        {
            byte[] originBody = data.bytes

            ByteArrayOutputStream baos = new ByteArrayOutputStream()
            GZIPOutputStream gzOut = new GZIPOutputStream(baos)
            gzOut.write(originBody)
            gzOut.close()

            return baos.toByteArray()
        }
    }

}