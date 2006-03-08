/*
 * JMEHttpUserManager.java
 *
 * Created on January 5, 2006, 9:38 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package com.sun.gi.comm.users.server.impl;

import com.sun.gi.comm.routing.Router;
import com.sun.gi.comm.users.server.UserManager;
import com.sun.gi.comm.users.validation.UserValidatorFactory;
import com.sun.gi.utils.http.ServletListener;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpSessionEvent;
import javax.servlet.http.HttpSessionListener;
import org.mortbay.http.handler.ResourceHandler;
import org.mortbay.http.nio.SocketChannelListener;
import org.mortbay.jetty.Server;
import org.mortbay.jetty.servlet.ServletHandler;
import org.mortbay.jetty.servlet.ServletHttpContext;
import org.mortbay.jetty.servlet.SessionManager;



/**
 *
 * @author as93050
 */
public class JMEHttpUserManager implements UserManager, ServletListener, HttpSessionListener {
    
    private String host = "localhost";
    private String gameName = "Test";
    private final static String USER_OBJECT = "USER_OBJECT";
    private static boolean servletContainerStarted = false;
    private String listenerServletname = "com.sun.gi.comm.users.server.impl.jme.JMEListenerServlet";
    
    private int port = 8080;
    
    private Router router;
    private JMEBatchProcessor batchProcessor;;
    private static Map<String,ServletListener> servletListeners = new HashMap<String,ServletListener>();

    private UserValidatorFactory validatorFactory;    

    private int sessionTimeout = 300;
    
    /** Creates a new instance of JMEHttpUserManager */
    public JMEHttpUserManager(Router router, Map params) throws InstantiationException {
        this.router = router;
        setParams(params);        
        init();
        servletListeners.put(gameName,this);
    }
    
    private void setParams(final Map params) throws NumberFormatException {
        String p = (String) params.get("host");
        if (p != null) {
            host = p;
        }
        p = (String) params.get("port");
        if (p != null) {
            port = Integer.parseInt(p);
        } 
        p = (String)params.get("gameName");
        if (p != null) {
           gameName = p; 
        }
        p = (String) params.get("session_timeout");
        if (p != null) {
            sessionTimeout = Integer.parseInt(p);
        }
    }
    
    private void init() throws InstantiationException {
        if (!servletContainerStarted) {
            startServletContainer();
        }
        new JMEBatchProcessor();
        
    }
    
    private void startServletContainer() throws InstantiationException {
/*        Connector[] connectors = createConnector();
        ContextHandler context = createContext();
        ServletHandler servletHandler = createServletHandler();
        SessionHandler sessionHandler = new SessionHandler();
        sessionHandler.setHandler(servletHandler);
        setupServletMappings(servletHandler);
        Server server = createJettyServer(context, connectors, sessionHandler);
        startServer(server);*/
        try {
            Server server = new Server();
            SocketChannelListener listener = new SocketChannelListener();
            listener.setHost(host);
            listener.setPort(port);
            //server.addListener(host + ":" + port);
            server.addListener(listener);
            ServletHttpContext context = (ServletHttpContext) server.getContext("/");            
            context.addServlet("JMEServlet","/Servlet/*",
                           listenerServletname);
            context.setResourceBase("static");
            context.addHandler(new ResourceHandler());
            ServletHandler handler = context.getServletHandler();
            SessionManager sessionManager = handler.getSessionManager();
            sessionManager.setMaxInactiveInterval(sessionTimeout);
            sessionManager.addEventListener(this);
            startServer(server);
            
            servletContainerStarted = true;
        } catch (Exception ex) {
            InstantiationException iEx = new InstantiationException();
            iEx.initCause(ex);
            throw iEx;
        }
        
    }
    
    private void startServer(Server server) throws InstantiationException {
        try {
            server.start();
        } catch (Exception ex) {
            InstantiationException iEx = new InstantiationException();
            iEx.initCause(ex);
            throw iEx;
        }
    }
/*    private Server createJettyServer(final ContextHandler context,
            final Connector[] connectors, final SessionHandler sessionHandler) {
        Server server = new Server();
        server.setConnectors(connectors);
        context.setHandler(sessionHandler);
        Handler[] handlers = {context};
        server.setHandlers(handlers);
        return server;
    }
    
    private ContextHandler createContext() {
        ContextHandler context = new ContextHandler();
        context.setContextPath("/");
        // prevent NPE in logging stuff:
        context.setDisplayName(getClass().getName());
        return context;
    }
    
    private Connector[] createConnector() {
        SelectChannelConnector connector = new SelectChannelConnector();
        connector.setHost(host);
        connector.setPort(port);
        Connector[] connectors = {connector};
        return connectors;
    }
    
    private void setupServletMappings(final ServletHandler servletHandler) {
        ServletMapping mapping = new ServletMapping();
        mapping.setPathSpec("/");
        mapping.setServletName("JMEServlet");
        ServletMapping[] mappings = {mapping};
        servletHandler.setServletMappings(mappings);
    }
    
    private ServletHandler createServletHandler() {
        ServletHandler servletHandler = new ServletHandler();
        ServletHolder holder = new ServletHolder(); 
        holder.setName("JMEServlet");
        holder.setClassName(listenerServletname);
        ServletHolder[] holders = {holder};
        servletHandler.setServlets(holders);
        return servletHandler;
    }*/
    public void setUserValidatorFactory(UserValidatorFactory validatorFactory) {
        this.validatorFactory = validatorFactory;
    }
    
    public String getClientClassname() {
        return "com.sun.gi.comm.users.client.impl.JMEHttpUserManagerClient";
    }
    
    public Map<String, String> getClientParams() {
        Map<String, String> params = new HashMap<String, String>();
        params.put("host", host);
        params.put("port", Integer.toString(port));
        return params;
    }

    public Queue<byte[]> dataArrived(byte[] data, HttpSession session) {
        JMESGSUserImpl user = (JMESGSUserImpl) session.getAttribute(USER_OBJECT);  
        JMEBatchProcessor bProcessor;
        if (user == null) {            
            bProcessor = new JMEBatchProcessor();
            batchProcessor = bProcessor;
            user = new JMESGSUserImpl(router,bProcessor,validatorFactory.newValidators());
            System.out.println("about to set user " + user);
            batchProcessor.setUser(user);
            session.setAttribute(USER_OBJECT,user);    
            System.out.println("set user " + bProcessor);
        } else {
            bProcessor = user.getBatchProcessor();
        }
        if (data != null) {
            bProcessor.packetsReceived(data);
        }
        return user.getOutgoingMessageQueue();
        
    }
    
    
    public static Map<String,ServletListener> getServletListeners() {
        return servletListeners;
    }

    public void sessionCreated(HttpSessionEvent se) {
    }

    public void sessionDestroyed(HttpSessionEvent se) {
        HttpSession session = se.getSession();
        JMESGSUserImpl user = (JMESGSUserImpl) session.getAttribute(USER_OBJECT);  
        if (user != null) {
            user.disconnected();
        }
        System.out.println("timing out user");
        
    }    
    
}
