package com.sun.sgs.impl.service.channel;

import com.sun.sgs.app.Channel;
import com.sun.sgs.app.ChannelListener;
import com.sun.sgs.app.ChannelManager;
import com.sun.sgs.app.Delivery;
import com.sun.sgs.app.NameNotBoundException;
import com.sun.sgs.app.TransactionNotActiveException;
import com.sun.sgs.impl.util.LoggerWrapper;
import com.sun.sgs.service.DataService;
import com.sun.sgs.service.NonDurableTransactionParticipant;
import com.sun.sgs.service.Service;
import com.sun.sgs.service.Transaction;
import com.sun.sgs.service.TransactionProxy;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Simple ChannelManager implementation.
 */
public class ChannelServiceImpl
    implements ChannelManager, Service, NonDurableTransactionParticipant
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

    //private TaskService taskService;

    /** The data service. */
    private DataService dataService;
    
    /**
     * Constructs an instance of this class with the specified properties.
     */
    public ChannelServiceImpl(Properties properties) {
	logger.log(Level.CONFIG, "Creating ChannelServiceImpl properties:{0}",
		   properties);
	try {
	    appName = properties.getProperty(APP_NAME_PROPERTY);
	    if (appName == null) {
		throw new IllegalArgumentException(
		    "The " + APP_NAME_PROPERTY +
		    " property must be specified");
	    }

	} catch (RuntimeException e) {
	    logger.log(Level.CONFIG, "Failed to create ChannelServiceImpl", e);
	    throw e;
	}
    }

    /**
     * Configures this service with the specified transaction proxy,
     * task service, and data service.
     *
     * @param	txnProxy the transaction proxy
     * @param	taskService the task service
     * @param	dataService the data service
     * @throws	IllegalStateException if this method has already been called
     */
    public void configure(TransactionProxy txnProxy,
			  // TaskService taskService,
			  DataService dataService)
    {
	if (txnProxy == null || dataService == null) {
	    throw new NullPointerException("null argument");
	}
	synchronized (lock) {
	    if (this.txnProxy != null) {
		throw new IllegalStateException("Already configured");
	    }
	    this.txnProxy = txnProxy;
	    //this.taskService = taskService;
	    this.dataService = dataService;
	}

	/*
	 * Create and store new channel table if one does not already
	 * exist in the data store.
	 */
	try {
	    dataService.getServiceBinding(ChannelTable.NAME, ChannelTable.class);
	} catch (NameNotBoundException e) {
	    dataService.setBinding(ChannelTable.NAME, new ChannelTable());
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
	    Context context = checkContext();
	    Channel channel = context.createChannel(name, listener, delivery);
	    if (logger.isLoggable(Level.FINEST)) {
		logger.log(
		    Level.FINEST, "createChannel name:{0} returns {1}",
		    name, channel);
	    }
	    return channel;
	    
	} catch (RuntimeException e) {
	    logger.logThrow(
		Level.FINEST, "createChannel name:{0} throws", e, name);
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
	    logger.logThrow(
		Level.FINEST, "getChannel name:{0} throws", e, name);
	    throw e;
	}
    }

    /* -- Implement Service -- */

    /** {@inheritDoc} */
    public String getName() {
	return toString();
    }


    /* -- Implement NonDurableTransactionParticipant -- */
       
    /** {@inheritDoc} */
    public boolean prepare(Transaction txn) throws Exception {
	try {
	    handleTransaction(txn, false);
	    if (logger.isLoggable(Level.FINE)) {
		logger.log(Level.FINER, "prepare txn:{0} returns {1}",
			   txn, true);
	    }
	    return true;
	} catch (RuntimeException e) {
	    logger.logThrow(Level.FINER, "prepare txn:{0} throws", e, txn);
	    throw e;
	}
    }

    /** {@inheritDoc} */
    public void commit(Transaction txn) {
	try {
	    handleTransaction(txn, true);
	    currentContext.set(null);
	    logger.log(Level.FINER, "commit txn:{0} returns", txn);
	} catch (RuntimeException e) {
	    logger.logThrow(Level.FINER, "commit txn:{0} throws", e, txn);
	    throw e;
	}
    }

    /** {@inheritDoc} */
    public void prepareAndCommit(Transaction txn) throws Exception {
	try {
	    handleTransaction(txn, true);
	    currentContext.set(null);
	    logger.log(Level.FINER, "prepareAndCommit txn:{0} returns", txn);
	} catch (RuntimeException e) {
	    logger.logThrow(
		Level.FINER, "prepareAndCommit txn:{0} throws", e, txn);
	    throw e;
	}
    }

    /** {@inheritDoc} */
    public void abort(Transaction txn) {
	try {
	    handleTransaction(txn, true);
	    logger.log(Level.FINER, "abort txn:{0} returns", txn);
	} catch (RuntimeException e) {
	    logger.logThrow(Level.FINER, "abort txn:{0} throws", e, txn);
	    throw e;
	}
    }

    /* -- other methods -- */

    /**
     * Checks the specified transaction, throwing IllegalStateException
     * if the current context is null or if the specified transaction is
     * not equal to the transaction in the current context. If
     * 'cancelContext' is 'true' or if the specified transaction does
     * not match the current context's transaction, then sets the
     * current context to null.
     */
    private void handleTransaction(Transaction txn, boolean cancelContext) {
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
	if (cancelContext) {
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
	    logger.log(Level.FINER, "join txn:{0}", txn);
	    txn.join(this);
	    context = new Context(dataService, txn);
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
     * Checks that the specified context is currently active, throwing
     * TransactionNotActiveException if it isn't.
     */
    static void checkContext(Context context) {
	if (context != currentContext.get()) {
	    throw new TransactionNotActiveException(
		"No transaction is active");
	}
    }
}
