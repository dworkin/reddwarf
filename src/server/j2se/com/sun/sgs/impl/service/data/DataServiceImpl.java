package com.sun.sgs.impl.service.data;

import com.sun.sgs.app.DataManager;
import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.ManagedReference;
import com.sun.sgs.app.NameNotBoundException;
import com.sun.sgs.app.TransactionNotActiveException;
import com.sun.sgs.impl.kernel.StandardProperties;
import com.sun.sgs.impl.service.data.store.DataStore;
import com.sun.sgs.impl.service.data.store.DataStoreImpl;
import com.sun.sgs.impl.util.LoggerWrapper;
import com.sun.sgs.impl.util.PropertiesWrapper;
import com.sun.sgs.kernel.ComponentRegistry;
import com.sun.sgs.service.DataService;
import com.sun.sgs.service.Service;
import com.sun.sgs.service.Transaction;
import com.sun.sgs.service.TransactionParticipant;
import com.sun.sgs.service.TransactionProxy;
import java.io.Serializable;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Provides an implementation of <code>DataService</code> based on {@link
 * DataStoreImpl}. <p>
 *
 * The {@linkplain DataServiceImpl#DataServiceImpl(Properties,
 * ComponentRegistry) constructor} supports the following properties:
 * <p>
 *
 * <ul>
 *
 * <li> <i>Key:</i> <code>{@value StandardProperties#APP_NAME}</code> <br>
 *	<i>No default &mdash; required</i> <br>
 *	Specifies the name of the application using this
 *	<code>DataService</code>. <p>
 *
 * <li> <i>Key:</i> <code>{@value #DEBUG_CHECK_INTERVAL_PROPERTY}</code> <br>
 *	<i>Default:</i> {@link Integer#MAX_VALUE} <br>
 *	Specifies the number of <code>DataService</code> operations to skip
 *	between checks of the consistency of the managed references table.
 *	Note that the number of operations is measured separately for each
 *	transaction.  This property is intended for use in debugging. <p>
 *
 * <li> <i>Key:</i> <code>{@value #DETECT_MODIFICATIONS_PROPERTY}</code> <br>
 *	<i>Default:</i> {@code true} <br>
 *	Specifies whether to automatically detect modifications to managed
 *	objects.  If set to something other than <code>true</code>, then
 *	applications need to call {@link DataManager#markForUpdate
 *	DataManager.markForUpdate} or {@link ManagedReference#getForUpdate
 *	ManagedReference.getForUpdate} for any modified objects to make sure
 *	that the modifications are recorded by the
 *	<code>DataService</code>. <p>
 *
 * </ul> <p>
 *
 * The constructor also passes the properties to the {@link DataStoreImpl}
 * constructor, which supports additional properties. <p>
 *
 * This class uses the {@link Logger} named
 * <code>com.sun.sgs.impl.service.data.DataServiceImpl</code> to log
 * information at the following logging levels: <p>
 *
 * <ul>
 * <li> {@link Level#SEVERE SEVERE} - Initialization failures
 * <li> {@link Level#CONFIG CONFIG} - Constructor properties, data service
 *	headers
 * <li> {@link Level#FINER FINER} - Transaction operations
 * <li> {@link Level#FINEST FINEST} - Name and object operations
 * </ul> <p>
 *
 * Instances of {@link ManagedReference} returned by the {@link
 * #createReference createReference} method use the <code>Logger</code> named
 * <code>com.sun.sgs.impl.service.data.ManagedReferenceImpl</code> to log
 * information at the following logging levels: <p>
 *
 * <ul>
 * <li> {@link Level#FINE FINE} - Managed reference table checks
 * <li> <code>FINEST</code> - Reference operations
 * </ul>
 */
public final class DataServiceImpl
    implements DataService, TransactionParticipant
{

    /** The name of this class. */
    private static final String CLASSNAME = DataServiceImpl.class.getName();

    /**
     * The property that specifies the number of operations to skip between
     * checks of the consistency of the managed references table.
     */
    private static final String DEBUG_CHECK_INTERVAL_PROPERTY =
	CLASSNAME + ".debugCheckInterval";

    /**
     * The property that specifies whether to automatically detect
     * modifications to objects.
     */
    private static final String DETECT_MODIFICATIONS_PROPERTY =
	CLASSNAME + ".detectModifications";

    /** The logger for this class. */
    private static final LoggerWrapper logger =
	new LoggerWrapper(Logger.getLogger(CLASSNAME));

    /** Provides transaction and other information for the current thread. */
    private static final ThreadLocal<Context> currentContext =
	new ThreadLocal<Context>();

    /** The name of this application. */
    private final String appName;

    /** The underlying data store. */
    private final DataStore store;

    /**
     * Synchronize on this object before accessing the state, txnProxy,
     * debugCheckInterval, or detectModifications fields.
     */
    private final Object lock = new Object();

    /** The possible states of this instance. */
    enum State {
	/** Before configure has been called */
	UNINITIALIZED,
	/** After configure and before shutdown */
	RUNNING,
	/** After start of a call to shutdown and before call finishes */
	SHUTTING_DOWN,
	/** After shutdown has completed successfully */
	SHUTDOWN
    }

    /** The current state of this instance. */
    private State state = State.UNINITIALIZED;

    /** The transaction proxy, or null if configure has not been called. */
    TransactionProxy txnProxy;

    /**
     * A list of operations to run on abort, and clear on commit, or null if
     * there are no abort actions.
     */
    private List<Runnable> abortActions = null;

    /**
     * The number of operations to skip between checks of the consistency of
     * the managed reference table.
     */
    private int debugCheckInterval;

    /** Whether to detect object modifications automatically. */
    private boolean detectModifications;

    /**
     * Creates an instance of this class configured with the specified
     * properties and services.  See the {@link DataServiceImpl class
     * documentation} for the list of supported properties.
     *
     * @param	properties the properties for configuring this service
     * @param	componentRegistry the registry of configured {@link Service}
     *		instances
     * @throws	IllegalArgumentException if the <code>com.sun.sgs.app.name
     *		</code> property is not specified, if the value of the
     *		<code>com.sun.sgs.impl.service.data.debugCheckInterval</code>
     *		property is not a valid integer, or if the data store
     *		constructor detects an illegal property value
     */
    public DataServiceImpl(
	Properties properties, ComponentRegistry componentRegistry)
    {
	if (logger.isLoggable(Level.CONFIG)) {
	    logger.log(Level.CONFIG,
		       "Creating DataServiceImpl properties:{0}, " +
		       "componentRegistry:{1}",
		       properties, componentRegistry);
	}
	try {
	    PropertiesWrapper wrappedProps = new PropertiesWrapper(properties);
	    appName = wrappedProps.getProperty(StandardProperties.APP_NAME);
	    if (appName == null) {
		throw new IllegalArgumentException(
		    "The " + StandardProperties.APP_NAME +
		    " property must be specified");
	    }
	    if (componentRegistry == null) {
		throw new NullPointerException(
		    "The componentRegistry argument must not be null");
	    }
	    debugCheckInterval = wrappedProps.getIntProperty(
		DEBUG_CHECK_INTERVAL_PROPERTY, Integer.MAX_VALUE);
	    detectModifications = wrappedProps.getBooleanProperty(
		DETECT_MODIFICATIONS_PROPERTY, Boolean.TRUE);
	    store = new DataStoreImpl(properties);
	} catch (RuntimeException e) {
	    logger.logThrow(
		Level.SEVERE, e, "DataService initialization failed");
	    throw e;
	}
    }

    /* -- Implement Service -- */

     /** {@inheritDoc} */
    public String getName() {
	return toString();
    }

    /** {@inheritDoc} */
    public void configure(ComponentRegistry registry,
			  TransactionProxy proxy)
    {
	if (registry == null || proxy == null) {
	    throw new NullPointerException("The arguments must not be null");
	}
	synchronized (lock) {
	    if (state != State.UNINITIALIZED) {
		throw new IllegalStateException(
		    "Service is already configured");
	    }
	    state = State.RUNNING;
	    addAbortAction(
		new Runnable() {
		    public void run() {
			state = State.UNINITIALIZED;
		    }
		});
	    txnProxy = proxy;
	    DataServiceHeader header;
	    try {
		header = getServiceBinding(
		    CLASSNAME + ".header", DataServiceHeader.class);
		logger.log(Level.CONFIG, "Found existing header {0}", header);
	    } catch (NameNotBoundException e) {
		header = new DataServiceHeader(appName);
		setServiceBinding(CLASSNAME + ".header", header);
		logger.log(Level.CONFIG, "Created new header {0}", header);
	    }
	}
    }

    /* -- Implement DataManager -- */

    /** {@inheritDoc} */
    public <T> T getBinding(String name, Class<T> type) {
	return getBindingInternal(name, type, false);
    }

    /** {@inheritDoc} */
     public void setBinding(String name, ManagedObject object) {
	 setBindingInternal(name, object, false);
    }

    /** {@inheritDoc} */
    public void removeBinding(String name) {
	removeBindingInternal(name, false);
    }

    /** {@inheritDoc} */
    public String nextBoundName(String name) {
	return nextBoundNameInternal(name, false);
    }

    /** {@inheritDoc} */
    public void removeObject(ManagedObject object) {
	try {
	    if (object == null) {
		throw new NullPointerException("The object must not be null");
	    } else if (!(object instanceof Serializable)) {
		throw new IllegalArgumentException(
		    "The object must be serializable");
	    }
	    Context context = getContext();
	    ManagedReferenceImpl ref = context.findReference(object);
	    if (ref != null) {
		ref.removeObject();
	    }
	    logger.log(
		Level.FINEST, "removeObject object:{0} returns", object);
	} catch (RuntimeException e) {
	    logger.logThrow(
		Level.FINEST, e, "removeObject object:{0} throws", object);
	    throw e;
	}
    }

    /** {@inheritDoc} */
    public void markForUpdate(ManagedObject object) {
	try {
	    if (object == null) {
		throw new NullPointerException("The object must not be null");
	    } else if (!(object instanceof Serializable)) {
		throw new IllegalArgumentException(
		    "The object must be serializable");
	    }
	    Context context = getContext();
	    ManagedReferenceImpl ref = context.findReference(object);
	    if (ref != null) {
		ref.markForUpdate();
	    }
	    logger.log(
		Level.FINEST, "markForUpdate object:{0} returns", object);
	} catch (RuntimeException e) {
	    logger.logThrow(
		Level.FINEST, e, "markForUpdate object:{0} throws", object);
	    throw e;
	}
    }

    /** {@inheritDoc} */
    public ManagedReference createReference(ManagedObject object) {
	try {
	    if (object == null) {
		throw new NullPointerException("The object must not be null");
	    } else if (!(object instanceof Serializable)) {
		throw new IllegalArgumentException(
		    "The object must be serializable");
	    }
	    Context context = getContext();
	    ManagedReference result = context.getReference(object);
	    if (logger.isLoggable(Level.FINEST)) {
		logger.log(
		    Level.FINEST, "createReference object:{0} returns {1}",
		    object, result);
	    }
	    return result;
	} catch (RuntimeException e) {
	    logger.logThrow(
		Level.FINEST, e, "createReference object:{0} throws", object);
	    throw e;
	}
    }

    /* -- Implement DataService -- */

    /** {@inheritDoc} */
    public <T> T getServiceBinding(String name, Class<T> type) {
	return getBindingInternal(name, type, true);
    }

    /** {@inheritDoc} */
     public void setServiceBinding(String name, ManagedObject object) {
	 setBindingInternal(name, object, true);
    }

    /** {@inheritDoc} */
    public void removeServiceBinding(String name) {
       removeBindingInternal(name, true);
    }

    /** {@inheritDoc} */
    public String nextServiceBoundName(String name) {
	return nextBoundNameInternal(name, true);
    }

    /* -- Implement TransactionParticipant -- */

    /** {@inheritDoc} */
    public boolean prepare(Transaction txn) throws Exception {
	try {
	    Context context = getContext(txn);
	    boolean result = context.prepare();
	    if (result) {
		currentContext.set(null);
		abortActions = null;
	    }
	    if (logger.isLoggable(Level.FINER)) {
		logger.log(Level.FINER, "prepare txn:{0} returns {1}",
			   txn, result);
	    }
	    return result;
	} catch (RuntimeException e) {
	    logger.logThrow(Level.FINER, e, "prepare txn:{0} throws", txn);
	    throw e;
	}
    }

    /** {@inheritDoc} */
    public void commit(Transaction txn) {
	try {
	    Context context = getContext(txn);
	    currentContext.set(null);
	    abortActions = null;
	    context.commit();
	    logger.log(Level.FINER, "commit txn:{0} returns", txn);
	} catch (RuntimeException e) {
	    logger.logThrow(Level.WARNING, e, "commit txn:{0} throws", txn);
	    throw e;
	}
    }

    /** {@inheritDoc} */
    public void prepareAndCommit(Transaction txn) throws Exception {
	try {
	    Context context = getContext(txn);
	    context.prepareAndCommit();
	    abortActions = null;
	    currentContext.set(null);
	    logger.log(Level.FINER, "prepareAndCommit txn:{0} returns", txn);
	} catch (RuntimeException e) {
	    logger.logThrow(
		Level.FINER, e, "prepareAndCommit txn:{0} throws", txn);
	    throw e;
	}
    }

    /** {@inheritDoc} */
    public void abort(Transaction txn) {
	try {
	    Context context = getContext(txn);
	    currentContext.set(null);
	    if (abortActions != null) {
		for (Runnable action : abortActions) {
		    action.run();
		}
		abortActions = null;
	    }
	    context.abort();
	    logger.log(Level.FINER, "abort txn:{0} returns", txn);
	} catch (RuntimeException e) {
	    logger.logThrow(Level.WARNING, e, "abort txn:{0} throws", txn);
	    throw e;
	}
    }

    /* -- Generic binding methods -- */

    /** Implement getBinding and getServiceBinding. */
    private <T> T getBindingInternal(
	 String name, Class<T> type, boolean serviceBinding)
    {
	try {
	    if (name == null || type == null) {
		throw new NullPointerException(
		    "The arguments must not be null");
	    }
	    Context context = getContext();
	    T result;
	    try {
		result = context.getBinding(
		    getInternalName(name, serviceBinding), type);
	    } catch (NameNotBoundException e) {
		throw new NameNotBoundException(
		    "Name '" + name + "' is not bound");
	    }
	    if (logger.isLoggable(Level.FINEST)) {
		logger.log(
		    Level.FINEST, "{0} name:{1}, type:{2} returns {3}",
		    serviceBinding ? "getServiceBinding" : "getBinding",
		    name, type, result);
	    }
	    return result;
	} catch (RuntimeException e) {
	    if (logger.isLoggable(Level.FINEST)) {
		logger.logThrow(
		    Level.FINEST, e, "{0} name:{1}, type:{2} throws",
		    serviceBinding ? "getServiceBinding" : "getBinding",
		    name, type);
	    }
	    throw e;
	}
    }

    /** Implement setBinding and setServiceBinding. */
    private void setBindingInternal(
	String name, ManagedObject object, boolean serviceBinding)
    {
	try {
	    if (name == null || object == null) {
		throw new NullPointerException(
		    "The arguments must not be null");
	    } else if (!(object instanceof Serializable)) {
		throw new IllegalArgumentException(
		    "The object must be serializable");
	    }
	    Context context = getContext();
	    context.setBinding(getInternalName(name, serviceBinding), object);
	    if (logger.isLoggable(Level.FINEST)) {
		logger.log(Level.FINEST, "{0} name:{1}, object:{2} returns",
			   serviceBinding ? "setServiceBinding" : "setBinding",
			   name, object);
	    }
	} catch (RuntimeException e) {
	    if (logger.isLoggable(Level.FINEST)) {
		logger.logThrow(
		    Level.FINEST, e, "{0} name:{1}, object:{2} throws",
		    serviceBinding ? "setServiceBinding" : "setBinding",
		    name, object);
	    }
	    throw e;
	}
    }

    /** Implement removeBinding and removeServiceBinding. */
    private void removeBindingInternal(String name, boolean serviceBinding) {
	try {
	    if (name == null) {
		throw new NullPointerException("The name must not be null");
	    }
	    Context context = getContext();
	    try {
		context.removeBinding(getInternalName(name, serviceBinding));
	    } catch (NameNotBoundException e) {
		throw new NameNotBoundException(
		    "Name '" + name + "' is not bound");
	    }
	    if (logger.isLoggable(Level.FINEST)) {
		logger.log(
		    Level.FINEST, "{0} name:{1} returns",
		    serviceBinding ? "removeServiceBinding" : "removeBinding",
		    name);
	    }
	} catch (RuntimeException e) {
	    if (logger.isLoggable(Level.FINEST)) {
		logger.logThrow(
		    Level.FINEST, e, "{0} name:{1} throws",
		    serviceBinding ? "removeServiceBinding" : "removeBinding",
		    name);
	    }
	    throw e;
	}
    }

    /** Implement nextBoundName and nextServiceBoundName. */
    private String nextBoundNameInternal(String name, boolean serviceBinding) {
	try {
	    Context context = getContext();
	    String result = getExternalName(
		context.nextBoundName(getInternalName(name, serviceBinding)),
		serviceBinding);
	    if (logger.isLoggable(Level.FINEST)) {
		logger.log(
		    Level.FINEST, "{0} name:{1} returns {2}",
		    serviceBinding ? "nextServiceBoundName" : "nextBoundName",
		    name, result);
	    }
	    return result;
	} catch (RuntimeException e) {
	    if (logger.isLoggable(Level.FINEST)) {
		logger.logThrow(
		    Level.FINEST, e, "{0} name:{1} throws",
		    serviceBinding ? "nextServiceBoundName" : "nextBoundName",
		    name);
	    }
	    throw e;
	}
    }

    /* -- Other public methods -- */

    /**
     * Returns a string representation of this instance.
     *
     * @return	a string representation of this instance
     */
    public String toString() {
	return "DataServiceImpl[appName:\"" + appName +
	    ", store:" + store + "\"]";
    }

    /**
     * Specifies the number of operations to skip between checks of the
     * consistency of the managed references table.
     *
     * @param	debugCheckInterval the number of operations to skip between
     *		checks of the consistency of the managed references table
     */
    public void setDebugCheckInterval(int debugCheckInterval) {
	synchronized (lock) {
	    this.debugCheckInterval = debugCheckInterval;
	}
    }

    /**
     * Specifies whether to automatically detect modifications to objects.
     *
     * @param	detectModifications whether to detect modifications
     */
    public void setDetectModifications(boolean detectModifications) {
	synchronized (lock) {
	    this.detectModifications = detectModifications;
	}
    }

    /* -- Other methods -- */

    /** 
     * Attempts to shut down this service, returning a value indicating whether
     * the attempt was successful.  The call will throw {@link
     * IllegalStateException} if a call to this method has already completed
     * with a return value of <code>true</code>. <p>
     *
     * This implementation will refuse to accept calls associated with
     * transactions that were not joined prior to the <code>shutdown</code>xs
     * call by throwing an <code>IllegalStateException</code>, and will wait
     * for already joined transactions to commit or abort before returning.  It
     * will also return <code>false</code> if {@link Thread#interrupt
     * Thread.interrupt} is called on a thread that is currently blocked within
     * a call to this method. <p>
     *
     * @return	<code>true</code> if the shut down was successful, else
     *		<code>false</code>
     * @throws	IllegalStateException if the <code>shutdown</code> method has
     *		already been called and returned <code>true</code>
     */
    public boolean shutdown() {
	synchronized (lock) {
	    while (state == State.SHUTTING_DOWN) {
		try {
		    lock.wait();
		} catch (InterruptedException e) {
		    return false;
		}
	    }
	    if (state == State.SHUTDOWN) {
		throw new IllegalStateException(
		    "Service is already shut down");
	    }
	    state = State.SHUTTING_DOWN;
	}
	boolean done = false;
	try {
	    if (store.shutdown()) {
		synchronized (lock) {
		    state = State.SHUTDOWN;
		    lock.notifyAll();
		}
		done = true;
		return true;
	    } else {
		return false;
	    }
	} finally {
	    if (!done) {
		synchronized (lock) {
		    state = State.RUNNING;
		    lock.notifyAll();
		}
	    }
	}
    }

    /**
     * Obtains information associated with the current transaction, throwing a
     * TransactionNotActiveException exception if there is no current
     * transaction, and throwing IllegalStateException if there is a problem
     * with the state of the transaction or if this service has not been
     * configured with a transaction proxy.  Joins the transaction if that has
     * not been done already.
     */
    private Context getContext() {
	Transaction txn;
	synchronized (lock) {
	    checkState();
	    txn = txnProxy.getCurrentTransaction();
	}
	Context context = currentContext.get();
	if (context == null) {
	    synchronized (lock) {
		if (state == State.SHUTTING_DOWN) {
		    throw new IllegalStateException(
			"Service is shutting down");
		}
	    }
	    logger.log(Level.FINER, "join txn:{0}", txn);
	    txn.join(this);
	    context = new Context(
		store, txn, txnProxy, debugCheckInterval, detectModifications);
	    currentContext.set(context);
	} else {
	    context.checkTxn(txn);
	}
	context.maybeCheckReferenceTable();
	return context;
    }

    /**
     * Checks that the specified transaction matches the current context, and
     * returns the current context.  Throws NullPointerException if the
     * transaction is null, and IllegalStateException if another or no
     * transaction has been joined.
     */
    private Context getContext(Transaction txn) {
	if (txn == null) {
	    throw new NullPointerException("The transaction must not be null");
	}
	Context context = currentContext.get();
	if (context == null) {
	    throw new IllegalStateException("Not joined");
	}
	synchronized (lock) {
	    checkState();
	}
	context.checkTxn(txn);
	context.maybeCheckReferenceTable();
	return context;
    }

    /** Checks that the current state is RUNNING or SHUTTING_DOWN. */
    @SuppressWarnings("fallthrough")
    private void checkState() {
	assert Thread.holdsLock(lock);
	switch (state) {
	case UNINITIALIZED:
	    throw new IllegalStateException("Service is not configured");
	case RUNNING:
	case SHUTTING_DOWN:
	    break;
	case SHUTDOWN:
	    throw new IllegalStateException("Service is shut down");
	default:
	    throw new AssertionError();
	}
    }

    /**
     * Checks that the specified context is currently active.  Throws
     * TransactionNotActiveException if there is no current transaction.
     * Otherwise, returns true if the current transaction matches the argument.
     */
    static boolean checkContext(Context context) {
	context.txnProxy.getCurrentTransaction();
	boolean result = (context == currentContext.get());
	context.maybeCheckReferenceTable();
	return result;
    }

    /**
     * Obtains the currently active context, throwing
     * TransactionNotActiveException if none is active.  Does not join the
     * transaction.
     */
    static Context getContextNoJoin() {
	Context context = currentContext.get();
	if (context == null) {
	    throw new TransactionNotActiveException(
		"No transaction is active");
	}
	context.maybeCheckReferenceTable();
	return context;
    }

    /**
     * Returns the name that should be used for a service or application
     * binding.  If name is null, then returns a name that will sort earlier
     * than any non-null name.
     */
    private static String getInternalName(
	String name, boolean serviceBinding)
    {
	if (name == null) {
	    return serviceBinding ? "s" : "a";
	} else {
	    return (serviceBinding ? "s." : "a.") + name;
	}
    }

    /**
     * Returns the external name for a service or application binding name.
     * Returns null if the name does not have the proper prefix, or is null.
     */
    private static String getExternalName(
	String name, boolean serviceBinding)
    {
	if (name == null) {
	    return null;
	}
	String prefix = serviceBinding ? "s." : "a.";
	/*
	 * If this is an application binding, then the name could start with
	 * "s." if we've moved past all of the application bindings.
	 * Otherwise, the prefix should be correct.  -tjb@sun.com (12/14/2006)
	 */
	assert name.startsWith(prefix) ||
	    (!serviceBinding && name.startsWith("s."))
	    : "Name has wrong prefix";
	return name.startsWith(prefix) ? name.substring(2) : null;
    }	    

    /**
     * Adds an action to be performed on abort, to roll back transient state
     * changes.
     */
    private void addAbortAction(Runnable action) {
	if (abortActions == null) {
	    abortActions = new LinkedList<Runnable>();
	}
	abortActions.add(action);
    }
}
