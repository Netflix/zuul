package com.netflix.zuul.util;

import com.netflix.zuul.context.RequestContext;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletRequest;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Created by IntelliJ IDEA.
 * User: mcohen
 * Date: 2/10/12
 * Time: 8:22 AM
 * To change this template use File | Settings | File Templates.
 */
public class HTTPRequestUtils {
	
	

	private static Logger logger = LoggerFactory.getLogger(HTTPRequestUtils.class);
	
    private final static HTTPRequestUtils INSTANCE = new HTTPRequestUtils();
    
    private static final String X_FORWARDED_FOR_HEADER = "x-forwarded-for";
    
    private final static String[] LOCAL_IP_ADDRS = {
    	"2607:fb10:",                          // IPv6 range is 2607:fb10::/32
        "216.35.131.141",                       // Netflix's NAT address.
        "127.0.0.1",                            // localhost
        "172.16.",                              //
        "172.17.",                              //
        "172.18.",                              //
        "172.19.",                              //
        "172.20.",                              //
        "172.21.",                              //
        "172.22.",                              //
        "172.23.",                              //
        "172.24.",                              //
        "172.25.",                              //
        "172.26.",                              //
        "172.27.",                              //
        "172.28.",                              //
        "172.29.",                              //
        "172.30.",                              //
        "172.31.",                              //
        "208.75.76.",                           // new datacenter
        "208.75.77.",                           // new datacenter
        "208.75.78.",                           // new datacenter
        "208.75.79.",                           // new datacenter
        "10.",
        "0:0:0:0:0:0:0:1",                     // IPv6 localhost (long)
        "::1",                                 // IPv6 localhost (short)
         "69.53.224.",
         "69.53.225.",
         "69.53.226.",
         "69.53.227.",
         "69.53.228.",
         "69.53.229.",
         "69.53.230.",
         "69.53.231.",
         "69.53.232.",
         "69.53.233.",
         "69.53.234.",
         "69.53.235.",
         "69.53.236.",
         "69.53.237.",
         "69.53.238.",
         "69.53.239.",
         "69.53.240.",
         "69.53.241.",
         "69.53.242.",
         "69.53.243.",
         "69.53.244.",
         "69.53.245.",
         "69.53.246.",
         "69.53.247.",
         "69.53.248.",
         "69.53.249.",
         "69.53.250.",
         "69.53.251.",
         "69.53.252.",
         "69.53.253.",
         "69.53.254.",
         "69.53.255.",
         "50.18.195.185",						// Requests coming from jenkins VPC slaves appear to be coming from this single EIP. Treat those requests as internal. 
         
        };
    
    public boolean isInternalIP(HttpServletRequest request) {
        try {
            final String xForwardedFor = request.getHeader(X_FORWARDED_FOR_HEADER);
            String clientIP = null;
            if (xForwardedFor == null) {
                clientIP = request.getRemoteAddr();
            } else {
                clientIP = extractClientIpFromXForwardedFor(xForwardedFor);
            }
            return isInternalIP(clientIP);
        } catch (Exception e) {
            logger.warn("Unable to analyze IP so return isInternalIP as FALSE.", e);
            return false;
        }
    }

    public boolean isInternalIP(String addr) {
    	
        // handle IPv6 localnets
        if (addr.matches("fe80:0?:0?:.*"))
            return true;
        // handle IPv6 localhost (and variants)
        if (addr.matches("(0?:0?:0?:0?:0?:0?)?:0?:1"))
            return true;
        for (int i = 0; i < LOCAL_IP_ADDRS.length; i++) {
            if (addr.startsWith(LOCAL_IP_ADDRS[i])) {
                return true;
            }
        }
        return false;
    }

   

    private final String extractClientIpFromXForwardedFor(String xForwardedFor) {
        if (xForwardedFor == null) {
            return null;
        }
        xForwardedFor = xForwardedFor.trim();
        String tokenized[] = xForwardedFor.split(",");
        if (tokenized.length == 0) {
            return null;
        } else {
            return tokenized[0].trim();
        }
    }


 


    public static HTTPRequestUtils getInstance() {
        return INSTANCE;
    }

    public String getHeaderValue(String sHeaderName) {
        return RequestContext.getCurrentContext().getRequest().getHeader(sHeaderName);
    }

    public String getFormValue(String sHeaderName) {
        return RequestContext.getCurrentContext().getRequest().getParameter(sHeaderName);
    }

    public Map<String, List<String>> getQueryParams() {

        Map<String, List<String>> qp = RequestContext.getCurrentContext().getRequestQueryParams();
        if (qp != null) return qp;

        HttpServletRequest request = RequestContext.getCurrentContext().getRequest();

        qp = new HashMap<String, List<String>>();

        if (request.getQueryString() == null) return null;
        StringTokenizer st = new StringTokenizer(request.getQueryString(), "&");
        int i;

        while (st.hasMoreTokens()) {
            String s = st.nextToken();
            i = s.indexOf("=");
            if (i > 0 && s.length() > i + 1) {
                String name = s.substring(0, i);
                String value = s.substring(i + 1);

                try {
                    name = URLDecoder.decode(name, "UTF-8");
                } catch (Exception e) {
                }
                try {
                    value = URLDecoder.decode(value, "UTF-8");
                } catch (Exception e) {
                }

                List valueList = qp.get(name);
                if (valueList == null) {
                    valueList = new LinkedList<String>();
                    qp.put(name, valueList);
                }

                valueList.add(value);
            }
        }

        RequestContext.getCurrentContext().setRequestQueryParams(qp);
        return qp;
    }

    /**
     * Checks headers, query string, and form body for a given parameter
     *
     * @param sName
     * @return
     */
    public String getValueFromRequestElements(String sName) {
        String sValue = null;
        if (getQueryParams() != null) {
            final List<String> v = getQueryParams().get(sName);
            if (v != null && !v.isEmpty()) sValue = v.iterator().next();
        }
        if (sValue != null) return sValue;
        sValue = getHeaderValue(sName);
        if (sValue != null) return sValue;
        sValue = getFormValue(sName);
        if (sValue != null) return sValue;
        return null;
    }

    public boolean isGzipped(String contentEncoding) {
        return contentEncoding.contains("gzip");
    }
    
    public static class UnitTest {

        @Test
        public void testExtractIPfromForwardedForHeader1() {
            String header = " 43.24.143.23, 10.92.59.188 ";
            HttpServletRequest request = mock(HttpServletRequest.class);
            when(request.getHeader(X_FORWARDED_FOR_HEADER)).thenReturn(header);
            assertFalse(HTTPRequestUtils.getInstance().isInternalIP(request));
        }

        @Test
        public void testExtractIPfromForwardedForHeader2() {
            String header = " 10.92.59.188 ";
            HttpServletRequest request = mock(HttpServletRequest.class);
            when(request.getHeader(X_FORWARDED_FOR_HEADER)).thenReturn(header);
            assertTrue(HTTPRequestUtils.getInstance().isInternalIP(request));
        }
        
        @Test
        public void testExtractIPfromForwardedForHeader3() {
            String header = " 2607:fb10:2:176:882f:8ff5:f22b:3c09, 10.249.46.3, 10.28.232.35 ";
            HttpServletRequest request = mock(HttpServletRequest.class);
            when(request.getHeader(X_FORWARDED_FOR_HEADER)).thenReturn(header);
            assertTrue(HTTPRequestUtils.getInstance().isInternalIP(request));
        }

        /**
         * ForwardedFor header is NULL, so use RemoteAddr
         */
        @Test
        public void testExtractIPfromRemoteAddr1() {
            HttpServletRequest request = mock(HttpServletRequest.class);
            when(request.getHeader(X_FORWARDED_FOR_HEADER)).thenReturn(null);
            when(request.getRemoteAddr()).thenReturn("10.92.59.188");
            assertTrue(HTTPRequestUtils.getInstance().isInternalIP(request));
        }

        /**
         * ForwardedFor header is NULL, so use RemoteAddr
         */
        @Test
        public void testExtractIPfromRemoteAddr2() {
            HttpServletRequest request = mock(HttpServletRequest.class);
            when(request.getHeader(X_FORWARDED_FOR_HEADER)).thenReturn(null);
            when(request.getRemoteAddr()).thenReturn("43.24.143.23");
            assertFalse(HTTPRequestUtils.getInstance().isInternalIP(request));
        }

        @Test
        public void detectsGzip() {
            assertTrue(HTTPRequestUtils.getInstance().isGzipped("gzip"));
        }

        @Test
        public void detectsNonGzip() {
            assertFalse(HTTPRequestUtils.getInstance().isGzipped("identity"));
        }

        @Test
        public void detectsGzipAmongOtherEncodings() {
            assertTrue(HTTPRequestUtils.getInstance().isGzipped("gzip, deflate"));
        }

    }

}
