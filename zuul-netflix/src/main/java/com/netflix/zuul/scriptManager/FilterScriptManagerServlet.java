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
package com.netflix.zuul.scriptManager;


import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import net.jcip.annotations.ThreadSafe;

import org.apache.commons.fileupload.FileItemFactory;
import org.apache.commons.fileupload.FileItemStream;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.config.DynamicBooleanProperty;
import com.netflix.config.DynamicPropertyFactory;
import com.netflix.config.DynamicStringProperty;
import com.netflix.zuul.constants.ZuulConstants;
import com.netflix.zuul.util.JsonUtility;

/**
 * Servlet for uploading/downloading/managing scripts.
 * <p/>
 * <ul>
 * <li>Upload scripts to the registry for a given endpoint.</li>
 * <li>Download scripts from the registry</li>
 * <li>List all revisions of scripts for a given endpoint.</li>
 * <li>Mark a particular script revision as active for production.</li>
 * </ul>
 */
@ThreadSafe
public class FilterScriptManagerServlet extends HttpServlet {

    public static final DynamicStringProperty redirectPath = new DynamicStringProperty(ZuulConstants.ZUUL_FILTER_ADMIN_REDIRECT, "filterLoader.jsp");
    
    private static final long serialVersionUID = -1L;
    private static final Logger logger = LoggerFactory.getLogger(FilterScriptManagerServlet.class);

    //Set this property to true to enable the admin page. Note that the admin page should be protected to be
    //accessed only internally, not open to the internet.
    public static DynamicBooleanProperty adminEnabled = DynamicPropertyFactory.getInstance().getBooleanProperty(ZuulConstants.ZUUL_FILTER_ADMIN_ENABLED, false);

    /* DAO for performing CRUD operations with scripts */
    private static ZuulFilterDAO scriptDAO;

    public static void setScriptDAO(ZuulFilterDAO scriptDAO) {
        FilterScriptManagerServlet.scriptDAO = scriptDAO;
    }


    /**
     * Default constructor that instantiates default dependencies (ie. the ones that are functional as opposed to those for testing).
     */
    public FilterScriptManagerServlet() {
        super();
        if (scriptDAO == null) scriptDAO = new ZuulFilterDAOCassandra(ZuulFilterDAOCassandra.getCassKeyspace());

    }

    /**
     * Construct with dependency injection for unit-testing (will never be invoked in production since servlets can't have constructors)
     *
     * @param sd
     */
    private FilterScriptManagerServlet(ZuulFilterDAO sd, DynamicBooleanProperty adminEnabledProperty) {
        super();
        scriptDAO = sd;
        adminEnabled = adminEnabledProperty;
    }

    /**
     * GET a script or list of scripts.
     * <p/>
     * Action: LIST
     * <p/>
     * Description: List of all script revisions for the given endpoint URI or list all endpoints if endpoint URI not given.
     * <ul>
     * <li>Request Parameter "endpoint": URI</li>
     * </ul>
     * <p/>
     * Action: DOWNLOAD
     * <p/>
     * Description: Download the text or zip file of scripts for a given endpoint URI + revision.
     * <ul>
     * <li>Request Parameter "endpoint": URI</li>
     * <li>Request Parameter "revision": int of revision to download</li>
     * </ul>
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        // retrieve arguments and validate
        String action = request.getParameter("action");
        /* validate the action and method */
        if (!isValidAction(request, response)) {
            return;
        }

        // perform action
        if ("LIST".equals(action)) {
            handleListAction(request, response);
        } else if ("DOWNLOAD".equals(action)) {
            handleDownloadAction(request, response);
        }
    }

    /**
     * PUT a script
     * <p/>
     * Action: UPLOAD
     * <p/>
     * Description: Upload a new script text or zip file for a given endpoint URI.
     * <ul>
     * <li>Request Parameter "endpoint": URI</li>
     * <li>Request Parameter "userAuthenticationRequired": true/false</li>
     * <li>POST Body: text or zip file with multiple text files</li>
     * </ul>
     * <p/>
     * Action: ACTIVATE
     * <p/>
     * Description: Activate a script to become the default to execute for a given endpoint URI + revision.
     * <ul>
     * <li>Request Parameter "endpoint": URI</li>
     * <li>Request Parameter "revision": int of revision to activate</li>
     * </ul>
     */
    @Override
    protected void doPut(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

        if (!adminEnabled.get()) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Filter admin is disabled. See the zuul.filters.admin.enabled FastProperty.");
            return;
        }

        // retrieve arguments and validate
        String action = request.getParameter("action");
        /* validate the action and method */
        if (!isValidAction(request, response)) {
            return;
        }

        // perform action
        if ("UPLOAD".equals(action)) {
            handleUploadAction(request, response);
        } else if ("ACTIVATE".equals(action)) {
            handleActivateAction(request, response);
        } else if ("CANARY".equals(action)) {
            handleCanaryAction(request, response);
        } else if ("DEACTIVATE".equals(action)) {
            handledeActivateAction(request, response);
        }

    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        doPut(request, response);
    }

    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        setUsageError(405, response);
        return;
    }

    @SuppressWarnings("unchecked")
    private void handleListAction(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String filter_id = request.getParameter("filter_id");
        if (filter_id == null) {
            // get list of all endpoints
            List<String> filterIDs = scriptDAO.getAllFilterIDs();
            Map<String, Object> json = new LinkedHashMap<String, Object>();
            json.put("filters", filterIDs);
            response.getWriter().write(JsonUtility.jsonFromMap(json));
        } else {
            List<FilterInfo> scripts;
            if (Boolean.parseBoolean(request.getParameter("active"))) {
                // get list of all scripts for this endpoint
                FilterInfo activeEndpoint = scriptDAO.getActiveFilterInfoForFilter(filter_id);
                scripts = activeEndpoint == null ? Collections.EMPTY_LIST : Collections.singletonList(activeEndpoint);
            } else {
                // get list of all scripts for this endpoint
                scripts = scriptDAO.getZuulFiltersForFilterId(filter_id);
            }
            if (scripts.size() == 0) {
                setUsageError(404, "ERROR: No scripts found for endpoint: " + filter_id, response);
            } else {
                // output JSON
                Map<String, Object> json = new LinkedHashMap<String, Object>();
                json.put("filter_id", filter_id);
                List<Map<String, Object>> scriptsJson = new ArrayList<Map<String, Object>>();
                for (FilterInfo script : scripts) {
                    Map<String, Object> scriptJson = createEndpointScriptJSON(script);
                    scriptsJson.add(scriptJson);
                }

                json.put("filters", scriptsJson);
                response.getWriter().write(JsonUtility.jsonFromMap(json));
            }
        }
    }

    private void handleDownloadAction(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String filter_id = request.getParameter("filter_id");
        if (filter_id == null) {
            // return error, endpoint is required
            setUsageError(404, "ERROR: No endpoint defined.", response);
        } else {
            String revision = request.getParameter("revision");
            FilterInfo script = null;
            if (revision == null) {
                // get latest
                script = scriptDAO.getLatestFilterInfoForFilter(filter_id);
            } else {
                int revisionNumber = -1;
                try {
                    revisionNumber = Integer.parseInt(revision);
                } catch (Exception e) {
                    setUsageError(400, "ERROR: revision must be an integer.", response);
                    return;
                }
                // get the specific revision
                script = scriptDAO.getFilterInfoForFilter(filter_id, revisionNumber);
            }

            // now output script
            if (script == null) {
                setUsageError(404, "ERROR: No scripts found.", response);
            } else {
                if (script.getFilterCode() == null) {
                    // this shouldn't occur but I want to handle it if it does
                    logger.error("Found FilterInfo object without scripts. Length==0. Request: " + request.getPathInfo());
                    setUsageError(500, "ERROR: script files not found", response);
                } else {
                    // output the single script
                    response.setContentType("text/plain");
                    response.getWriter().write(script.getFilterCode());
                }
            }
        }
    }

    private void handleCanaryAction(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String filter_id = request.getParameter("filter_id");
        if (filter_id == null) {
            // return error, endpoint is required
            setUsageError(404, "ERROR: No endpoint defined.", response);
        } else {
            String revision = request.getParameter("revision");
            if (revision == null) {
                setUsageError(404, "ERROR: No revision defined.", response);
            } else {
                int revisionNumber = -1;
                try {
                    revisionNumber = Integer.parseInt(revision);
                } catch (Exception e) {
                    setUsageError(400, "ERROR: revision must be an integer.", response);
                    return;
                }
                scriptDAO.setCanaryFilter(filter_id, revisionNumber);
                response.sendRedirect(redirectPath.get());
            }
        }
    }

    private void handledeActivateAction(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String filter_id = request.getParameter("filter_id");
        if (filter_id == null) {
            // return error, endpoint is required
            setUsageError(404, "ERROR: No endpoint defined.", response);
        } else {
            String revision = request.getParameter("revision");
            if (revision == null) {
                setUsageError(404, "ERROR: No revision defined.", response);
            } else {
                int revisionNumber = -1;
                try {
                    revisionNumber = Integer.parseInt(revision);
                } catch (Exception e) {
                    setUsageError(400, "ERROR: revision must be an integer.", response);
                    return;
                }
                try {
                    scriptDAO.deActivateFilter(filter_id, revisionNumber);
                } catch (Exception e) {
                    setUsageError(400, "ERROR: " + e.getMessage(), response);
                    return;
                }
                response.sendRedirect(redirectPath.get());
            }
        }

    }

    private void handleActivateAction(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String filter_id = request.getParameter("filter_id");
        if (filter_id == null) {
            // return error, endpoint is required
            setUsageError(404, "ERROR: No endpoint defined.", response);
        } else {
            String revision = request.getParameter("revision");
            if (revision == null) {
                setUsageError(404, "ERROR: No revision defined.", response);
            } else {
                int revisionNumber = -1;
                try {
                    revisionNumber = Integer.parseInt(revision);
                } catch (Exception e) {
                    setUsageError(400, "ERROR: revision must be an integer.", response);
                    return;
                }
                try {
                   scriptDAO.setFilterActive(filter_id, revisionNumber);
                } catch (Exception e) {
                    setUsageError(400, "ERROR: " + e.getMessage(), response);
                    return;
                }
                response.sendRedirect(redirectPath.get());

                //                Map<String, Object> scriptJson = createEndpointScriptJSON(filterInfo);
                //              response.getWriter().write(JsonUtility.jsonFromMap(scriptJson));
            }
        }

    }

    private void handleUploadAction(HttpServletRequest request, HttpServletResponse response) throws IOException {

        String filter = handlePostBody(request, response);


        if (filter != null) {
            FilterInfo filterInfo = null;
            try {
                filterInfo = FilterVerifier.getInstance().verifyFilter(filter);
            } catch (IllegalAccessException e) {
                logger.error(e.getMessage(), e);
                setUsageError(500, "ERROR: Unable to process uploaded data. " + e.getMessage(), response);
            } catch (InstantiationException e) {
                logger.error(e.getMessage(), e);
                setUsageError(500, "ERROR: Bad Filter. " + e.getMessage(), response);
            }
            filterInfo = scriptDAO.addFilter(filter, filterInfo.getFilterType(), filterInfo.getFilterName(), filterInfo.getFilterDisablePropertyName(), filterInfo.getFilterOrder());
            if (filterInfo == null) {
                setUsageError(500, "ERROR: Unable to process uploaded data.", response);
                return;
            }
            response.sendRedirect(redirectPath.get());
        }
    }


    private String handlePostBody(HttpServletRequest request, HttpServletResponse response) throws IOException {

        FileItemFactory factory = new DiskFileItemFactory();
        ServletFileUpload upload = new ServletFileUpload(factory);
        org.apache.commons.fileupload.FileItemIterator it = null;
        try {
            it = upload.getItemIterator(request);

            while (it.hasNext()) {
                FileItemStream stream = it.next();
                InputStream input = stream.openStream();

                // NOTE: we are going to pull the entire stream into memory
                // this will NOT work if we have huge scripts, but we expect these to be measured in KBs, not MBs or larger
                byte[] uploadedBytes = getBytesFromInputStream(input);
                input.close();

                if (uploadedBytes.length == 0) {
                    setUsageError(400, "ERROR: Body contained no data.", response);
                    return null;
                }

                return new String(uploadedBytes);
            }
        } catch (FileUploadException e) {
            throw new IOException(e.getMessage());
        }
        return null;
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

    private Map<String, Object> createEndpointScriptJSON(FilterInfo script) {
        Map<String, Object> scriptJson = new LinkedHashMap<String, Object>();
        scriptJson.put("filter_id", script.getFilterID());
        scriptJson.put("filter_name", script.getFilterName());
        scriptJson.put("filter_type", script.getFilterType());
        scriptJson.put("revision", script.getRevision());
        scriptJson.put("active", script.isActive());
        scriptJson.put("creationDate", script.getCreationDate());
        scriptJson.put("canary", script.isCanary());
        return scriptJson;
    }

    /**
     * Determine if the incoming action + method is a correct combination. If not, output the usage docs and set an error code on the response.
     *
     * @param request
     * @param response
     * @return true if valid, false if not
     */
    private static boolean isValidAction(HttpServletRequest request, HttpServletResponse response) {
        String action = request.getParameter("action");
        if (action != null) {
            return new ValidActionEvaluation(request, response).isValid();
        } else {
            setUsageError(400, "ERROR: Invalid arguments.", response);
            return false;
        }
    }

    /**
     * Set an error code and print out the usage docs to the response with a preceding error message
     *
     * @param statusCode
     * @param response
     */
    private static void setUsageError(int statusCode, String message, HttpServletResponse response) {
        new UsageError(statusCode, message).setOn(response);
    }

    /**
     * Set an error code and print out the usage docs to the response.
     *
     * @param statusCode
     * @param response
     */
    private static void setUsageError(int statusCode, HttpServletResponse response) {
        setUsageError(statusCode, null, response);
    }

    /**
     * Usage documentation to be output when a URL is malformed.
     *
     * @return
     */
    private static String getUsageDoc() {
        return new UsageDoc().get();
    }

    public static class UnitTest {
        @Mock
        HttpServletRequest request;
        @Mock
        HttpServletResponse response;
        @Mock
        PrintWriter responseWriter;
        @Mock
        ServletOutputStream outputStream;

        @Before
        public void before() {
            MockitoAnnotations.initMocks(this);
            try {
                when(response.getWriter()).thenReturn(responseWriter);
                when(response.getOutputStream()).thenReturn(outputStream);
            } catch (IOException e) {
                throw new RuntimeException("failed to initialize mock", e);
            }
        }

        private FilterScriptManagerServlet getEndpointScriptManagerImplementation(ZuulFilterDAO dao) {
            return new FilterScriptManagerServlet(dao, new DynamicBooleanProperty("test.admin.enabled", true));
        }

        /**
         * Not found response for a "list scripts" request.
         *
         * @throws Exception
         */
        @Test
        public void testListScriptNotFound() throws Exception {
            /* setup mock request to return URI */
            when(request.getPathInfo()).thenReturn("?action=LIST&filter_id=name2:type");
            when(request.getParameter("action")).thenReturn("LIST");
            when(request.getParameter("filter_id")).thenReturn("name:type");
            when(request.getMethod()).thenReturn("GET");

            /* setup mock DAO */
            ZuulFilterDAO dao = mock(ZuulFilterDAOCassandra.class);
            List<FilterInfo> emptyResponse = Collections.emptyList();
            when(dao.getZuulFiltersForFilterId(anyString())).thenReturn(emptyResponse);

            /* construct servlet */
            FilterScriptManagerServlet servlet = getEndpointScriptManagerImplementation(dao);
            servlet.service(request, response);

            // verify that we get a 404 when this endpoint isn't found
            verify(response).setStatus(404);
        }

        /**
         * List scripts for an endpoint.
         *
         * @throws Exception
         */
        @Test
        public void testListScripts() throws Exception {
            /* setup mock request to return URI */
            when(request.getPathInfo()).thenReturn("?action=LIST&filter_id=name1:type1");
            when(request.getParameter("action")).thenReturn("LIST");
            when(request.getParameter("filter_id")).thenReturn("name1:type");
            when(request.getMethod()).thenReturn("GET");

            /* setup mock DAO */

            ZuulFilterDAO dao = mock(ZuulFilterDAOCassandra.class);
            List<FilterInfo> scriptsForEndpoint = new ArrayList<FilterInfo>();
            scriptsForEndpoint.add(new FilterInfo("name1:type", "code", "type", "name", "disable", "order", "app"));
            scriptsForEndpoint.add(new FilterInfo("name2:type", "code", "type", "name", "disable", "order", "app"));
            scriptsForEndpoint.add(new FilterInfo("name3:type", "code", "type", "name", "disable", "order", "app"));
            when(dao.getZuulFiltersForFilterId(anyString())).thenReturn(scriptsForEndpoint);

            /* construct servlet */
            FilterScriptManagerServlet servlet = getEndpointScriptManagerImplementation(dao);
            servlet.service(request, response);

            /* verify the default status is used */
            verify(response, never()).setStatus(anyInt());

            /* build up the JSON we expect */
            Map<String, Object> expectedJson = new LinkedHashMap<String, Object>();
            expectedJson.put("filter_id", "name1:type");
            List<Map<String, Object>> scriptsJson = new ArrayList<Map<String, Object>>();
            for (FilterInfo script : scriptsForEndpoint) {
                Map<String, Object> scriptJson = createExpectedJsonMap(script);
                scriptsJson.add(scriptJson);
            }
            expectedJson.put("filters", scriptsJson);

            /* verify that we received the expected JSON */
            String expectedJsonString = JsonUtility.jsonFromMap(expectedJson);
            System.out.println("Expected JSON: \n" + expectedJsonString);
            verify(responseWriter).write(expectedJsonString);
        }

        /**
         * List all endpoints
         *
         * @throws Exception
         */
        @Test
        public void testListEndpoints() throws Exception {
            /* setup mock request to return URI */
            when(request.getPathInfo()).thenReturn("?action=LIST");
            when(request.getParameter("action")).thenReturn("LIST");
            when(request.getMethod()).thenReturn("GET");

            /* setup mock DAO */
            ZuulFilterDAO dao = mock(ZuulFilterDAOCassandra.class);
            List<String> filters = new ArrayList<String>();
            filters.add("name1:type");
            filters.add("name2:type");
            filters.add("name3:type");
            filters.add("name4:type");
            when(dao.getAllFilterIDs()).thenReturn(filters);

            /* construct servlet */
            FilterScriptManagerServlet servlet = getEndpointScriptManagerImplementation(dao);
            servlet.service(request, response);

            /* build up the JSON we expect */
            Map<String, Object> expectedJson = new LinkedHashMap<String, Object>();
            expectedJson.put("filters", filters);

            /* verify the default status is used */
            verify(response, never()).setStatus(anyInt());

            /* verify that we received the expected JSON */
            String expectedJsonString = JsonUtility.jsonFromMap(expectedJson);
            System.out.println("Expected JSON: \n" + expectedJsonString);
            verify(responseWriter).write(expectedJsonString);
        }

        @Test
        public void testErrorWithUsageIfIncorrectMethod() throws Exception {
            /* setup mock request to return URI */
            when(request.getPathInfo()).thenReturn("?action=LIST&filter=unknown:type");
            when(request.getParameter("action")).thenReturn("LIST");
            // send POST instead of GET so we should get an error
            when(request.getMethod()).thenReturn("POST");

            FilterScriptManagerServlet servlet = new FilterScriptManagerServlet();
            servlet.service(request, response);

            // a 405 because POST with those arguments is invalid
            verify(response).setStatus(405);
            // test that the usage docs were output
            verify(response).getWriter();
            verify(responseWriter).write("ERROR: Invalid HTTP method for action type.\n\n");
            verify(responseWriter).write(getUsageDoc());
        }

        @Test
        public void testErrorWithUsageIfIncorrectArguments1() throws Exception {
            /* setup mock request to return URI */
            when(request.getPathInfo()).thenReturn("?");
            when(request.getMethod()).thenReturn("GET");

            FilterScriptManagerServlet servlet = new FilterScriptManagerServlet();
            servlet.service(request, response);

            System.out.println("--------------------------------------------------------");
            System.out.println(getUsageDoc());
            System.out.println("--------------------------------------------------------");

            // a 400 because the resource exists, but arguments are incorrect
            verify(response).setStatus(400);
            // test that the usage docs were output
            verify(response).getWriter();
            verify(responseWriter).write("ERROR: Invalid arguments.\n\n");
            verify(responseWriter).write(getUsageDoc());
        }

        @Test
        public void testErrorWithUsageIfIncorrectArguments2() throws Exception {
            /* setup mock request to return URI */
            when(request.getPathInfo()).thenReturn("?action=UNKNOWN");
            when(request.getParameter("action")).thenReturn("UNKNOWN");
            when(request.getMethod()).thenReturn("GET");

            FilterScriptManagerServlet servlet = new FilterScriptManagerServlet();
            servlet.service(request, response);

            // a 400 because the resource exists, but arguments are incorrect
            verify(response).setStatus(400);
            // test that the usage docs were output
            verify(response).getWriter();
            verify(responseWriter).write("ERROR: Unknown action type.\n\n");
            verify(responseWriter).write(getUsageDoc());
        }

        @Test
        public void testDoDeleteReturnsError() throws Exception {
            /* setup mock request to return URI */
            when(request.getPathInfo()).thenReturn("?");
            // send POST instead of GET so we should get an error
            when(request.getMethod()).thenReturn("DELETE");

            FilterScriptManagerServlet servlet = new FilterScriptManagerServlet();
            servlet.service(request, response);

            // a 405 because POST with those arguments is invalid
            verify(response).setStatus(405);
            // test that the usage docs were output
            verify(response).getWriter();
            verify(responseWriter).write(getUsageDoc());
        }


        @Test
        public void testDownloadSingleScriptRevision() throws Exception {
            /* setup mock */
            String filter_id = "name:type";
            String action = "DOWNLOAD";
            when(request.getPathInfo()).thenReturn("?action=" + action + "&filter_id=" + filter_id + "&revision=2");
            when(request.getMethod()).thenReturn("GET");
            when(request.getParameter("action")).thenReturn(action);
            when(request.getParameter("filter_id")).thenReturn(filter_id);
            when(request.getParameter("revision")).thenReturn("2");

            /* setup mock DAO */
            ZuulFilterDAO dao = mock(ZuulFilterDAOCassandra.class);
            FilterInfo script = mock(FilterInfo.class);
            when(dao.getFilterInfoForFilter(filter_id, 2)).thenReturn(script);
            String code = "code";

            when(script.getFilterCode()).thenReturn(code);

            FilterScriptManagerServlet servlet = getEndpointScriptManagerImplementation(dao);
            servlet.service(request, response);

            /* verify the default status is used */
            verify(response, never()).setStatus(anyInt());

            // verify mime-type
            verify(response).setContentType("text/plain");

            // verify the script is written to the response
            verify(responseWriter).write("code");
        }

        @Test
        public void testDownloadSingleScriptLatest() throws Exception {
            /* setup mock */
            String filter_id = "name:type";
            String action = "DOWNLOAD";
            when(request.getPathInfo()).thenReturn("?action=" + action + "&filter_id=" + filter_id);
            when(request.getMethod()).thenReturn("GET");
            when(request.getParameter("action")).thenReturn(action);
            when(request.getParameter("filter_id")).thenReturn(filter_id);

            /* setup mock DAO */
            ZuulFilterDAO dao = mock(ZuulFilterDAOCassandra.class);
            FilterInfo script = mock(FilterInfo.class);
            when(dao.getLatestFilterInfoForFilter(filter_id)).thenReturn(script);
            when(dao.getFilterInfoForFilter(filter_id, 2)).thenReturn(script);
            when(script.getFilterCode()).thenReturn("code");

            FilterScriptManagerServlet servlet = getEndpointScriptManagerImplementation(dao);
            servlet.service(request, response);

            /* verify the default status is used */
            verify(response, never()).setStatus(anyInt());

            // verify mime-type
            verify(response).setContentType("text/plain");

            // verify the script is written to the response
            verify(responseWriter).write("code");
        }

        @Test
        public void testDownloadSingleScriptPlusErrorHandlerLatest() throws Exception {
            /* setup mock */
            String filter_id = "name:type";
            String action = "DOWNLOAD";
            when(request.getPathInfo()).thenReturn("?action=" + action + "&filter_id=" + filter_id);
            when(request.getMethod()).thenReturn("GET");
            when(request.getParameter("action")).thenReturn(action);
            when(request.getParameter("filter_id")).thenReturn(filter_id);

            /* setup mock DAO */
            ZuulFilterDAO dao = mock(ZuulFilterDAOCassandra.class);
            FilterInfo script = mock(FilterInfo.class);
            when(dao.getLatestFilterInfoForFilter(filter_id)).thenReturn(script);
            when(dao.getFilterInfoForFilter(filter_id, 2)).thenReturn(script);
            when(script.getFilterCode()).thenReturn("code");

            FilterScriptManagerServlet servlet = getEndpointScriptManagerImplementation(dao);
            servlet.service(request, response);

            /* verify the default status is used */
            verify(response, never()).setStatus(anyInt());


            // the writer should not be touched since we need binary, not text
            verify(responseWriter).write(anyString());
        }


        @Test
        public void testDownloadEndpointNotFound() throws Exception {
            /* setup mock */
            String action = "DOWNLOAD";
            when(request.getPathInfo()).thenReturn("?action=" + action);
            when(request.getMethod()).thenReturn("GET");
            when(request.getParameter("action")).thenReturn(action);

            ZuulFilterDAO dao = mock(ZuulFilterDAOCassandra.class);
            FilterScriptManagerServlet servlet = getEndpointScriptManagerImplementation(dao);
            servlet.service(request, response);

            // a 404 because incorrect arguments were given (so the resource 'does not exist')
            verify(response).setStatus(404);
            // test that the usage docs were output
            verify(response).getWriter();
            verify(responseWriter).write("ERROR: No endpoint defined.\n\n");
            verify(responseWriter).write(getUsageDoc());
        }

        @Test
        public void testActivateRevision() throws Exception {
            /* setup mock */
            String endpoint = "name:type";
            String action = "ACTIVATE";
            when(request.getPathInfo()).thenReturn("?action=" + action + "&filter_id=" + endpoint + "&revision=2");
            when(request.getMethod()).thenReturn("POST");
            when(request.getParameter("action")).thenReturn(action);
            when(request.getParameter("filter_id")).thenReturn(endpoint);
            when(request.getParameter("revision")).thenReturn("2");

            /* setup mock DAO */
            ZuulFilterDAOCassandra dao = mock(ZuulFilterDAOCassandra.class);

            /* setup the mock script that will be uploaded */
            FilterInfo script = mockEndpointScript();

            // userAuthenticationRequired should default to true
            when(dao.setFilterActive(endpoint, 2)).thenReturn(script);

            // execute the servlet
            FilterScriptManagerServlet servlet = getEndpointScriptManagerImplementation(dao);
            servlet.service(request, response);

            /* verify the default status is used */
            verify(response, never()).setStatus(anyInt());
            verify(dao).setFilterActive(endpoint, 2);
        }

        @Test
        public void testActivateEndpointNotFound() throws Exception {
            /* setup mock */
            String action = "ACTIVATE";
            when(request.getPathInfo()).thenReturn("?action=" + action);
            when(request.getMethod()).thenReturn("POST");
            when(request.getParameter("action")).thenReturn(action);

            ZuulFilterDAO dao = mock(ZuulFilterDAOCassandra.class);
            FilterScriptManagerServlet servlet = getEndpointScriptManagerImplementation(dao);
            servlet.service(request, response);

            // a 404 because incorrect arguments were given (so the resource 'does not exist')
            verify(response).setStatus(404);
            // test that the usage docs were output
            verify(response).getWriter();
            verify(responseWriter).write("ERROR: No endpoint defined.\n\n");
            verify(responseWriter).write(getUsageDoc());
        }

        @Test
        public void testActivateRevisionNotFound() throws Exception {
            /* setup mock */
            String endpoint = "/ps3/{userID}/home";
            String action = "ACTIVATE";
            when(request.getPathInfo()).thenReturn("?action=" + action + "&filter_id=" + endpoint);
            when(request.getMethod()).thenReturn("POST");
            when(request.getParameter("action")).thenReturn(action);
            when(request.getParameter("filter_id")).thenReturn(endpoint);

            ZuulFilterDAO dao = mock(ZuulFilterDAOCassandra.class);
            FilterScriptManagerServlet servlet = getEndpointScriptManagerImplementation(dao);
            servlet.service(request, response);

            // a 404 because incorrect arguments were given (so the resource 'does not exist')
            verify(response).setStatus(404);
            // test that the usage docs were output
            verify(response).getWriter();
            verify(responseWriter).write("ERROR: No revision defined.\n\n");
            verify(responseWriter).write(getUsageDoc());
        }

        private Map<String, Object> createExpectedJsonMap(FilterInfo script) {
            Map<String, Object> expectedJson = new LinkedHashMap<String, Object>();
            expectedJson.put("filter_id", script.getFilterID());
            expectedJson.put("filter_name", script.getFilterName());
            expectedJson.put("filter_type", script.getFilterType());
            expectedJson.put("revision", script.getRevision());
            expectedJson.put("active", script.isActive());
            expectedJson.put("creationDate", script.getCreationDate());
            expectedJson.put("canary", script.isCanary());
            return expectedJson;
        }

        private FilterInfo mockEndpointScript() {
            Calendar now = Calendar.getInstance();
            FilterInfo script = mock(FilterInfo.class);
            when(script.getCreationDate()).thenReturn(now.getTime());
            when(script.getRevision()).thenReturn(1);
            when(script.isActive()).thenReturn(false);
            when(script.isCanary()).thenReturn(false);
            return script;
        }
    }

}
