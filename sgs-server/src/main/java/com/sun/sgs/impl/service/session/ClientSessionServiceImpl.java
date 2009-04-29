/*
 * Copyright 2007-2009 Sun Microsystems, Inc.
 *
 * This file is part of Project Darkstar Server.
 *
 * Project Darkstar Server is free software: you can redistribute it
 * and/or modify it under the terms of the GNU General Public License
 * version 2 as published by the Free Software Foundation and
 * distributed hereunder to you.
 *
 * Project Darkstar Server is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sun.sgs.impl.service.session;

import com.sun.sgs.app.Delivery;
import com.sun.sgs.app.NameNotBoundException;
import com.sun.sgs.app.ObjectNotFoundException;
import com.sun.sgs.app.Task;
import com.sun.sgs.app.TransactionNotActiveException;
import com.sun.sgs.app.util.ManagedSerializable;
import com.sun.sgs.app.util.ScalableHashMap;
import com.sun.sgs.auth.Identity;
import com.sun.sgs.impl.kernel.ConfigManager;
import com.sun.sgs.impl.service.channel.ChannelServiceImpl;
import com.sun.sgs.impl.service.session.ClientSessionImpl.
    HandleNextDisconnectedSessionTask;
import com.sun.sgs.impl.sharedutil.HexDumper;
import com.sun.sgs.impl.sharedutil.LoggerWrapper;
import com.sun.sgs.impl.sharedutil.Objects;
import com.sun.sgs.impl.sharedutil.PropertiesWrapper;
import com.sun.sgs.impl.util.AbstractKernelRunnable;
import com.sun.sgs.impl.util.AbstractService;
import com.sun.sgs.impl.util.Exporter;
import com.sun.sgs.impl.util.TransactionContext;
import com.sun.sgs.impl.util.TransactionContextFactory;
import com.sun.sgs.kernel.ComponentRegistry;
import com.sun.sgs.kernel.KernelRunnable;
import com.sun.sgs.kernel.TaskQueue;
import com.sun.sgs.kernel.TaskScheduler;
import com.sun.sgs.protocol.LoginFailureException;
import com.sun.sgs.protocol.ProtocolAcceptor;
import com.sun.sgs.protocol.ProtocolDescriptor;
import com.sun.sgs.protocol.ProtocolListener;
import com.sun.sgs.protocol.RequestCompletionHandler;
import com.sun.sgs.protocol.SessionProtocol;
import com.sun.sgs.protocol.SessionProtocolHandler;
import com.sun.sgs.profile.ProfileCollector;
import com.sun.sgs.service.ClientSessionDisconnectListener;
import com.sun.sgs.service.ClientSessionService;
import com.sun.sgs.service.DataService;
import com.sun.sgs.service.Node;
import com.sun.sgs.service.NodeMappingListener;
import com.sun.sgs.service.NodeMappingService;
import com.sun.sgs.service.RecoveryCompleteFuture;
import com.sun.sgs.service.RecoveryListener;
import com.sun.sgs.service.TaskService;
import com.sun.sgs.service.Transaction;
import com.sun.sgs.service.TransactionProxy;
import com.sun.sgs.service.WatchdogService;
import java.io.IOException;
import java.io.Serializable;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;   
import java.util.Properties;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.management.JMException;

/**
 * Manages client sessions. <p>
 *
 * The {@link #ClientSessionServiceImpl constructor} requires the <a
 * href="../../../app/doc-files/config-properties.html#com.sun.sgs.app.name">
 * <code>com.sun.sgs.app.name</code></a> and <a
 * href="../../../app/doc-files/config-properties.html#com.sun.sgs.impl.service.session.transports">
 * <code>com.sun.sgs.impl.service.session.transports</code></a> configuration
 * properties and supports these public configuration <a
 * href="../../../app/doc-files/config-properties.html#ClientSessionService">
 * properties</a>. <p>
 */
public final class ClientSessionServiceImpl
    extends AbstractService
    implements ClientSessionService
{
    /** The package name. */
    private static final String PKG_NAME = "com.sun.sgs.impl.service.session";
    
    /** The name of this class. */
    private static final String CLASSNAME =
	ClientSessionServiceImpl.class.getName();
    
    /** The logger for this class. */
    private static final LoggerWrapper logger =
	new LoggerWrapper(Logger.getLogger(PKG_NAME));

    /** The name of the version key. */
    private static final String VERSION_KEY = PKG_NAME + ".service.version";

    /** The name of the key for the protocol descriptors map. */
    private static final String PROTOCOL_DESCRIPTORS_MAP_KEY =
	PKG_NAME + ".service.protocol.descriptors.map";

    /** The major version. */
    private static final int MAJOR_VERSION = 2;
    
    /** The minor version. */
    private static final int MINOR_VERSION = 0;
    
    /** The name of the server port property. */
    private static final String SERVER_PORT_PROPERTY =
	PKG_NAME + ".server.port";

    /** The default server port. */
    private static final int DEFAULT_SERVER_PORT = 0;

    /** The name of the write buffer size property. */
    private static final String WRITE_BUFFER_SIZE_PROPERTY =
        PKG_NAME + ".buffer.write.max";

    /** The default write buffer size: {@value #DEFAULT_WRITE_BUFFER_SIZE} */
    private static final int DEFAULT_WRITE_BUFFER_SIZE = 128 * 1024;

    /** The events per transaction property. */
    private static final String EVENTS_PER_TXN_PROPERTY =
	PKG_NAME + ".events.per.txn";

    /** The default events per transaction. */
    private static final int DEFAULT_EVENTS_PER_TXN = 1;

    /** The name of the allow new login property. */
    private static final String ALLOW_NEW_LOGIN_PROPERTY =
	PKG_NAME + ".allow.new.login";

    /** The protocol acceptor property name. */
    private static final String PROTOCOL_ACCEPTOR_PROPERTY =
	PKG_NAME + ".protocol.acceptor";

    /** The default protocol acceptor class. */
    private static final String DEFAULT_PROTOCOL_ACCEPTOR =
	"com.sun.sgs.impl.protocol.simple.SimpleSgsProtocolAcceptor";

    /** The default length of a relocation key, in bytes.
     * TBD: the relocation key length should be configurable.
     */
    private static final int DEFAULT_RELOCATION_KEY_LENGTH = 16;

    /** A random number generator for relocation keys. */
    private static final SecureRandom random = new SecureRandom();
    
    /** The write buffer size for new connections. */
    private final int writeBufferSize;

    /** The local node's ID. */
    private final long localNodeId;

    /** The registered session disconnect listeners. */
    private final Set<ClientSessionDisconnectListener>
	sessionDisconnectListeners =
	    Collections.synchronizedSet(
		new HashSet<ClientSessionDisconnectListener>());

    /** A map of local session handlers, keyed by session ID . */
    private final Map<BigInteger, ClientSessionHandler> handlers =
	Collections.synchronizedMap(
	    new HashMap<BigInteger, ClientSessionHandler>());

    /** Queue of contexts that are prepared (non-readonly) or committed. */
    private final Queue<Context> contextQueue =
	new ConcurrentLinkedQueue<Context>();

    /** Thread for flushing committed contexts. */
    private final Thread flushContextsThread = new FlushContextsThread();
    
    /** Lock for notifying the thread that flushes committed contexts. */
    private final Object flushContextsLock = new Object();

    /** The transaction context factory. */
    private final TransactionContextFactory<Context> contextFactory;

    /** The watchdog service. */
    final WatchdogService watchdogService;

    /** The node mapping service. */
    final NodeMappingService nodeMapService;

    /** The task service. */
    final TaskService taskService;

    /** The channel service. */
    private volatile ChannelServiceImpl channelService;
    
    /** The exporter for the ClientSessionServer. */
    private final Exporter<ClientSessionServer> exporter;

    /** The ClientSessionServer remote interface implementation. */
    private final SessionServerImpl serverImpl;
	
    /** The proxy for the ClientSessionServer. */
    private final ClientSessionServer serverProxy;

    /** The protocol listener. */
    private final ProtocolListener protocolListener;

    /** The protocol acceptor. */
    private final ProtocolAcceptor protocolAcceptor;

    /** The map of logged in {@code ClientSessionHandler}s, keyed by
     *  identity.
     */
    private final ConcurrentHashMap<Identity, ClientSessionHandler>
	loggedInIdentityMap =
	    new ConcurrentHashMap<Identity, ClientSessionHandler>();
	
    /** The map of session task queues, keyed by session ID. */
    private final ConcurrentHashMap<BigInteger, TaskQueue>
	sessionTaskQueues = new ConcurrentHashMap<BigInteger, TaskQueue>();

    private final ConcurrentHashMap<BigInteger, RelocationInfo>
	relocatingSessions =
	    new ConcurrentHashMap<BigInteger, RelocationInfo>();

    /** The maximum number of session events to service per transaction. */
    final int eventsPerTxn;

    /** The flag that indicates how to handle same user logins.  If {@code
     * true}, then if the same user logs in, the existing session will be
     * disconnected, and the new login is allowed to proceed.  If {@code
     * false}, then if the same user logs in, the new login will be denied.
     */
    final boolean allowNewLogin;

    /** Our JMX exposed statistics. */
    final ClientSessionServiceStats serviceStats;

    /**
     * Constructs an instance of this class with the specified properties.
     *
     * @param properties service properties
     * @param systemRegistry system registry
     * @param txnProxy transaction proxy
     * @throws Exception if a problem occurs when creating the service
     */
    public ClientSessionServiceImpl(Properties properties,
                                    ComponentRegistry systemRegistry,
				    TransactionProxy txnProxy)
	throws Exception
    {
	super(properties, systemRegistry, txnProxy, logger);	
	logger.log(Level.CONFIG,
		   "Creating ClientSessionServiceImpl properties:{0}",
		   properties);
	PropertiesWrapper wrappedProps = new PropertiesWrapper(properties);	
	try {
	    /*
	     * Get the property for controlling session event processing
	     * and connection disconnection.
	     */
            writeBufferSize = wrappedProps.getIntProperty(
                WRITE_BUFFER_SIZE_PROPERTY, DEFAULT_WRITE_BUFFER_SIZE,
                8192, Integer.MAX_VALUE);
	    eventsPerTxn = wrappedProps.getIntProperty(
		EVENTS_PER_TXN_PROPERTY, DEFAULT_EVENTS_PER_TXN,
		1, Integer.MAX_VALUE);
	    allowNewLogin = wrappedProps.getBooleanProperty(
 		ALLOW_NEW_LOGIN_PROPERTY, false);

            /* Export the ClientSessionServer. */
	    int serverPort = wrappedProps.getIntProperty(
		SERVER_PORT_PROPERTY, DEFAULT_SERVER_PORT, 0, 65535);
	    serverImpl = new SessionServerImpl();
	    exporter =
		new Exporter<ClientSessionServer>(ClientSessionServer.class);
	    try {
		int port = exporter.export(serverImpl, serverPort);
		serverProxy = exporter.getProxy();
		if (logger.isLoggable(Level.CONFIG)) {
		    logger.log(Level.CONFIG, 
                            "export successful. port:{0,number,#}", port);
		}
	    } catch (Exception e) {
		try {
		    exporter.unexport();
		} catch (RuntimeException re) {
		}
		throw e;
	    }

	    /* Get services and check service version. */
	    flushContextsThread.start();
	    contextFactory = new ContextFactory(txnProxy);
	    watchdogService = txnProxy.getService(WatchdogService.class);
	    nodeMapService = txnProxy.getService(NodeMappingService.class);
	    taskService = txnProxy.getService(TaskService.class);
	    localNodeId = watchdogService.getLocalNodeId();
	    watchdogService.addRecoveryListener(
		new ClientSessionServiceRecoveryListener());
	    
	    transactionScheduler.runTask(
		new AbstractKernelRunnable("CheckServiceVersion") {
		    public void run() {
			checkServiceVersion(
			    VERSION_KEY, MAJOR_VERSION, MINOR_VERSION);
		    } },  taskOwner);
	    
	    /* Store the ClientSessionServer proxy in the data store. */
	    transactionScheduler.runTask(
		new AbstractKernelRunnable("StoreClientSessionServiceProxy") {
		    public void run() {
			dataService.setServiceBinding(
			    getClientSessionServerKey(localNodeId),
			    new ManagedSerializable<ClientSessionServer>(
				serverProxy));
		    } },
		taskOwner);

	    /* Register the node mapping listener. */
	    nodeMapService.addNodeMappingListener(
		new NodeMappingListenerImpl());

	    /*
	     * Create the protocol listener and acceptor.
	     */
	    protocolListener = new ProtocolListenerImpl();

	    protocolAcceptor =
		wrappedProps.getClassInstanceProperty(
		    PROTOCOL_ACCEPTOR_PROPERTY,
                    DEFAULT_PROTOCOL_ACCEPTOR,
		    ProtocolAcceptor.class,
		    new Class[] {
			Properties.class, ComponentRegistry.class,
			TransactionProxy.class },
		    properties, systemRegistry, txnProxy);
	    
	    assert protocolAcceptor != null;
            
            /* Create our service profiling info and register our MBean */
            ProfileCollector collector = 
		systemRegistry.getComponent(ProfileCollector.class);
            serviceStats = new ClientSessionServiceStats(collector);
            try {
                collector.registerMBean(serviceStats,
                                        ClientSessionServiceStats.MXBEAN_NAME);
            } catch (JMException e) {
                logger.logThrow(Level.CONFIG, e, "Could not register MBean");
            }
            
            /* Set the protocol descriptor in the ConfigMXBean. */
            ConfigManager config = (ConfigManager)
                    collector.getRegisteredMBean(ConfigManager.MXBEAN_NAME);
            if (config == null) {
                logger.log(Level.CONFIG, "Could not find ConfigMXBean");
            } else {
                config.setProtocolDescriptor(
		    protocolAcceptor.getDescriptor().toString());
            }
	    
	} catch (Exception e) {
	    if (logger.isLoggable(Level.CONFIG)) {
		logger.logThrow(
		    Level.CONFIG, e,
		    "Failed to create ClientSessionServiceImpl");
	    }
	    doShutdown();
	    throw e;
	}
    }

    /* -- Implement AbstractService -- */

    /** {@inheritDoc} */
    protected void handleServiceVersionMismatch(
	Version oldVersion, Version currentVersion)
    {
	throw new IllegalStateException(
	    "unable to convert version:" + oldVersion +
	    " to current version:" + currentVersion);
    }
    
    /** {@inheritDoc} */
    public void doReady() throws Exception {
	channelService = txnProxy.getService(ChannelServiceImpl.class);
	try {
	    protocolAcceptor.accept(protocolListener);
	} catch (IOException e) {
	    if (logger.isLoggable(Level.CONFIG)) {
		logger.logThrow(
		    Level.CONFIG, e,
		    "Failed to start accepting connections");
	    }
	    throw e;
	}
	
	transactionScheduler.runTask(
            new AbstractKernelRunnable("AddProtocolDescriptorMapping") {
		public void run() {
		    getProtocolDescriptorsMap().
			put(localNodeId,
			    Collections.singleton(
				protocolAcceptor.getDescriptor()));
		} },
	    taskOwner);
    }

    /** {@inheritDoc} */
    public void doShutdown() {
	if (protocolAcceptor != null) {
	    try {
		protocolAcceptor.close();
	    } catch (IOException ignore) {
	    }
	}
	for (ClientSessionHandler handler : handlers.values()) {
	    handler.shutdown();
	}
	handlers.clear();
	
	if (exporter != null) {
	    try {
		exporter.unexport();
		logger.log(Level.FINEST, "client session server unexported");
	    } catch (RuntimeException e) {
		logger.logThrow(Level.FINEST, e, "unexport server throws");
		// swallow exception
	    }
	}
	
	synchronized (flushContextsLock) {
	    flushContextsLock.notifyAll();
	}
    }

    /**
     * Returns the proxy for the client session server on the specified
     * {@code nodeId}, or {@code null} if no server exists.
     *
     * @param	nodeId a node ID
     * @return	the proxy for the client session server on the specified
     * 		{@code nodeId}, or {@code null}
     */
    ClientSessionServer getClientSessionServer(long nodeId) {
	if (nodeId == localNodeId) {
	    return serverImpl;
	} else {
	    String sessionServerKey = getClientSessionServerKey(nodeId);
	    try {
		ManagedSerializable wrappedProxy = (ManagedSerializable)
		    dataService.getServiceBinding(sessionServerKey);
		return (ClientSessionServer) wrappedProxy.get();
	    } catch (NameNotBoundException e) {
		return null;
	    }  catch (ObjectNotFoundException e) {
		logger.logThrow(
		    Level.SEVERE, e,
		    "ClientSessionServer binding:{0} exists, " +
		    "but object removed", sessionServerKey);
		throw e;
	    }
	}
    }

    /* -- Implement ClientSessionService -- */

    /** {@inheritDoc} */
    public void registerSessionDisconnectListener(
        ClientSessionDisconnectListener listener)
    {
        if (listener == null) {
            throw new NullPointerException("null listener");
        }
        serviceStats.registerSessionDisconnectListenerOp.report();
        sessionDisconnectListeners.add(listener);
    }
    
    /** {@inheritDoc} */
    public SessionProtocol getSessionProtocol(BigInteger sessionRefId) {
	if (sessionRefId == null) {
	    throw new NullPointerException("null sessionRefId");
	}
        serviceStats.getSessionProtocolOp.report();
	ClientSessionHandler handler = handlers.get(sessionRefId);
	
	return handler != null ? handler.getSessionProtocol() : null;
    }

    /* -- Implement NodeMappingListener -- */

    /**
     * This listener receives notifications of identities being added or
     * removed from this node. <p>
     *
     * If an identity is removed from this node ({@link
     * #mappingRemoved}), and the identity corresponds to a local client
     * session, then the client session should be relocated to the new
     * node.<p>
     *
     * If an identity is added to this node ({@link #mappingAdded})
     * and the {@code oldNode} is non-{@code null}, then its
     * corresponding client session (if any) may be moving to this
     * node from the old node.
     */
    private class NodeMappingListenerImpl implements NodeMappingListener {

	/** {@inheritDoc} */
	public void mappingAdded(Identity id, Node oldNode) {
	    if (logger.isLoggable(Level.FINEST)) {
		logger.log(Level.FINEST,
			   "identity:{0} localNode:{1} oldNode:{2}",
			   id, localNodeId, oldNode);
	    }
	    if (oldNode == null) {
		// This indicates a new mapping, so a session
		// can't be relocating.
		return;
	    } /* else if (addedIdentities.putIfAbsent(id, oldNode) == null) {
		// TBD: Keep track of pending login or relocation to
		// node and clean up session if it doesn't relocate
		// to the local node in a timely fashion.
		
	    } else {
		// TBD: what if the identity is already moving here?
		}*/
	    
	}

	/** {@inheritDoc} */
	public void mappingRemoved(Identity id, final Node newNode) {
	    if (logger.isLoggable(Level.FINEST)) {
		logger.log(Level.FINEST,
			   "identity:{0} localNode:{1} newNode:{2}",
			   id, localNodeId, newNode);
	    }
	    final ClientSessionHandler handler = loggedInIdentityMap.get(id);
	    if (handler != null) {
		// This identity corresponds to a client session, so move it.
		transactionScheduler.scheduleTask(
		    new AbstractKernelRunnable("AddMoveEvent") {
			public void run() {
			    ClientSessionImpl session =
				ClientSessionImpl.getSession(
				    dataService, handler.sessionRefId);
			    if (session != null) {
				// TBD: if session already moving, need to put off
				// this move.
				session.addMoveEvent(newNode);
			    }
			}},
		    id);
	    }
	}
    }

    /* -- Implement ProtocolListener -- */

    private class ProtocolListenerImpl implements ProtocolListener {

	/** {@inheritDoc} */
	public void newLogin(
	    Identity identity, SessionProtocol protocol,
	    RequestCompletionHandler<SessionProtocolHandler> completionHandler)
	{
	    new ClientSessionHandler(
		ClientSessionServiceImpl.this, dataService, protocol,
		identity, completionHandler);
	}

	/** {@inheritDoc} */
	public void relocatedSession(
	    BigInteger relocationKey, SessionProtocol protocol,
	    RequestCompletionHandler<SessionProtocolHandler> completionHandler)
	{
	    RelocationInfo info = relocatingSessions.remove(relocationKey);
	    if (info == null) {
		// No information for specified relocation key.
		// Session is already relocated, or it's a possible
		// DOS attack, so close client connection.
		try {
		    protocol.close();
		} catch (IOException ignore) {
		}
		return;
	    }
	    new ClientSessionHandler(
		ClientSessionServiceImpl.this, dataService, protocol,
		info.identity, completionHandler,
		new BigInteger(1, info.sessionId));

	    // TBD: this services at least one event, but need to ensure
	    // that other events get serviced if serviceEventQueue
	    // messages got missed while the session was relocating.
	    // Also, the handler for this sessionId may not be stored yet
	    // so this serviceEventQueue request will get dropped.  This
	    // probably needs to be done when the client session handler
	    // gets registered, and only if its associated session is
	    // relocating. 
	    serverImpl.serviceEventQueue(info.sessionId);
	}
    }
    
    /* -- Implement TransactionContextFactory -- */

    private class ContextFactory extends TransactionContextFactory<Context> {
	ContextFactory(TransactionProxy txnProxy) {
	    super(txnProxy, CLASSNAME);
	}

	/** {@inheritDoc} */
	public Context createContext(Transaction txn) {
	    return new Context(txn);
	}
    }

    /* -- Context class to hold transaction state -- */
    
    final class Context extends TransactionContext {

	/** Map of client sessions to an object containing a list of
	 * actions to make upon transaction commit. */
        private final Map<BigInteger, CommitActions> commitActions =
	    new HashMap<BigInteger, CommitActions>();

	/**
	 * Constructs a context with the specified transaction.
	 */
        private Context(Transaction txn) {
	    super(txn);
	}

	/**
	 * Adds an action for the specified session to be performed when
	 * the associated transaction commits.  If {@code first} is
	 * {@code true}, then the action is added as the first commit
	 * action for the specified session.
	 */
	void addCommitAction(
	    BigInteger sessionRefId, Action action, boolean first)
	{
	    try {
		if (logger.isLoggable(Level.FINEST)) {
		    logger.log(
			Level.FINEST,
			"Context.addCommitAction session:{0} action:{1}",
			sessionRefId, action);
		}
		checkPrepared();

		CommitActions sessionActions = commitActions.get(sessionRefId);
		if (sessionActions == null) {
		    sessionActions = new CommitActions();
		    commitActions.put(sessionRefId, sessionActions);
		}
		if (first) {
		    sessionActions.addFirst(action);
		} else {
		    sessionActions.add(action);
		}
	    
	    } catch (RuntimeException e) {
                if (logger.isLoggable(Level.FINE)) {
                    logger.logThrow(
			Level.FINE, e,
			"Context.addCommitAction exception");
                }
                throw e;
            }
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
	 * pending changes, adds this context to the context queue and
	 * returns {@code false}.  Otherwise, if there are no pending
	 * changes returns {@code true} indicating readonly status.
	 */
        public boolean prepare() {
	    isPrepared = true;
	    boolean readOnly = commitActions.isEmpty();
	    if (!readOnly) {
		contextQueue.add(this);
	    } else {
		isCommitted = true;
	    }
            return readOnly;
        }

	/**
	 * Removes the context from the context queue containing
	 * pending actions, and checks for flushing committed contexts.
	 */
	public void abort(boolean retryable) {
	    contextQueue.remove(this);
	    checkFlush();
	}

	/**
	 * Marks this transaction as committed, and checks for
	 * flushing committed contexts.
	 */
	public void commit() {
	    isCommitted = true;
	    checkFlush();
        }

	/**
	 * Wakes up the thread to process committed contexts in the
	 * context queue if the queue is non-empty and the first
	 * context in the queue is committed.
	 */
	private void checkFlush() {
	    Context context = contextQueue.peek();
	    if ((context != null) && (context.isCommitted)) {
		synchronized (flushContextsLock) {
		    flushContextsLock.notifyAll();
		}
	    }
	}
	
	/**
	 * Sends all message enqueued during this context's
	 * transaction (via the {@code addMessage} and {@code
	 * addMessageFirst} methods), and disconnects any session
	 * whose disconnection was requested via the {@code
	 * requestDisconnect} method.
	 */
	private boolean flush() {
	    if (shuttingDown()) {
		return false;
	    } else if (isCommitted) {
		for (CommitActions actions : commitActions.values()) {
		    actions.flush();
		}
		return true;
	    } else {
		return false;
	    }
	}
    }

    /**
     * An action to perform during commit.
     */
    interface Action {
	
	/**
	 * Performs the commit action and returns {@code true} if
	 * further actions should be processed after this one, and
	 * returns {@code false} otherwise. A {@code false} value would
	 * be returned, for example, if an action disconnects the client
	 * session or notifies the client of login failure.
	 */
	boolean flush();
    }

    /**
     * Contains pending changes for a given client session.
     */
    private class CommitActions extends LinkedList<Action> {

	/**
	 * Flushes all actions enqueued with this instance.
	 */
	void flush() {
	    for (Action action : this) {
		if (!action.flush()) {
		    break;
		}
	    }
	}
    }

    /**
     * Thread to process the context queue, in order, to flush any
     * committed changes.
     */
    private class FlushContextsThread extends Thread {

	/**
	 * Constructs an instance of this class as a daemon thread.
	 */
	public FlushContextsThread() {
	    super(CLASSNAME + "$FlushContextsThread");
	    setDaemon(true);
	}
	
	/**
	 * Processes the context queue, in order, to flush any
	 * committed changes.  This thread waits to be notified that a
	 * committed context is at the head of the queue, then
	 * iterates through the context queue invoking {@code flush}
	 * on the {@code Context} returned by {@code next}.  Iteration
	 * ceases when either a context's {@code flush} method returns
	 * {@code false} (indicating that the transaction associated
	 * with the context has not yet committed) or when there are
	 * no more contexts in the context queue.
	 */
	public void run() {
	    
	    while (true) {
		
		/*
		 * Wait for a non-empty context queue, returning if
		 * the service is shutting down.
		 */
		synchronized (flushContextsLock) {
		    if (contextQueue.isEmpty()) {
			if (shuttingDown()) {
			    return;
			}
			try {
			    flushContextsLock.wait();
			} catch (InterruptedException e) {
			    return;
			}
		    }
		}
		if (shuttingDown()) {
		    return;
		}

		/*
		 * Remove committed contexts from head of context
		 * queue, and enqueue them to be flushed.
		 */
		if (!contextQueue.isEmpty()) {
		    Iterator<Context> iter = contextQueue.iterator();
		    while (iter.hasNext()) {
			if (shuttingDown()) {
			    return;
			}
			Context context = iter.next();
			if (context.flush()) {
			    iter.remove();
			} else {
			    break;
			}
		    }
		}
	    }
	}
    }

    /* -- Implement ClientSessionServer -- */

    /**
     * Implements the {@code ClientSessionServer} that receives
     * requests from {@code ClientSessionService}s on other nodes to
     * forward messages local client sessions or to service a client
     * session's event queue. 
     */
    private class SessionServerImpl implements ClientSessionServer {

	/** {@inheritDoc} */
	public void serviceEventQueue(final byte[] sessionId) {
	    callStarted();
	    try {
		if (logger.isLoggable(Level.FINEST)) {
		    logger.log(Level.FINEST, "serviceEventQueue sessionId:{0}",
			       HexDumper.toHexString(sessionId));
		}

		BigInteger sessionRefId = new BigInteger(1, sessionId);
		if (!handlers.containsKey(sessionRefId)) {
		    // The session is not local, so this node should not
		    // service the event queue.
		    return;
		}
		TaskQueue taskQueue = sessionTaskQueues.get(sessionRefId);
		if (taskQueue == null) {
		    TaskQueue newTaskQueue =
			transactionScheduler.createTaskQueue();
		    taskQueue = sessionTaskQueues.
			putIfAbsent(sessionRefId, newTaskQueue);
		    if (taskQueue == null) {
			taskQueue = newTaskQueue;
		    }
		}
		taskQueue.addTask(
		  new AbstractKernelRunnable("ServiceEventQueue") {
		    public void run() {
			ClientSessionImpl.serviceEventQueue(sessionId);
		    } }, taskOwner);
	    } finally {
		callFinished();
	    }
	    
	}

	/** {@inheritDoc} */
	public byte[] relocatingSession(
	    Identity identity, byte[] sessionId, long oldNode)
	{
	    callStarted();
	    try {
		if (logger.isLoggable(Level.FINEST)) {
		    logger.log(Level.FINEST, "sessionId:{0} oldNode:{1}",
			       HexDumper.toHexString(sessionId), oldNode);
		}
		
		// Cache relocation information.
		byte[] relocationKey = getNextRelocationKey();
		RelocationInfo info =
		    new RelocationInfo(identity, sessionId, oldNode);
		BigInteger key = new BigInteger(1, relocationKey);
		relocatingSessions.put(key, info);

		// Schedule task to monitor relocation.
		taskScheduler.scheduleTask(
		    new MonitorRelocatingSessionTask(key, info),
		    identity,
		    System.currentTimeMillis() + 5000L);
				      
		return relocationKey;

	    } finally {
		callFinished();
	    }
	}

	/** {@inheritDoc} */
	public void send(byte[] sessionId,
			 byte[] message,
			 byte deliveryOrdinal)
        {
	    callStarted();
	    try {
		if (logger.isLoggable(Level.FINEST)) {
		    logger.log(Level.FINEST, "sessionId:{0} message:{1}",
			       HexDumper.toHexString(sessionId),
			       HexDumper.toHexString(message));
		}
		SessionProtocol sessionProtocol =
		    getSessionProtocol(new BigInteger(1, sessionId));
		if (sessionProtocol != null) {
		    try {
			sessionProtocol.sessionMessage(
			    ByteBuffer.wrap(message),
			    Delivery.values()[deliveryOrdinal]);
		    } catch (IOException e) {
			if (logger.isLoggable(Level.FINE)) {
			    logger.logThrow(
				Level.FINE, e,
				"sending message: sessionId:{0} message:{1} " +
				"throws", 
				HexDumper.toHexString(sessionId),
				HexDumper.toHexString(message));
					    
			}
		    }
		} else {
		    if (logger.isLoggable(Level.FINE)) {
			logger.log(
			    Level.FINE,
			    "nonexistent session: dropping message for " +
			    "sessionId:{0} message:{1}",
			    HexDumper.toHexString(sessionId),
			    HexDumper.toHexString(message));
		    }
		}
	    } finally {
		callFinished();
	    }
	}
    }
    
    /* -- Other methods -- */

    TransactionProxy getTransactionProxy() {
	return txnProxy;
    }

    /**
     * Returns the local node's ID.
     * @return	the local node's ID
     */
    long getLocalNodeId() {
	return localNodeId;
    }
    
    /**
     * Returns the size of the write buffer to use for new connections.
     * 
     * @return the size of the write buffer to use for new connections
     */
    int getWriteBufferSize() {
        return writeBufferSize;
    }

    /**
     * Returns the {@code ClientSessionHandler} for the specified client
     * session ID.
     * @param	sessionRefId a client session ID
     * @return	the handler
     */
    ClientSessionHandler getHandler(BigInteger sessionRefId) {
	return handlers.get(sessionRefId);
    }

    /**
     * Returns the next relocation key.
     *
     * @return the next relocation key
     */
    private static byte[] getNextRelocationKey() {
	byte[] key = new byte[DEFAULT_RELOCATION_KEY_LENGTH];
	random.nextBytes(key);
	return key;
    }

    /**
     * Returns the key for accessing the {@code ClientSessionServer}
     * instance (which is wrapped in a {@code ManagedSerializable})
     * for the specified {@code nodeId}.
     */
    private static String getClientSessionServerKey(long nodeId) {
	return PKG_NAME + ".server." + nodeId;
    }
    
    /**
     * Checks if the local node is considered alive, and throws an
     * {@code IllegalStateException} if the node is no longer alive.
     * This method should be called within a transaction.
     */
    private void checkLocalNodeAlive() {
	if (!watchdogService.isLocalNodeAlive()) {
	    throw new IllegalStateException(
		"local node is not considered alive");
	}
    }

   /**
     * Obtains information associated with the current transaction,
     * throwing TransactionNotActiveException if there is no current
     * transaction, and throwing IllegalStateException if there is a
     * problem with the state of the transaction or if this service
     * has not been initialized with a transaction proxy.
     */
    Context checkContext() {
	checkLocalNodeAlive();
	return contextFactory.joinTransaction();
    }

    /**
     * Returns the client session service relevant to the current
     * context.
     *
     * @return the client session service relevant to the current
     * context
     */
    static synchronized ClientSessionServiceImpl getInstance() {
	if (txnProxy == null) {
	    throw new IllegalStateException("Service not initialized");
	} else {
	    return (ClientSessionServiceImpl)
		txnProxy.getService(ClientSessionService.class);
	}
    }

    /**
     * Validates the {@code identity} of the user logging in and returns
     * {@code true} if the login is allowed to proceed, and {@code false}
     * if the login is denied.
     *
     * <p>A user with the specified {@code identity} is allowed to log in
     * if one of the following conditions holds:
     *
     * <ul>
     * <li>the {@code identity} is not currently logged in, or
     * <li>the {@code identity} is logged in, and the {@code
     * com.sun.sgs.impl.service.session.allow.new.login} property is
     * set to {@code true}.
     * </ul>
     * In the latter case (new login allowed), the existing user session logged
     * in with {@code identity} is forcibly disconnected.
     *
     * <p>If this method returns {@code true}, the {@link #removeUserLogin}
     * method must be invoked when the user with the specified {@code
     * identity} is disconnected.
     *
     * @param	identity the user identity
     * @param	handler the client session handler
     * @return	{@code true} if the user is allowed to log in with the
     * specified {@code identity}, otherwise returns {@code false}
     */
    boolean validateUserLogin(Identity identity, ClientSessionHandler handler) {
	ClientSessionHandler previousHandler =
	    loggedInIdentityMap.putIfAbsent(identity, handler);
	if (previousHandler == null) {
	    // No user logged in with the same idenity; allow login.
	    return true;
	} else if (!allowNewLogin) {
	    // Same user logged in; new login not allowed, so deny login.
	    return false;
	} else if (!previousHandler.loginHandled()) {
	    // Same user logged in; can't preempt user in the
	    // process of logging in; deny login.
	    return false;
	} else {
	    if (loggedInIdentityMap.replace(
		    identity, previousHandler, handler)) {
		// Disconnect current user; allow new login.
		previousHandler.handleDisconnect(false, true);
		return true;
	    } else {
		// Another same user login beat this one; deny login.	
		return false;
	    }
	}
    }

    /**
     * Notifies this service that the specified {@code identity} is no
     * longer logged in using the specified {@code handler} so that
     * internal bookkeeping can be adjusted accordingly.
     *
     * @param	identity the user identity
     * @param	handler the client session handler
     */
    boolean removeUserLogin(Identity identity, ClientSessionHandler handler) {
	return loggedInIdentityMap.remove(identity, handler);
    }
    
    /**
     * Adds the handler for the specified session to the internal
     * session handler map.  This method is invoked by the handler once the
     * client has successfully logged in.
     */
    void addHandler(BigInteger sessionRefId, ClientSessionHandler handler) {
        assert handler != null;
	handlers.put(sessionRefId, handler);
    }
    
    /**
     * Removes the specified session from the internal session  handler
     * map.  This method is invoked by the handler when the session becomes
     * disconnected.
     */
    void removeHandler(BigInteger sessionRefId, boolean relocating) {
	if (shuttingDown()) {
	    return;
	}
	// Notify session listeners of disconnection
	if (!relocating) {
	    for (ClientSessionDisconnectListener disconnectListener :
		     sessionDisconnectListeners)
	    {
		disconnectListener.disconnected(sessionRefId);
	    }
	}
	handlers.remove(sessionRefId);
	sessionTaskQueues.remove(sessionRefId);
    }

    /**
     * Schedules a non-durable, transactional task using the given
     * {@code Identity} as the owner.
     */
    void scheduleTask(KernelRunnable task, Identity ownerIdentity) {
	if (ownerIdentity == null) {
	    throw new NullPointerException("Owner identity cannot be null");
	}
        transactionScheduler.scheduleTask(task, ownerIdentity);
    }

    /**
     * Schedules a non-durable, transactional task using the task service.
     */
    void scheduleTaskOnCommit(KernelRunnable task) {
        taskService.scheduleNonDurableTask(task, true);
    }

    /**
     * Runs the specified {@code task} immediately, in a transaction.
     */
    void runTransactionalTask(KernelRunnable task, Identity ownerIdentity)
	throws Exception
    {
	if (ownerIdentity == null) {
	    throw new NullPointerException("Owner identity cannot be null");
	}
	transactionScheduler.runTask(task, ownerIdentity);
    }
    
    /**
     * Returns the task service.
     */
    static TaskService getTaskService() {
	return txnProxy.getService(TaskService.class);
    }

    /**
     * Returns the task scheduler.
     * @return	the task scheduler
     */
    TaskScheduler getTaskScheduler() {
	return taskScheduler;
    }

    /**
     * Returns the channel service.
     */
    ChannelServiceImpl getChannelService() {
	return channelService;
    }

    /**
     * The {@code RecoveryListener} for handling requests to recover
     * for a failed {@code ClientSessionService}.
     */
    private class ClientSessionServiceRecoveryListener
	implements RecoveryListener
    {
	/** {@inheritDoc} */
	public void recover(final Node node, RecoveryCompleteFuture future) {
	    final long nodeId = node.getId();
	    final TaskService taskService = getTaskService();
	    
	    try {
		if (logger.isLoggable(Level.INFO)) {
		    logger.log(Level.INFO, "Node:{0} recovering for node:{1}",
			       localNodeId, nodeId);
		}

		/*
		 * Schedule persistent tasks to perform recovery.
		 */
		transactionScheduler.runTask(
		    new AbstractKernelRunnable("ScheduleRecoveryTasks") {
			public void run() {
			    /*
			     * For each session on the failed node, notify
			     * the session's ClientSessionListener and
			     * clean up the session's persistent data and
			     * bindings. 
			     */
			    taskService.scheduleTask(
				new HandleNextDisconnectedSessionTask(nodeId));
				
			    /*
			     * Remove client session server proxy and
			     * associated binding for failed node, as
			     * well as protocol descriptors for the
			     * failed node.
			     */
			    taskService.scheduleTask(
				new RemoveNodeSpecificDataTask(nodeId));
			} },
		    taskOwner);
					     
		future.done();
		    
	    } catch (Exception e) {
		logger.logThrow(
 		    Level.WARNING, e,
		    "Node:{0} recovering for node:{1} throws",
		    localNodeId, nodeId);
		// TBD: what should it do if it can't recover?
	    }
	}
    }

    /**
     * A persistent task to remove the client session server proxy
     * and protocol descriptors for a failed node.
     */
    private static class RemoveNodeSpecificDataTask
	 implements Task, Serializable
    {
	/** The serialVersionUID for this class. */
	private static final long serialVersionUID = 1L;

	/** The node ID. */
	private final long nodeId;

	/**
	 * Constructs an instance of this class with the specified
	 * {@code nodeId}.
	 */
	RemoveNodeSpecificDataTask(long nodeId) {
	    this.nodeId = nodeId;
	}

	/**
	 * Removes the client session server proxy and binding and
	 * also removes the protocol descriptors for the node
	 * specified during construction.
	 */
	public void run() {
	    String sessionServerKey = getClientSessionServerKey(nodeId);
	    DataService dataService = getDataService();
	    try {
		dataService.removeObject(
		    dataService.getServiceBinding(sessionServerKey));
		getProtocolDescriptorsMap().remove(nodeId);
	    } catch (NameNotBoundException e) {
		// already removed
		return;
	    } catch (ObjectNotFoundException e) {
	    }
	    dataService.removeServiceBinding(sessionServerKey);
	}
    }

    /**
     * Returns a set of protocol descriptors for the specified
     * {@code nodeId}, or {@code null} if there are no descriptors
     * for the node.  This method must be run outside a transaction.
     */
    Set<ProtocolDescriptor> getProtocolDescriptors(long nodeId) {
	checkNonTransactionalContext();
	GetProtocolDescriptorsTask protocolDescriptorsTask =
	    new GetProtocolDescriptorsTask(nodeId);
	try {
	    transactionScheduler.runTask(protocolDescriptorsTask, taskOwner);
	    return protocolDescriptorsTask.descriptors;
	} catch (Exception e) {
            logger.logThrow(Level.WARNING, e,
                            "GetProtocolDescriptorsTask for node:{0} throws",
                            nodeId);
	    return null;
	}
    }

    /**
     * A task to obtain the protocol descriptors for a given node.
     */
    private static class GetProtocolDescriptorsTask
	extends AbstractKernelRunnable
    {
	private final long nodeId;
	volatile Set<ProtocolDescriptor> descriptors = null;

	/** Constructs an instance with the specified {@code nodeId}. */
	GetProtocolDescriptorsTask(long nodeId) {
	    super(null);
	    this.nodeId = nodeId;
	}

	/** {@inheritDoc} */
	public void run() {
	    descriptors = getProtocolDescriptorsMap().get(nodeId);
	}
    }

    /**
     * Returns the protocol descriptors map, keyed by node ID.  Creates and
     * stores the map if it doesn't already exist.  This method must be run
     * within a transaction.
     */
    private static Map<Long, Set<ProtocolDescriptor>>
	getProtocolDescriptorsMap()
    {
	DataService dataService = getDataService();
	Map<Long, Set<ProtocolDescriptor>> protocolDescriptorsMap;
	try {
	    protocolDescriptorsMap = Objects.uncheckedCast(
		dataService.getServiceBinding(PROTOCOL_DESCRIPTORS_MAP_KEY));
	} catch (NameNotBoundException e) {
	    protocolDescriptorsMap =
		new ScalableHashMap<Long, Set<ProtocolDescriptor>>();
	    dataService.setServiceBinding(PROTOCOL_DESCRIPTORS_MAP_KEY,
					  protocolDescriptorsMap);
	}
	return protocolDescriptorsMap;
    }

    /**
     * Contains information about a client session relocating to the
     * local node.
     */
    private static class RelocationInfo {
	final Identity identity;
	final byte[] sessionId;
	final long oldNode;

	RelocationInfo(Identity identity, byte[] sessionId, long oldNode) {
	    this.identity = identity;
	    this.sessionId = sessionId;
	    this.oldNode = oldNode;
	}
    }

    /**
     * A task (run after a delay) that checks to see if a client
     * session with the associated relocation key has attempted to
     * relocate to this node.  If not, the associated client session
     * is cleaned up.
     */
    private class MonitorRelocatingSessionTask
	extends AbstractKernelRunnable
    {
	final BigInteger relocationKey;
	final RelocationInfo info;

	/**
	 * Constructs an instance with the specified relocation info.
	 * @param	relocationKey a relocation key
	 * @param	info a client session's relocation info
	 */
	MonitorRelocatingSessionTask(
	    BigInteger relocationKey, RelocationInfo info)
	{
	    super(null);
	    this.relocationKey = relocationKey;
	    this.info = info;
	}

	/**
	 * If the associated client doesn't attempt reestablish the
	 * client session before this task runs, then clean up the
	 * client session.  Either the original server failed to
	 * inform the client that it should relocate or the client
	 * session has failed.  In either case, the client session
	 * needs to be removed.
	 */
	public void run() {
	    if (relocatingSessions.remove(relocationKey) == null) {
		return;
	    }
	    transactionScheduler.scheduleTask(
 		new AbstractKernelRunnable("RemoveNonrelocatedSession") {
		    public void run() {
			ClientSessionImpl sessionImpl =
			    ClientSessionImpl.getSession(
 				dataService, new BigInteger(1, info.sessionId));
			sessionImpl.notifyListenerAndRemoveSession(
 			    dataService, false, true);
		    } }, info.identity);
	}
    }
}

