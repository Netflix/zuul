package outbound

import com.netflix.zuul.context.SessionContext
import com.netflix.zuul.message.Headers
import com.netflix.zuul.message.http.HttpRequestMessage
import com.netflix.zuul.message.http.HttpResponseMessage
import com.netflix.zuul.message.http.HttpResponseMessageImpl
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.runners.MockitoJUnitRunner

import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

@RunWith(MockitoJUnitRunner.class)
class GZipResponseFilterTest extends GroovyTestCase {

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

        when(request.getContext()).thenReturn(ctx)
        when(request.getInboundRequest()).thenReturn(request)
        reqHeaders = new Headers()
        when(request.getHeaders()).thenReturn(reqHeaders)

        response = new HttpResponseMessageImpl(ctx, request, 99)
    }

    @Test
    public void prepareResponseBody_NeedsGZipping()
    {
        reqHeaders.set("Accept-Encoding", "gzip")

        byte[] originBody = "blah".bytes
        response.setBody(originBody)

        HttpResponseMessageImpl result = filter.applyAsync(response).toBlocking().first()

        // Check body is a gzipped version of the origin body.
        byte[] unzippedBytes = new GZIPInputStream(new ByteArrayInputStream(result.getBody())).bytes
        String bodyStr = new String(unzippedBytes, "UTF-8")
        assertEquals("blah", bodyStr)
        assertEquals("gzip", result.getHeaders().getFirst("Content-Encoding"))
    }

    @Test
    public void prepareResponseBody_NeedsUnGZipping() {
        reqHeaders.set("Accept-Encoding", "identity")
        response.getHeaders().set("Content-Encoding", "gzip")

        byte[] originBody = gzip("blah")
        response.setBody(originBody)

        HttpResponseMessageImpl result = filter.applyAsync(response).toBlocking().first()

        // Check returned body is same as origin body.
        String bodyStr = new String(result.getBody(), "UTF-8")
        assertEquals("blah", bodyStr)
        assertNull(result.getHeaders().getFirst("Content-Encoding"))
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
