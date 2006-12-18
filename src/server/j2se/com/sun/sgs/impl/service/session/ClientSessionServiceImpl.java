package com.sun.sgs.impl.service.session;

import com.sun.sgs.app.AppListener;
import com.sun.sgs.impl.io.AcceptorFactory;
import com.sun.sgs.impl.io.IOConstants.TransportType;
import com.sun.sgs.impl.util.LoggerWrapper;
import com.sun.sgs.io.AcceptedHandleListener;
import com.sun.sgs.io.IOAcceptor;
import com.sun.sgs.io.IOHandle;
import com.sun.sgs.io.IOHandler;
import com.sun.sgs.kernel.KernelRunnable;
import com.sun.sgs.kernel.KernelAppContext;
import com.sun.sgs.kernel.ComponentRegistry;
import com.sun.sgs.service.ClientSessionService;
import com.sun.sgs.service.DataService;
import com.sun.sgs.service.ServiceListener;
import com.sun.sgs.service.SgsClientSession;
import com.sun.sgs.service.TransactionProxy;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;   
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Manages client sessions.
 *
 * <p>Properties should include:
 * <ul>
 * <li><code>com.sun.sgs.app.name</code>
 * <li><code>com.sun.sgs.app.port</code>
 * </ul>
 */
public class ClientSessionServiceImpl implements ClientSessionService {

    /** The property that specifies the application name. */
    public static final String APP_NAME_PROPERTY = "com.sun.sgs.app.name";

    /** The property that specifies the port number. */
    public static final String PORT_PROPERTY = "com.sun.sgs.app.port";

    /** The logger for this class. */
    private static final LoggerWrapper logger =
	new LoggerWrapper(
	    Logger.getLogger(ClientSessionServiceImpl.class.getName()));

    /** The application name. */
    private final String appName;

    /** The port number for accepting connections. */
    private final int port;

    /** The listener for accpeted connections. */
    private final AcceptedHandleListener listener = new Listener();

    /** The registered service listeners. */
    private final Map<Byte, ServiceListener> serviceListeners =
	Collections.synchronizedMap(new HashMap<Byte, ServiceListener>());

    /** A map of current sessions, from session ID to ClientSessionImpl. */
    private final Map<byte[], ClientSessionImpl> sessions =
	Collections.synchronizedMap(new HashMap<byte[], ClientSessionImpl>());

    /** The IOAcceptor for listening for new connections. */
    private IOAcceptor acceptor;

    /** The component registry for this application. */
    private ComponentRegistry registry;

    /** Synchronize on this object before accessing the txnProxy. */
    private final Object lock = new Object();
    
    /** The transaction proxy, or null if configure has not been called. */    
    private TransactionProxy txnProxy;

    /** The task service. */
    //    `TaskService taskService;
    
    /** The data service. */
    DataService dataService;
    
    /** The kernel context for tasks. */
    KernelAppContext kernelAppContext;

    /**
     * Constructs an instance of this class with the specified properties.
     */
    public ClientSessionServiceImpl(Properties properties) {
	if (logger.isLoggable(Level.CONFIG)) {
	    logger.log(
	        Level.CONFIG, "Creating ClientSessionServiceImpl properties:{0}",
		properties);
	}
	try {
	    appName = properties.getProperty(APP_NAME_PROPERTY);
	    if (appName == null) {
		throw new IllegalArgumentException(
		    "The " + APP_NAME_PROPERTY +
		    " property must be specified");
	    }

	    String portString = properties.getProperty(PORT_PROPERTY);
	    if (portString == null) {
		throw new IllegalArgumentException(
		    "The " + PORT_PROPERTY +
		    " property must be specified");
	    }
	    port = Integer.parseInt(portString);
	    // TBD: do we want to restrict ports to > 1024?
	    if (port < 0) {
		throw new IllegalArgumentException(
		    "Port number can't be negative: " + port);
	    }

	} catch (RuntimeException e) {
	    if (logger.isLoggable(Level.CONFIG)) {
		logger.logThrow(
		    Level.CONFIG, e,
		    "Failed to create ClientSessionServiceImpl");
	    }
	    throw e;
	}
    }

    /* -- Implement Service -- */

    /** {@inheritDoc} */
    public String getName() {
	return toString();
    }
    
    /** {@inheritDoc} */
    public void configure(ComponentRegistry registry, TransactionProxy proxy) {

	if (logger.isLoggable(Level.CONFIG)) {
	    logger.log(Level.CONFIG, "Configuring ClientSessionServiceImpl");
	}
	try {
	    if (registry == null) {
		throw new NullPointerException("null registry");
	    } else if (proxy == null) {
		throw new NullPointerException("null transaction proxy");
	    }
	    synchronized (lock) {
		if (this.txnProxy != null) {
		    throw new IllegalStateException("Already configured");
		}
		this.registry = registry;
		txnProxy = proxy;
		kernelAppContext = proxy.getCurrentOwner().getContext();
		dataService = registry.getComponent(DataService.class);
		//taskService = registry.getComponent(TaskService.class);
		acceptor =
		    AcceptorFactory.createAcceptor(TransportType.RELIABLE);
		SocketAddress address = new InetSocketAddress(port);
		try {
		    acceptor.listen(address, listener);
		} catch (IOException e) {
		    throw (RuntimeException) new RuntimeException().initCause(e);
		}
		// TBD: listen for UNRELIABLE connections as well?
	    }
	} catch (RuntimeException e) {
	    if (logger.isLoggable(Level.CONFIG)) {
		logger.logThrow(
		    Level.CONFIG, e,
		    "Failed to configure ClientSessionServiceImpl");
	    }
	    throw e;
	}
    }

    /**
     * Shuts down this service.
     */
    public void shutdown() {
	acceptor.shutdown();
    }

    /* -- Implement ClientSessionService -- */

    /** {@inheritDoc} */
    public void registerServiceListener(
	byte serviceId, ServiceListener listener)
    {
	serviceListeners.put(serviceId, listener);
    }

    /* -- Implement AcceptedHandleListener -- */

    class Listener implements AcceptedHandleListener {

	/**
	 * {@inheritDoc}
	 *
	 * <p>Creates a new client session with the specified handle,
	 * and adds the session to the internal session map.
	 */
	public void newHandle(IOHandle handle) {
	    ClientSessionImpl session =
		new ClientSessionImpl(ClientSessionServiceImpl.this, handle);
	    handle.setIOHandler(session.getHandler());
	    sessions.put(session.getSessionId(), session);
	}
    }
    
    /* -- Other methods -- */

    /**
     * Returns the service listener for the specified service id.
     */
    ServiceListener getServiceListener(byte serviceId) {
	return serviceListeners.get(serviceId);
    }

    /**
     * Returns the client session associated with the specified
     * session id.
     */
    SgsClientSession getClientSession(byte[] sessionId) {
	return sessions.get(sessionId);
    }

    /**
     * Removes the specified session from the internal session map.
     */
    void disconnected(ClientSessionImpl session) {
	sessions.remove(session.getSessionId());
    }
}
