/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.impl.service.session;

import com.sun.sgs.app.ClientSessionId;
import com.sun.sgs.app.ClientSessionListener;
import com.sun.sgs.app.Delivery;
import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.TransactionNotActiveException;
import com.sun.sgs.auth.Identity;
import com.sun.sgs.auth.IdentityManager;
import com.sun.sgs.impl.io.ServerSocketEndpoint;
import com.sun.sgs.impl.io.TransportType;
import com.sun.sgs.impl.kernel.StandardProperties;
import com.sun.sgs.impl.service.session.ClientSessionImpl.
    ClientSessionListenerWrapper;
import com.sun.sgs.impl.sharedutil.LoggerWrapper;
import com.sun.sgs.impl.sharedutil.PropertiesWrapper;
import com.sun.sgs.impl.util.AbstractKernelRunnable;
import com.sun.sgs.impl.util.BoundNamesUtil;
import com.sun.sgs.impl.util.IdGenerator;
import com.sun.sgs.impl.util.NonDurableTaskScheduler;
import com.sun.sgs.impl.util.TransactionContext;
import com.sun.sgs.impl.util.TransactionContextFactory;
import com.sun.sgs.io.Acceptor;
import com.sun.sgs.io.AcceptorListener;
import com.sun.sgs.io.ConnectionListener;
import com.sun.sgs.kernel.ComponentRegistry;
import com.sun.sgs.kernel.KernelRunnable;
import com.sun.sgs.kernel.TaskScheduler;
import com.sun.sgs.service.ClientSessionService;
import com.sun.sgs.service.DataService;
import com.sun.sgs.service.ProtocolMessageListener;
import com.sun.sgs.service.SgsClientSession;
import com.sun.sgs.service.TaskService;
import com.sun.sgs.service.Transaction;
import com.sun.sgs.service.TransactionProxy;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;   
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Manages client sessions. <p>
 *
 * The {@link #ClientSessionServiceImpl constructor} supports the
 * following properties: <p>
 *
 * <ul>
 *
 * <li> <i>Key:</i> {@code com.sun.sgs.app.name} <br>
 *	<i>No default &mdash; required</i> <br>
 *	Specifies the application name. <p>
 *
 * <li> <i>Key:</i> {@code com.sun.sgs.app.port} <br>
 *	<i>No default &mdash; required</i> <br>
 *	Specifies the TCP port to listen for client connections. <p>
 *
 * <li> <i>Key:</i>
 * 	{@code com.sun.sgs.impl.service.ClientSessionServiceImpl.id.block.size}
 *	<br>
 *	<i>Default:</i> {@code 256} <br>
 *	Specifies the block size to use when reserving session IDs.  Must be
 *	&gt; {@link IdGenerator#MIN_BLOCK_SIZE}. <p>
 *
 * </ul> <p>
 */
public class ClientSessionServiceImpl implements ClientSessionService {

    /** The prefix for ClientSessionListeners bound in the data store. */
    public static final String LISTENER_PREFIX =
	ClientSessionImpl.class.getName();

    /** The name of this class. */
    private static final String CLASSNAME =
	ClientSessionServiceImpl.class.getName();
    
    /** The logger for this class. */
    private static final LoggerWrapper logger =
	new LoggerWrapper(Logger.getLogger(CLASSNAME));

    /** The name of the IdGenerator. */
    private static final String ID_GENERATOR_NAME =
	CLASSNAME + ".generator";

    /** The default block size for the IdGenerator. */
    private static final int ID_GENERATOR_BLOCK_SIZE = 256;
    
    /** The transaction proxy for this class. */
    static TransactionProxy txnProxy;

    /** Provides transaction and other information for the current thread. */
    private static final ThreadLocal<Context> currentContext =
        new ThreadLocal<Context>();

    /** The application name. */
    private final String appName;

    /** The port number for accepting connections. */
    private final int port;

    /** The listener for accepted connections. */
    private final AcceptorListener acceptorListener = new Listener();

    /** The registered service listeners. */
    private final Map<Byte, ProtocolMessageListener> serviceListeners =
	Collections.synchronizedMap(
	    new HashMap<Byte, ProtocolMessageListener>());

    /** A map of current sessions, from session ID to ClientSessionImpl. */
    private final Map<ClientSessionId, ClientSessionImpl> sessions =
	Collections.synchronizedMap(
	    new HashMap<ClientSessionId, ClientSessionImpl>());

    /** List of contexts that have been prepared (non-readonly) or commited. */
    private final List<Context> contextList = new LinkedList<Context>();

    /** The Acceptor for listening for new connections. */
    private Acceptor<SocketAddress> acceptor;

    /** Synchronize on this object before accessing the registry. */
    private final Object lock = new Object();

    /** The task scheduler. */
    private TaskScheduler taskScheduler;

    /** The task scheduler for non-durable tasks. */
    NonDurableTaskScheduler nonDurableTaskScheduler;

    /** The transaction context factory. */
    private TransactionContextFactory<Context> contextFactory;
    
    /** The data service. */
    DataService dataService;

    /** The identity manager. */
    IdentityManager identityManager;

    /** The ID block size for the IdGenerator. */
    private int idBlockSize;
    
    /** The IdGenerator. */
    private IdGenerator idGenerator;

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
	    appName = properties.getProperty(StandardProperties.APP_NAME);
	    if (appName == null) {
		throw new IllegalArgumentException(
		    "The " + StandardProperties.APP_NAME +
		    " property must be specified");
	    }

	    String portString =
            properties.getProperty(StandardProperties.APP_PORT);
	    if (portString == null) {
		throw new IllegalArgumentException(
		    "The " + StandardProperties.APP_PORT +
		    " property must be specified");
	    }
	    port = Integer.parseInt(portString);
	    // TBD: do we want to restrict ports to > 1024?
	    if (port < 0) {
		throw new IllegalArgumentException(
		    "Port number can't be negative: " + port);
	    }

	    PropertiesWrapper wrappedProperties =
		new PropertiesWrapper(properties);
	    idBlockSize =
		wrappedProperties.getIntProperty(
 		    CLASSNAME + ".id.block.size", ID_GENERATOR_BLOCK_SIZE);
	    if (idBlockSize < IdGenerator.MIN_BLOCK_SIZE) {
		throw new IllegalArgumentException(
		    "idBlockSize must be > " + IdGenerator.MIN_BLOCK_SIZE);
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
		if (this.acceptor != null) {
		    throw new IllegalStateException("Already configured");
		}
		(new ConfigureServiceContextFactory(txnProxy)).
		    joinTransaction();
		contextFactory = new ContextFactory(txnProxy);
		dataService = registry.getComponent(DataService.class);
		nonDurableTaskScheduler =
		    new NonDurableTaskScheduler(
			taskScheduler, proxy.getCurrentOwner(),
			registry.getComponent(TaskService.class));
		notifyDisconnectedSessions();
		idGenerator =
		    new IdGenerator(ID_GENERATOR_NAME,
				    idBlockSize,
				    txnProxy,
				    nonDurableTaskScheduler);
		ServerSocketEndpoint endpoint =
		    new ServerSocketEndpoint(
		        new InetSocketAddress(port), TransportType.RELIABLE);
		try {
                    acceptor = endpoint.createAcceptor();
		    acceptor.listen(acceptorListener);
		    if (logger.isLoggable(Level.CONFIG)) {
			logger.log(
			    Level.CONFIG,
			    "configure: listen successful. port:{0,number,#}",
                            getListenPort());
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
     * Returns the port this service is listening on.
     *
     * @return the port this service is listening on
     */
    public int getListenPort() {
	synchronized (lock) {
	    if (acceptor == null) {
		throw new IllegalArgumentException("not configured");
	    }
	    return ((InetSocketAddress) acceptor.getBoundEndpoint().getAddress()).
		getPort();
	}
    }

    /**
     * Shuts down this service.
     *
     * @return {@code true} if shutdown is successful, otherwise
     * {@code false}
     */
    public boolean shutdown() {
	logger.log(Level.FINEST, "shutdown");
	
	synchronized (this) {
	    if (shuttingDown) {
		logger.log(Level.FINEST, "shutdown in progress");
		return false;
	    }
	    shuttingDown = true;
	}

	try {
	    if (acceptor != null) {
		acceptor.shutdown();
		logger.log(Level.FINEST, "acceptor shutdown");
	    }
	} catch (RuntimeException e) {
	    logger.logThrow(Level.FINEST, e, "shutdown exception occurred");
	    // swallow exception
	}

	for (ClientSessionImpl session : sessions.values()) {
	    session.shutdown();
	}
	sessions.clear();
	
	// TBI: The bindings can only be removed if this is called within a
	// transaction, so comment out for now...
	// notifyDisconnectedSessions();

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
	return sessions.get(new ClientSessionId(sessionId));
    }

    /* -- Implement AcceptorListener -- */

    private class Listener implements AcceptorListener {

	/**
	 * {@inheritDoc}
	 *
	 * <p>Creates a new client session with the specified handle,
	 * and adds the session to the internal session map.
	 */
	public ConnectionListener newConnection() {
	    if (shuttingDown()) {
		return null;
	    }
	    byte[] nextId;
	    try {
		nextId = idGenerator.nextBytes();
	    } catch (InterruptedException e) {
		// This shouldn't happen; noone should be interrupting
		// this thread.
		return null;
	    }
	    ClientSessionImpl session =
		new ClientSessionImpl(ClientSessionServiceImpl.this, nextId);
	    sessions.put(session.getSessionId(), session);
	    return session.getConnectionListener();
	}

        /** {@inheritDoc} */
	public void disconnected() {
	    logger.log(
	        Level.SEVERE,
		"The acceptor has become disconnected from port: {0}", port);
	    // TBD: take other actions, such as restarting acceptor?
	}
    }

    /* -- Implement transaction participant/context for 'configure' -- */

    private class ConfigureServiceContextFactory
	extends TransactionContextFactory
		<ConfigureServiceTransactionContext>
    {
	ConfigureServiceContextFactory(TransactionProxy txnProxy) {
	    super(txnProxy);
	}
	
	/** {@inheritDoc} */
	public ConfigureServiceTransactionContext
	    createContext(Transaction txn)
	{
	    return new ConfigureServiceTransactionContext(txn);
	}
    }

    private final class ConfigureServiceTransactionContext
	extends TransactionContext
    {
	/**
	 * Constructs a context with the specified transaction.
	 */
        private ConfigureServiceTransactionContext(Transaction txn) {
	    super(txn);
	}
	
	/**
	 * {@inheritDoc}
	 *
	 * Performs cleanup in the case that the transaction invoking
	 * the service's {@code configure} method aborts.
	 */
	public void abort(boolean retryable) {
	    synchronized (lock) {
		try {
		    if (acceptor != null) {
			acceptor.shutdown();
		    }
		} catch (Exception e) {
		    // ignore exception shutting down acceptor
		    logger.logThrow(
			Level.FINEST, e, " exception shutting down acceptor");
		} finally {
		    acceptor = null;
		}
		
		for (ClientSessionImpl session : sessions.values()) {
		    try {
			session.shutdown();
		    } catch (Exception e) {
			// ignore exception shutting down session
			logger.logThrow(
			    Level.FINEST, e,
			    " exception shutting down session");
		    }
		}
		sessions.clear();
	    }
	}

	/** {@inheritDoc} */
	public void commit() {
        }
    }
	
    /* -- Implement TransactionContextFactory -- */

    private class ContextFactory extends TransactionContextFactory<Context> {
	ContextFactory(TransactionProxy txnProxy) {
	    super(txnProxy);
	}

	/** {@inheritDoc} */
	public Context createContext(Transaction txn) {
	    return new Context(txn);
	}
    }

    /**
     * Iterates through the context list, in order, to flush any
     * committed changes.  During iteration, this method invokes
     * {@code flush} on the {@code Context} returned by {@code next}.
     * Iteration ceases when either a context's {@code flush} method
     * returns {@code false} (indicating that the transaction
     * associated with the context has not yet committed) or when
     * there are no more contexts in the context list.
     */
    private void flushContexts() {
	synchronized (contextList) {
	    Iterator<Context> iter = contextList.iterator();
	    while (iter.hasNext()) {
		Context context = iter.next();
		if (context.flush()) {
		    iter.remove();
		} else {
		    break;
		}
	    }
	}
    }

    /* -- Context class to hold transaction state -- */
    
    final class Context extends TransactionContext {

	/** Map of client sessions to an object containing a list of
	 * updates to make upon transaction commit. */
        private final Map<ClientSessionImpl, Updates> sessionUpdates =
	    new HashMap<ClientSessionImpl, Updates>();

	/**
	 * Constructs a context with the specified transaction.
	 */
        private Context(Transaction txn) {
	    super(txn);
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

		getUpdates(session).disconnect = true;
		
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

		Updates updates = getUpdates(session);
		if (isFirst) {
		    updates.messages.add(0, message);
		} else {
		    updates.messages.add(message);
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

	private Updates getUpdates(ClientSessionImpl session) {

	    Updates updates = sessionUpdates.get(session);
	    if (updates == null) {
		updates = new Updates(session);
		sessionUpdates.put(session, updates);
	    }
	    return updates;
	}
	
	/**
	 * Throws a {@code TransactionNotActiveException} if this
	 * transaction is prepared.
	 */
	private void checkPrepared() {
	    if (isPrepared) {
		throw new TransactionNotActiveException("Already prepared");
	    }
	}
	
	/**
	 * Marks this transaction as prepared, and if there are
	 * pending changes, adds this context to the context list and
	 * returns {@code false}.  Otherwise, if there are no pending
	 * changes returns {@code true} indicating readonly status.
	 */
        public boolean prepare() {
	    isPrepared = true;
	    boolean readOnly = sessionUpdates.values().isEmpty();
	    if (! readOnly) {
		synchronized (contextList) {
		    contextList.add(this);
		}
	    }
            return readOnly;
        }

	/**
	 * Marks this transaction as aborted, removes the context from
	 * the context list containing pending updates, and flushes
	 * all committed contexts preceding prepared ones.
	 */
	public void abort(boolean retryable) {
	    synchronized (contextList) {
		contextList.remove(this);
	    }
	    flushContexts();
	}

	/**
	 * Marks this transaction as committed and flushes all
	 * committed contexts preceding prepared ones.
	 */
	public void commit() {
	    isCommitted = true;
	    flushContexts();
        }

	/**
	 * Sends all protocol messages enqueued during this context's
	 * transaction (via the {@code addMessage} and {@code
	 * addMessageFirst} methods), and disconnects any session
	 * whose disconnection was requested via the {@code
	 * requestDisconnect} method.
	 */
	private boolean flush() {
	    if (isCommitted) {
		for (Updates updates : sessionUpdates.values()) {
		    updates.flush();
		}
		return true;
	    } else {
		return false;
	    }
	}
    }
    
    /**
     * Contains pending changes for a given client session.
     */
    private static class Updates {

	/** The client session. */
	private final ClientSessionImpl session;
	
	/** List of protocol messages to send on commit. */
	List<byte[]> messages = new ArrayList<byte[]>();

	/** If true, disconnect after sending messages. */
	boolean disconnect = false;

	Updates(ClientSessionImpl session) {
	    this.session = session;
	}

	private void flush() {
	    for (byte[] message : messages) {
		session.sendProtocolMessage(message, Delivery.RELIABLE);
	    }
	    if (disconnect) {
		session.handleDisconnect(false);
	    }
	}
    }
    
    /* -- Other methods -- */

   /**
     * Obtains information associated with the current transaction,
     * throwing TransactionNotActiveException if there is no current
     * transaction, and throwing IllegalStateException if there is a
     * problem with the state of the transaction or if this service
     * has not been configured with a transaction proxy.
     */
    Context checkContext() {
	return contextFactory.joinTransaction();
    }

    /**
     * Returns the client session service relevant to the current
     * context.
     *
     * @return the client session service relevant to the current
     * context
     */
    public synchronized static ClientSessionService getInstance() {
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
    void disconnected(SgsClientSession session) {
	if (shuttingDown()) {
	    return;
	}
	// Notify session listeners of disconnection
	for (ProtocolMessageListener serviceListener :
		 serviceListeners.values())
	{
	    serviceListener.disconnected(session);
	}
	sessions.remove(session.getSessionId());
    }

    /**
     * Schedules a non-durable, transactional task using the given
     * {@code Identity} as the owner.
     * 
     * @see NonDurableTaskScheduler#scheduleTask(KernelRunnable, Identity)
     */
    void scheduleTask(KernelRunnable task, Identity ownerIdentity) {
        nonDurableTaskScheduler.scheduleTask(task, ownerIdentity);
    }

    /**
     * Schedules a non-durable, non-transactional task using the given
     * {@code Identity} as the owner.
     * 
     * @see NonDurableTaskScheduler#scheduleNonTransactionalTask(KernelRunnable, Identity)
     */
    void scheduleNonTransactionalTask(KernelRunnable task,
            Identity ownerIdentity)
    {
        nonDurableTaskScheduler.
            scheduleNonTransactionalTask(task, ownerIdentity);
    }

    /**
     * Schedules a non-durable, transactional task using the task service.
     */
    void scheduleTaskOnCommit(KernelRunnable task) {
        nonDurableTaskScheduler.scheduleTaskOnCommit(task);
    }

    /**
     * Returns {@code true} if this service is shutting down.
     */
    private synchronized boolean shuttingDown() {
	return shuttingDown;
    }

    /**
     * For each {@code ClientSessionListener} bound in the data
     * service, schedules a transactional task that a) notifies the
     * listener that its corresponding session has been forcibly
     * disconnected, and that b) removes the listener's binding from
     * the data service.  If the listener was a serializable object
     * wrapped in a managed {@code ClientSessionListenerWrapper}, the
     * task removes the wrapper as well.
     */
    private void notifyDisconnectedSessions() {
	
	for (String key : BoundNamesUtil.getServiceBoundNamesIterable(
 				dataService, LISTENER_PREFIX))
	{
	    logger.log(
		Level.FINEST,
		"notifyDisconnectedSessions key: {0}",
		key);

	    final String listenerKey = key;		
		
	    scheduleTaskOnCommit(
		new AbstractKernelRunnable() {
		    public void run() throws Exception {
			ManagedObject obj = 
			    dataService.getServiceBinding(
				listenerKey, ManagedObject.class);
			 boolean isWrapped =
			     obj instanceof ClientSessionListenerWrapper;
			 ClientSessionListener listener =
			     isWrapped ?
			     ((ClientSessionListenerWrapper) obj).get() :
			     ((ClientSessionListener) obj);
			listener.disconnected(false);
			dataService.removeServiceBinding(listenerKey);
			if (isWrapped) {
			    dataService.removeObject(obj);
			}
		    }});
	}
    }
}
