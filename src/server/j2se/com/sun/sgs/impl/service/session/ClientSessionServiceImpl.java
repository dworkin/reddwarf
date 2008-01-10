/*
 * Copyright 2007 Sun Microsystems, Inc.
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

import com.sun.sgs.app.ClientSession;
import com.sun.sgs.app.Delivery;
import com.sun.sgs.app.TransactionNotActiveException;
import com.sun.sgs.auth.Identity;
import com.sun.sgs.auth.IdentityCoordinator;
import com.sun.sgs.impl.io.ServerSocketEndpoint;
import com.sun.sgs.impl.io.TransportType;
import com.sun.sgs.impl.kernel.StandardProperties;
import com.sun.sgs.impl.kernel.TaskOwnerImpl;
import com.sun.sgs.impl.sharedutil.LoggerWrapper;
import com.sun.sgs.impl.sharedutil.PropertiesWrapper;
import com.sun.sgs.impl.util.AbstractKernelRunnable;
import com.sun.sgs.impl.util.AbstractService;
import com.sun.sgs.impl.util.BoundNamesUtil;
import com.sun.sgs.impl.util.Exporter;
import com.sun.sgs.impl.util.NonDurableTaskScheduler;
import com.sun.sgs.impl.util.TransactionContext;
import com.sun.sgs.impl.util.TransactionContextFactory;
import com.sun.sgs.io.Acceptor;
import com.sun.sgs.io.AcceptorListener;
import com.sun.sgs.io.ConnectionListener;
import com.sun.sgs.kernel.ComponentRegistry;
import com.sun.sgs.kernel.KernelRunnable;
import com.sun.sgs.kernel.TaskOwner;
import com.sun.sgs.service.ClientSessionService;
import com.sun.sgs.service.Node;
import com.sun.sgs.service.NodeMappingService;
import com.sun.sgs.service.ProtocolMessageListener;
import com.sun.sgs.service.RecoveryCompleteFuture;
import com.sun.sgs.service.RecoveryListener;
import com.sun.sgs.service.TaskService;
import com.sun.sgs.service.Transaction;
import com.sun.sgs.service.TransactionProxy;
import com.sun.sgs.service.WatchdogService;
import java.io.IOException;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;   
import java.util.Properties;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
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
public class ClientSessionServiceImpl
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
    
    /** The port for accepting connections. */
    private final int appPort;

    /** The local node's ID. */
    private final long localNodeId;

    /** The listener for accepted connections. */
    private final AcceptorListener acceptorListener = new Listener();

    /** The registered service listeners. */
    private final Map<Byte, ProtocolMessageListener> serviceListeners =
	Collections.synchronizedMap(
	    new HashMap<Byte, ProtocolMessageListener>());

    /** A map of local session handlers, keyed by session ID . */
    private final Map<BigInteger, ClientSessionHandler> handlers =
	Collections.synchronizedMap(
	    new HashMap<BigInteger, ClientSessionHandler>());

    /** Queue of contexts that are prepared (non-readonly) or committed. */
    private final Queue<Context> contextQueue =
	new ConcurrentLinkedQueue<Context>();

    /** Thread for flushing committed contexts. */
    private final Thread flushContextsThread = new FlushContextsThread();
    
    /** Lock for notifying the thread that flushes commmitted contexts. */
    private final Object flushContextsLock = new Object();

    /** The Acceptor for listening for new connections. */
    private final Acceptor<SocketAddress> acceptor;

    /** The task scheduler for non-durable tasks. */
    volatile NonDurableTaskScheduler nonDurableTaskScheduler;

    /** The transaction context factory. */
    private final TransactionContextFactory<Context> contextFactory;
    
    /** The watchdog service. */
    final WatchdogService watchdogService;

    /** The node mapping service. */
    final NodeMappingService nodeMapService;

    /** The identity manager. */
    final IdentityCoordinator identityManager;

    /** The exporter for the ClientSessionServer. */
    private final Exporter<ClientSessionServer> exporter;

    /** The ClientSessionServer remote interface implementation. */
    private final SessionServerImpl serverImpl;
	
    /** The proxy for the ClientSessionServer. */
    private final ClientSessionServer serverProxy;

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
	    }

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

	    identityManager =
		systemRegistry.getComponent(IdentityCoordinator.class);
	    flushContextsThread.start();

	    contextFactory = new ContextFactory(txnProxy);
	    watchdogService = txnProxy.getService(WatchdogService.class);
	    nodeMapService = txnProxy.getService(NodeMappingService.class);
	    localNodeId = watchdogService. getLocalNodeId();
	    watchdogService.addRecoveryListener(
		new ClientSessionServiceRecoveryListener());
	    ServerSocketEndpoint endpoint =
		new ServerSocketEndpoint(
		    new InetSocketAddress(appPort), TransportType.RELIABLE);
	    acceptor = endpoint.createAcceptor();
	    try {
		acceptor.listen(acceptorListener);
		if (logger.isLoggable(Level.CONFIG)) {
		    logger.log(
			Level.CONFIG, "listen successful. port:{0,number,#}",
			getListenPort());
		}
	    } catch (Exception e) {
		try {
		    acceptor.shutdown();
		} catch (RuntimeException re) {
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
	    throw e;
	}
    }

    /* -- Implement AbstractService -- */

    /** {@inheritDoc} */
    public void doReady() {
	// TBD: the AcceptorListener.newConnection method needs to
	// reject connections until ready is invoked.  Need to
	// implement interlock for this.  -- ann (8/29/07)

        nonDurableTaskScheduler =
		new NonDurableTaskScheduler(
		    taskScheduler, taskOwner,
		    txnProxy.getService(TaskService.class));
    }

    /** {@inheritDoc} */
    public void doShutdown() {
	try {
	    acceptor.shutdown();
	    logger.log(Level.FINEST, "acceptor shutdown");
	} catch (RuntimeException e) {
	    logger.logThrow(Level.FINEST, e, "shutdown acceptor throws");
	    // swallow exception
	}

	try {
	    exporter.unexport();
	} catch (RuntimeException e) {
	    logger.logThrow(Level.FINEST, e, "unexport server throws");
	    // swallow exception
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
     */
    public int getListenPort() {
	return ((InetSocketAddress) acceptor.getBoundEndpoint().getAddress()).
	    getPort();
    }

    /**
     * Returns the proxy for the client session server
     *
     * @return	the proxy for the client session server
     */
    ClientSessionServer getServerProxy() {
	return serverProxy;
    }

    /* -- Implement ClientSessionService -- */

    /** {@inheritDoc} */
    public void registerProtocolMessageListener(
	byte serviceId, ProtocolMessageListener listener)
    {
	if (listener == null) {
	    throw new NullPointerException("null listener");
	}
	serviceListeners.put(serviceId, listener);
    }

    /** {@inheritDoc} */
    public ClientSession getClientSession(byte[] sessionId) {
	if (sessionId == null) {
	    throw new NullPointerException("null sessionId");
	}
	// TBD: is this method invocation necessary?
	checkLocalNodeAlive();
	return
	    ClientSessionImpl.getSession(
		dataService, new BigInteger(1, sessionId));
    }

    /** {@inheritDoc} */
    public void sendProtocolMessage(
	ClientSession session, byte[] message, Delivery delivery)
    {
	checkContext().addMessage(
	    getClientSessionImpl(session), message, delivery);
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
     *
     * @throws 	TransactionException if there is a problem with the
     *		current transaction
     */
    void sendProtocolMessageFirst(
	ClientSessionImpl session, byte[] message, Delivery delivery)
    {
	checkContext().addMessageFirst(session, message, delivery);
    }

    /** {@inheritDoc} */
    public void sendProtocolMessageNonTransactional(
	byte[] sessionId, byte[] message, Delivery delivery)
    {
	BigInteger sessionRefId = new BigInteger(1, sessionId);
	ClientSessionHandler handler = handlers.get(sessionRefId);
	/*
	 * If a local handler exists, forward message to local handler
	 * to send to client session.
	 */
	if (handler != null) {
	    handler.sendProtocolMessage(message, delivery);
	} else {
	    logger.log(
		Level.FINE,
		"Discarding messages for unknown session:{0}",
		sessionRefId);
		return;
	}
    }

    /** {@inheritDoc} */
    public void disconnect(ClientSession session) {
	checkContext().requestDisconnect(getClientSessionImpl(session));
    }

    /* -- Implement AcceptorListener -- */

    private class Listener implements AcceptorListener {

	/**
	 * {@inheritDoc}
	 *
	 * <p>Creates a new client session with the specified handle,
	 * and adds the session to the internal session map.  If the
	 * handler determine that the session should not be
	 * redirected, then the handler should be stored locally by
	 * invoking the {@link connected} method.
	 */
	public ConnectionListener newConnection() {
	    if (shuttingDown()) {
		return null;
	    }
	    ClientSessionHandler handler =
		new ClientSessionHandler(
		    ClientSessionServiceImpl.this, dataService);
	    return handler.getConnectionListener();
	}

        /** {@inheritDoc} */
	public void disconnected() {
	    logger.log(
	        Level.SEVERE,
		"The acceptor has become disconnected from port: {0}", appPort);
	    // TBD: take other actions, such as restarting acceptor?
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
	    boolean readOnly = commitActions.values().isEmpty();
	    if (! readOnly) {
		contextQueue.add(this);
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
	 * context in the queue is committed, .
	 */
	private void checkFlush() {
	    Context context = contextQueue.peek();
	    if ((context != null) && (context.isCommitted)) {
		synchronized (flushContextsLock) {
		    flushContextsLock.notify();
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

	/** The client session. */
	private final ClientSessionImpl sessionImpl;
	
	/** List of protocol messages to send on commit. */
	private List<Action> actions = new ArrayList<Action>();

	/** If true, disconnect after sending messages. */
	private boolean disconnect = false;

	CommitActions(ClientSessionImpl sessionImpl) {
	    if (sessionImpl == null) {
		throw new NullPointerException("null session");
	    }
	    this.sessionImpl = sessionImpl;
	}

	boolean isEmpty() {
	    return actions.isEmpty();
	}

	void addMessage(byte[] message, boolean isFirst) {
 	    if (isFirst) {
		actions.add(0, new SendMessageAction(message));
	    } else {
		if (actions.isEmpty() ||
		    ! actions.get(actions.size()-1).addMessage(message))
		{
		    actions.add(new SendMessageAction(message));
		}
	    }
	    
	}
	
	void setDisconnect() {
	    if (sessionImpl == null) {
		throw new UnsupportedOperationException(
		    "setDisconnect not supported if sessionImpl is null");
	    }
	    disconnect = true;
	}

	void flush() {
	    for (Action action : actions) {
		action.doAction();
	    }
	    if (disconnect) {
		BigInteger sessionId = sessionImpl.getId();
		ClientSessionHandler handler = handlers.get(sessionId);
		if (handler != null) {
		    handler.handleDisconnect(false);
		} else {
		    // TBD: handle remote disconnect here...
		    throw new AssertionError("not implemented");
		}
	    }
	}

	/**
	 * Represents an action.
	 *
	 
	 */
	private abstract class Action {

	    boolean addMessage(byte[] message) {
		return false;
	    }
	    
	    abstract void doAction();
	}

	private class SendMessageAction extends Action {

	    private final List<byte[]> messages = new ArrayList<byte[]>();

	    SendMessageAction(byte[] message) {
		messages.add(message);
	    }
	
	    boolean addMessage(byte[] message) {
		messages.add(message);
		return true;
	    }

	    void doAction() {

		BigInteger sessionId = sessionImpl.getId();
		ClientSessionHandler handler = handlers.get(sessionId);
		/*
		 * If a local handler exists, forward messages to
		 * local handler to send to client session; otherwise
		 * obtain remote server for client session and forward
		 * messages for delivery.
		 */
		if (handler != null) {
		    if (! handler.isConnected()) {
			logger.log(
			  Level.FINE,
			    "Discarding messages for disconnected session:{0}",
			handler);
			return;
		    }
		    for (byte[] message : messages) {
			handler.sendProtocolMessage(message, Delivery.RELIABLE);
		    }

		} else {
		    if (! sessionImpl.isConnected()) {
			logger.log(
			  Level.FINE,
			    "Discarding messages for disconnected session:{0}",
			sessionImpl);
			return;
		    }

		    int size = messages.size();
		    byte[][] messageData = new byte[size][];
		    Delivery[] deliveryData = new Delivery[size];
		    int i = 0;
		    for (byte[] message : messages) {
			messageData[i] = message;
			deliveryData[i] = Delivery.RELIABLE;
			i++;
		    }
		
		    try {
			boolean connected = sessionImpl.getClientSessionServer().
			    sendProtocolMessages(sessionId.toByteArray(), null,
						 messageData,
						 deliveryData);
			if (! connected) {
			    sessionImpl.setDisconnected();
			}
		    } catch (IOException e) {
			logger.logThrow(
		    	    Level.FINE, e,
			    "Sending messages to session:{0} throws",
			    sessionImpl);
			// TBD: set the session as disconnected?
			// sessionImpl.setDisconnected();
		    }
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
	    
	    for (;;) {
		
		if (shuttingDown()) {
		    return;
		}

		/*
		 * Wait for a non-empty context queue, returning if
		 * this thread is interrupted.
		 */
		if (contextQueue.isEmpty()) {
		    synchronized (flushContextsLock) {
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
	public boolean sendProtocolMessage(
 	    byte[] idBytes, long seq, byte[] message, Delivery delivery)
	{
	    BigInteger id = new BigInteger(1, idBytes);
	    ClientSessionHandler handler = handlers.get(id);
	    if (handler != null) {
		if (handler.isConnected()) {
		    handler.sendProtocolMessage(message, delivery);
		    return true;
		} else {
		    logger.log (
		    	Level.FINER,
			"Unable to send message to disconnected session:{0}",
			id);
		}
	    } else {
		logger.log(
		    Level.FINER,
		    "Unable to send message to unknown session:{0}", id);
	    }
	    return false;
	}

	/** {@inheritDoc} */
	public boolean sendProtocolMessages(byte[] sessionId,
					    long [] seq,
					    byte[][] messages,
					    Delivery[] delivery)
	{
	    BigInteger id = new BigInteger(1, sessionId);
	    ClientSessionHandler handler = handlers.get(id);
	    if (handler == null || ! handler.isConnected()) {
		logger.log (
		    Level.FINE,
		    "Unable to send message to unknown or " +
		    "disconnected session:{0}", id);
		return false;
	    }

	    if (messages.length != delivery.length) {
		throw new IllegalArgumentException(
		    "length of messages and delivery arrays must match");
	    }

	    for (int i = 0; i < messages.length; i++) {
		handler.sendProtocolMessage(messages[i], delivery[i]);
	    }
	    return true;
	}

	/** {@inheritDoc} */
	public boolean disconnect(byte[] sessionId) {
	    BigInteger id = new BigInteger(1, sessionId);
	    ClientSessionHandler handler = handlers.get(id);
	    if (handler != null) {
		if (handler.isConnected()) {
		    handler.handleDisconnect(false);
		    return true;
		} else {
		    logger.log (
		    	Level.FINE, "Session:{0} already disconnected", id);
		}
	    } else {
		logger.log(
		    Level.FINE, "Unable to disconnect unknown session:{0}", id);
	    }
	    return false;
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
     * Returns the client session handler for the specified session
     * {@code idBytes}.
     */
    private ClientSessionHandler getHandler(byte[] idBytes) {
	return handlers.get(new BigInteger(1, idBytes));
    }

    /**
     * Returns the {@code ClientSessionImpl} corresponding to the
     * specified client {@code session}.
     */
    private ClientSessionImpl getClientSessionImpl(ClientSession session) {
	if (session instanceof ClientSessionImpl) {
	    return (ClientSessionImpl) session;
	} else {
	    throw new AssertionError(
		"session not instanceof ClientSessionImpl: " + session);
	}
    }
    
    /**
     * Checks if the local node is considered alive, and throws an
     * {@code IllegalStateException} if the node is no loger alive.
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
     * Returns the service listener for the specified service id.
     */
    ProtocolMessageListener getProtocolMessageListener(byte serviceId) {
	return serviceListeners.get(serviceId);
    }

    /**
     * Adds the handler for the specified session to the internal
     * session handler map.  This method is invoked by the handler once the
     * client has succesfully logged in.
     */
    void connected(BigInteger sessionRefId, ClientSessionHandler handler) {
	handlers.put(sessionRefId, handler);
    }
    
    /**
     * Removes the specified session from the internal session  handler
     * map.  This method is invoked by the handler when the session becomes
     * disconnected.
     */
    void disconnected(BigInteger sessionRefId) {
	if (shuttingDown()) {
	    return;
	}
	// Notify session listeners of disconnection
	for (ProtocolMessageListener serviceListener :
		 serviceListeners.values())
	{
	    serviceListener.disconnected(sessionRefId.toByteArray());
	}
	handlers.remove(sessionRefId);
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
	TaskOwner owner =
	    (ownerIdentity == null) ?
	    taskOwner :
	    new TaskOwnerImpl(ownerIdentity, taskOwner.getContext());
	    
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
	    try {
		taskScheduler.runTransactionalTask(
		    new AbstractKernelRunnable() {
			public void run() {
			    notifyDisconnectedSessions(node.getId());
			}},
		    taskOwner);
		future.done();
	    } catch (Exception e) {
		logger.logThrow(
 		    Level.WARNING, e,
		    "notifying disconnected sessions for node:{0} throws",
		    node.getId());
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
		    dataService.getServiceBinding(
			sessionKey, ClientSessionImpl.class);
		sessionImpl.notifyListenerAndRemoveSession(dataService, false);
	    }
	}
    }
}
