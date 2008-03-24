/*
 * Copyright 2007-2008 Sun Microsystems, Inc.
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
import com.sun.sgs.auth.Identity;
import com.sun.sgs.auth.IdentityCoordinator;
import com.sun.sgs.impl.kernel.StandardProperties;
import com.sun.sgs.impl.sharedutil.HexDumper;
import com.sun.sgs.impl.sharedutil.LoggerWrapper;
import com.sun.sgs.impl.sharedutil.PropertiesWrapper;
import com.sun.sgs.impl.util.AbstractKernelRunnable;
import com.sun.sgs.impl.util.AbstractService;
import com.sun.sgs.impl.util.BoundNamesUtil;
import com.sun.sgs.impl.util.Exporter;
import com.sun.sgs.impl.util.ManagedSerializable;
import com.sun.sgs.impl.util.NonDurableTaskQueue;
import com.sun.sgs.impl.util.NonDurableTaskScheduler;
import com.sun.sgs.impl.util.TransactionContext;
import com.sun.sgs.impl.util.TransactionContextFactory;
import com.sun.sgs.kernel.ComponentRegistry;
import com.sun.sgs.kernel.KernelRunnable;
import com.sun.sgs.nio.channels.AsynchronousChannelGroup;
import com.sun.sgs.nio.channels.AsynchronousServerSocketChannel;
import com.sun.sgs.nio.channels.AsynchronousSocketChannel;
import com.sun.sgs.nio.channels.CompletionHandler;
import com.sun.sgs.nio.channels.IoFuture;
import com.sun.sgs.nio.channels.spi.AsynchronousChannelProvider;
import com.sun.sgs.service.ClientSessionDisconnectListener;
import com.sun.sgs.service.ClientSessionService;
import com.sun.sgs.service.DataService;
import com.sun.sgs.service.Node;
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
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;   
import java.util.Properties;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Manages client sessions. <p>
 *
 * The {@link #ClientSessionServiceImpl constructor} requires the <a
 * href="../../../app/doc-files/config-properties.html#com.sun.sgs.app.name">
 * <code>com.sun.sgs.app.name</code></a> and <a
 * href="../../../app/doc-files/config-properties.html#com.sun.sgs.app.port">
 * <code>com.sun.sgs.app.port</code></a> configuration properties and supports
 * these public configuration <a
 * href="../../../app/doc-files/config-properties.html#ClientSessionService">
 * properties</a>. <p>
 */
public final class ClientSessionServiceImpl
    extends AbstractService
    implements ClientSessionService
{
    /** The package name. */
    public static final String PKG_NAME = "com.sun.sgs.impl.service.session";
    
    /** The name of this class. */
    private static final String CLASSNAME =
	ClientSessionServiceImpl.class.getName();
    
    /** The logger for this class. */
    private static final LoggerWrapper logger =
	new LoggerWrapper(Logger.getLogger(PKG_NAME));

    /** The name of the server port property. */
    private static final String SERVER_PORT_PROPERTY =
	PKG_NAME + ".server.port";
	
    /** The default server port. */
    private static final int DEFAULT_SERVER_PORT = 0;

    /** The name of the acceptor backlog property. */
    private static final String ACCEPTOR_BACKLOG_PROPERTY =
        PKG_NAME + ".acceptor.backlog";

    /** The default acceptor backlog (&lt;= 0 means default). */
    private static final int DEFAULT_ACCEPTOR_BACKLOG = 0;

    private static final String EVENTS_PER_TXN_PROPERTY =
	PKG_NAME + ".events.per.txn";

    /** The default events per transaction. */
    private static final int DEFAULT_EVENTS_PER_TXN = 1;

    /** The name of the read buffer size property. */
    private static final String READ_BUFFER_SIZE_PROPERTY =
        PKG_NAME + ".buffer.read.max";

    /** The default read buffer size: {@value #DEFAULT_READ_BUFFER_SIZE} */
    private static final int DEFAULT_READ_BUFFER_SIZE = 128 * 1024;
    
    /** The name of the write buffer size property. */
    private static final String WRITE_BUFFER_SIZE_PROPERTY =
        PKG_NAME + ".buffer.write.max";

    /** The default write buffer size: {@value #DEFAULT_WRITE_BUFFER_SIZE} */
    private static final int DEFAULT_WRITE_BUFFER_SIZE = 128 * 1024;

    /** The read buffer size for new connections. */
    private final int readBufferSize;

    /** The write buffer size for new connections. */
    private final int writeBufferSize;
    
    /** The port for accepting connections. */
    private final int appPort;

    /** The local node's ID. */
    private final long localNodeId;

    /** The async channel group for this service. */
    private final AsynchronousChannelGroup asyncChannelGroup;

    /** The acceptor for listening for new connections. */
    private final AsynchronousServerSocketChannel acceptor;

    /** The currently-active accept operation, or {@code null} if none. */
    volatile IoFuture<?, ?> acceptFuture;

    /** The registered session disconnect listeners. */
    private final Set<ClientSessionDisconnectListener> sessionDisconnectListeners =
	Collections.synchronizedSet(new HashSet<ClientSessionDisconnectListener>());

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

    /** The task scheduler for non-durable tasks. */
    final NonDurableTaskScheduler nonDurableTaskScheduler;

    /** The transaction context factory. */
    private final TransactionContextFactory<Context> contextFactory;
    
    /** The watchdog service. */
    final WatchdogService watchdogService;

    /** The node mapping service. */
    final NodeMappingService nodeMapService;

    /** The task service. */
    final TaskService taskService;

    /** The identity manager. */
    final IdentityCoordinator identityManager;

    /** The exporter for the ClientSessionServer. */
    private final Exporter<ClientSessionServer> exporter;

    /** The ClientSessionServer remote interface implementation. */
    private final SessionServerImpl serverImpl;
	
    /** The proxy for the ClientSessionServer. */
    private final ClientSessionServer serverProxy;

    /** The map of session task queues, keyed by session ID. */
    private final ConcurrentHashMap<BigInteger, NonDurableTaskQueue>
	sessionTaskQueues =
	    new ConcurrentHashMap<BigInteger, NonDurableTaskQueue>();
    
    /** The maximum number of session events to sevice per transaction. */
    final int eventsPerTxn;

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
	
	logger.log(
	    Level.CONFIG,
	    "Creating ClientSessionServiceImpl properties:{0}",
	    properties);
	PropertiesWrapper wrappedProps = new PropertiesWrapper(properties);
	
	try {
	    String portString =
		wrappedProps.getProperty(StandardProperties.APP_PORT);
	    if (portString == null) {
		throw new IllegalArgumentException(
		    "The " + StandardProperties.APP_PORT +
		    " property must be specified");
	    }
	    appPort = Integer.parseInt(portString);
	    // TBD: do we want to restrict ports to > 1024?
	    if (appPort < 0) {
		throw new IllegalArgumentException(
		    "The " + StandardProperties.APP_PORT +
		    " property can't be negative: " + appPort);
	    } else if (appPort > 65535) {
		throw new IllegalArgumentException(
		    "The " + StandardProperties.APP_PORT +
		    " property can be greater than 65535: " + appPort);
	    }

	    /*
	     * Get the property for controlling session event processing.
	     */
	    eventsPerTxn = wrappedProps.getIntProperty(
		EVENTS_PER_TXN_PROPERTY, DEFAULT_EVENTS_PER_TXN,
		1, Integer.MAX_VALUE);

            readBufferSize = wrappedProps.getIntProperty(
                READ_BUFFER_SIZE_PROPERTY, DEFAULT_READ_BUFFER_SIZE,
                8192, Integer.MAX_VALUE);

            writeBufferSize = wrappedProps.getIntProperty(
                WRITE_BUFFER_SIZE_PROPERTY, DEFAULT_WRITE_BUFFER_SIZE,
                8192, Integer.MAX_VALUE);

	    /*
	     * Export the ClientSessionServer.
	     */
	    int serverPort = wrappedProps.getIntProperty(
		SERVER_PORT_PROPERTY, DEFAULT_SERVER_PORT, 0, 65535);
	    serverImpl = new SessionServerImpl();
	    exporter =
		new Exporter<ClientSessionServer>(ClientSessionServer.class);
	    try {
		int port = exporter.export(serverImpl, serverPort);
		serverProxy = exporter.getProxy();
		if (logger.isLoggable(Level.CONFIG)) {
		    logger.log(
			Level.CONFIG, "export successful. port:{0,number,#}",
			port);
		}
	    } catch (Exception e) {
		try {
		    exporter.unexport();
		} catch (RuntimeException re) {
		}
		throw e;
	    }

	    /*
	     * Get services and initialize service-related and other
	     * instance fields.
	     */
	    identityManager =
		systemRegistry.getComponent(IdentityCoordinator.class);
	    flushContextsThread.start();
	    contextFactory = new ContextFactory(txnProxy);
	    watchdogService = txnProxy.getService(WatchdogService.class);
	    nodeMapService = txnProxy.getService(NodeMappingService.class);
	    taskService = txnProxy.getService(TaskService.class);
            nonDurableTaskScheduler =
		new NonDurableTaskScheduler(
		    taskScheduler, taskOwner, taskService);
	    localNodeId = watchdogService.getLocalNodeId();
	    watchdogService.addRecoveryListener(
		new ClientSessionServiceRecoveryListener());
	    int acceptorBacklog = wrappedProps.getIntProperty(
	                ACCEPTOR_BACKLOG_PROPERTY, DEFAULT_ACCEPTOR_BACKLOG);

	    /*
	     * Store the ClientSessionServer proxy in the data store.
	     */
	    taskScheduler.runTransactionalTask(
		new AbstractKernelRunnable() {
		    public void run() {
			dataService.setServiceBinding(
			    getClientSessionServerKey(localNodeId),
			    new ManagedSerializable<ClientSessionServer>(
				serverProxy));
		    }},
		taskOwner);
	    
	    /*
	     * Listen for incoming client connections.
	     */
            InetSocketAddress endpoint = new InetSocketAddress(appPort);
            AsynchronousChannelProvider provider =
                // TODO fetch from config
                AsynchronousChannelProvider.provider();
            asyncChannelGroup =
                // TODO fetch from config
                provider.openAsynchronousChannelGroup(
                    Executors.newCachedThreadPool());
            acceptor =
                provider.openAsynchronousServerSocketChannel(asyncChannelGroup);
	    try {
                acceptor.bind(endpoint, acceptorBacklog);
		if (logger.isLoggable(Level.CONFIG)) {
		    logger.log(
			Level.CONFIG, "bound to port:{0,number,#}",
			getListenPort());
		}
	    } catch (Exception e) {
		try {
		    acceptor.close();
                } catch (IOException ioe) {
                    logger.logThrow(Level.WARNING, ioe,
                        "problem closing acceptor");
                }
		throw e;
	    }
	    // TBD: listen for UNRELIABLE connections as well?

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
    public void doReady() {
        acceptFuture = acceptor.accept(new AcceptorListener());
        try {
            if (logger.isLoggable(Level.CONFIG)) {
                logger.log(
                    Level.CONFIG, "listening on {0}",
                    acceptor.getLocalAddress());
            }
        } catch (IOException ioe) {
            throw new RuntimeException(ioe.getMessage(), ioe);
        }
    }

    /** {@inheritDoc} */
    public void doShutdown() {
	final IoFuture<?, ?> future = acceptFuture;
	acceptFuture = null;
	if (future != null) {
	    future.cancel(true);
	}

	if (acceptor != null) {
	    try {
		acceptor.close();
            } catch (IOException e) {
                logger.logThrow(Level.FINEST, e, "closing acceptor throws");
                // swallow exception
            }
	}

	if (asyncChannelGroup != null) {
	    asyncChannelGroup.shutdown();
	    boolean groupShutdownCompleted = false;
	    try {
		groupShutdownCompleted =
		    asyncChannelGroup.awaitTermination(1, TimeUnit.SECONDS);
	    } catch (InterruptedException e) {
		logger.logThrow(Level.FINEST, e,
				"shutdown acceptor interrupted");
		Thread.currentThread().interrupt();
	    }
	    if (!groupShutdownCompleted) {
		logger.log(Level.WARNING, "forcing async group shutdown");
		try {
		    asyncChannelGroup.shutdownNow();
		} catch (IOException e) {
		    logger.logThrow(Level.FINEST, e,
				    "shutdown acceptor throws");
		    // swallow exception
		}
	    }
	}
	logger.log(Level.FINEST, "acceptor shutdown");

	if (exporter != null) {
	    try {
		exporter.unexport();
		logger.log(Level.FINEST, "client session server unexported");
	    } catch (RuntimeException e) {
		logger.logThrow(Level.FINEST, e, "unexport server throws");
		// swallow exception
	    }
	}

	for (ClientSessionHandler handler : handlers.values()) {
	    handler.shutdown();
	}
	handlers.clear();

	flushContextsThread.interrupt();
    }

    /**
     * Returns the port this service is listening on for incoming
     * client session connections.
     *
     * @return the port this service is listening on
     * @throws IOException if an IO problem occurs
     */
    public int getListenPort() throws IOException {
        return ((InetSocketAddress) acceptor.getLocalAddress()).getPort();
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
        if (listener == null)
            throw new NullPointerException("null listener");
        
        sessionDisconnectListeners.add(listener);
    }
    
    /** {@inheritDoc} */
    public void sendProtocolMessageNonTransactional(
	BigInteger sessionRefId, ByteBuffer message, Delivery delivery)
    {
	ClientSessionHandler handler = handlers.get(sessionRefId);
	/*
	 * If a local handler exists, forward message to local handler
	 * to send to client session.
	 */
	if (handler != null) {
	    byte[] bytes = new byte[message.remaining()];
	    message.get(bytes);
	    handler.sendProtocolMessage(bytes, delivery);
	} else {
	    logger.log(
		Level.FINE,
		"Discarding messages for unknown session:{0}",
		sessionRefId);
		return;
	}
    }

    /* -- Package access methods for adding commit actions -- */
    
    /**
     * Sends the specified protocol {@code message} to the specified
     * client {@code session} with the specified {@code delivery}
     * guarantee.  This method must be called within a transaction.
     *
     * @param	session	a client session
     * @param	message a complete protocol message
     * @param	delivery a delivery requirement
     *
     * @throws 	TransactionException if there is a problem with the
     *		current transaction
     */
    void sendProtocolMessage(
	ClientSessionImpl session, ByteBuffer message, Delivery delivery)
    {
        byte[] bytes = new byte[message.remaining()];
        message.get(bytes);
	checkContext().addMessage(session, bytes, delivery);
    }

    /**
     * Sends the specified protocol {@code message} to the specified
     * client {@code session} with the specified {@code delivery}
     * guarantee.  This method must be called within a transaction.
     *
     * <p>The message is placed at the head of the queue of messages sent
     * during the current transaction and is delivered along with any
     * other queued messages when the transaction commits.
     *
     * @param	session	a client session
     * @param	message a complete protocol message
     * @param	delivery a delivery requirement
     * @param	clearMessages if true, clear message queue of any other
     *		messages
     *
     * @throws 	TransactionException if there is a problem with the
     *		current transaction
     */
    void sendProtocolMessageFirst(
 	ClientSessionImpl session, byte[] message,
	Delivery delivery, boolean clearMessages)
    {
	Context context = checkContext();
	if (clearMessages) {
	    context.clearMessages(session);
	}
	context.addMessageFirst(session, message, delivery);
    }

    /**
     * Disconnects the specified client {@code session}.  This method must
     * be invoked within a transaction.
     *
     * @param	session a client session
     *
     * @throws 	TransactionException if there is a problem with the
     *		current transaction
     */
    void disconnect(ClientSessionImpl session) {
	checkContext().requestDisconnect(session);
    }

    /**
     * Returns the size of the read buffer to use for new connections.
     * 
     * @return the size of the read buffer to use for new connections
     */
    int getReadBufferSize() {
        return readBufferSize;
    }

    /**
     * Returns the size of the write buffer to use for new connections.
     * 
     * @return the size of the write buffer to use for new connections
     */
    int getWriteBufferSize() {
        return writeBufferSize;
    }

    /** A completion handler for accepting connections. */
    private class AcceptorListener
        implements CompletionHandler<AsynchronousSocketChannel, Void>
    {

	/** Handle new connection or report failure. */
        public void completed(
	    IoFuture<AsynchronousSocketChannel, Void> result)
        {
            try {
                try {
                    AsynchronousSocketChannel newChannel = result.getNow();
                    logger.log(Level.FINER, "Accepted {0}", newChannel);

		    new ClientSessionHandler(
			ClientSessionServiceImpl.this, dataService,
			new AsynchronousMessageChannel(
			    newChannel, readBufferSize));

                    // Resume accepting connections
                    acceptFuture = acceptor.accept(this);

                } catch (ExecutionException e) {
                    throw (e.getCause() == null) ? e : e.getCause();
                }
            } catch (CancellationException e) {               
                logger.logThrow(Level.FINE, e, "acceptor cancelled"); 
                //ignore
            } catch (Throwable e) {
                SocketAddress addr = null;
                try {
                    addr = acceptor.getLocalAddress();
                } catch (IOException ioe) {
                    // ignore
                }

                logger.logThrow(
		    Level.SEVERE, e, "acceptor error on {0}", addr);

                // TBD: take other actions, such as restarting acceptor?
            }
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
        private final Map<ClientSessionImpl, CommitActions> commitActions =
	    new HashMap<ClientSessionImpl, CommitActions>();

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
	 * Clears the message queue for the given client session.  This
	 * method is invoked when a login failure happens due to the
	 * AppListener.loggedIn method either throwing a non-retryable
	 * exception, or returning a null or non-serializable client
	 * session listener.  It those cases, no messages other than the
	 * LOGIN_FAILED protocol message should reach the client.
	 */
	void clearMessages(ClientSessionImpl session) {
	    getCommitActions(session).clearMessages();
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

		getCommitActions(session).setDisconnect();
		
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
	    ClientSessionImpl session, byte[] message,
	    Delivery delivery, boolean isFirst)
	{
	    try {
		if (logger.isLoggable(Level.FINEST)) {
		    logger.log(
			Level.FINEST,
			"Context.addMessage first:{0} session:{1}, message:{2}",
			isFirst, session, message);
		}
		checkPrepared();

		getCommitActions(session).addMessage(message, isFirst);
	    
	    } catch (RuntimeException e) {
                if (logger.isLoggable(Level.FINE)) {
                    logger.logThrow(
			Level.FINE, e,
			"Context.addMessage exception");
                }
                throw e;
            }
	}

	/**
	 * Returns the commit actions for the given {@code session}.
	 */
	private CommitActions getCommitActions(ClientSessionImpl session) {

	    CommitActions actions = commitActions.get(session);
	    if (actions == null) {
		actions = new CommitActions(session);
		commitActions.put(session, actions);
	    }
	    return actions;
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
	    if (! readOnly) {
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
	 * Sends all protocol messages enqueued during this context's
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
     * Contains pending changes for a given client session.
     */
    private class CommitActions {

	/** The client session ID as a BigInteger. */
	private final BigInteger sessionRefId;

	/** List of protocol messages to send on commit. */
	private List<byte[]> messages = new ArrayList<byte[]>();

	/** If true, disconnect after sending messages. */
	private boolean disconnect = false;

	CommitActions(ClientSessionImpl sessionImpl) {
	    if (sessionImpl == null) {
		throw new NullPointerException("null sessionImpl");
	    } 
	    this.sessionRefId = sessionImpl.getId();
	}

	void addMessage(byte[] message, boolean isFirst) {
 	    if (isFirst) {
		messages.add(0, message);
	    } else {
		messages.add(message);
	    }
	}

	void clearMessages() {
	    messages.clear();
	}
	
	void setDisconnect() {
	    disconnect = true;
	}

	void flush() {
	    sendMessages();
	    if (disconnect) {
		ClientSessionHandler handler = handlers.get(sessionRefId);
		/*
		 * If session is local, disconnect session; otherwise, log
		 * error message. 
		 */
		if (handler != null) {
		    handler.handleDisconnect(false);
		} else {
		    logger.log(
		        Level.FINE,
			"discarding request to disconnect unknown session:{0}",
			sessionRefId);
		}
	    }
	}

	void sendMessages() {

	    ClientSessionHandler handler = handlers.get(sessionRefId);
	    /*
	     * If a local handler exists, forward messages to local
	     * handler to send to client session; otherwise log
	     * error message.
	     */
	    if (handler != null && handler.isConnected()) {
		for (byte[] message : messages) {
		    handler.sendProtocolMessage(message, Delivery.RELIABLE);
		}
	    } else {
		logger.log(
		    Level.FINE,
		    "Discarding messages for disconnected session:{0}",
		    handler);
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
	    
	    for (;;) {
		
		if (shuttingDown()) {
		    return;
		}

		/*
		 * Wait for a non-empty context queue, returning if
		 * this thread is interrupted.
		 */
		synchronized (flushContextsLock) {
		    if (contextQueue.isEmpty()) {
			try {
			    flushContextsLock.wait();
			} catch (InterruptedException e) {
			    return;
			}
		    }
		}

		/*
		 * Remove committed contexts from head of context
		 * queue, and enqueue them to be flushed.
		 */
		if (! contextQueue.isEmpty()) {
		    Iterator<Context> iter = contextQueue.iterator();
		    while (iter.hasNext()) {
			if (Thread.currentThread().isInterrupted()) {
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
     * forward messages to or disconnect local client sessions.
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
		NonDurableTaskQueue taskQueue =
		    sessionTaskQueues.get(sessionRefId);
		if (taskQueue == null) {
		    NonDurableTaskQueue newTaskQueue =
			new NonDurableTaskQueue(txnProxy, taskScheduler,
						taskOwner);
		    taskQueue = sessionTaskQueues.
			putIfAbsent(sessionRefId, newTaskQueue);
		    if (taskQueue == null) {
			taskQueue = newTaskQueue;
		    }
		}
		taskQueue.addTask(new AbstractKernelRunnable() {
		    public void run() {
			ClientSessionImpl.serviceEventQueue(sessionId);
		    }});
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
	if (! watchdogService.isLocalNodeAlive()) {
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
    synchronized static ClientSessionServiceImpl getInstance() {
	if (txnProxy == null) {
	    throw new IllegalStateException("Service not initialized");
	} else {
	    return (ClientSessionServiceImpl)
		txnProxy.getService(ClientSessionService.class);
	}
    }

    /**
     * Adds the handler for the specified session to the internal
     * session handler map.  This method is invoked by the handler once the
     * client has successfully logged in.
     */
    void addHandler(BigInteger sessionRefId, ClientSessionHandler handler) {
	handlers.put(sessionRefId, handler);
    }
    
    /**
     * Removes the specified session from the internal session  handler
     * map.  This method is invoked by the handler when the session becomes
     * disconnected.
     */
    void removeHandler(BigInteger sessionRefId) {
	if (shuttingDown()) {
	    return;
	}
	// Notify session listeners of disconnection
	for (ClientSessionDisconnectListener disconnectListener :
		 sessionDisconnectListeners)
	{
	    disconnectListener.disconnected(sessionRefId);
	}
	handlers.remove(sessionRefId);
	sessionTaskQueues.remove(sessionRefId);
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
     */
    void scheduleNonTransactionalTask(
	KernelRunnable task, Identity ownerIdentity)
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
     * Runs the specified {@code task} immediately, in a transaction.
     */
    void runTransactionalTask(KernelRunnable task, Identity ownerIdentity)
	throws Exception
    {
	Identity owner =
	    (ownerIdentity == null) ?
	    taskOwner :
	    ownerIdentity;
	    
	taskScheduler.runTransactionalTask(task, owner);
    }

    /**
     * The {@code RecoveryListener} for handling requests to recover
     * for a failed {@code ClientSessionService}.
     */
    private class ClientSessionServiceRecoveryListener
	implements RecoveryListener
    {
	/** {@inheritDoc}
	 *
	 * TBD: Recovery (due to being possibly-lengthy) should not be
	 * performed in this remote method.  Recovery operations should
	 * be performed in a separate thread.
	 */
	public void recover(final Node node, RecoveryCompleteFuture future) {
	    final long nodeId = node.getId();
	    try {
		taskScheduler.runTransactionalTask(
		    new AbstractKernelRunnable() {
			public void run() {
			    taskService.scheduleTask(
				new RemoveClientSessionServerProxyTask(nodeId));
			    notifyDisconnectedSessions(nodeId);
			}},
		    taskOwner);
		future.done();
	    } catch (Exception e) {
		logger.logThrow(
 		    Level.WARNING, e,
		    "notifying disconnected sessions for node:{0} throws",
		    nodeId);
		// TBD: what should it do if it can't recover?
	    }
	}
	
	/**
	 * For each {@code ClientSession} assigned to the specified
	 * failed node, notifies the {@code ClientSessionListener} (if
	 * any) that its corresponding session has been forcibly
	 * disconnected, removes the listener's binding from the data
	 * service, and then removes the client session's state and
	 * its bindings from the data service.
	 */
	private void notifyDisconnectedSessions(long nodeId) {
	    String nodePrefix = ClientSessionImpl.getNodePrefix(nodeId);
	    for (String key : BoundNamesUtil.getServiceBoundNamesIterable(
 				    dataService, nodePrefix))
	    {
		logger.log(
		    Level.FINEST,
		    "notifyDisconnectedSessions key: {0}",
		    key);

		final String sessionKey = key;		

		// TBD: should each notification/removal happen as a
		// separate task?
		ClientSessionImpl sessionImpl = 
		    (ClientSessionImpl) dataService.getServiceBinding(
			sessionKey);
		sessionImpl.notifyListenerAndRemoveSession(
		    dataService, false, true);
	    }
	}
    }

    /**
     * A persistent task to remove the client session server proxy for a
     * specified node.
     */
    private static class RemoveClientSessionServerProxyTask
	 implements Task, Serializable
    {
	/** The serialVersionUID for this class. */
	private final static long serialVersionUID = 1L;

	/** The node ID. */
	private final long nodeId;

	/**
	 * Constructs an instance of this class with the specified
	 * {@code nodeId}.
	 */
	RemoveClientSessionServerProxyTask(long nodeId) {
	    this.nodeId = nodeId;
	}

	/**
	 * Removes the client session server proxy and binding for the node
	 * specified during construction.
	 */
	public void run() {
	    String sessionServerKey = getClientSessionServerKey(nodeId);
	    DataService dataService = getDataService();
	    try {
		dataService.removeObject(
		    dataService.getServiceBinding(sessionServerKey));
	    } catch (NameNotBoundException e) {
		// already removed
		return;
	    } catch (ObjectNotFoundException e) {
	    }
	    dataService.removeServiceBinding(sessionServerKey);
	}
    }
}
