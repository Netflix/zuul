package com.netflix.zuul.jetty;

import org.eclipse.jetty.alpn.server.ALPNServerConnectionFactory;
import org.eclipse.jetty.http2.server.HTTP2ServerConnectionFactory;
import org.eclipse.jetty.server.*;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.ssl.SslContextFactory;

import javax.servlet.RequestDispatcher;
import javax.servlet.Servlet;
import javax.servlet.ServletException;
import javax.servlet.http.*;
import java.io.IOException;
import java.util.Date;

public class JettySampleServer
{

    public static void main(String... args) throws Exception
    {
        Server server = new Server();

        ServletContextHandler context = new ServletContextHandler(server, "/",ServletContextHandler.SESSIONS);
        context.setResourceBase("");
        //context.addFilter(PushCacheFilter.class,"/*", EnumSet.of(DispatcherType.REQUEST));
        context.addServlet(new ServletHolder(servlet), "/test/*");
        context.addServlet(new ServletHolder(servlet2), "/test2/*");
        context.addServlet(DefaultServlet.class, "/").setInitParameter("maxCacheSize","81920");
        server.setHandler(context);

        // HTTP Configuration
        HttpConfiguration http_config = new HttpConfiguration();
        http_config.setSecureScheme("https");
        http_config.setSecurePort(8443);
        http_config.setSendXPoweredBy(true);
        http_config.setSendServerVersion(true);

        // HTTP Connector
        ServerConnector http = new ServerConnector(server,new HttpConnectionFactory(http_config));
        http.setPort(8080);
        server.addConnector(http);

        // SSL Context Factory for HTTPS and HTTP/2
        SslContextFactory sslContextFactory = new SslContextFactory();
        sslContextFactory.setKeyStorePath("conf/keystore.jks");
        sslContextFactory.setKeyStorePassword("OBF:1vny1zlo1x8e1vnw1vn61x8g1zlu1vn4");
        sslContextFactory.setKeyManagerPassword("OBF:1u2u1wml1z7s1z7a1wnl1u2g");

        // HTTPS Configuration
        HttpConfiguration https_config = new HttpConfiguration(http_config);
        https_config.addCustomizer(new SecureRequestCustomizer());

        // HTTP/2 Connection Factory
        HTTP2ServerConnectionFactory h2 = new HTTP2ServerConnectionFactory(https_config);

        NegotiatingServerConnectionFactory.checkProtocolNegotiationAvailable();
        ALPNServerConnectionFactory alpn = new ALPNServerConnectionFactory(
                http.getDefaultProtocol(),
                h2.getProtocol()
        );
        alpn.setDefaultProtocol(http.getDefaultProtocol());

        // SSL Connection Factory
        SslConnectionFactory ssl = new SslConnectionFactory(sslContextFactory,alpn.getProtocol());

        // HTTP/2 Connector
        ServerConnector http2Connector =
                new ServerConnector(server,ssl,alpn,h2,new HttpConnectionFactory(https_config));
        http2Connector.setPort(8443);
        server.addConnector(http2Connector);

        //ALPN.debug=true;

        server.start();
        server.dumpStdErr();
        server.join();
    }

    static Servlet servlet = new HttpServlet()
    {
        private static final long serialVersionUID = 1L;

        @Override
        protected void service(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
        {
            String code=request.getParameter("code");
            if (code!=null)
                response.setStatus(Integer.parseInt(code));

            String pushOne = request.getParameter("push");
            if(pushOne != null) {
                String path = "/conf/jetty-logging.properties";
                RequestDispatcher dispatcher = request.getServletContext().getRequestDispatcher(path);
                ((Dispatcher)dispatcher).push(request);
            }

            HttpSession session = request.getSession(true);
            if (session.isNew())
                response.addCookie(new Cookie("bigcookie",
                        "This is a test cookies that was created on "+new Date()+" and is used by the jetty http/2 test servlet."));
            response.setHeader("Custom","Value");
            response.setContentType("text/plain");
            String content = "Hello from Jetty using "+request.getProtocol() +"\n";
            content+="uri="+request.getRequestURI()+"\n";
            content+="session="+session.getId()+(session.isNew()?"(New)\n":"\n");
            content+="date="+new Date()+"\n";
            response.setContentLength(content.length());
            response.getOutputStream().print(content);
        }
    };


    static Servlet servlet2 = new HttpServlet()
    {
        private static final long serialVersionUID = 1L;

        @Override
        protected void service(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
        {
            // Push the css file.
            RequestDispatcher dispatcher = request.getServletContext().getRequestDispatcher("/web/test.css");
            ((Dispatcher)dispatcher).push(request);

            // Forward to the html file.
            RequestDispatcher dispatcherHtml = request.getServletContext().getRequestDispatcher("/web/test.html");
            dispatcherHtml.forward(request, response);
        }
    };
}