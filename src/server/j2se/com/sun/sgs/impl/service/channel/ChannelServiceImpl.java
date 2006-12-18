package com.sun.sgs.impl.service.channel;

import com.sun.sgs.app.AppListener;
import com.sun.sgs.app.Channel;
import com.sun.sgs.app.ChannelListener;
import com.sun.sgs.app.ChannelManager;
import com.sun.sgs.app.Delivery;
import com.sun.sgs.app.NameNotBoundException;
import com.sun.sgs.app.TransactionNotActiveException;
import com.sun.sgs.impl.util.LoggerWrapper;
import com.sun.sgs.kernel.ComponentRegistry;
import com.sun.sgs.service.DataService;
import com.sun.sgs.service.NonDurableTransactionParticipant;
import com.sun.sgs.service.Service;
import com.sun.sgs.service.ServiceListener;
import com.sun.sgs.service.SgsClientSession;
//import com.sun.sgs.service.TaskService;
import com.sun.sgs.service.Transaction;
import com.sun.sgs.service.TransactionProxy;
import java.io.Serializable;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Simple ChannelService implementation.
 */
public class ChannelServiceImpl
    implements ChannelManager, NonDurableTransactionParticipant
{

    /** The property that specifies the application name. */
    public static final String APP_NAME_PROPERTY = "com.sun.sgs.app.name";

    /** The name of this class. */
    private static final String CLASSNAME = ChannelServiceImpl.class.getName();
    
    /** The logger for this class. */
    private static final LoggerWrapper logger =
	new LoggerWrapper(Logger.getLogger(CLASSNAME));

    /** Provides transaction and other information for the current thread. */
    private static final ThreadLocal<Context> currentContext =
	new ThreadLocal<Context>();
    
    /** The name of this application. */
    private final String appName;

    /** Synchronize on this object before accessing the txnProxy. */
    private final Object lock = new Object();
    
    /** The transaction proxy, or null if configure has not been called. */    
    private TransactionProxy txnProxy;

    /** List of tasks (e.g. for sending channel messages). */
    private List<Runnable> tasks =
	new LinkedList<Runnable>();

    /** Lock for task list. */
    private final Object tasksLock = new Object();

    /** Thread for handling send tasks. */
    private HandleTasksThread handleTasksThread;

    /** The data service. */
    private DataService dataService;

    /** The task service. */
    //private TaskService taskService;

    /**
     * Constructs an instance of this class with the specified properties.
     */
    public ChannelServiceImpl(Properties properties) {
	if (logger.isLoggable(Level.CONFIG)) {
	    logger.log(
		Level.CONFIG, "Creating ChannelServiceImpl properties:{0}",
		properties);
	}
	try {
	    appName = properties.getProperty(APP_NAME_PROPERTY);
	    if (appName == null) {
		throw new IllegalArgumentException(
		    "The " + APP_NAME_PROPERTY +
		    " property must be specified");
	    }

	} catch (RuntimeException e) {
	    if (logger.isLoggable(Level.CONFIG)) {
		logger.logThrow(
		    Level.CONFIG, e, "Failed to create ChannelServiceImpl");
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
	    logger.log(Level.CONFIG, "Configuring ChannelServiceImpl");
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
		this.txnProxy = proxy;
		dataService = registry.getComponent(DataService.class);
		//taskService = registry.getComponent(TaskService.class);
	    }

	    /*
	     * Create and store new channel table if one does not already
	     * exist in the data store.
	     */
	    try {
		dataService.getServiceBinding(
		    ChannelTable.NAME, ChannelTable.class);
	    } catch (NameNotBoundException e) {
		dataService.setServiceBinding(
		    ChannelTable.NAME, new ChannelTable());
	    }
	    
	} catch (RuntimeException e) {
	    if (logger.isLoggable(Level.CONFIG)) {
		logger.logThrow(
		    Level.CONFIG, e,
		    "Failed to configure ChannelServiceImpl");
	    }
	    throw e;
	}
    }

    /* -- Implement ChannelManager -- */

    /** {@inheritDoc} */
    public Channel createChannel(String name,
				 ChannelListener listener,
				 Delivery delivery)
    {
	try {
	    if (name == null) {
		throw new NullPointerException("null name");
	    }
	    if (listener != null && !(listener instanceof Serializable)) {
		throw new IllegalArgumentException("listener is not serializable");
	    }
	    Context context = checkContext();
	    Channel channel = context.createChannel(name, listener, delivery);
	    if (logger.isLoggable(Level.FINEST)) {
		logger.log(
		    Level.FINEST, "createChannel name:{0} returns {1}",
		    name, channel);
	    }
	    return channel;
	    
	} catch (RuntimeException e) {
	    if (logger.isLoggable(Level.FINEST)) {
		logger.logThrow(
		    Level.FINEST, e, "createChannel name:{0} throws", name);
	    }
	    throw e;
	}
    }

    /** {@inheritDoc} */
    public Channel getChannel(String name) {
	try {
	    if (name == null) {
		throw new NullPointerException("null name");
	    }
	    Context context = checkContext();
	    Channel channel = context.getChannel(name);
	    if (logger.isLoggable(Level.FINEST)) {
		logger.log(
		    Level.FINEST, "getChannel name:{0} returns {1}",
		    name, channel);
	    }
	    return channel;
	    
	} catch (RuntimeException e) {
	    if (logger.isLoggable(Level.FINEST)) {
		logger.logThrow(
		    Level.FINEST, e, "getChannel name:{0} throws", name);
	    }
	    throw e;
	}
    }

    /* -- Implement ServiceListener -- */

    /** {@inheritDoc} */
    public void receivedMessage(SgsClientSession session, byte[] message) {
    }

    /* -- Implement NonDurableTransactionParticipant -- */
       
    /** {@inheritDoc} */
    public boolean prepare(Transaction txn) throws Exception {
	try {
	    boolean prepared = true; // nothing to do on commit (yet).
	    handleTransaction(txn, prepared);
	    if (logger.isLoggable(Level.FINE)) {
		logger.log(Level.FINER, "prepare txn:{0} returns {1}",
			   txn, true);
	    }
	    
	    return true;
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
	    currentContext.set(null);
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
	try {
	    handleTransaction(txn, true);
	    currentContext.set(null);
	    if (logger.isLoggable(Level.FINER)) {
		logger.log(Level.FINER, "prepareAndCommit txn:{0} returns", txn);
	    }
	} catch (RuntimeException e) {
	    if (logger.isLoggable(Level.FINER)) {
		logger.logThrow(
		    Level.FINER, e, "prepareAndCommit txn:{0} throws", txn);
	    }
	    throw e;
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
	    context = new Context(dataService, this, txn);
	    currentContext.set(context);
	} else if (!txn.equals(context.txn)) {
	    currentContext.set(null);
	    throw new IllegalStateException(
		"Wrong transaction: Expected " + context.txn +
		", found " + txn);
	}
	return context;
    }

    static Context getContext() {
	return currentContext.get();
    }

    /**
     * Checks that the specified context is currently active, throwing
     * TransactionNotActiveException if it isn't.
     */
    static void checkContext(Context context) {
	if (context != currentContext.get()) {
	    throw new TransactionNotActiveException(
		"No transaction is active");
	}
    }

    /**
     * Adds a task to this instance's task queue.
     */
    void addTask(Runnable runnable) {
	synchronized (tasksLock) {
	    tasks.add(runnable);
	    tasksLock.notify();
	}
    }

    /**
     * Thread for executing this channel service's tasks.
     */
    private final class HandleTasksThread extends Thread {

	private boolean interrupted = false;
	
	HandleTasksThread() {
	    super("HandleTasksThread");
	    setDaemon(true);
	}

	public void interrupt() {
	    interrupted = true;
	}
	
	public void run() {
	    while (!interrupted) {
		try {
		    Runnable task;
		    synchronized (tasksLock) {
			if (tasks.isEmpty()) {
			    try {
				tasksLock.wait();
				continue;
			    } catch (InterruptedException e) {
				return;
			    }
			} else {
			    task = tasks.remove(0);
			}
		    }
		    try {
			if (logger.isLoggable(Level.FINEST)) {
			    logger.log(Level.FINEST, "Processing {0}", task);
			}
		    } catch (Throwable t) {
		    }

		    task.run();

		} catch (Throwable t) {
		    try {
			if (logger.isLoggable(Level.FINEST)) {
			    logger.logThrow(
			        Level.FINEST, t,
				"Problem during processing -- continuing");
			}
		    } catch (Throwable t2) {
		    }
		}
	    }
	};
    }
}
