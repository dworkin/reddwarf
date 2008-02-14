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

import com.sun.sgs.app.ClientSessionId;
import com.sun.sgs.app.ClientSessionListener;
import com.sun.sgs.app.Delivery;
import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.TransactionNotActiveException;
import com.sun.sgs.auth.Identity;
import com.sun.sgs.auth.IdentityCoordinator;
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
import com.sun.sgs.io.AsynchronousMessageChannel;
import com.sun.sgs.kernel.ComponentRegistry;
import com.sun.sgs.kernel.KernelRunnable;
import com.sun.sgs.kernel.TaskScheduler;
import com.sun.sgs.nio.channels.AsynchronousChannelGroup;
import com.sun.sgs.nio.channels.AsynchronousServerSocketChannel;
import com.sun.sgs.nio.channels.AsynchronousSocketChannel;
import com.sun.sgs.nio.channels.CompletionHandler;
import com.sun.sgs.nio.channels.IoFuture;
import com.sun.sgs.nio.channels.spi.AsynchronousChannelProvider;
import com.sun.sgs.service.ClientSessionService;
import com.sun.sgs.service.DataService;
import com.sun.sgs.service.ProtocolMessageListener;
import com.sun.sgs.service.SgsClientSession;
import com.sun.sgs.service.TaskService;
import com.sun.sgs.service.Transaction;
import com.sun.sgs.service.TransactionProxy;
import com.sun.sgs.service.TransactionRunner;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;   
import java.util.Properties;
import java.util.Queue;
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

    /** The application name. */
    private final String appName;

    /** The port number for accepting connections. */
    private final int port;

    /** The async channel group for this service. */
    private final AsynchronousChannelGroup asyncChannelGroup;

    /** The acceptor for listening for new connections. */
    private final AsynchronousServerSocketChannel acceptor;

    volatile IoFuture<?, ?> acceptFuture;

    /** The registered service listeners. */
    private final Map<Byte, ProtocolMessageListener> serviceListeners =
	Collections.synchronizedMap(
	    new HashMap<Byte, ProtocolMessageListener>());

    /** A map of current sessions, from session ID to ClientSessionImpl. */
    private final Map<ClientSessionId, ClientSessionImpl> sessions =
	Collections.synchronizedMap(
	    new HashMap<ClientSessionId, ClientSessionImpl>());

    /** Queue of contexts that are prepared (non-readonly) or committed. */
    private final Queue<Context> contextQueue =
	new ConcurrentLinkedQueue<Context>();

    /** Thread for flushing committed contexts. */
    private final Thread flushContextsThread = new FlushContextsThread();
    
    /** Lock for notifying the thread that flushes commmitted contexts. */
    private final Object flushContextsLock = new Object();

    /** The task scheduler. */
    private final TaskScheduler taskScheduler;

    /** The task scheduler for non-durable tasks. */
    volatile NonDurableTaskScheduler nonDurableTaskScheduler;

    /** The transaction context factory. */
    private final TransactionContextFactory<Context> contextFactory;
    
    /** The data service. */
    final DataService dataService;

    /** The identity manager. */
    final IdentityCoordinator identityManager;

    /** The ID block size for the IdGenerator. */
    private final int idBlockSize;
    
    /** The IdGenerator. */
    private final IdGenerator idGenerator;

    /** If true, this service is shutting down; initially, false. */
    private boolean shuttingDown = false;

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
	if (logger.isLoggable(Level.CONFIG)) {
	    logger.log(
	        Level.CONFIG,
		"Creating ClientSessionServiceImpl properties:{0}",
		properties);
	}
	try {
	    if (systemRegistry == null) {
		throw new NullPointerException("null systemRegistry");
	    } else if (txnProxy == null) {
		throw new NullPointerException("null txnProxy");
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
		systemRegistry.getComponent(IdentityCoordinator.class);
	    flushContextsThread.start();

	    synchronized (ClientSessionServiceImpl.class) {
		if (ClientSessionServiceImpl.txnProxy == null) {
		    ClientSessionServiceImpl.txnProxy = txnProxy;
		} else {
		    assert ClientSessionServiceImpl.txnProxy == txnProxy;
		}
	    }
	    contextFactory = new ContextFactory(txnProxy);
	    dataService = txnProxy.getService(DataService.class);

	    idGenerator =
		new IdGenerator(ID_GENERATOR_NAME,
				idBlockSize,
				txnProxy,
				taskScheduler);

            InetSocketAddress endpoint = new InetSocketAddress(port);
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
                acceptor.bind(endpoint, 0);
                acceptFuture = acceptor.accept(new AcceptorListener());
                if (logger.isLoggable(Level.CONFIG)) {
                    logger.log(
                        Level.CONFIG,
                        "configure: listen successful. port:{0,number,#}",
                        getListenPort());
                }
            } catch (Exception e) {
                if (acceptFuture != null) {
                    acceptFuture.cancel(true);
                }
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
	    throw e;
	}
    }

    /* -- Implement Service -- */

    /** {@inheritDoc} */
    public String getName() {
	return toString();
    }
    
    /** {@inheritDoc} */
    public void ready() throws Exception { 
        // Update with the application owner
        nonDurableTaskScheduler =
		new NonDurableTaskScheduler(
		    taskScheduler, txnProxy.getCurrentOwner(),
		    txnProxy.getService(TaskService.class));
        
        taskScheduler.runTask(
		new TransactionRunner(
		    new AbstractKernelRunnable() {
			public void run() {
			    notifyDisconnectedSessions();
			}
		    }),
		txnProxy.getCurrentOwner(), true);
    }

    /**
     * Returns the port this service is listening on.
     *
     * @return the port this service is listening on
     */
    public int getListenPort() throws IOException {
	return ((InetSocketAddress) acceptor.getLocalAddress()).getPort();
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

	    try {
                acceptFuture.cancel(true);
	        acceptor.close();
                asyncChannelGroup.shutdown();
                if (! asyncChannelGroup.awaitTermination(1, TimeUnit.SECONDS)) {
                    logger.log(Level.WARNING, "forcing async group shutdown");
                    asyncChannelGroup.shutdownNow();
                }
	        logger.log(Level.FINEST, "acceptor shutdown");
	    } catch (IOException e) {
	        logger.logThrow(Level.FINEST, e, "shutdown exception occurred");
	        // swallow exception
	    } catch (InterruptedException e) {
                logger.logThrow(Level.FINEST, e, "shutdown interrupted");
                // TODO: re-interrupt the thread?
            }
        }

	for (ClientSessionImpl session : sessions.values()) {
	    session.shutdown();
	}
	sessions.clear();

	flushContextsThread.interrupt();
	
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

    /* -- Implement accept() handler -- */

    private class AcceptorListener
        implements CompletionHandler<AsynchronousSocketChannel, Void>
    {
        /**
         * {@inheritDoc}
         * <p>
         * Creates a new client session with the specified handle,
         * and adds the session to the internal session map.
         */
        public void
        completed(IoFuture<AsynchronousSocketChannel, Void> result)
        {
            try {
                AsynchronousSocketChannel newChannel = result.getNow();
                logger.log(Level.FINER, "Accepted {0}", newChannel);

                byte[] nextId;
                try {
                    nextId = idGenerator.nextBytes();
                } catch (Exception e) {
                    logger.logThrow(
                        Level.SEVERE, e,
                        "Failed to obtain client session ID, throws");
                    try {
                        newChannel.close();
                    } catch (IOException ioe) {
                        // ignore
                    }
                    acceptFuture = acceptor.accept(this);
                    return;
                }

                ClientSessionImpl session =
                    new ClientSessionImpl(
                        ClientSessionServiceImpl.this,
                        nextId);
                sessions.put(session.getSessionId(), session);
                session.connected(new AsynchronousMessageChannel(newChannel));

                acceptFuture = acceptor.accept(this);

            } catch (ExecutionException e) {
                logger.logThrow(
                    Level.SEVERE, e.getCause(),
                    "acceptor error on port: {0}", port);
                // TBD: take other actions, such as restarting acceptor?
            }
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
	void addReservation(ClientSessionImpl session, Object reservation) {
	    addReservation0(session, reservation, false);
	}

	/**
	 * Adds to the head of the list a message to be sent to the
	 * specified session after this transaction commits.
	 */
	void addReservationFirst(ClientSessionImpl session, Object reservation) {
	    addReservation0(session, reservation, true);
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

	private void addReservation0(
	    ClientSessionImpl session, Object reservation, boolean isFirst)
	{
	    try {
		if (logger.isLoggable(Level.FINEST)) {
		    logger.log(
			Level.FINEST,
			"Context.addMessage first:{0} session:{1}, rsvp:{2}",
			isFirst, session, reservation);
		}
		checkPrepared();

		Updates updates = getUpdates(session);
		if (isFirst) {
		    updates.reservations.add(0, reservation);
		} else {
		    updates.reservations.add(reservation);
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
	 * pending changes, adds this context to the context queue and
	 * returns {@code false}.  Otherwise, if there are no pending
	 * changes returns {@code true} indicating readonly status.
	 */
        public boolean prepare() {
	    isPrepared = true;
	    boolean readOnly = sessionUpdates.values().isEmpty();
	    if (! readOnly) {
		contextQueue.add(this);
	    }
            return readOnly;
        }

	/**
	 * Removes the context from the context queue containing
	 * pending updates, and checks for flushing committed contexts.
	 */
	public void abort(boolean retryable) {
	    contextQueue.remove(this);
	    for (Updates updates : sessionUpdates.values())
	        updates.cancel();
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
	List<Object> reservations = new ArrayList<Object>();

	/** If true, disconnect after sending messages. */
	boolean disconnect = false;

	Updates(ClientSessionImpl session) {
	    this.session = session;
	}

        private void cancel() {
            for (Object rsvp : reservations)
                session.cancelReservation(rsvp);
        }

        private void flush() {
	    for (Object rsvp : reservations) {
		session.invokeReservation(rsvp);
	    }
	    if (disconnect) {
		session.handleDisconnect(false);
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
    
    /* -- Other methods -- */

   /**
     * Obtains information associated with the current transaction,
     * throwing TransactionNotActiveException if there is no current
     * transaction, and throwing IllegalStateException if there is a
     * problem with the state of the transaction or if this service
     * has not been initialized with a transaction proxy.
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
	    throw new IllegalStateException("Service not initialized");
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
