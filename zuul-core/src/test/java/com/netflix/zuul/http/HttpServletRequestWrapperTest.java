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
package com.netflix.zuul.http;


import com.netflix.zuul.context.RequestContext;
import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.runners.MockitoJUnitRunner;

import javax.servlet.http.HttpServletRequest;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public final class HttpServletRequestWrapperTest {

    @Mock
    HttpServletRequest request;

    @Before
    public void before() {
        RequestContext.getCurrentContext().unset();
        MockitoAnnotations.initMocks(this);

        RequestContext.getCurrentContext().setRequest(request);

        method("GET");
        contentType("zuul/test-content-type");
    }

    private void body(byte[] body) throws IOException {
        when(request.getInputStream()).thenReturn(new ServletInputStreamWrapper(body));
        when(request.getContentLength()).thenReturn(body.length);
    }

    private void method(String s) {
        when(request.getMethod()).thenReturn(s);
    }

    private void contentType(String s) {
        when(request.getContentType()).thenReturn(s);
    }

    private static String readZipInputStream(InputStream input) throws IOException {

        byte[] uploadedBytes = getBytesFromInputStream(input);
        input.close();

            /* try to read it as a zip file */
        String uploadFileTxt = null;
        ZipInputStream zInput = new ZipInputStream(new ByteArrayInputStream(uploadedBytes));
        ZipEntry zipEntry = zInput.getNextEntry();
        if (zipEntry != null) {
            // we have a ZipEntry, so this is a zip file
            while (zipEntry != null) {
                byte[] fileBytes = getBytesFromInputStream(zInput);
                uploadFileTxt = new String(fileBytes);

                zipEntry = zInput.getNextEntry();
            }
        }
        return uploadFileTxt;
    }

    private static byte[] getBytesFromInputStream(InputStream input) throws IOException {
        int v = 0;
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        while ((v = input.read()) != -1) {
            bos.write(v);
        }
        bos.close();
        return bos.toByteArray();
    }

    @Test
    public void handlesDuplicateParams() {
        when(request.getQueryString()).thenReturn("path=one&key1=val1&path=two");
        final HttpServletRequestWrapper w = new HttpServletRequestWrapper(request);

        // getParameters doesn't call parseRequest internally, not sure why
        // so I'm forcing it here
        w.getParameterMap();

        final Map<String, String[]> params = w.getParameters();
        assertFalse("params should not be empty", params.isEmpty());
        final String[] paths = params.get("path");
        assertTrue("paths param should not be empty", paths.length > 0);
        assertEquals("one", paths[0]);
        assertEquals("two", paths[1]);
    }

    @Test
    public void handlesPlainRequestBody() throws IOException {
        final String body = "hello";
        body(body.getBytes());

        final HttpServletRequestWrapper wrapper = new HttpServletRequestWrapper(request);
        assertEquals(body, IOUtils.toString(wrapper.getInputStream()));
    }

    @Test
    public void handlesGzipRequestBody() throws IOException {
        // creates string, gzips into byte array which will be mocked as InputStream of request
        final String body = "hello";
        final byte[] bodyBytes = body.getBytes();
        // in this case the compressed stream is actually larger - need to allocate enough space
        final ByteArrayOutputStream byteOutStream = new ByteArrayOutputStream(0);
        final GZIPOutputStream gzipOutStream = new GZIPOutputStream(byteOutStream);
        gzipOutStream.write(bodyBytes);
        gzipOutStream.finish();
        gzipOutStream.flush();
        body(byteOutStream.toByteArray());

        final HttpServletRequestWrapper wrapper = new HttpServletRequestWrapper(request);
        assertEquals(body, IOUtils.toString(new GZIPInputStream(wrapper.getInputStream())));
    }

    @Test
    public void handlesZipRequestBody() throws IOException {

        final String body = "hello";
        final byte[] bodyBytes = body.getBytes();

        final ByteArrayOutputStream byteOutStream = new ByteArrayOutputStream(0);
        ZipOutputStream zOutput = new ZipOutputStream(byteOutStream);

        zOutput.putNextEntry(new ZipEntry("f1"));
        zOutput.write(bodyBytes);
        zOutput.finish();
        zOutput.flush();
        body(byteOutStream.toByteArray());


        final HttpServletRequestWrapper wrapper = new HttpServletRequestWrapper(request);


        assertEquals(body, readZipInputStream(wrapper.getInputStream()));
    }

    @Test
    public void parsesParamsFromFormBody() throws Exception {
        method("POST");
        body("one=1&two=2".getBytes());
        contentType("application/x-www-form-urlencoded");

        final HttpServletRequestWrapper wrapper = new HttpServletRequestWrapper(request);
        final Map params = wrapper.getParameterMap();
        assertTrue(params.containsKey("one"));
        assertTrue(params.containsKey("two"));
    }

    @Test
    public void ignoresParamsInBodyForNonPosts() throws Exception {
        method("PUT");
        body("one=1&two=2".getBytes());
        contentType("application/x-www-form-urlencoded");

        final HttpServletRequestWrapper wrapper = new HttpServletRequestWrapper(request);
        final Map params = wrapper.getParameterMap();
        assertFalse(params.containsKey("one"));
    }

    @Test
    public void ignoresParamsInBodyForNonForms() throws Exception {
        method("POST");
        body("one=1&two=2".getBytes());
        contentType("application/json");

        final HttpServletRequestWrapper wrapper = new HttpServletRequestWrapper(request);
        final Map params = wrapper.getParameterMap();
        assertFalse(params.containsKey("one"));
    }

    @Test
    public void handlesPostsWithNoContentTypeHeader() throws Exception {
        method("POST");
        body("one=1&two=2".getBytes());
        contentType(null);

        final HttpServletRequestWrapper wrapper = new HttpServletRequestWrapper(request);
        final Map params = wrapper.getParameterMap();
        assertFalse(params.containsKey("one"));
    }

}