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
package filters.endpoint

import com.netflix.zuul.context.HttpQueryParams
import com.netflix.zuul.context.HttpRequestMessage
import com.netflix.zuul.context.HttpResponseMessage
import com.netflix.zuul.context.SessionContext
import com.netflix.zuul.exception.ZuulException
import com.netflix.zuul.filters.BaseSyncFilter
import com.netflix.zuul.filters.SyncEndpoint
import com.netflix.zuul.stats.ErrorStatsManager
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.runners.MockitoJUnitRunner

class ErrorResponse extends SyncEndpoint<HttpRequestMessage, HttpResponseMessage>
{
    @Override
    HttpResponseMessage apply(HttpRequestMessage request)
    {
        SessionContext context = request.getContext()

        HttpResponseMessage response = new HttpResponseMessage(context, request, 500)

        Throwable ex = context.getAttributes().getThrowable()
        try {
            throw ex
        } catch (ZuulException e) {
            String cause = e.errorCause
            if (cause == null) cause = "UNKNOWN"
            response.getHeaders().add("X-Netflix-Error-Cause", "Zuul Error: " + cause)
            if (e.nStatusCode == 404) {
                ErrorStatsManager.manager.putStats("ROUTE_NOT_FOUND", "")
            } else {
                ErrorStatsManager.manager.putStats(context.getAttributes().route, "Zuul_Error_" + cause)
            }

            if (getOverrideStatusCode(request)) {
                response.setStatus(200);


            } else {
                response.setStatus(e.nStatusCode);
            }
            response.setBody("${getErrorMessage(request, response, e, e.nStatusCode)}".getBytes("UTF-8"))

        } catch (Throwable throwable) {
            response.getHeaders().add("X-Zuul-Error-Cause", "Zuul Error UNKNOWN Cause")
            ErrorStatsManager.manager.putStats(context.getAttributes().route, "Zuul_Error_UNKNOWN_Cause")

            if (getOverrideStatusCode(request)) {
                response.setStatus(200);
            } else {
                response.setStatus(500);
            }
            response.setBody("${getErrorMessage(request, response, throwable, 500)}".getBytes("UTF-8"))

        }

        return response
    }
    /*
    JSON/ xml ErrorResponse responses

v=1 or unspecified:
<status>
<status_code>status_code</status_code>
<message>message</message>
</status>

v=1.5,2.0:
<status>
<message>user_id is invalid</message>
</status>

v=1.5,2.0:
{"status": {"message": "user_id is invalid"}}

v=1 or unspecified:

     */

    String getErrorMessage(HttpRequestMessage request, HttpResponseMessage response, Throwable ex, int status_code) {

        String ver = getVersion(request)
        String format = getOutputType(request)
        switch (ver) {
            case '1':
            case '1.0':
                switch (format) {
                    case 'json':
                        response.getHeaders().set("Content-Type", "application/json")
                        String errorMessage = """{"status": {"message": "${ex.message}", "status_code": ${status_code}}}"""
                        String callback = getCallback(request)
                        if (callback) {
                            errorMessage = callback + "(" + errorMessage + ");"
                        }
                        return errorMessage
                    case 'xml':
                    default:
                        response.getHeaders().set("Content-Type", "application/xml")
                        return """<status>
  <status_code>${status_code}</status_code>
  <message>${ex.message}</message>
</status>"""
                }
                break;
            case '1.5':
            case '2.0':
            default:
                switch (format) {
                    case 'json':
                        response.getHeaders().set("Content-Type", "application/json")
                        String errorMessage = """{"status": {"message": "${ex.message}"}}"""
                        String callback = getCallback(request)
                        if (callback) {
                            errorMessage = callback + "(" + errorMessage + ");"
                        }
                        return errorMessage
                    case 'xml':
                    default:
                        response.getHeaders().set("Content-Type", "application/xml")
                        return """<status>
<message>${ex.message}</message>
</status>"""
                }
                break;

        }

    }

    boolean getOverrideStatusCode(HttpRequestMessage request) {
        String override = request.getQueryParams().getFirst("override_error_status")
        String callback = getCallback(request)
        if (callback != null) return true;
        if (override == null) return false
        return Boolean.valueOf(override)

    }

    String getCallback(HttpRequestMessage request) {
        String callback = request.getQueryParams().getFirst("callback")
        if (callback == null) return null;
        return callback;
    }

    String getOutputType(HttpRequestMessage request) {
        String output = request.getQueryParams().getFirst("output")
        if (output == null) return "xml"
        return output;
    }

    String getVersion(HttpRequestMessage request) {
        String version = request.getQueryParams().getFirst("v")
        if (version == null) return "1"
        if (getOverrideStatusCode(request)) return "1"
        return version;
    }


    @RunWith(MockitoJUnitRunner.class)
    public static class TestUnit {

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
            ctx.getAttributes().throwable = th
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


}