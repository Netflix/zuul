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

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletInputStream;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.io.*;
import java.net.URLDecoder;
import java.security.Principal;
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
public class HttpServletRequestWrapper implements HttpServletRequest {

    private final static HashMap<String, String[]> EMPTY_MAP = new HashMap<String, String[]>();
    protected static final Logger LOG = LoggerFactory.getLogger(HttpServletRequestWrapper.class);

    private HttpServletRequest req;
    private byte[] contentData;
    private HashMap<String, String[]> parameters;

    public HttpServletRequestWrapper() {
        //a trick for Groovy
        throw new IllegalArgumentException("Please use HttpServletRequestWrapper(HttpServletRequest request) constructor!");
    }

    private HttpServletRequestWrapper(HttpServletRequest request, byte[] contentData, HashMap<String, String[]> parameters) {
        req = request;
        this.contentData = contentData;
        this.parameters = parameters;
    }

    public HttpServletRequestWrapper(HttpServletRequest request) {
        if (request == null)
            throw new IllegalArgumentException("The HttpServletRequest is null!");
        req = request;
    }

    /**
     * Returns the wrapped HttpServletRequest.
     * Using the getParameterXXX(), getInputStream() or getReader() methods may interfere
     * with this class operation.
     *
     * @return The wrapped HttpServletRequest.
     */
    public HttpServletRequest getRequest() {
        try {
            parseRequest();
        } catch (IOException e) {
            throw new IllegalStateException("Cannot parse the request!", e);
        }
        return new HttpServletRequestWrapper(req, contentData, parameters);
    }

    /**
     * This method is safe to use multiple times.
     * Changing the returned array will not interfere with this class operation.
     *
     * @return The cloned content data.
     */
    public byte[] getContentData() {
        return contentData.clone();
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

        LOG.debug("Path = " + req.getPathInfo());
        LOG.debug("Transfer-Encoding = " + String.valueOf(req.getHeader(ZuulHeaders.TRANSFER_ENCODING)));
        LOG.debug("Content-Encoding = " + String.valueOf(req.getHeader(ZuulHeaders.CONTENT_ENCODING)));

        LOG.debug("Content-Length header = " + req.getContentLength());
        if (req.getContentLength() > 0) {

            // Read the request body inputstream into a byte array.
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            IOUtils.copy(req.getInputStream(), baos);
            contentData = baos.toByteArray();

            try {
                LOG.debug("Length of contentData byte array = " + contentData.length);
                if (req.getContentLength() != contentData.length) {
                    LOG.warn("Content-length different from byte array length! cl=" + req.getContentLength() + ", array=" + contentData.length);
                }
            } catch(Exception e) {
                LOG.error("Error checking if request body gzipped!", e);
            }

            String enc = req.getCharacterEncoding();

            if (enc == null)
                enc = "UTF-8";
            String s = new String(contentData, enc), name, value;
            StringTokenizer st = new StringTokenizer(s, "&");
            int i;

            boolean decode = req.getContentType() != null && req.getContentType().equalsIgnoreCase("application/x-www-form-urlencoded");
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

        } else if (req.getContentLength() == -1) {
            final String transferEncoding = req.getHeader(ZuulHeaders.TRANSFER_ENCODING);
            if (transferEncoding != null && transferEncoding.equals(ZuulHeaders.CHUNKED)) {
                RequestContext.getCurrentContext().setChunkedRequestBody();
                LOG.debug("Set flag that request body is chunked.");
            }
        } else {
            LOG.warn("Content-Length is neither greater than zero or -1. = " + req.getContentLength());
        }

        HashMap<String, String[]> map = new HashMap<String, String[]>(mapA.size() * 2);
        for (String key : mapA.keySet()) {
            list = mapA.get(key);
            map.put(key, list.toArray(new String[list.size()]));
        }

        parameters = map;

    }

    /**
     * This method is safe to call multiple times.
     * Calling it will not interfere with getParameterXXX() or getReader().
     * Every time a new ServletInputStream is returned that reads data from the begining.
     *
     * @return A new ServletInputStream.
     */
    public ServletInputStream getInputStream() throws IOException {
        parseRequest();

        if (RequestContext.getCurrentContext().isChunkedRequestBody()) {
            return req.getInputStream();
        } else {
            return new ServletInputStreamWrapper(contentData);
        }
    }

    /**
     * This method is safe to call multiple times.
     * Calling it will not interfere with getParameterXXX() or getInputStream().
     * Every time a new BufferedReader is returned that reads data from the begining.
     *
     * @return A new BufferedReader with the wrapped request's character encoding (or UTF-8 if null).
     */
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
    public Enumeration getParameterNames() {
        try {
            parseRequest();
        } catch (IOException e) {
            throw new IllegalStateException("Cannot parse the request!", e);
        }
        return new Enumeration<String>() {
            private String[] arr = getParameters().keySet().toArray(new String[0]);
            private int idx = 0;

            public boolean hasMoreElements() {
                return idx < arr.length;
            }

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

    /* (non-Javadoc)
    * @see javax.servlet.http.HttpServletRequest#getAuthType()
    */
    public String getAuthType() {
        return req.getAuthType();
    }

    /* (non-Javadoc)
    * @see javax.servlet.http.HttpServletRequest#getContextPath()
    */
    public String getContextPath() {
        return req.getContextPath();
    }

    /* (non-Javadoc)
    * @see javax.servlet.http.HttpServletRequest#getCookies()
    */
    public Cookie[] getCookies() {
        return req.getCookies();
    }

    /* (non-Javadoc)
    * @see javax.servlet.http.HttpServletRequest#getDateHeader(java.lang.String)
    */
    public long getDateHeader(String name) {
        return req.getDateHeader(name);
    }

    /* (non-Javadoc)
    * @see javax.servlet.http.HttpServletRequest#getHeader(java.lang.String)
    */
    public String getHeader(String name) {
        return req.getHeader(name);
    }

    /* (non-Javadoc)
    * @see javax.servlet.http.HttpServletRequest#getHeaderNames()
    */
    @SuppressWarnings("unchecked")
    public Enumeration getHeaderNames() {
        return req.getHeaderNames();
    }

    /* (non-Javadoc)
    * @see javax.servlet.http.HttpServletRequest#getHeaders(java.lang.String)
    */
    @SuppressWarnings("unchecked")
    public Enumeration getHeaders(String name) {
        return req.getHeaders(name);
    }

    /* (non-Javadoc)
    * @see javax.servlet.http.HttpServletRequest#getIntHeader(java.lang.String)
    */
    public int getIntHeader(String name) {
        return req.getIntHeader(name);
    }

    /* (non-Javadoc)
    * @see javax.servlet.http.HttpServletRequest#getMethod()
    */
    public String getMethod() {
        return req.getMethod();
    }

    /* (non-Javadoc)
    * @see javax.servlet.http.HttpServletRequest#getPathInfo()
    */
    public String getPathInfo() {
        return req.getPathInfo();
    }

    /* (non-Javadoc)
    * @see javax.servlet.http.HttpServletRequest#getPathTranslated()
    */
    public String getPathTranslated() {
        return req.getPathTranslated();
    }

    /* (non-Javadoc)
    * @see javax.servlet.http.HttpServletRequest#getQueryString()
    */
    public String getQueryString() {
        return req.getQueryString();
    }

    /* (non-Javadoc)
    * @see javax.servlet.http.HttpServletRequest#getRemoteUser()
    */
    public String getRemoteUser() {
        return req.getRemoteUser();
    }

    /* (non-Javadoc)
    * @see javax.servlet.http.HttpServletRequest#getRequestURI()
    */
    public String getRequestURI() {
        return req.getRequestURI();
    }

    /* (non-Javadoc)
    * @see javax.servlet.http.HttpServletRequest#getRequestURL()
    */
    public StringBuffer getRequestURL() {
        return req.getRequestURL();
    }

    /* (non-Javadoc)
    * @see javax.servlet.http.HttpServletRequest#getRequestedSessionId()
    */
    public String getRequestedSessionId() {
        return req.getRequestedSessionId();
    }

    /* (non-Javadoc)
    * @see javax.servlet.http.HttpServletRequest#getServletPath()
    */
    public String getServletPath() {
        return req.getServletPath();
    }

    /* (non-Javadoc)
    * @see javax.servlet.http.HttpServletRequest#getSession()
    */
    public HttpSession getSession() {
        return req.getSession();
    }

    /* (non-Javadoc)
    * @see javax.servlet.http.HttpServletRequest#getSession(boolean)
    */
    public HttpSession getSession(boolean create) {
        return req.getSession(create);
    }

    /* (non-Javadoc)
    * @see javax.servlet.http.HttpServletRequest#getUserPrincipal()
    */
    public Principal getUserPrincipal() {
        return req.getUserPrincipal();
    }

    /* (non-Javadoc)
    * @see javax.servlet.http.HttpServletRequest#isRequestedSessionIdFromCookie()
    */
    public boolean isRequestedSessionIdFromCookie() {
        return req.isRequestedSessionIdFromCookie();
    }

    /* (non-Javadoc)
    * @see javax.servlet.http.HttpServletRequest#isRequestedSessionIdFromURL()
    */
    public boolean isRequestedSessionIdFromURL() {
        return req.isRequestedSessionIdFromURL();
    }

    /* (non-Javadoc)
    * @see javax.servlet.http.HttpServletRequest#isRequestedSessionIdFromUrl()
    */
    @SuppressWarnings("deprecation")
    public boolean isRequestedSessionIdFromUrl() {
        return req.isRequestedSessionIdFromUrl();
    }

    /* (non-Javadoc)
    * @see javax.servlet.http.HttpServletRequest#isRequestedSessionIdValid()
    */
    public boolean isRequestedSessionIdValid() {
        return req.isRequestedSessionIdValid();
    }

    /* (non-Javadoc)
    * @see javax.servlet.http.HttpServletRequest#isUserInRole(java.lang.String)
    */
    public boolean isUserInRole(String role) {
        return req.isUserInRole(role);
    }

    /* (non-Javadoc)
    * @see javax.servlet.ServletRequest#getAttribute(java.lang.String)
    */
    public Object getAttribute(String name) {
        return req.getAttribute(name);
    }

    /* (non-Javadoc)
    * @see javax.servlet.ServletRequest#getAttributeNames()
    */
    @SuppressWarnings("unchecked")
    public Enumeration getAttributeNames() {
        return req.getAttributeNames();
    }

    /* (non-Javadoc)
    * @see javax.servlet.ServletRequest#getCharacterEncoding()
    */
    public String getCharacterEncoding() {
        return req.getCharacterEncoding();
    }

    /* (non-Javadoc)
    * @see javax.servlet.ServletRequest#getContentLength()
    */
    public int getContentLength() {
        return req.getContentLength();
    }

    /* (non-Javadoc)
    * @see javax.servlet.ServletRequest#getContentType()
    */
    public String getContentType() {
        return req.getContentType();
    }

    /* (non-Javadoc)
    * @see javax.servlet.ServletRequest#getLocalAddr()
    */
    public String getLocalAddr() {
        return req.getLocalAddr();
    }

    /* (non-Javadoc)
    * @see javax.servlet.ServletRequest#getLocalName()
    */
    public String getLocalName() {
        return req.getLocalName();
    }

    /* (non-Javadoc)
    * @see javax.servlet.ServletRequest#getLocalPort()
    */
    public int getLocalPort() {
        return req.getLocalPort();
    }

    /* (non-Javadoc)
    * @see javax.servlet.ServletRequest#getLocale()
    */
    public Locale getLocale() {
        return req.getLocale();
    }

    /* (non-Javadoc)
    * @see javax.servlet.ServletRequest#getLocales()
    */
    @SuppressWarnings("unchecked")
    public Enumeration getLocales() {
        return req.getLocales();
    }

    /* (non-Javadoc)
    * @see javax.servlet.ServletRequest#getProtocol()
    */
    public String getProtocol() {
        return req.getProtocol();
    }

    /* (non-Javadoc)
    * @see javax.servlet.ServletRequest#getRealPath(java.lang.String)
    */
    @SuppressWarnings("deprecation")
    public String getRealPath(String path) {
        return req.getRealPath(path);
    }

    /* (non-Javadoc)
    * @see javax.servlet.ServletRequest#getRemoteAddr()
    */
    public String getRemoteAddr() {
        return req.getRemoteAddr();
    }

    /* (non-Javadoc)
    * @see javax.servlet.ServletRequest#getRemoteHost()
    */
    public String getRemoteHost() {
        return req.getRemoteHost();
    }

    /* (non-Javadoc)
    * @see javax.servlet.ServletRequest#getRemotePort()
    */
    public int getRemotePort() {
        return req.getRemotePort();
    }

    /* (non-Javadoc)
    * @see javax.servlet.ServletRequest#getRequestDispatcher(java.lang.String)
    */
    public RequestDispatcher getRequestDispatcher(String path) {
        return req.getRequestDispatcher(path);
    }

    /* (non-Javadoc)
    * @see javax.servlet.ServletRequest#getScheme()
    */
    public String getScheme() {
        return req.getScheme();
    }

    /* (non-Javadoc)
    * @see javax.servlet.ServletRequest#getServerName()
    */
    public String getServerName() {
        return req.getServerName();
    }

    /* (non-Javadoc)
    * @see javax.servlet.ServletRequest#getServerPort()
    */
    public int getServerPort() {
        return req.getServerPort();
    }

    /* (non-Javadoc)
    * @see javax.servlet.ServletRequest#isSecure()
    */
    public boolean isSecure() {
        return req.isSecure();
    }

    /* (non-Javadoc)
    * @see javax.servlet.ServletRequest#removeAttribute(java.lang.String)
    */
    public void removeAttribute(String name) {
        req.removeAttribute(name);
    }

    /* (non-Javadoc)
    * @see javax.servlet.ServletRequest#setAttribute(java.lang.String, java.lang.Object)
    */
    public void setAttribute(String name, Object value) {
        req.setAttribute(name, value);
    }

    /* (non-Javadoc)
    * @see javax.servlet.ServletRequest#setCharacterEncoding(java.lang.String)
    */
    public void setCharacterEncoding(String env)
            throws UnsupportedEncodingException {
        req.setCharacterEncoding(env);
    }

    public static final class UnitTest {

        @Mock
        HttpServletRequest request;

        @Before
        public void before() {
            RequestContext.getCurrentContext().unset();
            MockitoAnnotations.initMocks(this);

            RequestContext.getCurrentContext().setRequest(request);
        }

        private void body(byte[] body) throws IOException {
            when(request.getInputStream()).thenReturn(new ServletInputStreamWrapper(body));
            when(request.getContentLength()).thenReturn(body.length);
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

        public String readZipInputStream(InputStream input) throws IOException {

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

        private byte[] getBytesFromInputStream(InputStream input) throws IOException {
            int v = 0;
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            while ((v = input.read()) != -1) {
                bos.write(v);
            }
            bos.close();
            return bos.toByteArray();
        }


    }

}