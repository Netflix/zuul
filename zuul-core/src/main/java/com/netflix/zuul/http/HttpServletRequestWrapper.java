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


import com.netflix.zuul.constants.ZuulHeaders;
import com.netflix.zuul.context.RequestContext;
import com.netflix.zuul.util.HTTPRequestUtils;
import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;
import java.io.*;
import java.net.SocketTimeoutException;
import java.net.URLDecoder;
import java.util.*;
import java.util.zip.*;

import static org.junit.Assert.*;
import static org.mockito.Mockito.when;

/**
 * This class implements the Wrapper or Decorator pattern.<br/>
 * Methods default to calling through to the wrapped request object,
 * except the ones that read the request's content (parameters, stream or reader).
 * <p/>
 * This class provides a buffered content reading that allows the methods
 * {@link #getReader()}, {@link #getInputStream()} and any of the getParameterXXX to be     called
 * safely and repeatedly with the same results.
 * <p/>
 * This class is intended to wrap relatively small HttpServletRequest instances.
 *
 * @author pgurov
 */
public class HttpServletRequestWrapper extends javax.servlet.http.HttpServletRequestWrapper {

    private final static HashMap<String, String[]> EMPTY_MAP = new HashMap<String, String[]>();
    protected static final Logger LOG = LoggerFactory.getLogger(HttpServletRequestWrapper.class);

    private HttpServletRequest req;
    private byte[] contentData = null;
    private HashMap<String, String[]> parameters = null;

    private long bodyBufferingTimeNs = 0;

    public HttpServletRequestWrapper() {
        super(groovyTrick());
    }

    private static HttpServletRequest groovyTrick() {
        //a trick for Groovy
        throw new IllegalArgumentException("Please use HttpServletRequestWrapper(HttpServletRequest request) constructor!");
    }

    private HttpServletRequestWrapper(HttpServletRequest request, byte[] contentData, HashMap<String, String[]> parameters) {
        super(request);
        req = request;
        this.contentData = contentData;
        this.parameters = parameters;
    }

    public HttpServletRequestWrapper(HttpServletRequest request) {
        super(request);
        req = request;
    }

    /**
     * Returns the wrapped HttpServletRequest.
     * Using the getParameterXXX(), getInputStream() or getReader() methods may interfere
     * with this class operation.
     *
     * @return The wrapped HttpServletRequest.
     */
    @Override
    public HttpServletRequest getRequest() {
        try {
            parseRequest();
        } catch (IOException e) {
            throw new IllegalStateException("Cannot parse the request!", e);
        }
        return req;
    }

    /**
     * This method is safe to use multiple times.
     *
     * @return The request body data.
     */
    public byte[] getContentData() {
        return contentData;
    }


    /**
     * This method is safe to use multiple times.
     * Changing the returned map or the array of any of the map's values will not
     * interfere with this class operation.
     *
     * @return The cloned parameters map.
     */

    public HashMap<String, String[]> getParameters() {
        if (parameters == null) return EMPTY_MAP;
        HashMap<String, String[]> map = new HashMap<String, String[]>(parameters.size() * 2);
        for (String key : parameters.keySet()) {
            map.put(key, parameters.get(key).clone());
        }
        return map;
    }

    private void parseRequest() throws IOException {
        if (parameters != null)
            return; //already parsed

        HashMap<String, List<String>> mapA = new HashMap<String, List<String>>();
        List<String> list;

        Map<String, List<String>> query = HTTPRequestUtils.getInstance().getQueryParams();
        if (query != null) {
            for (String key : query.keySet()) {
                list = query.get(key);
                mapA.put(key, list);
            }
        }

        if (shouldBufferBody()) {

            // Read the request body inputstream into a byte array.
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try {
                // Copy all bytes from inputstream to byte array, and record time taken.
                long bufferStartTime = System.nanoTime();
                IOUtils.copy(req.getInputStream(), baos);
                bodyBufferingTimeNs = System.nanoTime() - bufferStartTime;

                contentData = baos.toByteArray();
            } catch (SocketTimeoutException e) {
                // This can happen if the request body is smaller than the size specified in the
                // Content-Length header, and using tomcat APR connector.
                LOG.error("SocketTimeoutException reading request body from inputstream. error=" + String.valueOf(e.getMessage()));
                if (contentData == null) {
                    contentData = new byte[0];
                }
            }

            try {
                LOG.debug("Length of contentData byte array = " + contentData.length);
                if (req.getContentLength() != contentData.length) {
                    LOG.warn("Content-length different from byte array length! cl=" + req.getContentLength() + ", array=" + contentData.length);
                }
            } catch(Exception e) {
                LOG.error("Error checking if request body gzipped!", e);
            }

            final boolean isPost = req.getMethod().equals("POST");

            String contentType = req.getContentType();
            final boolean isFormBody = contentType != null && contentType.contains("application/x-www-form-urlencoded");

            // only does magic body param parsing for POST form bodies
            if (isPost && isFormBody) {
                String enc = req.getCharacterEncoding();

                if (enc == null) enc = "UTF-8";
                String s = new String(contentData, enc), name, value;
                StringTokenizer st = new StringTokenizer(s, "&");
                int i;

                boolean decode = req.getContentType() != null;
                while (st.hasMoreTokens()) {
                    s = st.nextToken();
                    i = s.indexOf("=");
                    if (i > 0 && s.length() > i + 1) {
                        name = s.substring(0, i);
                        value = s.substring(i + 1);
                        if (decode) {
                            try {
                                name = URLDecoder.decode(name, "UTF-8");
                            } catch (Exception e) {
                            }
                            try {
                                value = URLDecoder.decode(value, "UTF-8");
                            } catch (Exception e) {
                            }
                        }
                        list = mapA.get(name);
                        if (list == null) {
                            list = new LinkedList<String>();
                            mapA.put(name, list);
                        }
                        list.add(value);
                    }
                }
            }
        }

        HashMap<String, String[]> map = new HashMap<String, String[]>(mapA.size() * 2);
        for (String key : mapA.keySet()) {
            list = mapA.get(key);
            map.put(key, list.toArray(new String[list.size()]));
        }

        parameters = map;

    }

    private boolean shouldBufferBody() {

        if (LOG.isDebugEnabled()) {
            LOG.debug("Path = " + req.getPathInfo());
            LOG.debug("Transfer-Encoding = " + String.valueOf(req.getHeader(ZuulHeaders.TRANSFER_ENCODING)));
            LOG.debug("Content-Encoding = " + String.valueOf(req.getHeader(ZuulHeaders.CONTENT_ENCODING)));
            LOG.debug("Content-Length header = " + req.getContentLength());
        }

        boolean should = false;
        if (req.getContentLength() > 0) {
            should = true;
        }
        else if (req.getContentLength() == -1) {
            final String transferEncoding = req.getHeader(ZuulHeaders.TRANSFER_ENCODING);
            if (transferEncoding != null && transferEncoding.equals(ZuulHeaders.CHUNKED)) {
                RequestContext.getCurrentContext().setChunkedRequestBody();
                should = true;
            }
        }

        return should;
    }

    /**
     * Time taken to buffer the request body in nanoseconds.
     * @return
     */
    public long getBodyBufferingTimeNs()
    {
        return bodyBufferingTimeNs;
    }

    /**
     * This method is safe to call multiple times.
     * Calling it will not interfere with getParameterXXX() or getReader().
     * Every time a new ServletInputStream is returned that reads data from the begining.
     *
     * @return A new ServletInputStream.
     */
    @Override
    public ServletInputStream getInputStream() throws IOException {
        parseRequest();

        return new ServletInputStreamWrapper(contentData);
    }

    /**
     * This method is safe to call multiple times.
     * Calling it will not interfere with getParameterXXX() or getInputStream().
     * Every time a new BufferedReader is returned that reads data from the begining.
     *
     * @return A new BufferedReader with the wrapped request's character encoding (or UTF-8 if null).
     */
    @Override
    public BufferedReader getReader() throws IOException {
        parseRequest();

        String enc = req.getCharacterEncoding();
        if (enc == null)
            enc = "UTF-8";
        return new BufferedReader(new InputStreamReader(new ByteArrayInputStream(contentData), enc));
    }

    /**
     * This method is safe to execute multiple times.
     *
     * @see javax.servlet.ServletRequest#getParameter(java.lang.String)
     */
    @Override
    public String getParameter(String name) {
        try {
            parseRequest();
        } catch (IOException e) {
            throw new IllegalStateException("Cannot parse the request!", e);
        }
        if (parameters == null) return null;
        String[] values = parameters.get(name);
        if (values == null || values.length == 0)
            return null;
        return values[0];
    }

    /**
     * This method is safe.
     *
     * @see {@link #getParameters()}
     * @see javax.servlet.ServletRequest#getParameterMap()
     */
    @SuppressWarnings("unchecked")
    @Override
    public Map getParameterMap() {
        try {
            parseRequest();
        } catch (IOException e) {
            throw new IllegalStateException("Cannot parse the request!", e);
        }
        return getParameters();
    }

    /**
     * This method is safe to execute multiple times.
     *
     * @see javax.servlet.ServletRequest#getParameterNames()
     */
    @SuppressWarnings("unchecked")
    @Override
    public Enumeration getParameterNames() {
        try {
            parseRequest();
        } catch (IOException e) {
            throw new IllegalStateException("Cannot parse the request!", e);
        }
        return new Enumeration<String>() {
            private String[] arr = getParameters().keySet().toArray(new String[0]);
            private int idx = 0;

            @Override
            public boolean hasMoreElements() {
                return idx < arr.length;
            }

            @Override
            public String nextElement() {
                return arr[idx++];
            }

        };
    }

    /**
     * This method is safe to execute multiple times.
     * Changing the returned array will not interfere with this class operation.
     *
     * @see javax.servlet.ServletRequest#getParameterValues(java.lang.String)
     */
    @Override
    public String[] getParameterValues(String name) {
        try {
            parseRequest();
        } catch (IOException e) {
            throw new IllegalStateException("Cannot parse the request!", e);
        }
        if (parameters == null) return null;
        String[] arr = parameters.get(name);
        if (arr == null)
            return null;
        return arr.clone();
    }

    public static final class UnitTest {

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

}