package endpoint

import com.netflix.zuul.bytebuf.ByteBufUtils
import com.netflix.zuul.context.Debug
import com.netflix.zuul.context.SessionContext
import com.netflix.zuul.message.http.HttpRequestMessage
import com.netflix.zuul.message.http.HttpResponseMessage
import com.netflix.zuul.message.http.HttpResponseMessageImpl
import org.apache.http.*
import org.apache.http.entity.ByteArrayEntity
import org.apache.http.message.BasicHeader
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.runners.MockitoJUnitRunner

import static org.mockito.Mockito.*

@RunWith(MockitoJUnitRunner.class)
class ZuulHostRequestTest extends GroovyTestCase {

    @Mock
    HttpRequestMessage request

    SessionContext ctx
    HttpResponseMessage response

    @Before
    public void setup()
    {
        ctx = new SessionContext()
        when(request.getContext()).thenReturn(ctx)
        response = new HttpResponseMessageImpl(ctx, request, 200)
    }

    @Test
    public void testSetResponse() {

        Debug.setDebugRouting(ctx, false)
        Debug.setDebugRequest(ctx, false)

        ZuulHostRequest filter = new ZuulHostRequest()
        filter = spy(filter)

        Header[] headers = [
                new BasicHeader("test", "test"),
                new BasicHeader("content-length", "100")
        ]

        HttpResponse httpResponse = mock(HttpResponse.class)
        StatusLine status = mock(StatusLine.class)
        when(status.getStatusCode()).thenReturn(200)
        when(httpResponse.getStatusLine()).thenReturn(status)

        byte[] body = "test".bytes
        HttpEntity entity = new ByteArrayEntity(body)
        when(httpResponse.getEntity()).thenReturn(entity)
        when(httpResponse.getAllHeaders()).thenReturn(headers)
        response = filter.createHttpResponseMessage(httpResponse, request)

        Assert.assertEquals(response.getStatus(), 200)
        byte[] respBodyBytes = ByteBufUtils.toBytes(response.getBodyStream().toBlocking().single())
        Assert.assertEquals(body.length, respBodyBytes.length)
        Assert.assertTrue(response.getHeaders().contains('test', "test"))
    }

    @Test
    public void testShouldFilter() {
        ctx.setRouteHost(new URL("http://www.moldfarm.com"))
        ZuulHostRequest filter = new ZuulHostRequest()
        Assert.assertTrue(filter.shouldFilter(request))
    }

    @Test
    public void testGetHost() {

        ZuulHostRequest filter = new ZuulHostRequest()

        URL url = new URL("http://www.moldfarm.com")
        HttpHost host = filter.getHttpHost(url)
        Assert.assertNotNull(host)
        Assert.assertEquals(host.hostName, "www.moldfarm.com")
        Assert.assertEquals(host.port, -1)
        Assert.assertEquals(host.schemeName, "http")

        url = new URL("https://www.moldfarm.com:8000")
        host = filter.getHttpHost(url)
        Assert.assertNotNull(host)
        Assert.assertEquals(host.hostName, "www.moldfarm.com")
        Assert.assertEquals(host.port, 8000)
        Assert.assertEquals(host.schemeName, "https")
    }
}
