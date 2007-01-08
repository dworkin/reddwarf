package com.sun.sgs.impl.service.session;

import com.sun.sgs.app.Delivery;
import com.sun.sgs.app.TransactionNotActiveException;
import com.sun.sgs.auth.IdentityManager;
import com.sun.sgs.impl.io.AcceptorFactory;
import com.sun.sgs.impl.io.CompleteMessageFilter;
import com.sun.sgs.impl.io.PassthroughFilter;
import com.sun.sgs.impl.io.IOConstants.TransportType;
import com.sun.sgs.impl.util.HexDumper;
import com.sun.sgs.impl.util.LoggerWrapper;
import com.sun.sgs.impl.util.MessageBuffer;
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
import com.sun.sgs.service.NonDurableTransactionParticipant;
import com.sun.sgs.service.ServiceListener;
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
    
    /** Provides transaction and other information for the current thread. */
    private static final ThreadLocal<Context> currentContext =
        new ThreadLocal<Context>();

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
        if (! prepare(txn)) {
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

    /* -- other methods -- */

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
     * Obtains information associated with the current transaction, throwing a
     * TransactionNotActiveException exception if there is no current
     * transaction, and throwing IllegalStateException if there is a problem
     * with the state of the transaction or if this service has not been
     * configured with a transaction proxy.
     */
    private Context checkContext() {
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

    void addCommitBuffer(ClientSessionImpl session, byte[] message,
            Delivery delivery)
    {
        checkContext().getSendTask().appendMessage(session, message, delivery);
    }
    
    void addCommitBuffer(ClientSessionImpl session, Callable<byte[]> message,
            Delivery delivery)
    {
        checkContext().getSendTask().appendMessage(session, message, delivery);
    }
    
    static final class Context {
        /** The transaction. */
        private final Transaction txn;
        private SendTask sendTask;
        
        Context(Transaction txn) {
            this.txn = txn;
            this.sendTask = null;
        }
        
        boolean prepare() {
            if (sendTask != null) {
                sendTask.prepare();
            }
            return true;
        }
        
        SendTask getSendTask() {
            if (sendTask == null) {
                sendTask = new SendTask();
                txnProxy.getService(TaskService.class)
                    .scheduleNonDurableTask(sendTask);
            }
            return sendTask;
        }
    }
    
    static final class SendTask implements KernelRunnable {

        private final Map<ClientSessionImpl, List<Callable<byte[]>>>
            sessionMessages;
        private boolean prepared;

        SendTask() {
            sessionMessages =
                new HashMap<ClientSessionImpl, List<Callable<byte[]>>>();
            prepared = false;
        }

        void appendMessage(ClientSessionImpl session, final byte[] message,
                Delivery delivery)
        {
            if (prepared) {
                RuntimeException e =
                    new IllegalStateException("SendTask is locked");
                if (logger.isLoggable(Level.FINE)) {
                    logger.logThrow(Level.FINE, e,
                            "Already locked while adding {0}, {1}",
                            session, HexDumper.format(message));
                }
                throw e;
            }
            appendMessage(session, new Callable<byte[]>() {

                public byte[] call() throws Exception {
                    return message;
                }
                
            }, delivery);
        }
        
        void appendMessage(ClientSessionImpl session, Callable<byte[]> message,
                Delivery delivery)
        {
            if (prepared) {
                RuntimeException e =
                    new IllegalStateException("SendTask is already prepared");
                if (logger.isLoggable(Level.FINE)) {
                    logger.logThrow(Level.FINE, e,
                            "Already prepared while adding {0}, {1}",
                            session, message);
                }
                throw e;
            }
            List<Callable<byte[]>> messages = sessionMessages.get(session);
            if (messages == null) {
                messages = new ArrayList<Callable<byte[]>>();
                sessionMessages.put(session, messages);
            }

            if (logger.isLoggable(Level.FINEST)) {
                logger.log(Level.FINEST,
                        "adding {0}, {1}",
                        session, message);
            }
            messages.add(message);
        }
        
        void prepare() {
            if (prepared) {
                RuntimeException e =
                    new IllegalStateException("SendTask was already prepared");
                logger.logThrow(Level.WARNING, e, "Already prepared");
                throw e;
            }
            prepared = true;
        }

        public void run() throws Exception {
            if (!prepared) {
                RuntimeException e =
                    new IllegalStateException(
                            "prepare() must be called before run()");
                logger.logThrow(Level.FINE, e, "Not yet prepared");
                throw e;
            }
            for (Map.Entry<ClientSessionImpl, List<Callable<byte[]>>> entry
                 : sessionMessages.entrySet())
            {
                ClientSessionImpl session = entry.getKey();
                List<Callable<byte[]>> messages = entry.getValue();
                // TODO it'd be nice to have IOHandle do gathering writes -JM
                for (Callable<byte[]> message : messages) {
                   session.sendMessage(message.call(), Delivery.RELIABLE);
                }
            }
        }
    }
}
