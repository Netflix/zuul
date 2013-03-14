package com.netflix.zuul.groovy

import org.junit.runner.RunWith
import org.mockito.runners.MockitoJUnitRunner
import org.mockito.Mock
import javax.servlet.http.HttpServletResponse
import javax.servlet.http.HttpServletRequest
import org.junit.Test

import com.netflix.zuul.context.RequestContext

import static org.junit.Assert.assertNotNull
import static org.junit.Assert.assertEquals

/**
 * Created by IntelliJ IDEA.
 * User: mcohen
 * Date: 1/3/12
 * Time: 4:55 PM
 * To change this template use File | Settings | File Templates.
 */
class GroovyCompatability {


    @RunWith(MockitoJUnitRunner.class)
    public static class TestUnit {

        @Mock
        HttpServletResponse response
        @Mock
        HttpServletRequest request

        @Test
        public void testRequestContext() {
            RequestContext.getCurrentContext().setRequest(request)
            RequestContext.getCurrentContext().setResponse(response)
            assertNotNull(RequestContext.getCurrentContext().getRequest())
            assertNotNull(RequestContext.getCurrentContext().getResponse())
            assertEquals(RequestContext.getCurrentContext().request, request)
            RequestContext.getCurrentContext().test = "moo"
            assertNotNull(RequestContext.getCurrentContext().test)
            assertEquals(RequestContext.getCurrentContext().test, "moo")
            assertNotNull(RequestContext.getCurrentContext().get("test"))
            assertEquals(RequestContext.getCurrentContext().get("test"), "moo")
            RequestContext.getCurrentContext().set("test", "ik")
            assertEquals(RequestContext.getCurrentContext().get("test"), "ik")
            assertEquals(RequestContext.getCurrentContext().test, "ik")
            assertNotNull(RequestContext.currentContext)
            assertEquals(RequestContext.currentContext.test, "ik")

        }

    }

}
