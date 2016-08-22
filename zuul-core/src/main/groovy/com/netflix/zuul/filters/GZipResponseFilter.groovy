package com.netflix.zuul.filters

import com.netflix.zuul.bytebuf.ByteBufUtils
import com.netflix.zuul.context.SessionContext
import com.netflix.zuul.filters.http.HttpOutboundSyncFilter
import com.netflix.zuul.message.Headers
import com.netflix.zuul.message.http.HttpHeaderNames
import com.netflix.zuul.message.http.HttpRequestInfo
import com.netflix.zuul.message.http.HttpResponseMessage
import com.netflix.zuul.message.http.HttpResponseMessageImpl
import com.netflix.zuul.util.HttpUtils
import io.netty.buffer.ByteBuf
import io.netty.buffer.ByteBufInputStream
import io.netty.buffer.ByteBufOutputStream
import io.netty.buffer.Unpooled
import org.apache.commons.io.IOUtils
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.runners.MockitoJUnitRunner
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import rx.Observable

import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

import static junit.framework.Assert.assertEquals
import static junit.framework.Assert.assertNull

/**
 * General-purpose filter for gzipping/ungzipping response bodies if requested/needed.
 *
 * You can just subclass this in your project, and use as-is.
 *
 * @author Mike Smith
 */
class GZipResponseFilter extends HttpOutboundSyncFilter
{
    private static final Logger LOG = LoggerFactory.getLogger(GZipResponseFilter.class);

    private static final String OVERRIDE_GZIP_REQUESTED = "overrideGzipRequested";
    private static final String GZIP_RESP_IF_ORIGIN_DIDNT = "gzipResponseIfOriginDidnt";


    @Override
    int filterOrder() {
        return 5
    }

    @Override
    boolean shouldFilter(HttpResponseMessage response) {
        return response.hasBody()
    }

    @Override
    HttpResponseMessage apply(HttpResponseMessage response)
    {
        HttpRequestInfo request = response.getInboundRequest()
        SessionContext ctx = response.getContext()
        Headers respHeaders = response.getHeaders()

        // A flag on SessionContext can be set to override normal mechanism of checking if client
        // accepts gzip.
        boolean isGzipRequested
        Boolean overrideGzipRequested = ctx.get(OVERRIDE_GZIP_REQUESTED)
        if (overrideGzipRequested != null) {
            isGzipRequested = overrideGzipRequested.booleanValue()
        }
        else {
            isGzipRequested = HttpUtils.acceptsGzip(request.getHeaders())
        }

        // Default to gzipping response if origin did not.
        boolean gzipResponseIfOriginDidnt = ctx.getBoolean(GZIP_RESP_IF_ORIGIN_DIDNT, true)

        // Check the headers to see if response is already gzipped.
        boolean isResponseGzipped = HttpUtils.isGzipped(respHeaders)

        // Decide what to do.
        boolean shouldUnGzip = isResponseGzipped && !isGzipRequested
        boolean shouldGzip = gzipResponseIfOriginDidnt && isGzipRequested && !isResponseGzipped

        // Ungzip the response body.
        if (shouldUnGzip) {
            respHeaders.remove(HttpHeaderNames.CONTENT_ENCODING)
            respHeaders.remove(HttpHeaderNames.CONTENT_LENGTH)

            Observable<ByteBuf> newBody = unGzip(response.getBodyStream())
            response.setBodyStream(newBody)
        }
        // Gzip the response body.
        else if (shouldGzip) {
            respHeaders.set(HttpHeaderNames.CONTENT_ENCODING, "gzip")
            respHeaders.remove(HttpHeaderNames.CONTENT_LENGTH)

            Observable<ByteBuf> newBody = gzip(response.getBodyStream())
            response.setBodyStream(newBody)
        }

        return response
    }

    private Observable<ByteBuf> gzip(Observable<ByteBuf> body)
    {
        return body.map({bb ->

            byte[] bytes = ByteBufUtils.toBytes(bb)
            bb.release()

            ByteBuf gzippedBB = Unpooled.buffer()
            ByteBufOutputStream bbos = new ByteBufOutputStream(gzippedBB)
            GZIPOutputStream gzos
            try {
                gzos = new GZIPOutputStream(bbos)
                IOUtils.write(bytes, gzos)
            }
            catch (Throwable e) {
                LOG.error("Error gzipping response body ByteBuf!", e)
            }
            finally {
                IOUtils.closeQuietly(gzos)
                IOUtils.closeQuietly(bbos)
            }

            return gzippedBB
        })
    }

    private Observable<ByteBuf> unGzip(Observable<ByteBuf> body)
    {
        return body.map({bb ->

            GZIPInputStream gzis = new GZIPInputStream(new ByteBufInputStream(bb))

            ByteBuf unGzippedBB = Unpooled.buffer()
            ByteBufOutputStream bbos
            try {
                bbos = new ByteBufOutputStream(unGzippedBB)
                IOUtils.copy(gzis, bbos)
            }
            catch (Throwable e) {
                LOG.error("Error un-gzipping response body ByteBuf!", e)
            }
            finally {
                IOUtils.closeQuietly(gzis)
                IOUtils.closeQuietly(bbos)
                bb.release()
            }

            return unGzippedBB
        })
    }

    @RunWith(MockitoJUnitRunner.class)
    public static class TestUnit extends BaseFilterTest
    {
        GZipResponseFilter filter
        HttpResponseMessage response

        @Before
        public void setup()
        {
            super.setup()
            filter = new GZipResponseFilter()
            response = new HttpResponseMessageImpl(context, request, 99)
        }

        @Test
        public void prepareResponseBody_NeedsGZipping()
        {
            originalRequestHeaders.set("Accept-Encoding", "gzip")

            byte[] originBody = "blah".bytes
            response.setBodyStream(Observable.just(Unpooled.wrappedBuffer(originBody)))
            response.getHeaders().set("Content-Length", Integer.toString(originBody.length))

            HttpResponseMessage result = filter.apply(response)
            byte[] body = result.bufferBody().toBlocking().first()

            // Check body is a gzipped version of the origin body.
            byte[] unzippedBytes = new GZIPInputStream(new ByteArrayInputStream(body)).bytes
            String bodyStr = new String(unzippedBytes, "UTF-8")
            assertEquals("blah", bodyStr)
            assertEquals("gzip", result.getHeaders().getFirst("Content-Encoding"))

            // Check Content-Length header has been removed.
            assertEquals(0, result.getHeaders().get("Content-Length").size())
        }

        @Test
        public void prepareResponseBody_NeedsUnGZipping()
        {
            originalRequestHeaders.set("Accept-Encoding", "identity")
            response.getHeaders().set("Content-Encoding", "gzip")

            byte[] originBody = gzip("blah")
            response.setBodyStream(Observable.just(Unpooled.wrappedBuffer(originBody)))
            response.getHeaders().set("Content-Length", Integer.toString(originBody.length))

            HttpResponseMessage result = filter.applyAsync(response).toBlocking().first()
            byte[] body = result.bufferBody().toBlocking().first()

            // Check returned body is same as origin body.
            String bodyStr = new String(body, "UTF-8")
            assertEquals("blah", bodyStr)
            assertNull(result.getHeaders().getFirst("Content-Encoding"))

            // Check Content-Length header has been removed.
            assertEquals(0, result.getHeaders().get("Content-Length").size())
        }

        @Test
        public void prepareResponseBody_NeedsGZipping_Overridden()
        {
            context.set(OVERRIDE_GZIP_REQUESTED, true)

            byte[] originBody = "blah".bytes
            response.setBodyStream(Observable.just(Unpooled.wrappedBuffer(originBody)))
            response.getHeaders().set("Content-Length", Integer.toString(originBody.length))

            HttpResponseMessage result = filter.applyAsync(response).toBlocking().first()
            byte[] body = result.bufferBody().toBlocking().first()

            // Check body is a gzipped version of the origin body.
            byte[] unzippedBytes = new GZIPInputStream(new ByteArrayInputStream(body)).bytes
            String bodyStr = new String(unzippedBytes, "UTF-8")
            assertEquals("blah", bodyStr)
            assertEquals("gzip", result.getHeaders().getFirst("Content-Encoding"))

            // Check Content-Length header has been removed.
            assertEquals(0, result.getHeaders().get("Content-Length").size())
        }

        @Test
        public void prepareResponseBody_NeedsGZipping_OverriddenNot()
        {
            originalRequestHeaders.set("Accept-Encoding", "gzip")
            context.set(OVERRIDE_GZIP_REQUESTED, false)

            byte[] originBody = "blah".bytes
            response.setBodyStream(Observable.just(Unpooled.wrappedBuffer(originBody)))
            response.getHeaders().set("Content-Length", Integer.toString(originBody.length))

            HttpResponseMessage result = filter.applyAsync(response).toBlocking().first()
            byte[] body = result.bufferBody().toBlocking().first()

            // Check body is a not gzipped.
            String bodyStr = new String(body, "UTF-8")
            assertEquals("blah", bodyStr)
            assertNull(result.getHeaders().getFirst("Content-Encoding"))

            // Check Content-Length header is correct.
            assertEquals(result.getBody().length, Integer.parseInt(result.getHeaders().getFirst("Content-Length")))
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
