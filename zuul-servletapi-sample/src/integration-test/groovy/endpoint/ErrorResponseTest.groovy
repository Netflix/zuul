package endpoint

import com.netflix.zuul.context.SessionContext
import com.netflix.zuul.message.http.HttpQueryParams
import com.netflix.zuul.message.http.HttpRequestMessage
import com.netflix.zuul.message.http.HttpResponseMessage
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.runners.MockitoJUnitRunner

@RunWith(MockitoJUnitRunner.class)
class ErrorResponseTest {

    ErrorResponse filter
    SessionContext ctx
    Throwable th

    @Mock
    HttpRequestMessage request

    HttpQueryParams queryParams

    @Before
    public void setup() {
        filter = new ErrorResponse()
        ctx = new SessionContext()
        Mockito.when(request.getContext()).thenReturn(ctx)

        queryParams = new HttpQueryParams()
        Mockito.when(request.getQueryParams()).thenReturn(queryParams)
        th = new Exception("test")
        ctx.setError(th)
    }

    @Test
    public void testErrorXMLv10() {
        queryParams.set("v", "1.0")
        queryParams.set("override_error_status", "true")

        HttpResponseMessage response = filter.apply(request);

        String body = response.getBody() ? new String(response.getBody(), "UTF-8") : ""
        Assert.assertTrue(body.contains("<message>test</message>"))
        Assert.assertTrue(body.contains("<status_code>500</status_code>"))
        Assert.assertEquals(200, response.getStatus())
    }

    @Test
    public void testErrorXMLv10OverrideErrorStatus() {
        queryParams.set("v", "1.0")

        HttpResponseMessage response = filter.apply(request);

        String body = response.getBody() ? new String(response.getBody(), "UTF-8") : ""
        Assert.assertTrue(body.contains("<message>test</message>"))
        Assert.assertTrue(body.contains("<status_code>500</status_code>"))
        Assert.assertTrue(response.getStatus() == 500)
    }


    @Test
    public void testErrorXML() {
        HttpResponseMessage response = filter.apply(request);

        String body = response.getBody() ? new String(response.getBody(), "UTF-8") : ""
        Assert.assertTrue(body.contains("<message>test</message>"))
        Assert.assertTrue(body.contains("<status_code>500</status_code>"))
        Assert.assertTrue(response.getStatus() == 500)
    }

    @Test
    public void testErrorXMLv20() {
        queryParams.set("v", "2.0")

        HttpResponseMessage response = filter.apply(request);

        String body = response.getBody() ? new String(response.getBody(), "UTF-8") : ""
        Assert.assertTrue(body.contains("<message>test</message>"))
        Assert.assertFalse(body.contains("<status_code>500</status_code>"))
        Assert.assertTrue(response.getStatus() == 500)
    }

    @Test
    public void testErrorJSON() {
        queryParams.set("output", "json")

        HttpResponseMessage response = filter.apply(request);

        String body = response.getBody() ? new String(response.getBody(), "UTF-8") : ""
        Assert.assertTrue(body.equals("{\"status\": {\"message\": \"test\", \"status_code\": 500}}"))
        Assert.assertTrue(response.getStatus() == 500)
    }

    @Test
    public void testErrorJSONv20() {
        queryParams.set("output", "json")
        queryParams.set("v", "2.0")

        HttpResponseMessage response = filter.apply(request);

        String body = response.getBody() ? new String(response.getBody(), "UTF-8") : ""
        Assert.assertTrue(body.equals("{\"status\": {\"message\": \"test\"}}"))
        Assert.assertTrue(response.getStatus() == 500)
    }


    @Test
    public void testErrorJSONv20Callback() {
        queryParams.set("output", "json")
        queryParams.set("v", "2.0")
        queryParams.set("callback", "moo")

        HttpResponseMessage response = filter.apply(request);

        String body = response.getBody() ? new String(response.getBody(), "UTF-8") : ""
        Assert.assertEquals(body, "moo({\"status\": {\"message\": \"test\", \"status_code\": 500}});")
        Assert.assertEquals(200, response.getStatus())
    }

    @Test
    public void testErrorJSONCallback() {
        queryParams.set("output", "json")
        queryParams.set("callback", "moo")

        HttpResponseMessage response = filter.apply(request);

        String body = response.getBody() ? new String(response.getBody(), "UTF-8") : ""
        Assert.assertEquals(body, "moo({\"status\": {\"message\": \"test\", \"status_code\": 500}});")
        Assert.assertEquals(200, response.getStatus())
    }


    @Test
    public void testErrorJSONv20OverrideErrorStatus() {
        queryParams.set("output", "json")
        queryParams.set("v", "2.0")
        queryParams.set("override_error_status", "true")

        HttpResponseMessage response = filter.apply(request);

        String body = response.getBody() ? new String(response.getBody(), "UTF-8") : ""
        Assert.assertEquals(body, "{\"status\": {\"message\": \"test\", \"status_code\": 500}}")
        Assert.assertEquals(200, response.getStatus())
    }
}
