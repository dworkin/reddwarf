/*
 * Copyright © 2006 Sun Microsystems, Inc., 4150 Network Circle, Santa
 * Clara, California 95054, U.S.A. All rights reserved.
 * 
 * Sun Microsystems, Inc. has intellectual property rights relating to
 * technology embodied in the product that is described in this
 * document. In particular, and without limitation, these intellectual
 * property rights may include one or more of the U.S. patents listed at
 * http://www.sun.com/patents and one or more additional patents or
 * pending patent applications in the U.S. and in other countries.
 * 
 * U.S. Government Rights - Commercial software. Government users are
 * subject to the Sun Microsystems, Inc. standard license agreement and
 * applicable provisions of the FAR and its supplements.
 * 
 * Use is subject to license terms.
 * 
 * This distribution may include materials developed by third parties.
 * 
 * Sun, Sun Microsystems, the Sun logo and Java are trademarks or
 * registered trademarks of Sun Microsystems, Inc. in the U.S. and other
 * countries.
 * 
 * This product is covered and controlled by U.S. Export Control laws
 * and may be subject to the export or import laws in other countries.
 * Nuclear, missile, chemical biological weapons or nuclear maritime end
 * uses or end users, whether direct or indirect, are strictly
 * prohibited. Export or reexport to countries subject to U.S. embargo
 * or to entities identified on U.S. export exclusion lists, including,
 * but not limited to, the denied persons and specially designated
 * nationals lists is strictly prohibited.
 * 
 * Copyright © 2006 Sun Microsystems, Inc., 4150 Network Circle, Santa
 * Clara, California 95054, Etats-Unis. Tous droits réservés.
 * 
 * Sun Microsystems, Inc. détient les droits de propriété intellectuels
 * relatifs à la technologie incorporée dans le produit qui est décrit
 * dans ce document. En particulier, et ce sans limitation, ces droits
 * de propriété intellectuelle peuvent inclure un ou plus des brevets
 * américains listés à l'adresse http://www.sun.com/patents et un ou les
 * brevets supplémentaires ou les applications de brevet en attente aux
 * Etats - Unis et dans les autres pays.
 * 
 * L'utilisation est soumise aux termes de la Licence.
 * 
 * Cette distribution peut comprendre des composants développés par des
 * tierces parties.
 * 
 * Sun, Sun Microsystems, le logo Sun et Java sont des marques de
 * fabrique ou des marques déposées de Sun Microsystems, Inc. aux
 * Etats-Unis et dans d'autres pays.
 * 
 * Ce produit est soumis à la législation américaine en matière de
 * contrôle des exportations et peut être soumis à la règlementation en
 * vigueur dans d'autres pays dans le domaine des exportations et
 * importations. Les utilisations, ou utilisateurs finaux, pour des
 * armes nucléaires,des missiles, des armes biologiques et chimiques ou
 * du nucléaire maritime, directement ou indirectement, sont strictement
 * interdites. Les exportations ou réexportations vers les pays sous
 * embargo américain, ou vers des entités figurant sur les listes
 * d'exclusion d'exportation américaines, y compris, mais de manière non
 * exhaustive, la liste de personnes qui font objet d'un ordre de ne pas
 * participer, d'une façon directe ou indirecte, aux exportations des
 * produits ou des services qui sont régis par la législation américaine
 * en matière de contrôle des exportations et la liste de ressortissants
 * spécifiquement désignés, sont rigoureusement interdites.
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
public class JMEHttpUserManager
        implements UserManager, ServletListener, HttpSessionListener {

    private String host = "localhost";
    private String gameName = "Test";
    private final static String USER_OBJECT = "USER_OBJECT";
    private static boolean servletContainerStarted = false;
    private String listenerServletname =
        "com.sun.gi.comm.users.server.impl.jme.JMEListenerServlet";

    private int port = 8080;

    private Router router;
    private JMEBatchProcessor batchProcessor;;
    private static Map<String, ServletListener> servletListeners =
        new HashMap<String, ServletListener>();

    private UserValidatorFactory validatorFactory;

    private int sessionTimeout = 300;

    /** Creates a new instance of JMEHttpUserManager */
    public JMEHttpUserManager(Router router, Map params)
            throws InstantiationException {
        this.router = router;
        setParams(params);
        init();
        servletListeners.put(gameName, this);
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
        p = (String) params.get("gameName");
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
        /*
         * Connector[] connectors = createConnector(); ContextHandler
         * context = createContext(); ServletHandler servletHandler =
         * createServletHandler(); SessionHandler sessionHandler = new
         * SessionHandler(); sessionHandler.setHandler(servletHandler);
         * setupServletMappings(servletHandler); Server server =
         * createJettyServer(context, connectors, sessionHandler);
         * startServer(server);
         */
        try {
            Server server = new Server();
            SocketChannelListener listener = new SocketChannelListener();
            listener.setHost(host);
            listener.setPort(port);
            // server.addListener(host + ":" + port);
            server.addListener(listener);
            ServletHttpContext context =
                (ServletHttpContext) server.getContext("/");
            context.addServlet("JMEServlet", "/Servlet/*", listenerServletname);
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

/*
    private Server createJettyServer(final ContextHandler context,
            final Connector[] connectors, final SessionHandler sessionHandler) {
        Server server = new Server();
        server.setConnectors(connectors);
        context.setHandler(sessionHandler);
        Handler[] handlers = { context };
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
        Connector[] connectors = { connector };
        return connectors;
    }

    private void setupServletMappings(final ServletHandler servletHandler) {
        ServletMapping mapping = new ServletMapping();
        mapping.setPathSpec("/");
        mapping.setServletName("JMEServlet");
        ServletMapping[] mappings = { mapping };
        servletHandler.setServletMappings(mappings);
    }

    private ServletHandler createServletHandler() {
        ServletHandler servletHandler = new ServletHandler();
        ServletHolder holder = new ServletHolder();
        holder.setName("JMEServlet");
        holder.setClassName(listenerServletname);
        ServletHolder[] holders = { holder };
        servletHandler.setServlets(holders);
        return servletHandler;
    }
*/

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
        JMESGSUserImpl user =
            (JMESGSUserImpl) session.getAttribute(USER_OBJECT);
        JMEBatchProcessor bProcessor;
        if (user == null) {
            bProcessor = new JMEBatchProcessor();
            batchProcessor = bProcessor;
            user = new JMESGSUserImpl(router, bProcessor,
                    validatorFactory.newValidators());
            System.out.println("about to set user " + user);
            batchProcessor.setUser(user);
            session.setAttribute(USER_OBJECT, user);
            System.out.println("set user " + bProcessor);
        } else {
            bProcessor = user.getBatchProcessor();
        }
        if (data != null) {
            bProcessor.packetsReceived(data);
        }
        return user.getOutgoingMessageQueue();

    }

    public static Map<String, ServletListener> getServletListeners() {
        return servletListeners;
    }

    public void sessionCreated(HttpSessionEvent se) {}

    public void sessionDestroyed(HttpSessionEvent se) {
        HttpSession session = se.getSession();
        JMESGSUserImpl user =
            (JMESGSUserImpl) session.getAttribute(USER_OBJECT);
        if (user != null) {
            user.disconnected();
        }
        System.out.println("timing out user");
    }
    
    public void shutdown() {
    	// TODO implement me
    }

}
