package com.sun.sgs.impl.service.session;

import com.sun.sgs.app.Delivery;
import com.sun.sgs.app.TransactionNotActiveException;
import com.sun.sgs.auth.IdentityManager;
import com.sun.sgs.impl.io.CompleteMessageFilter;
import com.sun.sgs.impl.io.PassthroughFilter;
import com.sun.sgs.impl.io.SocketEndpoint;
import com.sun.sgs.impl.io.IOConstants.TransportType;
import com.sun.sgs.impl.util.LoggerWrapper;
import com.sun.sgs.impl.util.MessageBuffer;
import com.sun.sgs.impl.util.NonDurableTaskScheduler;
import com.sun.sgs.io.IOAcceptor;
import com.sun.sgs.io.IOAcceptorListener;
import com.sun.sgs.io.IOHandle;
import com.sun.sgs.io.IOHandler;
import com.sun.sgs.kernel.ComponentRegistry;
import com.sun.sgs.kernel.KernelRunnable;
import com.sun.sgs.kernel.TaskScheduler;
import com.sun.sgs.service.ClientSessionService;
import com.sun.sgs.service.DataService;
import com.sun.sgs.service.NonDurableTransactionParticipant;
import com.sun.sgs.service.ProtocolMessageListener;
import com.sun.sgs.service.SgsClientSession;
import com.sun.sgs.service.TaskService;
import com.sun.sgs.service.Transaction;
import com.sun.sgs.service.TransactionProxy;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;   
import java.util.Properties;
import java.util.concurrent.Callable;
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
public class ClientSessionServiceImpl
    implements ClientSessionService, NonDurableTransactionParticipant
{

    /** The property that specifies the application name. */
    public static final String APP_NAME_PROPERTY = "com.sun.sgs.appName";

    /** The property that specifies the port number. */
    public static final String PORT_PROPERTY = "com.sun.sgs.port";

    /** The prefix for ClientSessionListeners bound in the data store. */
    public static final String LISTENER_PREFIX =
	ClientSessionImpl.class.getName();
    
    /** The logger for this class. */
    private static final LoggerWrapper logger =
	new LoggerWrapper(
	    Logger.getLogger(ClientSessionServiceImpl.class.getName()));

    /** The transaction proxy for this class. */
    private static TransactionProxy txnProxy;

    /** Provides transaction and other information for the current thread. */
    private static final ThreadLocal<Context> currentContext =
        new ThreadLocal<Context>();

    /** The application name. */
    private final String appName;

    /** The port number for accepting connections. */
    private final int port;

    /** The listener for accpeted connections. */
    private final IOAcceptorListener listener = new Listener();

    /** The registered service listeners. */
    private final Map<Byte, ProtocolMessageListener> serviceListeners =
	Collections.synchronizedMap(
	    new HashMap<Byte, ProtocolMessageListener>());

    /** A map of current sessions, from session ID to ClientSessionImpl. */
    private final Map<SessionId, ClientSessionImpl> sessions =
	Collections.synchronizedMap(new HashMap<SessionId, ClientSessionImpl>());

    /** The component registry for this application, or null if
     * configure has not been called.
     */
    private ComponentRegistry registry;
    
    /** The IOAcceptor for listening for new connections. */
    private IOAcceptor<SocketAddress> acceptor;

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

    /** If true, this service is shutting down; initially, false. */
    private boolean shuttingDown = false;

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
	        Level.CONFIG,
		"Creating ClientSessionServiceImpl properties:{0}",
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
		removeListenerBindings();
		nonDurableTaskScheduler =
		    new NonDurableTaskScheduler(
			taskScheduler, proxy.getCurrentOwner(),
			registry.getComponent(TaskService.class));
		SocketEndpoint endpoint =
		    new SocketEndpoint(
		        new InetSocketAddress(port), TransportType.RELIABLE);
		try {
                    acceptor = endpoint.createAcceptor();
		    acceptor.listen(listener, CompleteMessageFilter.class);
		    if (logger.isLoggable(Level.CONFIG)) {
			logger.log(
			    Level.CONFIG,
			    "configure: listen successful. address:{0}",
                            acceptor.getEndpoint().getAddress());
		    }
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
     * Returns the address that this service is listening on.
     *
     * @return the address that this service is listening on
     */
    public SocketAddress getAddress() {
	synchronized (lock) {
	    if (acceptor == null) {
		throw new IllegalStateException("not configured");
	    }
	    return acceptor.getEndpoint().getAddress();
	}
    }

    /**
     * Shuts down this service.
     *
     * @return <code>true</code> if shutdown is successful, otherwise
     * <code>false</code>
     */
    public boolean shutdown() {
	if (logger.isLoggable(Level.FINEST)) {
	    logger.log(Level.FINEST, "shutdown");
	}
	
	synchronized (this) {
	    if (shuttingDown) {
		if (logger.isLoggable(Level.FINEST)) {
		    logger.log(Level.FINEST, "shutdown in progress");
		}
		return false;
	    }
	    shuttingDown = true;
	}

	try {
	    if (acceptor != null) {
		acceptor.shutdown();
		if (logger.isLoggable(Level.FINEST)) {
		    logger.log(Level.FINEST, "acceptor shutdown");
		}
	    }
	} catch (RuntimeException e) {
	    if (logger.isLoggable(Level.FINEST)) {
		logger.logThrow(Level.FINEST, e, "shutdown exception occurred");
	    }
	    // swallow exception
	}

	sessions.clear();
	
	// TBI: The bindings can only be removed if this is called within a
	// transaction, so comment out for now...
	// removeListenerBindings();

	return true;
    }

    /* -- Implement ClientSessionService -- */

    /** {@inheritDoc} */
    public void registerProtocolMessageListener(
	byte serviceId, ProtocolMessageListener listener)
    {
	serviceListeners.put(serviceId, listener);
    }

    /** {@inheritDoc} */
    public SgsClientSession getClientSession(byte[] sessionId) {
	return sessions.get(new SessionId(sessionId));
    }

    /* -- Implement IOAcceptorListener -- */

    class Listener implements IOAcceptorListener {

	/**
	 * {@inheritDoc}
	 *
	 * <p>Creates a new client session with the specified handle,
	 * and adds the session to the internal session map.
	 */
	public IOHandler newHandle() {
	    if (shuttingDown()) {
		return null;
	    }
	    ClientSessionImpl session =
		new ClientSessionImpl(ClientSessionServiceImpl.this);
	    sessions.put(new SessionId(session.getSessionId()), session);
	    return session.getHandler();
	}

	public void disconnected() {
	    // TBI...
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
    
    /* -- Implement NonDurableTransactionParticipant -- */
       
    /** {@inheritDoc} */
    public boolean prepare(Transaction txn) throws Exception {
        try {
            boolean readOnly = currentContext.get().prepare();
            handleTransaction(txn, readOnly);
            if (logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINER, "prepare txn:{0} returns {1}",
                           txn, readOnly);
            }
            
            return readOnly;
        } catch (RuntimeException e) {
            if (logger.isLoggable(Level.FINER)) {
                logger.logThrow(Level.FINER, e, "prepare txn:{0} throws", txn);
            }
            throw e;
        }
    }

    /** {@inheritDoc} */
    public void commit(Transaction txn) {
        try {
            handleTransaction(txn, true);
            if (logger.isLoggable(Level.FINER)) {
                logger.log(Level.FINER, "commit txn:{0} returns", txn);
            }
        } catch (RuntimeException e) {
            if (logger.isLoggable(Level.FINER)) {
                logger.logThrow(Level.FINER, e, "commit txn:{0} throws", txn);
            }
            throw e;
        }
    }

    /** {@inheritDoc} */
    public void prepareAndCommit(Transaction txn) throws Exception {
        if (!prepare(txn)) {
            commit(txn);
        }
    }

    /** {@inheritDoc} */
    public void abort(Transaction txn) {
        try {
            handleTransaction(txn, true);
            if (logger.isLoggable(Level.FINER)) {
                logger.log(Level.FINER, "abort txn:{0} returns", txn);
            }
        } catch (RuntimeException e) {
            if (logger.isLoggable(Level.FINER)) {
                logger.logThrow(Level.FINER, e, "abort txn:{0} throws", txn);
            }
            throw e;
        }
    }

    /* -- Context class to hold transaction state -- */
    
    static final class Context implements KernelRunnable {
        /** The transaction. */
        private final Transaction txn;

	/** Map of client sessions to an object containing a list of
	 * messages to send when transaction commits. */
        private final Map<ClientSessionImpl, SessionInfo> sessionsInfo =
	    new HashMap<ClientSessionImpl, SessionInfo>();

	/** If true, indicates the associated transaction is prepared. */
        private boolean prepared = false;

	/**
	 * Constructs a context with the specified transaction.
	 */
        private Context(Transaction txn) {
            this.txn = txn;
	    txnProxy.getService(TaskService.class).
		scheduleNonDurableTask(this);
        }

	/**
	 * Adds a message to be sent to the specified session after
	 * this transaction commits.
	 */
	void addMessage(
	    ClientSessionImpl session, byte[] message, Delivery delivery)
	{
	    addMessage0(session, message, delivery, false);
	}

	/**
	 * Adds to the head of the list a message to be sent to the
	 * specified session after this transaction commits.
	 */
	void addMessageFirst(
	    ClientSessionImpl session, byte[] message, Delivery delivery)
	{
	    addMessage0(session, message, delivery, true);
	}

	/**
	 * Requests that the specified session be disconnected when
	 * this transaction commits, but only after all session
	 * messages are sent.
	 */
	void requestDisconnect(ClientSessionImpl session) {
	    try {
		if (logger.isLoggable(Level.FINEST)) {
		    logger.log(
			Level.FINEST,
			"Context.setDisconnect session:{0}", session);
		}
		checkPrepared();

		getSessionInfo(session).disconnect = true;
		
	    } catch (RuntimeException e) {
                if (logger.isLoggable(Level.FINE)) {
                    logger.logThrow(
			Level.FINE, e,
			"Context.setDisconnect throws");
                }
                throw e;
            }
	}

	private void addMessage0(
	    ClientSessionImpl session, byte[] message, Delivery delivery,
	    boolean isFirst)
	{
	    try {
		if (logger.isLoggable(Level.FINEST)) {
		    logger.log(
			Level.FINEST,
			"Context.addMessage first:{0} session:{1}, message:{2}",
			isFirst, session, message);
		}
		checkPrepared();

		SessionInfo info = getSessionInfo(session);
		if (isFirst) {
		    info.messages.add(0, message);
		} else {
		    info.messages.add(message);
		}
	    
	    } catch (RuntimeException e) {
                if (logger.isLoggable(Level.FINE)) {
                    logger.logThrow(
			Level.FINE, e,
			"Context.addMessage exception");
                }
                throw e;
            }
	}

	private SessionInfo getSessionInfo(ClientSessionImpl session) {

	    SessionInfo info = sessionsInfo.get(session);
	    if (info == null) {
		info = new SessionInfo(session);
		sessionsInfo.put(session, info);
	    }
	    return info;
	}

	private void checkPrepared() {
	    if (prepared) {
		throw new TransactionNotActiveException("Already prepared");
	    }
	}
	
        private boolean prepare() {
	    checkPrepared();
	    prepared = true;
            return true;
        }
	
        public void run() throws Exception {
            if (!prepared) {
                RuntimeException e =
                    new IllegalStateException("transaction not prepared");
		if (logger.isLoggable(Level.FINE)) {
		    logger.logThrow(
			Level.FINE, e, "Context.run: not yet prepared txn:{0}",
			txn);
		}
                throw e;
            }
	    
            for (SessionInfo info : sessionsInfo.values()) {
		info.sendProtocolMessages();
            }
        }

	private static class SessionInfo {

	    private final ClientSessionImpl session;
	    /** List of protocol messages to send on commit. */
	    List<byte[]> messages = new ArrayList<byte[]>();

	    /** If true, disconnect after sending messages. */
	    boolean disconnect = false;

	    SessionInfo(ClientSessionImpl session) {
		this.session = session;
	    }

	    private void sendProtocolMessages() {
                for (byte[] message : messages) {
                   session.sendProtocolMessage(message, Delivery.RELIABLE);
                }
		if (disconnect) {
		    session.handleDisconnect(false);
		}
	    }
	}
    }
    
    /* -- Other methods -- */

    /**
     * Checks the specified transaction, throwing IllegalStateException
     * if the current context is null or if the specified transaction is
     * not equal to the transaction in the current context. If
     * 'nullifyContext' is 'true' or if the specified transaction does
     * not match the current context's transaction, then sets the
     * current context to null.
     */
    private void handleTransaction(Transaction txn, boolean nullifyContext) {
        if (txn == null) {
            throw new NullPointerException("null transaction");
        }
        Context context = currentContext.get();
        if (context == null) {
            throw new IllegalStateException("null context");
        }
        if (!txn.equals(context.txn)) {
            currentContext.set(null);
            throw new IllegalStateException(
                "Wrong transaction: Expected " + context.txn + ", found " + txn);
        }
        if (nullifyContext) {
            currentContext.set(null);
        }
    }
    
   /**
     * Obtains information associated with the current transaction,
     * throwing TransactionNotActiveException if there is no current
     * transaction, and throwing IllegalStateException if there is a
     * problem with the state of the transaction or if this service
     * has not been configured with a transaction proxy.
     */
    Context checkContext() {
        Transaction txn;
        synchronized (lock) {
            if (txnProxy == null) {
                throw new IllegalStateException("Not configured");
            }
            txn = txnProxy.getCurrentTransaction();
        }
        if (txn == null) {
            throw new TransactionNotActiveException(
                "No transaction is active");
        }
        Context context = currentContext.get();
        if (context == null) {
            if (logger.isLoggable(Level.FINER)) {
                logger.log(Level.FINER, "join txn:{0}", txn);
            }
            txn.join(this);
            context =
                new Context(txn);
            currentContext.set(context);
        } else if (!txn.equals(context.txn)) {
            currentContext.set(null);
            throw new IllegalStateException(
                "Wrong transaction: Expected " + context.txn +
                ", found " + txn);
        }
        return context;
    }
    
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
    ProtocolMessageListener getProtocolMessageListener(byte serviceId) {
	return serviceListeners.get(serviceId);
    }

    /**
     * Removes the specified session from the internal session map.
     */
    void disconnected(byte[] sessionId) {
	if (shuttingDown()) {
	    return;
	}
	sessions.remove(new SessionId(sessionId));
    }

    /**
     * Schedules a non-durable, transactional task.
     */
    void scheduleTask(KernelRunnable task) {
	nonDurableTaskScheduler.scheduleTask(task);
    }

    /**
     * Schedules a non-durable, non-transactional task.
     */
    void scheduleNonTransactionalTask(KernelRunnable task) {
	nonDurableTaskScheduler.scheduleNonTransactionalTask(task);
    }

    /**
     * Schedules a non-durable, non-transactional task using the task service.
     */
    void scheduleNonTransactionalTaskOnCommit(KernelRunnable task) {
	nonDurableTaskScheduler.scheduleNonTransactionalTaskOnCommit(task);
    }
    
    private synchronized boolean shuttingDown() {
	return shuttingDown;
    }

    /**
     * Removes all ClientSessionListener bindings from the data store.
     */
    private void removeListenerBindings() {

	for (;;) {
	    String listenerKey =
		dataService.nextServiceBoundName(LISTENER_PREFIX);
	    if (listenerKey != null && isListenerKey(listenerKey)) {
		if (logger.isLoggable(Level.FINEST)) {
		    logger.log(
			Level.FINEST,
			"removeListenerBindings removing: {0}",
			listenerKey);
		}
		dataService.removeServiceBinding(listenerKey);
	    } else {
		break;
	    }
	}
    }

    /**
     * Returns true if the specified key has the prefix of a
     * ClientSessionListener key.
     */
    private static boolean isListenerKey(String key) {
	return key.regionMatches(
	    0, LISTENER_PREFIX, 0, LISTENER_PREFIX.length());
    }
}
