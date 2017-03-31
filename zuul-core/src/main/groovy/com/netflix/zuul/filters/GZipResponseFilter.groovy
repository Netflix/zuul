package com.netflix.zuul.filters

import com.netflix.zuul.bytebuf.ByteBufUtils
import com.netflix.zuul.context.SessionContext
import com.netflix.zuul.filters.http.HttpOutboundSyncFilter
import com.netflix.zuul.message.Headers
import com.netflix.zuul.message.ZuulMessage
import com.netflix.zuul.message.http.HttpHeaderNames
import com.netflix.zuul.message.http.HttpRequestInfo
import com.netflix.zuul.message.http.HttpResponseMessage
import com.netflix.zuul.message.http.HttpResponseMessageImpl
import com.netflix.zuul.util.HttpUtils
import io.netty.buffer.ByteBuf
import io.netty.buffer.ByteBufInputStream
import io.netty.buffer.ByteBufOutputStream
import io.netty.buffer.Unpooled
import io.netty.handler.codec.http.DefaultHttpContent
import io.netty.handler.codec.http.HttpContent
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
import static junit.framework.Assert.assertFalse
import static junit.framework.Assert.assertNull
import static junit.framework.Assert.assertTrue

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
    private static final String SHOULD_UNGZIP_RESPONSE = "shouldungzipresponse";
    private static final String SHOULD_GZIP_RESPONSE = "shouldgzipresponse";

    @Override
    int filterOrder() {
        return 5
    }

    @Override
    boolean shouldFilter(HttpResponseMessage response) {
        if (! response.hasBody()) {
            return false;
        }

        final HttpRequestInfo request = response.getInboundRequest()
        final SessionContext ctx = response.getContext()
        final Headers respHeaders = response.getHeaders()

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
        ctx.set(SHOULD_UNGZIP_RESPONSE, shouldUnGzip);
        boolean shouldGzip = gzipResponseIfOriginDidnt && isGzipRequested && !isResponseGzipped
        ctx.set(SHOULD_GZIP_RESPONSE, shouldGzip);

        return (shouldGzip || shouldUnGzip);
    }

    @Override
    HttpResponseMessage apply(HttpResponseMessage response) {
        final Headers respHeaders = response.getHeaders()
        final Boolean shouldUnGzip = response.getContext().get(SHOULD_UNGZIP_RESPONSE);
        final Boolean shouldGzip = response.getContext().get(SHOULD_GZIP_RESPONSE);

        // UnGzip the response body.
        if (shouldUnGzip != null && shouldUnGzip.booleanValue()) {
            respHeaders.remove(HttpHeaderNames.CONTENT_ENCODING)
            respHeaders.remove(HttpHeaderNames.CONTENT_LENGTH)
        }
        // Gzip the response body.
        else if (shouldGzip != null && shouldGzip.booleanValue()) {
            respHeaders.set(HttpHeaderNames.CONTENT_ENCODING, "gzip")
            respHeaders.remove(HttpHeaderNames.CONTENT_LENGTH)
        }

        return response
    }

    @Override
    HttpContent processContentChunk(final ZuulMessage response, final HttpContent chunk) {
        final Boolean shouldUnGzip = response.getContext().get(SHOULD_UNGZIP_RESPONSE);
        final Boolean shouldGzip = response.getContext().get(SHOULD_GZIP_RESPONSE);
        // UnGzip the response body.
        if (shouldUnGzip != null && shouldUnGzip.booleanValue()) {
            return unGzip(chunk);
        }
        // Gzip the response body.
        if (shouldGzip != null && shouldGzip.booleanValue()) {
            return gzip(chunk);
        }
        return chunk;
    }

    private HttpContent gzip(final HttpContent chunk) {
        final byte[] bytes = ByteBufUtils.toBytes(chunk.content())
        chunk.release()
        final ByteBuf gzippedBB = Unpooled.buffer()
        final ByteBufOutputStream bbos = new ByteBufOutputStream(gzippedBB)
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
        return new DefaultHttpContent(gzippedBB)
    }

    private HttpContent unGzip(HttpContent chunk) {
        final GZIPInputStream gzis = new GZIPInputStream(new ByteBufInputStream(chunk.content()))
        final ByteBuf unGzippedBB = Unpooled.buffer()
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
            chunk.release()
        }
        return new DefaultHttpContent(unGzippedBB)
    }

    @RunWith(MockitoJUnitRunner.class)
    public static class TestUnit extends BaseFilterTest {
        GZipResponseFilter filter
        HttpResponseMessage response

        @Before
        public void setup() {
            super.setup()
            filter = new GZipResponseFilter()
            response = new HttpResponseMessageImpl(context, request, 99)
        }

        private HttpContent toHttpContent(byte[] chunk) {
            return new DefaultHttpContent(Unpooled.wrappedBuffer(chunk));
        }

        private byte[] fromHttpContent(HttpContent chunk) {
            return ByteBufUtils.toBytes(chunk.content());
        }

        @Test
        public void prepareResponseBody_NeedsGZipping() {
            originalRequestHeaders.set("Accept-Encoding", "gzip")

            byte[] originBody = "blah".bytes
            response.setBody(originBody)
            response.getHeaders().set("Content-Length", Integer.toString(originBody.length))

            assertTrue(filter.shouldFilter(response));

            HttpResponseMessage result = filter.apply(response)
            HttpContent bodyChunk = filter.processContentChunk(response, toHttpContent(originBody))
            byte[] body = fromHttpContent(bodyChunk);

            // Check body is a gzipped version of the origin body.
            byte[] unzippedBytes = new GZIPInputStream(new ByteArrayInputStream(body)).bytes
            String bodyStr = new String(unzippedBytes, "UTF-8")
            assertEquals("blah", bodyStr)
            assertEquals("gzip", result.getHeaders().getFirst("Content-Encoding"))

            // Check Content-Length header has been removed.
            assertEquals(0, result.getHeaders().get("Content-Length").size())
        }

        @Test
        public void prepareResponseBody_NeedsUnGZipping() {
            originalRequestHeaders.set("Accept-Encoding", "identity")
            response.getHeaders().set("Content-Encoding", "gzip")

            byte[] originBody = gzip("blah")
            response.setBody(originBody)
            response.getHeaders().set("Content-Length", Integer.toString(originBody.length))

            assertTrue(filter.shouldFilter(response));

            HttpResponseMessage result = filter.apply(response)
            HttpContent bodyChunk = filter.processContentChunk(response, toHttpContent(originBody))
            byte[] body = fromHttpContent(bodyChunk);

            // Check returned body is same as origin body.
            String bodyStr = new String(body, "UTF-8")
            assertEquals("blah", bodyStr)
            assertNull(result.getHeaders().getFirst("Content-Encoding"))

            // Check Content-Length header has been removed.
            assertEquals(0, result.getHeaders().get("Content-Length").size())
        }

        @Test
        public void prepareResponseBody_NeedsGZipping_Overridden() {
            context.set(OVERRIDE_GZIP_REQUESTED, true)

            byte[] originBody = "blah".bytes
            response.setBody(originBody)
            response.getHeaders().set("Content-Length", Integer.toString(originBody.length))

            assertTrue(filter.shouldFilter(response));

            HttpResponseMessage result = filter.apply(response)
            HttpContent bodyChunk = filter.processContentChunk(response, toHttpContent(originBody))
            byte[] body = fromHttpContent(bodyChunk);

            // Check body is a gzipped version of the origin body.
            byte[] unzippedBytes = new GZIPInputStream(new ByteArrayInputStream(body)).bytes
            String bodyStr = new String(unzippedBytes, "UTF-8")
            assertEquals("blah", bodyStr)
            assertEquals("gzip", result.getHeaders().getFirst("Content-Encoding"))

            // Check Content-Length header has been removed.
            assertEquals(0, result.getHeaders().get("Content-Length").size())
        }

        @Test
        public void prepareResponseBody_NeedsGZipping_OverriddenNot() {
            originalRequestHeaders.set("Accept-Encoding", "gzip")
            context.set(OVERRIDE_GZIP_REQUESTED, false)

            byte[] originBody = "blah".bytes
            response.setBody(originBody)
            response.getHeaders().set("Content-Length", Integer.toString(originBody.length))

            assertFalse(filter.shouldFilter(response));

            HttpResponseMessage result = filter.apply(response)
            HttpContent bodyChunk = filter.processContentChunk(response, toHttpContent(originBody))
            byte[] body = fromHttpContent(bodyChunk);

            // Check body is a not gzipped.
            String bodyStr = new String(body, "UTF-8")
            assertEquals("blah", bodyStr)
            assertNull(result.getHeaders().getFirst("Content-Encoding"))

            // Check Content-Length header is correct.
            assertEquals(result.getBody().length, Integer.parseInt(result.getHeaders().getFirst("Content-Length")))
        }

        private byte[] gzip(String data) {
            byte[] originBody = data.bytes

            ByteArrayOutputStream baos = new ByteArrayOutputStream()
            GZIPOutputStream gzOut = new GZIPOutputStream(baos)
            gzOut.write(originBody)
            gzOut.close()

            return baos.toByteArray()
        }
    }
}
