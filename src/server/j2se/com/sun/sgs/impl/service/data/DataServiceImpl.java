/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.impl.service.data;

import com.sun.sgs.app.DataManager;
import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.ManagedReference;
import com.sun.sgs.app.NameNotBoundException;
import com.sun.sgs.app.TransactionNotActiveException;
import com.sun.sgs.impl.kernel.StandardProperties;
import com.sun.sgs.impl.service.data.store.DataStore;
import com.sun.sgs.impl.service.data.store.DataStoreImpl;
import com.sun.sgs.impl.service.data.store.DataStoreImpl.Scheduler;
import com.sun.sgs.impl.service.data.store.DataStoreImpl.TaskHandle;
import com.sun.sgs.impl.sharedutil.LoggerWrapper;
import com.sun.sgs.impl.sharedutil.PropertiesWrapper;
import com.sun.sgs.impl.util.TransactionContextFactory;
import com.sun.sgs.kernel.ComponentRegistry;
import com.sun.sgs.kernel.KernelRunnable;
import com.sun.sgs.kernel.ProfileProducer;
import com.sun.sgs.kernel.ProfileRegistrar;
import com.sun.sgs.kernel.RecurringTaskHandle;
import com.sun.sgs.kernel.TaskOwner;
import com.sun.sgs.kernel.TaskScheduler;
import com.sun.sgs.service.DataService;
import com.sun.sgs.service.Service;
import com.sun.sgs.service.Transaction;
import com.sun.sgs.service.TransactionParticipant;
import com.sun.sgs.service.TransactionProxy;
import java.io.Serializable;
import java.math.BigInteger;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Properties;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Provides an implementation of <code>DataService</code> based on {@link
 * DataStoreImpl}. <p>
 *
 * The {@link #DataServiceImpl constructor} supports the following properties:
 * <p>
 *
 * <ul>
 *
 * <li> <i>Key:</i> <code>com.sun.sgs.app.name</code> <br>
 *	<i>No default &mdash; required</i> <br>
 *	Specifies the name of the application using this
 *	<code>DataService</code>. <p>
 *
 * <li> <i>Key:</i> <code>
 *	com.sun.sgs.impl.service.data.DataServiceImpl.debug.check.interval
 *	</code> <br>
 *	<i>Default:</i> <code>Integer.MAX_VALUE</code> <br>
 *	Specifies the number of <code>DataService</code> operations to skip
 *	between checks of the consistency of the managed references table.
 *	Note that the number of operations is measured separately for each
 *	transaction.  This property is intended for use in debugging. <p>
 *
 * <li> <i>Key:</i> <code>
 *	com.sun.sgs.impl.service.data.DataServiceImpl.detect.modifications
 *	</code> <br>
 *	<i>Default:</i> <code>true</code> <br>
 *	Specifies whether to automatically detect modifications to managed
 *	objects.  If set to something other than <code>true</code>, then
 *	applications need to call {@link DataManager#markForUpdate
 *	DataManager.markForUpdate} or {@link ManagedReference#getForUpdate
 *	ManagedReference.getForUpdate} for any modified objects to make sure
 *	that the modifications are recorded by the
 *	<code>DataService</code>. <p>
 *
 * <li> <i>Key:</i> <code>
 *	com.sun.sgs.impl.service.data.DataServiceImpl.data.store.class
 *	</code> <br>
 *	<i>Default:</i>
 *	<code>com.sun.sgs.impl.service.data.store.DataStoreImpl</code> <br>
 *	The name of the class that implements {@link DataStore}.  The class
 *	should be public, not abstract, and should provide a public constructor
 *	with a {@link Properties} parameter.
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
 * <li> {@link Level#FINE FINE} - Task scheduling operations
 * <li> {@link Level#FINER FINER} - Transaction operations
 * <li> {@link Level#FINEST FINEST} - Name and object operations
 * </ul> <p>
 *
 * It also uses an additional {@code Logger} named {@code
 * com.sun.sgs.impl.service.data.DataServiceImpl.detect.modifications} to log
 * information about managed objects that are found to be modified but were not
 * marked for update.  Note that this logging output will only be performed if
 * the {@code
 * com.sun.sgs.impl.service.data.DataServiceImpl.detect.modifications} property
 * is {@code true}. <p>
 *
 * <ul>
 * <li> {@code FINEST} - Modified object was not marked for update
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
public final class DataServiceImpl implements DataService, ProfileProducer {

    /** The name of this class. */
    private static final String CLASSNAME = DataServiceImpl.class.getName();

    /**
     * The property that specifies the number of operations to skip between
     * checks of the consistency of the managed references table.
     */
    private static final String DEBUG_CHECK_INTERVAL_PROPERTY =
	CLASSNAME + ".debug.check.interval";

    /**
     * The property that specifies whether to automatically detect
     * modifications to objects.
     */
    private static final String DETECT_MODIFICATIONS_PROPERTY =
	CLASSNAME + ".detect.modifications";

    /**
     * The property that specifies the name of the class that implements
     * DataStore.
     */
    private static final String DATA_STORE_CLASS_PROPERTY =
	CLASSNAME + ".data.store.class";

    /** The logger for this class. */
    private static final LoggerWrapper logger =
	new LoggerWrapper(Logger.getLogger(CLASSNAME));

    /** Synchronize on this object when accessing the txnProxy field. */
    private static final Object txnProxyLock = new Object();

    /** The transaction proxy, or null if configure has not been called. */
    private static TransactionProxy txnProxy = null;

    /** The name of this application. */
    private final String appName;

    /** Scheduler supplied to the data store. */
    private final DelegatingScheduler scheduler;

    /** The underlying data store. */
    private final DataStore store;

    /** Table that stores information about classes used in serialization. */
    private final ClassesTable classesTable;

    /**
     * Synchronize on this object before accessing the state,
     * debugCheckInterval, detectModifications, or contextFactory fields.
     */
    private final Object stateLock = new Object();

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

    /**
     * The number of operations to skip between checks of the consistency of
     * the managed reference table.
     */
    private int debugCheckInterval;

    /** Whether to detect object modifications automatically. */
    private boolean detectModifications;

    /** The transaction context factory. */
    private TransactionContextFactory<Context> contextFactory;

    /**
     * Defines the transaction context factory for this class.  The
     * customizations create a durable transaction participant, check the
     * reference table, and check the service state when checking a
     * transaction.
     */
    private final class ContextFactory
	extends TransactionContextFactory<Context>
    {
	ContextFactory(TransactionProxy txnProxy) {
	    super(txnProxy);
	}
	@Override
	public Context joinTransaction() {
	    Context context = super.joinTransaction();
	    context.maybeCheckReferenceTable();
	    return context;
	}
	@Override
	public Context getContext() {
	    Context context = super.getContext();
	    context.maybeCheckReferenceTable();
	    return context;
	}
	@Override
	public void checkContext(Context context) {
	    super.checkContext(context);
	    context.maybeCheckReferenceTable();
	}
	@Override
	protected Context createContext(Transaction txn) {
	    /* Prevent joining a new transaction during shutdown */
	    synchronized (stateLock) {
		if (state == State.SHUTTING_DOWN) {
		    throw new IllegalStateException(
			"Service is shutting down");
		}
	    }
	    return new Context(store, txn, debugCheckInterval,
			       detectModifications, classesTable);
	}
	@Override
	protected TransactionParticipant createParticipant() {
	    return new Participant();
	}
	@Override
	protected Context checkTransaction(Transaction txn) {
	    checkState();
	    Context context = super.checkTransaction(txn);
	    context.maybeCheckReferenceTable();
	    return context;
	}
    }

    /**
     * Provides an implementation of Scheduler that uses the TaskScheduler, and
     * waits to schedule tasks until the task owner is supplied in a call to
     * setTaskOwner.
     */
    private static class DelegatingScheduler implements Scheduler {

	/** The task scheduler. */
	private TaskScheduler taskScheduler;

	/** The task owner, or null if not yet supplied. */
	private TaskOwner taskOwner;

	/**
	 * Handles for tasks that were scheduled before the task scheduler was
	 * supplied.
	 */
	private Set<Handle> pending = new HashSet<Handle>();

	DelegatingScheduler(TaskScheduler taskScheduler) {
	    this.taskScheduler = taskScheduler;
	}

	public synchronized TaskHandle scheduleRecurringTask(
	    Runnable task, long period)
	{
	    Handle handle = new Handle(task, period);
	    if (taskOwner != null) {
		handle.start();
	    } else {
		logger.log(Level.FINE, "Adding pending task {0}", handle);
		pending.add(handle);
	    }
	    return handle;
	}

	/**
	 * Supplies the task owner that will be used to schedule tasks, and
	 * schedules any tasks that were already provided.
	 */
	public synchronized void setTaskOwner(TaskOwner taskOwner) {
	    assert taskOwner != null;
	    this.taskOwner = taskOwner;
	    for (Iterator<Handle> i = pending.iterator(); i.hasNext(); ) {
		Handle handle = i.next();
		i.remove();
		handle.start();
	    }
	}

	/** Implementation of task handle. */
	private class Handle implements TaskHandle, KernelRunnable {
	    private final Runnable task;
	    private final long period;

	    /**
	     * The associated handle from the task handler, or null if not yet
	     * scheduled.
	     */
	    private RecurringTaskHandle handle;

	    Handle(Runnable task, long period) {
		this.task = task;
		this.period = period;
	    }

	    public String toString() {
		return "Handle[task:" + task + ", period:" + period + "]";
	    }

	    public String getBaseTaskType() {
		return task.getClass().getName();
	    }

	    public void run() {
		task.run();
	    }

	    public void cancel() {
		logger.log(Level.FINE, "Cancelling task {0}", this);
		synchronized (DelegatingScheduler.this) {
		    if (handle != null) {
			handle.cancel();
		    } else {
			pending.remove(this);
		    }
		}
	    }

	    /** Schedules a task using the task scheduler. */
	    private void start() {
		logger.log(Level.FINE, "Starting task {0}", this);
		assert Thread.holdsLock(DelegatingScheduler.this);
		handle = taskScheduler.scheduleRecurringTask(
		    this, taskOwner, System.currentTimeMillis() + period,
		    period);
		handle.start();
	    }
	}
    }

    /**
     * Creates an instance of this class configured with the specified
     * properties and services.  See the {@link DataServiceImpl class
     * documentation} for the list of supported properties.
     *
     * @param	properties the properties for configuring this service
     * @param	componentRegistry the registry of configured {@link Service}
     *		instances
     * @throws	IllegalArgumentException if the <code>com.sun.sgs.app.name
     *		</code> property is not specified, if the value of the <code>
     *		com.sun.sgs.impl.service.data.DataServiceImpl.debug.check.interval
     *		</code> property is not a valid integer, or if the data store
     *		constructor detects an illegal property value
     * @throws	Exception if a problem occurs creating the service
     */
    public DataServiceImpl(
	Properties properties, ComponentRegistry componentRegistry)
	throws Exception
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
	    String dataStoreClassName = wrappedProps.getProperty(
		DATA_STORE_CLASS_PROPERTY);
	    scheduler = new DelegatingScheduler(
		componentRegistry.getComponent(TaskScheduler.class));
	    if (dataStoreClassName == null) {
		store = new DataStoreImpl(properties, scheduler);
	    } else {
		store = wrappedProps.getClassInstanceProperty(
		    DATA_STORE_CLASS_PROPERTY, DataStore.class,
		    new Class[] { Properties.class }, properties);
		logger.log(Level.CONFIG, "Using data store {0}", store);
	    }
	    classesTable = new ClassesTable(store);
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
	synchronized (txnProxyLock) {
	    if (txnProxy == null) {
		txnProxy = proxy;
	    }
	    assert txnProxy == proxy;
	}
	synchronized (stateLock) {
	    if (state != State.UNINITIALIZED) {
		throw new IllegalStateException(
		    "Service is already configured");
	    }
	    state = State.RUNNING;
	    boolean addedAbortAction = false;
	    try {
		contextFactory = new ContextFactory(proxy);
		contextFactory.joinTransaction().addAbortAction(
		    new Runnable() {
			public void run() {
			    state = State.UNINITIALIZED;
			}
		    });
		addedAbortAction = true;
	    } finally {
		if (!addedAbortAction) {
		    state = State.UNINITIALIZED;
		}
	    }
	    scheduler.setTaskOwner(proxy.getCurrentOwner());
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

    /** {@inheritDoc} */
    public ManagedReference createReferenceForId(BigInteger id) {
	try {
	    if (id == null) {
		throw new NullPointerException("The id must not be null");
	    } else if (id.bitLength() > 63 || id.signum() < 0) {
		throw new IllegalArgumentException("The id is invalid: " + id);
	    }
	    ManagedReference result =
		getContext().getReference(id.longValue());
	    if (logger.isLoggable(Level.FINEST)) {
		logger.log(Level.FINEST,
			   "createReferenceForId id:{0} returns {1}",
			   id, result);
	    }
	    return result;
	} catch (RuntimeException e) {
	    logger.logThrow(
		Level.FINEST, e, "createReferenceForId id:{0} throws", id);
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
	synchronized (stateLock) {
	    this.debugCheckInterval = debugCheckInterval;
	}
    }

    /**
     * Specifies whether to automatically detect modifications to objects.
     *
     * @param	detectModifications whether to detect modifications
     */
    public void setDetectModifications(boolean detectModifications) {
	synchronized (stateLock) {
	    this.detectModifications = detectModifications;
	}
    }

    /* -- Implement ProfileProducer -- */

    /**
     * {@inheritDoc}
     */
    public void setProfileRegistrar(ProfileRegistrar profileRegistrar) {
        if (store instanceof ProfileProducer)
            ((ProfileProducer) store).setProfileRegistrar(profileRegistrar);
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
	synchronized (stateLock) {
	    while (state == State.SHUTTING_DOWN) {
		try {
		    stateLock.wait();
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
		synchronized (stateLock) {
		    state = State.SHUTDOWN;
		    stateLock.notifyAll();
		}
		done = true;
		return true;
	    } else {
		return false;
	    }
	} finally {
	    if (!done) {
		synchronized (stateLock) {
		    state = State.RUNNING;
		    stateLock.notifyAll();
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
	return getContextFactory().joinTransaction();
    }

    /**
     * Returns the transaction context factory, first checking the state of the
     * service.
     */
    private TransactionContextFactory<Context> getContextFactory() {
	synchronized (stateLock) {
	    checkState();
	    return contextFactory;
	}
    }

    /** Checks that the current state is RUNNING or SHUTTING_DOWN. */
    void checkState() {
	synchronized (stateLock) {
	    switch (state) {
	    case UNINITIALIZED:
		throw new IllegalStateException("Service is not configured");
	    case RUNNING:
		break;
	    case SHUTTING_DOWN:
		break;
	    case SHUTDOWN:
		throw new IllegalStateException("Service is shut down");
	    default:
		throw new AssertionError();
	    }
	}
    }

    /**
     * Checks that the specified context is currently active.  Throws
     * TransactionNotActiveException if there is no current transaction or if
     * the current transaction doesn't match the context.
     */
    static void checkContext(Context context) {
	getInstance().getContextFactory().checkContext(context);
    }

    /** Returns the current instance of this service. */
    private static DataServiceImpl getInstance() {
	synchronized (txnProxyLock) {
	    if (txnProxy == null) {
		throw new IllegalStateException("Service is not configured");
	    }
	}
	return txnProxy.getService(DataServiceImpl.class);
    }

    /**
     * Obtains the currently active context, throwing
     * TransactionNotActiveException if none is active.  Does not join the
     * transaction.
     */
    static Context getContextNoJoin() {
	return getInstance().getContextFactory().getContext();
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
}
