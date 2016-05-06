package com.netflix.zuul.scriptManager;

import com.netflix.zuul.util.JsonUtility;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


public class FilterScriptManagerServletTest {
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
        return new FilterScriptManagerServlet(dao);
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
        verify(responseWriter).write(new UsageDoc().get());
    }

    @Test
    public void testErrorWithUsageIfIncorrectArguments1() throws Exception {
            /* setup mock request to return URI */
        when(request.getPathInfo()).thenReturn("?");
        when(request.getMethod()).thenReturn("GET");

        FilterScriptManagerServlet servlet = new FilterScriptManagerServlet();
        servlet.service(request, response);

        System.out.println("--------------------------------------------------------");
        System.out.println(new UsageDoc().get());
        System.out.println("--------------------------------------------------------");

        // a 400 because the resource exists, but arguments are incorrect
        verify(response).setStatus(400);
        // test that the usage docs were output
        verify(response).getWriter();
        verify(responseWriter).write("ERROR: Invalid arguments.\n\n");
        verify(responseWriter).write(new UsageDoc().get());
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
        verify(responseWriter).write(new UsageDoc().get());
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
        verify(responseWriter).write(new UsageDoc().get());
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
        verify(responseWriter).write(new UsageDoc().get());
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
        verify(responseWriter).write(new UsageDoc().get());
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
        verify(responseWriter).write(new UsageDoc().get());
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