/*
 * Copyright 2007-2010 Sun Microsystems, Inc.
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
 *
 * --
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
import com.sun.sgs.impl.kernel.StandardProperties;
import com.sun.sgs.impl.service.channel.ChannelServiceImpl;
import com.sun.sgs.impl.service.session.ClientSessionHandler.
    SetupCompletionFuture;
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
import com.sun.sgs.protocol.ProtocolAcceptor;
import com.sun.sgs.protocol.ProtocolDescriptor;
import com.sun.sgs.protocol.ProtocolListener;
import com.sun.sgs.protocol.RelocateFailureException;
import com.sun.sgs.protocol.RequestCompletionHandler;
import com.sun.sgs.protocol.SessionProtocol;
import com.sun.sgs.protocol.SessionProtocolHandler;
import com.sun.sgs.protocol.simple.SimpleSgsProtocol;
import com.sun.sgs.profile.ProfileCollector;
import com.sun.sgs.service.ClientSessionStatusListener;
import com.sun.sgs.service.ClientSessionService;
import com.sun.sgs.service.DataService;
import com.sun.sgs.service.IdentityRelocationListener;
import com.sun.sgs.service.Node;
import com.sun.sgs.service.NodeMappingListener;
import com.sun.sgs.service.NodeMappingService;
import com.sun.sgs.service.RecoveryListener;
import com.sun.sgs.service.SimpleCompletionHandler;
import com.sun.sgs.service.TaskService;
import com.sun.sgs.service.Transaction;
import com.sun.sgs.service.TransactionProxy;
import com.sun.sgs.service.WatchdogService;
import java.io.IOException;
import java.io.Serializable;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
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
 * href="../../../impl/kernel/doc-files/config-properties.html#com.sun.sgs.app.name">
 * <code>com.sun.sgs.app.name</code></a> configuration
 * property and supports these
 * public configuration <a
 * href="../../../impl/kernel/doc-files/config-properties.html#ClientSessionService">
 * properties</a>.  It also supports the following additional properties: <p>
 * 
 * <dl style="margin-left: 1em">
 *
 * <dt> <i>Property:</i> <code><b>
 *	{@value #SERVER_PORT_PROPERTY}
 *	</b></code><br>
 *	<i>Default:</i> {@value #DEFAULT_SERVER_PORT}
 *
 * <dd style="padding-top: .5em">Specifies the port for the 
 *      <code>ClientSessionService</code>'s internal server.<p>
 * 
 * <dt> <i>Property:</i> <code><b>
 *	{@value #WRITE_BUFFER_SIZE_PROPERTY}
 *	</b></code><br>
 *	<i>Default:</i> {@value #DEFAULT_WRITE_BUFFER_SIZE}
 *
 * <dd style="padding-top: .5em">Specifies the approximate write buffer capacity
 *      per client session.<p>
 * 
 * <dt> <i>Property:</i> <code><b>
 *	{@value #EVENTS_PER_TXN_PROPERTY}
 *	</b></code><br>
 *	<i>Default:</i> {@value #DEFAULT_EVENTS_PER_TXN}
 *
 * <dd style="padding-top: .5em">Specifies the number of client session events
 *      to process per transaction.<p>
 * 
 * <dt> <i>Property:</i> <code><b>
 *	{@value #ALLOW_NEW_LOGIN_PROPERTY}
 *	</b></code><br>
 *	<i>Default:</i> {@code false}
 *
 * <dd style="padding-top: .5em">If {@code false}, any connecting client with 
 * the same username as an already connected client will not be permitted
 * to login.  If {@code true}, the user's existing session will be
 * disconnected and the new login is allowed to proceed.<p>
 * 
 * <dt> <i>Property:</i> <code><b>
 *	{@value #PROTOCOL_ACCEPTOR_PROPERTY}
 *	</b></code><br>
 *	<i>Default:</i> {@value #DEFAULT_PROTOCOL_ACCEPTOR}
 *
 * <dd style="padding-top: .5em">Specifies the name of the class
 * which will be used as the protocol acceptor.  The default value uses
 * an acceptor based on the {@link SimpleSgsProtocol}.  Other values should
 * specify the fully qualified name of a non-abstract class that implements
 * {@link ProtocolAcceptor}.<p>
 * 
 * <dt> <i>Property:</i> <code><b>
 *	{@value #RELOCATION_KEY_LENGTH_PROPERTY}
 *	</b></code><br>
 *	<i>Default:</i> {@value #DEFAULT_RELOCATION_KEY_LENGTH}
 *
 * <dd style="padding-top: .5em">Specifies the length, in bytes, of a
 *	relocation key.<p>
 * 
 * <dt> <i>Property:</i> <code><b>
 *	{@value
 * com.sun.sgs.impl.kernel.StandardProperties#SESSION_RELOCATION_TIMEOUT_PROPERTY}
 *	</b></code><br>
 *	<i>Default:</i>
 *	{@value
 * com.sun.sgs.impl.kernel.StandardProperties#DEFAULT_SESSION_RELOCATION_TIMEOUT}
 *
 * <dd style="padding-top: .5em">Specifies the timeout, in milliseconds,
 *	for client session relocation.<p>
 * 
 * </dl> <p>
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
    static final String SERVER_PORT_PROPERTY =
	PKG_NAME + ".server.port";

    /** The default server port. */
    static final int DEFAULT_SERVER_PORT = 0;

    /** The name of the write buffer size property. */
    static final String WRITE_BUFFER_SIZE_PROPERTY =
        PKG_NAME + ".buffer.write.max";

    /** The default write buffer size: {@value #DEFAULT_WRITE_BUFFER_SIZE} */
    static final int DEFAULT_WRITE_BUFFER_SIZE = 128 * 1024;

    /** The events per transaction property. */
    static final String EVENTS_PER_TXN_PROPERTY =
	PKG_NAME + ".events.per.txn";

    /** The default events per transaction. */
    static final int DEFAULT_EVENTS_PER_TXN = 1;

    /** The name of the allow new login property. */
    static final String ALLOW_NEW_LOGIN_PROPERTY =
	PKG_NAME + ".allow.new.login";

    /** The protocol acceptor property name. */
    static final String PROTOCOL_ACCEPTOR_PROPERTY =
	PKG_NAME + ".protocol.acceptor";

    /** The default protocol acceptor class. */
    static final String DEFAULT_PROTOCOL_ACCEPTOR =
	"com.sun.sgs.impl.protocol.simple.SimpleSgsProtocolAcceptor";

    /** The relocation key length property. */
    static final String RELOCATION_KEY_LENGTH_PROPERTY =
	PKG_NAME + ".relocation.key.length";
    
    /** The default length of a relocation key, in bytes. */
    static final int DEFAULT_RELOCATION_KEY_LENGTH = 16;

    /** A random number generator for relocation keys. */
    private static final SecureRandom random = new SecureRandom();
    
    /** The write buffer size for new connections. */
    private final int writeBufferSize;

    /** The local node's ID. */
    private final long localNodeId;

    /** The registered session disconnect listeners. */
    private final Set<ClientSessionStatusListener>
	sessionStatusListeners =
	    Collections.synchronizedSet(
		new HashSet<ClientSessionStatusListener>());

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

    /** Information for preparing a session to relocate from this node,
     * keyed by session ID. 
     */
    private final ConcurrentHashMap<BigInteger, PrepareRelocationInfo>
	prepareRelocationMap =
	    new ConcurrentHashMap<BigInteger, PrepareRelocationInfo>();

    /** The map of relocation information for sessions relocating to
     * this node, keyed by relocation key. */
    private final ConcurrentHashMap<BigInteger, RelocationInfo>
	incomingSessionRelocationKeys =
	    new ConcurrentHashMap<BigInteger, RelocationInfo>();

    /** The map of relocation information for sessions relocating to this
     * node, keyed by session ID. */
    private final ConcurrentHashMap<BigInteger, RelocationInfo>
	incomingSessionRelocationInfo =
	    new ConcurrentHashMap<BigInteger, RelocationInfo>();
    
    /** The set of identities that are relocating to this node. */
    private final Set<Identity> incomingRelocatingIdentities =
	Collections.synchronizedSet(new HashSet<Identity>());
    
    /** The maximum number of session events to service per transaction. */
    final int eventsPerTxn;

    /** The flag that indicates how to handle same user logins.  If {@code
     * true}, then if the same user logs in, the existing session will be
     * disconnected, and the new login is allowed to proceed.  If {@code
     * false}, then if the same user logs in, the new login will be denied.
     */
    final boolean allowNewLogin;

    /** The session relocation key length. */
    final int relocationKeyLength;

    /** The session relocation timeout. */
    final long relocationTimeout;

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
        logger.log(Level.CONFIG, "Creating ClientSessionServiceImpl");
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
	    relocationKeyLength = wrappedProps.getIntProperty(
 		RELOCATION_KEY_LENGTH_PROPERTY, DEFAULT_RELOCATION_KEY_LENGTH,
		16, Integer.MAX_VALUE);
	    relocationTimeout = wrappedProps.getLongProperty(
		StandardProperties.SESSION_RELOCATION_TIMEOUT_PROPERTY,
		StandardProperties.DEFAULT_SESSION_RELOCATION_TIMEOUT,
		1000L, Long.MAX_VALUE);

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
	    localNodeId = dataService.getLocalNodeId();
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
			// TBD: this could use a BindingKeyedMap.
			dataService.setServiceBinding(
			    getClientSessionServerKey(localNodeId),
			    new ManagedSerializable<ClientSessionServer>(
				serverProxy));
		    } },
		taskOwner);

	    /* Register the identity relocation and node mapping listeners. */
	    nodeMapService.addIdentityRelocationListener(
		new IdentityRelocationListenerImpl());
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

            logger.log(Level.CONFIG,
                       "Created ClientSessionServiceImpl with properties:" +
                       "\n  " + ALLOW_NEW_LOGIN_PROPERTY + "=" + allowNewLogin +
                       "\n  " + WRITE_BUFFER_SIZE_PROPERTY + "=" +
                       writeBufferSize +
                       "\n  " + EVENTS_PER_TXN_PROPERTY + "=" + eventsPerTxn +
		       "\n  " + RELOCATION_KEY_LENGTH_PROPERTY + "=" +
		       relocationKeyLength +
		       "\n  " +
		       StandardProperties.SESSION_RELOCATION_TIMEOUT_PROPERTY +
		       "=" + relocationTimeout +
                       "\n  " + PROTOCOL_ACCEPTOR_PROPERTY + "=" +
                       protocolAcceptor.getClass().getName() +
                       "\n  " + SERVER_PORT_PROPERTY + "=" + serverPort);
	    
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
		logger.logThrow(Level.FINE, e, "unexport server throws");
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
    public void addSessionStatusListener(
        ClientSessionStatusListener listener)
    {
	Objects.checkNull("listener", listener);
	checkNonTransactionalContext();
        serviceStats.addSessionStatusListenerOp.report();
        sessionStatusListeners.add(listener);
    }
    
    /** {@inheritDoc} */
    public SessionProtocol getSessionProtocol(BigInteger sessionRefId) {
	Objects.checkNull("sessionRefId", sessionRefId);
	// This operation is used within a transaction by ChannelService.
	//checkNonTransactionalContext();
        serviceStats.getSessionProtocolOp.report();
	ClientSessionHandler handler = handlers.get(sessionRefId);
	
	return handler != null ? handler.getSessionProtocol() : null;
    }

    /** {@inheritDoc} */
    public boolean isRelocatingToLocalNode(BigInteger sessionRefId) {
	Objects.checkNull("sessionRefId", sessionRefId);
	checkNonTransactionalContext();
        serviceStats.isRelocatingToLocalNodeOp.report();
	return incomingSessionRelocationInfo.containsKey(sessionRefId);
    }

    /* -- Implement IdentityRelocationListener -- */

    /**
     * This listener receives notifications of identities that are going
     * to be relocated from this node to give services a chance to
     * prepare for the relocation. <p>
     *
     * Before an identity is relocated from this node the {@link
     * #prepareToRelocate} method is invoked on this listener.  If the
     * identity corresponds to a local client session, then this service
     * starts to prepare the client session for relocation.  First, it adds
     * a {@code MoveEvent} to the client session's event queue so that the
     * client session can be marked as relocating.
     */
    private class IdentityRelocationListenerImpl
	implements IdentityRelocationListener
    {
	/** {@inheritDoc} */
	public void prepareToRelocate(Identity id, final long newNodeId,
				      SimpleCompletionHandler handler)
	{
	    if (logger.isLoggable(Level.FINEST)) {
		logger.log(Level.FINEST,
			   "identity:{0} localNode:{1} newNodeId:{2}",
			   id, localNodeId, newNodeId);
	    }
	    
	    final ClientSessionHandler sessionHandler =
		loggedInIdentityMap.get(id);
	    
	    if (sessionHandler != null) {
		if (!sessionHandler.supportsRelocation()) {
		    // Ignore request if client session does not suppport
		    // relocation.  If request is ignored, then identity
		    // mapping will not be modified.
		    logger.log(
			Level.SEVERE,
			"Attempt to relocate client:{0} that does not " +
			"support relocation", id);
		    return;
		}
		// The specified identity corresponds to a local client session,
		// so prepare to move it.
		final BigInteger sessionRefId = sessionHandler.sessionRefId;
		PrepareRelocationInfo prepareInfo =
		    new PrepareRelocationInfo(sessionRefId, newNodeId, handler);
		PrepareRelocationInfo oldPrepareInfo =
		    prepareRelocationMap.putIfAbsent(sessionRefId, prepareInfo);
		if (oldPrepareInfo == null) {
		    if (sessionHandler.isRelocating() ||
			!sessionHandler.isConnected())
		    {
			// Request to prepare identity for relocation is
			// received after preparation has already been completed
			// and session is in the process of relocating.
			prepareRelocationMap.remove(sessionRefId);
			handler.completed();
			return;
		    }
			    
		    transactionScheduler.scheduleTask(
		      new AbstractKernelRunnable("AddMoveEvent") {
			public void run() {
			    ClientSessionImpl session =
				ClientSessionImpl.getSession(
				    dataService, sessionHandler.sessionRefId);
			    if (session != null) {
				session.addMoveEvent(newNodeId);
			    }
			} },
		      id);

		    // Add a task to monitor preparation and relocation and
		    // disconnect client session if preparation and
		    // relocation has not completed in time.
		    taskScheduler.scheduleTask(
 			new MonitorSessionRelocatingFromLocalNodeTask(
			    sessionRefId),
			id, System.currentTimeMillis() + relocationTimeout);
		    
		} else {
		    // Duplicate request to prepare for relocation;  add
		    // completion handler to be notified
		    oldPrepareInfo.addNmsCompletionHandler(handler);
		}
		
	    } else {
		// The specified identity does not correspond to a local
		// client session.
		handler.completed();
	    }
	}
    }

    /* -- Implement NodeMappingListener -- */

    /**
     * The client session service's {@code NodeMappingListener}
     * implementation is interested in mappings that have been removed from
     * the local node that correspond to client sessions that are
     * relocating from this node.  When the node mapping service removes
     * the identity mapping of a relocating session from this node, that
     * means relocation preparation is complete and the client session
     * corresponding to the identity can be notified to relocate its
     * connection to the new node.
     */
    private class NodeMappingListenerImpl implements NodeMappingListener {

	/** {@inheritDoc} */
	public void mappingAdded(Identity id, Node oldNode) {
	}
    
	/** {@inheritDoc} */
	public void mappingRemoved(Identity id, Node newNode) {

	    ClientSessionHandler sessionHandler = loggedInIdentityMap.get(id);
	    
	    if (sessionHandler != null) {
		// The specified identity corresponds to a local client session,
		// which should have already been prepared to move
		BigInteger sessionRefId = sessionHandler.sessionRefId;
	    
		// Remove session from table of relocation preparers.
		if (prepareRelocationMap.remove(sessionRefId) != null) {
		    // Notify client to start relocating its connection.
		    sessionHandler.setRelocatePreparationComplete();
		    
		} else {
		    if (newNode != null) {
			if (logger.isLoggable(Level.WARNING)) {
			    logger.log(
			        Level.WARNING,
				"Disconnecting unprepared session:{0} whose " +
				"identity:{1} was remapped from " +
				"localNodeId:{2} to node:{3}",
				HexDumper.toHexString(sessionRefId),
				id, localNodeId, newNode.getId());
			}
		    } else {
			if (logger.isLoggable(Level.WARNING)) {
			    logger.log(
				Level.WARNING,
				"Disconnecting session:{0} whose " +
				"identity:{1} was removed prematurely " +
				"from localNodeId:{2} ",
				HexDumper.toHexString(sessionRefId),
				id, localNodeId);
			}
		    }
		    sessionHandler.handleDisconnect(false, false);
		}
	    }
	}
    }

    /* -- Implement ProtocolListener -- */

    /**
     * This {@code ProtocolListener} implementation handles new and
     * relocated client sessions.
     */
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
	    RelocationInfo info =
		incomingSessionRelocationKeys.remove(relocationKey);
	    if (info == null) {
		// No information for specified relocation key.
		// Session is already relocated, or it's a possible
		// DOS attack, so notify completion handler of failure.
		if (logger.isLoggable(Level.FINE)) {
		    logger.log(
			Level.FINE,
			"Attempt to relocate to node:{0} with " +
			"invalid relocation key:{1}", localNodeId,
			HexDumper.toHexString(relocationKey));
		}
		(new SetupCompletionFuture(null, completionHandler)).
		    setException(
			new RelocateFailureException(
 			    ClientSessionHandler.RELOCATE_REFUSED_REASON,
			    RelocateFailureException.FailureReason.OTHER));
		return;
	    }
	    new ClientSessionHandler(
		ClientSessionServiceImpl.this, dataService, protocol,
		info.identity, completionHandler, info.sessionRefId);
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

	/** Map of client session IDs to an object containing a list of
	 * actions to perform upon transaction commit. */
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
    private static class CommitActions extends LinkedList<Action> {

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
			    logger.log(Level.SEVERE,
				       "FlushContextsThread interrupted, " +
				       "node:{0}", localNodeId);
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
	public void serviceEventQueue(byte[] sessionId) {
	    callStarted();
	    try {
		if (logger.isLoggable(Level.FINEST)) {
		    logger.log(Level.FINEST, "serviceEventQueue sessionId:{0}",
			       HexDumper.toHexString(sessionId));
		}
		addServiceEventQueueTask(sessionId);
	    } finally {
		callFinished();
	    }
	    
	}

	/** {@inheritDoc} */
	public byte[] relocatingSession(
	    Identity identity, byte[] sessionId, long oldNodeId)
	{
	    callStarted();
	    try {
		if (logger.isLoggable(Level.FINEST)) {
		    logger.log(Level.FINEST, "sessionId:{0} oldNodeId:{1}",
			       HexDumper.toHexString(sessionId), oldNodeId);
		}
		
		// Cache relocation information.
		byte[] relocationKey = getNextRelocationKey();
		final BigInteger sessionRefId = new BigInteger(1, sessionId);
		RelocationInfo info =
		    new RelocationInfo(identity, sessionRefId);
		BigInteger key = new BigInteger(1, relocationKey);
		incomingSessionRelocationKeys.put(key, info);
		incomingSessionRelocationInfo.put(sessionRefId, info);
		incomingRelocatingIdentities.add(identity);

		// Modify ClientSession's state to indicate that it has been
		// relocated to the local node.
		try {
		    transactionScheduler.runTask(
			new AbstractKernelRunnable(
			    "RelocateSessionToLocalNode")
			{
			    public void run() {
				ClientSessionImpl session =
				    ClientSessionImpl.getSession(
				        dataService, sessionRefId);
				session.move(localNodeId);
			    } },
			taskOwner);
		} catch (Exception e) {
		    // This exception probably means that the client
		    // session is gone, so throw an exception.  This will
		    // cause the requester to disconnect the client session
		    // because it can't assign the new node ID to the
		    // session.
		    logger.logThrow(
			Level.WARNING, e,
			"Assigning new node ID:{0} to session:{1} throws",
			localNodeId, HexDumper.toHexString(sessionId));
		    throw new RuntimeException(e);
		}
		
		// Schedule task to monitor relocation.
		taskScheduler.scheduleTask(
		    new MonitorSessionRelocatingToLocalNodeTask(key, info),
		    identity, System.currentTimeMillis() + relocationTimeout);
				      
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
		    Delivery delivery = Delivery.values()[deliveryOrdinal];

		    try {
			sessionProtocol.sessionMessage(
			    ByteBuffer.wrap(message), delivery);
		    } catch (IOException e) {
			// TBD: should we disconnect the session because
			// messages can't be delivered?
			if (logger.isLoggable(Level.FINE)) {
			    logger.logThrow(
				Level.FINE, e,
				"sending message: sessionId:{0} message:{1} " +
				"throws", 
				HexDumper.toHexString(sessionId),
				HexDumper.toHexString(message));
					    
			}
		    } catch (RuntimeException e) {
			// i.e., IllegalStateException
			// This shouldn't happen because client session
			// events are not processed while a session is
			// relocating.
			logger.logThrow(
			    Level.SEVERE, e,
			    "Attempted send to session:{0} during " +
			    "relocation, message:{1}",
			    HexDumper.toHexString(sessionId),
			    HexDumper.toHexString(message));
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

    /**
     * Returns the transaction proxy.
     */
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
    private byte[] getNextRelocationKey() {
	byte[] key = new byte[relocationKeyLength];
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
     * <li>the {@code identity} is not currently logged in and is not
     * relocating to the current node, or
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
     * @param	loggingIn if {@code true} session with specified
     *		identity is loggingIn; otherwise it is relocating
     * @return	{@code true} if the user is allowed to log in with the
     * specified {@code identity}, otherwise returns {@code false}
     */
    boolean validateUserLogin(Identity identity, ClientSessionHandler handler,
			      boolean loggingIn)
    {
	if (loggingIn && incomingRelocatingIdentities.contains(identity)) {
	    return false;
	}
		
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
     * session handler map.  This method is invoked by the handler
     * once the client has successfully logged in or has
     * successfully relocated.  If the client has relocated, the
     * {@code identity} should be non-null, otherwise, the identity
     * should be {@code null}.
     *
     * @param	sessionRefId the session ID, as a {@code BigInteger}
     * @param	handler the client session handler to cache
     * @param	identity if the session has been relocated, a non-null
     *		identity to be removed from the
     *		{@code incomingRelocatingIdentities} cache
     */
    void addHandler(BigInteger sessionRefId,
		    ClientSessionHandler handler,
		    Identity identity)
    {
        assert handler != null;
	handlers.put(sessionRefId, handler);
	incomingSessionRelocationInfo.remove(sessionRefId);
	if (identity != null) {
	    incomingRelocatingIdentities.remove(identity);
	    //  Notify status listeners that the specified client session
	    //  has completed relocating to this node.
	    for (ClientSessionStatusListener statusListener :
		     sessionStatusListeners)
	    {
		try {
		    statusListener.relocated(sessionRefId);
		} catch (Exception e) {
		    if (logger.isLoggable(Level.FINE)) {
			logger.logThrow(
			    Level.FINE, e, "Invoking 'relocated' method " +
			    "on listener:{0} throws", statusListener);
		    }
		}
	    }
	}
    }
    
    /**
     * Removes the specified session from the internal session handler map,
     * cleans up other session-related transient data, and if {@code
     * isDisconnecting} is {@code true} notifies all {@link
     * ClientSessionStatusListener}s of the session's disconnection.  This
     * method is invoked by the handler (in order to clean up the session's
     * transient data structures) when the session is disconnecting
     * its connection due to session termination or session relocation.  If
     * a session is disconnecting because the session relocating to another
     * node, then {@code isDisconnecting} will be {@code false}.
     */
    void removeHandler(BigInteger sessionRefId, boolean isRelocating) {
	if (shuttingDown()) {
	    return;
	}
	// Notify session listeners of disconnection
	notifyStatusListenersOfDisconnection(sessionRefId, isRelocating);
	handlers.remove(sessionRefId);
	sessionTaskQueues.remove(sessionRefId);
	prepareRelocationMap.remove(sessionRefId); // just in case...
    }

    /**
     * Notifies all registered {@code ClientSessionStatusListener}s that
     * the session with the specified {@code sessionRefId} has disconnected.
     */
    private void notifyStatusListenersOfDisconnection(
	BigInteger sessionRefId, boolean isRelocating)
    {
	for (ClientSessionStatusListener statusListener :
		 sessionStatusListeners)
	{
	    try {
		statusListener.disconnected(sessionRefId, isRelocating);
	    } catch (Exception e) {
		if (logger.isLoggable(Level.WARNING)) {
		    logger.logThrow(
			Level.WARNING, e, "notifying listener:{0} of " +
			"disconnected session:{1} throws", statusListener,
			HexDumper.toHexString(sessionRefId));
		}
	    }
	}
    }
    
    /**
     * Schedules a transactional task to service the event queue for the
     * session with the specified {@code sessionId}.  If there is no
     * locally-connected session with the specified {@code sessionId}, then
     * no action is taken.  <p>
     *
     * This method should be invoked outside of a transaction.
     *
     * @param	sessionId a session ID
     */
    void addServiceEventQueueTask(final byte[] sessionId) {
	final BigInteger sessionRefId = new BigInteger(1, sessionId);
	if (!handlers.containsKey(sessionRefId)) {
	    // The session is not local or is disconnected, so this node
	    // should not service the event queue.
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
		    if (getHandler(sessionRefId) != null) {
			ClientSessionImpl.serviceEventQueue(sessionId);
		    }
		} }, taskOwner);
    }

    /**
     * Notifies each registered {@link ClientSessionStatusListener} that
     * the session with the specified {@code sessionRefId} is moving to a
     * new node (specified by {@code newNodeId}).  The specified completion
     * {@code handler} should be notified (via its {@code completed}
     * method), when all listeners are finished preparing for relocation.
     *
     * @param	sessionRefId the ID for the relocating client session
     * @param	newNodeId the ID of the new node for the client session
     */
    void notifyPrepareToRelocate(final BigInteger sessionRefId,
				 final long newNodeId)
    {
	if (logger.isLoggable(Level.INFO)) {
	    logger.log(Level.INFO,
		       "Preparing session:{0} to relocate to node:{1}",
		       sessionRefId, newNodeId);
	}

	PrepareRelocationInfo info = prepareRelocationMap.get(sessionRefId);
	if (info != null) {
	    info.prepareToRelocate();
	} else {
	    logger.log(Level.WARNING,
		       "Ignoring request for session:{0} whichi is not " +
		       "relocating from local node:{1}",
		       sessionRefId, localNodeId);
	}
    }

    /**
     * Schedules a non-durable, transactional task using the given
     * {@code Identity} as the owner.
     */
    void scheduleTask(KernelRunnable task, Identity ownerIdentity) {
	Objects.checkNull("ownerIdentity", ownerIdentity);
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
	Objects.checkNull("ownerIdentity", ownerIdentity);
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
	public void recover(final Node node, SimpleCompletionHandler handler) {
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
					     
		handler.completed();
		    
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
	    DataService dataService = getInstance().getDataService();
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
	DataService dataService = getInstance().getDataService();
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
	final BigInteger sessionRefId;

	RelocationInfo(Identity identity, BigInteger sessionRefId) {
	    this.identity = identity;
	    this.sessionRefId = sessionRefId;
	}
    }

    /**
     * A task (run after a delay) that checks to see if a client session
     * with the {@code sessionRefId} specified during construction has
     * relocated from the local node. If not, the associated client session
     * is cleaned up.
     */
    private class MonitorSessionRelocatingFromLocalNodeTask
	extends AbstractKernelRunnable
    {
	final BigInteger sessionRefId;

	/**
	 * Constructs an instance with the specified {@code sessionRefId}.
	 * @param	sessionRefId a session ID
	 */
	MonitorSessionRelocatingFromLocalNodeTask(BigInteger sessionRefId) {
	    super(null);
	    this.sessionRefId = sessionRefId;
	}

	/**
	 * If the associated client hasn't finished preparing to relocate
	 * before this task runs, then clean up the client session.  Either
	 * the original server failed to inform the client that it should
	 * relocate or the client session has failed.  In either case, the
	 * client session needs to be removed.
	 */
	public void run() {

	    ClientSessionHandler sessionHandler = handlers.get(sessionRefId);
	    if (sessionHandler != null) {
		prepareRelocationMap.remove(sessionRefId);
		logger.log(
		    Level.WARNING, "Disconnecting session:{0} that timed out " +
		    "relocating from node:{1}", sessionHandler.identity,
		    localNodeId);
		sessionHandler.handleDisconnect(false, true);
	    }
	}
    }
    
    /**
     * A task (run after a delay) that checks to see if a client session
     * with the associated relocation key has finished relocating to this
     * node.  If not, the associated client session is cleaned up and all
     * client session status listeners are notified that the client session
     * has been disconnected.
     */
    private class MonitorSessionRelocatingToLocalNodeTask
	extends AbstractKernelRunnable
    {
	final BigInteger relocationKey;
	final RelocationInfo info;

	/**
	 * Constructs an instance with the specified relocation info.
	 * @param	relocationKey a relocation key
	 * @param	info a client session's relocation info
	 */
	MonitorSessionRelocatingToLocalNodeTask(
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
	    if (incomingSessionRelocationKeys.remove(relocationKey) != null) {
		logger.log(
		    Level.FINE, "Scheduling clean up of session:{0} that " +
		    "failed to relocate to local node:{1}",
		    info.identity, localNodeId);
		transactionScheduler.scheduleTask(
 		    new AbstractKernelRunnable("RemoveNonrelocatedSession") {
			public void run() {
			    ClientSessionImpl sessionImpl =
				ClientSessionImpl.getSession(
				    dataService, info.sessionRefId);
			    if (sessionImpl != null) {
				sessionImpl.notifyListenerAndRemoveSession(
				    dataService, false, true);
			    }
			} }, info.identity);
		incomingSessionRelocationInfo.remove(info.sessionRefId);
		incomingRelocatingIdentities.remove(info.identity);
		notifyStatusListenersOfDisconnection(info.sessionRefId, false);
	    }
	}
    }

    /**
     * An abstraction to keep track of the progress of session's
     * relocation preparation and notify all NodeMappingService's
     * completion handlers when relocation preparation is complete.
     *
     *
     * An instance of this class is constructed when the
     * ClientSessionService's IdentityRelocationListener is notified to
     * prepare to relocate (via the {@link
     * IdentityRelocationListener#prepareToRelocate prepareToRelocate}
     * method.
     */
    private final class PrepareRelocationInfo {
	
	/** The session that is relocating. */
	private final BigInteger sessionRefId;

	/** The ID of the session's new node. */
	private final long newNodeId;
	
	/** Completion handlers for the node mapping service. */
	private final Set<SimpleCompletionHandler> nmsCompletionHandlers =
	    new HashSet<SimpleCompletionHandler>();
	
	/** Completion handlers for {@code ClientSessionStatusListener}s
	 * that need to prepare before the node mapping service completion
	 * handler(s) are notified.
	 */
	private final Set<PrepareCompletionHandler> preparers =
	    new HashSet<PrepareCompletionHandler>();

	/** Indicates whether the {@code ClientSessionStatusListener}s have
	 * been notified to prepare for relocation. */
	private boolean isPreparing = false;

	/**
	 * Constructs an instance with the specified {@code sessionRefId},
	 * {@code newNodeId}, and completion {@code handler} from the node
	 * mapping service. <p>
	 *
	 * This constructor takes a snapshot of all registered {@code
	 * ClientSessionStatusListener}s that need to be notified to
	 * prepare to relocate after the client session service has
	 * finished preparing the client session for relocation.  Once the
	 * client session has been prepared to relocate, the client
	 * session service invokes this instance's {@link
	 * #prepareToRelocate} method so that it can notify each {@code
	 * ClientSessionStatusListener} to prepare to relocate. <p>
	 *
	 * When all {@code ClientSessionStatusListener}s have finished
	 * preparing, this instance notifies all node mapping service
	 * completion handlers that relocation preparation has been
	 * completed for the session.  There may be more than one
	 * completion handler from the node mapping service because the
	 * node mapping service may notify the client session service more
	 * than once to prepare to relocate a given client session.
	 */
	PrepareRelocationInfo(BigInteger sessionRefId, long newNodeId,
			      SimpleCompletionHandler handler)
	{
	    this.sessionRefId = sessionRefId;
	    this.newNodeId = newNodeId;
	    nmsCompletionHandlers.add(handler);
	    for (ClientSessionStatusListener listener :
		     sessionStatusListeners)
	    {
		preparers.add(new PrepareCompletionHandler(listener));
	    }
	}

	/**
	 * Notifies all {@code ClientSessionStatusListener}s to prepare for
	 * the client session (specified at construction) to relocate.  If
	 * preparing the {@code ClientSessionStatusListener}s is or has
	 * already taken place, this method takes no action.
	 */
	synchronized void prepareToRelocate() {
	    if (isPreparing) {
		return;
	    }
	    isPreparing = true;
	    for (final PrepareCompletionHandler handler : preparers) {
		taskScheduler.scheduleTask(
		  new AbstractKernelRunnable("PrepareToRelocateSession") {
		    public void run() {
			try {
			    handler.listener.prepareToRelocate(
				sessionRefId, newNodeId, handler);
			} catch (Exception e) {
			    logger.logThrow(
			        Level.WARNING, e,
				"Notifying listener:{0} to prepare " +
				"session:{1} to relocate to node:{2} throws",
				handler.listener, sessionRefId, newNodeId);
			}
		    }
		  }, taskOwner);
	    }
	}

	/**
	 * Adds the specified completion {@code handler} from the {@code
	 * NodeMappingService} to the set of completion handlers that need
	 * to be notified when all {@code ClientSessionStatusListener}s are
	 * finished preparing for relocation.
	 */
	synchronized void addNmsCompletionHandler(
	    SimpleCompletionHandler handler)
	{
	    if (preparers.isEmpty()) {
		handler.completed();
	    } else {
		nmsCompletionHandlers.add(handler);
	    }
	}

	/**
	 * Notifies this instance that the {@code
	 * ClientSessionStatusListener} for the specified {@code
	 * listenerCompletionhandler} has completed preparing for
	 * relocation.  If all listeners have completed preparation, then
	 * all {@code NodeMappingService} completion handlers are notified
	 * that preparation is complete.  Once the node mapping service has
	 * removed the mapping (and subsequently invoked this client session
	 * service's {@code NodeMappingListener.mappingRemoved} method),
	 * the client can be informed that it can start relocating its
	 * connection.
	 */
	synchronized void preparationCompleted(
	    PrepareCompletionHandler listenerCompletionHandler)
	{
	    preparers.remove(listenerCompletionHandler);
	    if (preparers.isEmpty()) {
		// Notify NodeMappingService completion handlers that
		// preparation is complete.
		for (SimpleCompletionHandler nmsCompletionHandler :
			 nmsCompletionHandlers)
		{
		    if (logger.isLoggable(Level.FINEST)) {
			logger.log(
			    Level.FINEST,
			    "Notifying NMS relocate preparation complete, " +
			    "session:{0} localNodeId:{1} newNodeId:{2}",
			    sessionRefId, getLocalNodeId(), newNodeId);
		    }
		    nmsCompletionHandler.completed();
		}
	    }
	}

	/**
	 * A completion handler for a {@code ClientSessionStatusListener}
	 * preparing for relocation, specified as an argument to the {@link
	 * ClientSessionStatusListener#prepareToRelocate prepareToRelocate}
	 * method. 
	 */
	private final class PrepareCompletionHandler
	    implements SimpleCompletionHandler
	{
	    private final ClientSessionStatusListener listener;
	    /** Indicates whether preparation is completed. */
	    private boolean completed = false;

	    /** Constructs an instance. */
	    PrepareCompletionHandler(ClientSessionStatusListener listener) {
		this.listener = listener;
	    }

	    /** {@inheritDoc} */
	    public void completed() {
		synchronized (this) {
		    if (completed) {
			return;
		    }
		    completed = true;
		}

		PrepareRelocationInfo.this.preparationCompleted(this);
	    }
	}
    }
}
