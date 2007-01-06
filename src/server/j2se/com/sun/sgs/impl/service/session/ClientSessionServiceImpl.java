package com.sun.sgs.impl.service.session;

import com.sun.sgs.auth.IdentityManager;
import com.sun.sgs.impl.io.AcceptorFactory;
import com.sun.sgs.impl.io.CompleteMessageFilter;
import com.sun.sgs.impl.io.PassthroughFilter;
import com.sun.sgs.impl.io.IOConstants.TransportType;
import com.sun.sgs.impl.util.LoggerWrapper;
import com.sun.sgs.impl.util.NonDurableTaskScheduler;
import com.sun.sgs.io.AcceptedHandleListener;
import com.sun.sgs.io.IOAcceptor;
import com.sun.sgs.io.IOHandle;
import com.sun.sgs.io.IOHandler;
import com.sun.sgs.kernel.ComponentRegistry;
import com.sun.sgs.kernel.KernelRunnable;
import com.sun.sgs.kernel.TaskScheduler;
import com.sun.sgs.service.ClientSessionService;
import com.sun.sgs.service.DataService;
import com.sun.sgs.service.ServiceListener;
import com.sun.sgs.service.SgsClientSession;
import com.sun.sgs.service.TaskService;
import com.sun.sgs.service.TransactionProxy;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Arrays;
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
 * <li><code>com.sun.sgs.appName</code>
 * <li><code>com.sun.sgs.app.port</code>
 * </ul>
 */
public class ClientSessionServiceImpl implements ClientSessionService {

    /** The property that specifies the application name. */
    public static final String APP_NAME_PROPERTY = "com.sun.sgs.appName";

    /** The property that specifies the port number. */
    public static final String PORT_PROPERTY = "com.sun.sgs.port";

    /** The logger for this class. */
    private static final LoggerWrapper logger =
	new LoggerWrapper(
	    Logger.getLogger(ClientSessionServiceImpl.class.getName()));

    /** The transaction proxy for this class. */
    private static TransactionProxy txnProxy;

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
    private final Map<SessionId, ClientSessionImpl> sessions =
	Collections.synchronizedMap(new HashMap<SessionId, ClientSessionImpl>());

    /** The component registry for this application, or null if
     * configure has not been called.
     */
    private ComponentRegistry registry;
    
    /** The IOAcceptor for listening for new connections. */
    private IOAcceptor acceptor;

    /** Synchronize on this object before accessing the registry. */
    private final Object lock = new Object();
    
    /** The task scheduler. */
    private TaskScheduler taskScheduler;

    /** The task scheduler for non-durable tasks. */
    private NonDurableTaskScheduler nonDurableTaskScheduler;
    
    /** The data service. */
    DataService dataService;

    /** The identity manager. */
    IdentityManager identityManager;

    /**
     * Constructs an instance of this class with the specified properties.
     *
     * @param properties service properties
     * @param systemRegistry system registry
     */
    public ClientSessionServiceImpl(
	Properties properties, ComponentRegistry systemRegistry)
    {
	if (logger.isLoggable(Level.CONFIG)) {
	    logger.log(
	        Level.CONFIG, "Creating ClientSessionServiceImpl properties:{0}",
		properties);
	}
	try {
	    if (systemRegistry == null) {
		throw new NullPointerException("null systemRegistry");
	    }
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

	    taskScheduler = systemRegistry.getComponent(TaskScheduler.class);
	    identityManager =
		systemRegistry.getComponent(IdentityManager.class);

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
	    
	    synchronized (ClientSessionServiceImpl.class) {
		if (ClientSessionServiceImpl.txnProxy == null) {
		    ClientSessionServiceImpl.txnProxy = proxy;
		} else {
		    assert ClientSessionServiceImpl.txnProxy == proxy;
		}
	    }
	    
	    synchronized (lock) {
		if (this.registry != null) {
		    throw new IllegalArgumentException("Already configured");
		}
		this.registry = registry;
		dataService = registry.getComponent(DataService.class);
		nonDurableTaskScheduler =
		    new NonDurableTaskScheduler(taskScheduler,
                            proxy.getCurrentOwner(),
                            registry.getComponent(TaskService.class));
		acceptor =
		    AcceptorFactory.createAcceptor(TransportType.RELIABLE);
		SocketAddress address = new InetSocketAddress(port);
		try {
		    acceptor.listen(
			address,
                        listener,
                        //PassthroughFilter.class
                        CompleteMessageFilter.class
                        );
		} catch (IOException e) {
		    throw new RuntimeException(e);
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

    /** {@inheritDoc} */
    public SgsClientSession getClientSession(byte[] sessionId) {
	return sessions.get(new SessionId(sessionId));
    }

    /* -- Implement AcceptedHandleListener -- */

    class Listener implements AcceptedHandleListener {

	/**
	 * {@inheritDoc}
	 *
	 * <p>Creates a new client session with the specified handle,
	 * and adds the session to the internal session map.
	 */
	public IOHandler newHandle(IOHandle handle) {
	    ClientSessionImpl session =
		new ClientSessionImpl(ClientSessionServiceImpl.this, handle);
	    sessions.put(new SessionId(session.getSessionIdInternal()),
                    session);
	    return session.getHandler();
	}
    }

    /* -- Implement wrapper for session ids. -- */

    private final static class SessionId {
        private final byte[] bytes;
        
        SessionId(byte[] bytes) {
            this.bytes = bytes;
        }
        
        public byte[] getBytes() {
            return bytes;
        }

        /** {@inheritDoc} */
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            
            if (! (obj instanceof SessionId)) {
                return false;
            }
            
            return Arrays.equals(bytes, ((SessionId) obj).bytes);
        }
        /** {@inheritDoc} */
        public int hashCode() {
            return Arrays.hashCode(bytes);
        }
    }
    
    /* -- Other methods -- */

    /**
     * Returns the client session service relevant to the current
     * context.
     */
    synchronized static ClientSessionService getInstance() {
	if (txnProxy == null) {
	    throw new IllegalStateException("Service not configured");
	} else {
	    return txnProxy.getService(ClientSessionService.class);
	}
    }

    /**
     * Returns the service listener for the specified service id.
     */
    ServiceListener getServiceListener(byte serviceId) {
	return serviceListeners.get(serviceId);
    }

    /**
     * Removes the specified session from the internal session map.
     */
    void disconnected(ClientSessionImpl session) {
	sessions.remove(new SessionId(session.getSessionIdInternal()));
    }

    /**
     * Schedules a non-durable, transactional task using the task scheduler.
     */
    void scheduleTask(KernelRunnable task) {
	nonDurableTaskScheduler.scheduleTask(task);
    }

    /**
     * Schedules a non-durable, non-transactional task using the task scheduler.
     */
    void scheduleNonTransactionalTask(KernelRunnable task) {
        nonDurableTaskScheduler.scheduleNonTransactionalTask(task);
    }

    /**
     * Schedules a non-durable, non-transactional task using the task service.
     */
    void scheduleNonTransactionalTaskUsingService(KernelRunnable task) {
	nonDurableTaskScheduler.scheduleNonTransactionalTaskUsingService(task);
    }
}
