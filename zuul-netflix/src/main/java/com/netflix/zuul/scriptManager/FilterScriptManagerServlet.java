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


import com.netflix.config.DynamicBooleanProperty;
import com.netflix.config.DynamicPropertyFactory;
import com.netflix.config.DynamicStringProperty;
import com.netflix.zuul.constants.ZuulConstants;
import com.netflix.zuul.util.JsonUtility;
import net.jcip.annotations.ThreadSafe;
import org.apache.commons.fileupload.FileItemFactory;
import org.apache.commons.fileupload.FileItemStream;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

    public static final DynamicBooleanProperty adminEnabled = DynamicPropertyFactory.getInstance().getBooleanProperty(ZuulConstants.ZUUL_FILTER_ADMIN_ENABLED, true);
    public static final DynamicStringProperty redirectPath = new DynamicStringProperty(ZuulConstants.ZUUL_FILTER_ADMIN_REDIRECT, "filterLoader.jsp");
    
    private static final long serialVersionUID = -1L;
    private static final Logger logger = LoggerFactory.getLogger(FilterScriptManagerServlet.class);

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
    FilterScriptManagerServlet(ZuulFilterDAO sd) {
        super();
        scriptDAO = sd;
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
}
