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

package com.sun.sgs.impl.service.data;

import com.sun.sgs.app.DataManager;
import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.ManagedObjectRemoval;
import com.sun.sgs.app.ManagedReference;
import com.sun.sgs.app.NameNotBoundException;
import com.sun.sgs.app.TransactionAbortedException;
import com.sun.sgs.auth.Identity;
import com.sun.sgs.impl.kernel.StandardProperties;
import com.sun.sgs.impl.service.data.store.DataStore;
import com.sun.sgs.impl.service.data.store.DataStoreImpl;
import com.sun.sgs.impl.service.data.store.Scheduler;
import com.sun.sgs.impl.service.data.store.TaskHandle;
import com.sun.sgs.impl.service.data.store.net.DataStoreClient;
import com.sun.sgs.impl.sharedutil.LoggerWrapper;
import com.sun.sgs.impl.sharedutil.PropertiesWrapper;
import com.sun.sgs.impl.util.AbstractKernelRunnable;
import com.sun.sgs.impl.util.TransactionContextFactory;
import com.sun.sgs.impl.util.TransactionContextMap;
import com.sun.sgs.kernel.ComponentRegistry;
import com.sun.sgs.kernel.KernelRunnable;
import com.sun.sgs.kernel.RecurringTaskHandle;
import com.sun.sgs.kernel.TaskScheduler;
import com.sun.sgs.kernel.TransactionScheduler;
import com.sun.sgs.profile.ProfileCollector;
import com.sun.sgs.profile.ProfileProducer;
import com.sun.sgs.service.DataService;
import com.sun.sgs.service.Transaction;
import com.sun.sgs.service.TransactionParticipant;
import com.sun.sgs.service.TransactionProxy;
import java.io.Serializable;
import java.math.BigInteger;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Provides an implementation of <code>DataService</code> based on {@link
 * DataStoreImpl}. <p>
 *
 * The {@link #DataServiceImpl constructor} requires the <a
 * href="../../../app/doc-files/config-properties.html#com.sun.sgs.app.name">
 * <code>com.sun.sgs.app.name</code></a> property, and supports both these
 * public configuration <a
 * href="../../../app/doc-files/config-properties.html#DataService">
 * properties</a> and the following additional properties: <p>
 *
 * <dl style="margin-left: 1em">
 *
 * <dt> <i>Property:</i> <code><b>{@value #DATA_STORE_CLASS_PROPERTY}
 *	</b></code> <br>
 *	<i>Default:</i>
 *	<code>com.sun.sgs.impl.service.data.store.net.DataStoreClient</code> if
 *	the {@code com.sun.sgs.server.start} property is {@code false}, else
 *	<code>com.sun.sgs.impl.service.data.store.DataStoreImpl</code>
 *
 * <dd style="padding-top: .5em">The name of the class that implements {@link
 *	DataStore}.  The class should be public, not abstract, and should
 *	provide a public constructor with a {@link Properties} parameter. <p>
 *
 * <dt> <i>Property:</i> <code><b>{@value #DETECT_MODIFICATIONS_PROPERTY}
 *	</b></code> <br>
 *	<i>Default:</i> <code>true</code>
 *
 * <dd style="padding-top: .5em">Whether to automatically detect modifications
 *	to managed objects.  If set to something other than <code>true</code>,
 *	then applications need to call {@link DataManager#markForUpdate
 *	DataManager.markForUpdate} or {@link ManagedReference#getForUpdate
 *	ManagedReference.getForUpdate} for any modified objects to make sure
 *	that the modifications are recorded by the
 *	<code>DataService</code>. <p>
 *
 * <dt> <i>Property:</i> <code><b>{@value #DEBUG_CHECK_INTERVAL_PROPERTY}
 *	</b></code> <br>
 *	<i>Default:</i> <code>Integer.MAX_VALUE</code>
 *
 * <dd style="padding-top: .5em">The number of <code>DataService</code>
 *	operations to skip between checks of the consistency of the managed
 *	references table.  Note that the number of operations is measured
 *	separately for each transaction.  This property is intended for use in
 *	debugging. <p>
 *
 * </dl> <p>
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
 * <li> {@link Level#FINE FINE} - Task scheduling operations, managed reference
 *	table checks
 * <li> {@link Level#FINER FINER} - Transaction operations
 * <li> {@link Level#FINEST FINEST} - Name, object, and reference operations
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
 * In addition, operations that throw a {@link TransactionAbortedException}
 * will log the failure to the {@code Logger} named {@code
 * com.sun.sgs.impl.service.data.DataServiceImpl.abort}, to make it easier to
 * debug concurrency conflicts by just logging aborts.
 */
public final class DataServiceImpl implements DataService, ProfileProducer {

    /** The name of this class. */
    private static final String CLASSNAME = 
            "com.sun.sgs.impl.service.data.DataServiceImpl";

    /**
     * The property that specifies the number of operations to skip between
     * checks of the consistency of the managed references table.
     */
    public static final String DEBUG_CHECK_INTERVAL_PROPERTY =
	CLASSNAME + ".debug.check.interval";

    /**
     * The property that specifies whether to automatically detect
     * modifications to objects.
     */
    public static final String DETECT_MODIFICATIONS_PROPERTY =
	CLASSNAME + ".detect.modifications";

    /**
     * The property that specifies the name of the class that implements
     * DataStore.
     */
    public static final String DATA_STORE_CLASS_PROPERTY =
	CLASSNAME + ".data.store.class";

    /** The logger for this class. */
    static final LoggerWrapper logger =
	new LoggerWrapper(Logger.getLogger(CLASSNAME));

    /** The logger for transaction abort exceptions. */
    private static final LoggerWrapper abortLogger =
	new LoggerWrapper(Logger.getLogger(CLASSNAME + ".abort"));

    /** Synchronize on this object when accessing the contextMap field. */
    private static final Object contextMapLock = new Object();

    /**
     * The transaction context map, or null if configure has not been called.
     */
    private static TransactionContextMap<Context> contextMap = null;

    /** The name of this application. */
    private final String appName;

    /** Scheduler supplied to the data store. */
    private final Scheduler scheduler;

    /** The underlying data store. */
    private final DataStore store;

    /** Table that stores information about classes used in serialization. */
    private final ClassesTable classesTable;

    /** The transaction context factory. */
    private final TransactionContextFactory<Context> contextFactory;

    /**
     * Synchronize on this object before accessing the state,
     * debugCheckInterval, or detectModifications fields.
     */
    private final Object stateLock = new Object();

    /** The possible states of this instance. */
    enum State {
	/** Before constructor has completed */
	UNINITIALIZED,
	/** After construction and before shutdown */
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

    /**
     * Defines the transaction context map for this class.  This class checks
     * the service state and the reference table whenever the context is
     * accessed.  No check is needed for joinTransaction, though, because the
     * check is already being made when obtaining the context factory.
     */
    private static final class ContextMap
	extends TransactionContextMap<Context>
    {
	ContextMap(TransactionProxy proxy) {
	    super(proxy);
	}
	@Override public Context getContext() {
	    return check(super.getContext());
	}
	@Override public void checkContext(Context context) {
	    check(context);
	    super.checkContext(context);
	}
	@Override public Context checkTransaction(Transaction txn) {
	    return check(super.checkTransaction(txn));
	}
	private static Context check(Context context) {
	    context.checkState();
	    context.maybeCheckReferenceTable();
	    return context;
	}
    }

    /** Defines the transaction context factory for this class. */
    private final class ContextFactory
	extends TransactionContextFactory<Context>
    {
	ContextFactory(TransactionContextMap<Context> contextMap) {
	    super(contextMap, CLASSNAME);
	}
	@Override protected Context createContext(Transaction txn) {
	    /*
	     * Prevent joining a new transaction during shutdown, even though
	     * other operations will have been allowed to proceed.
	     */
	    synchronized (stateLock) {
		if (state == State.SHUTTING_DOWN) {
		    throw new IllegalStateException(
			"Service is shutting down");
		}
	    }
	    return new Context(
		DataServiceImpl.this, store, txn, debugCheckInterval,
		detectModifications, classesTable);
	}
	@Override protected TransactionParticipant createParticipant() {
	    /* Create a durable participant */
	    return new Participant();
	}
    }

    /**
     * Provides an implementation of Scheduler that uses the TaskScheduler and
     * TaskOwner.  Note that this class is created in the DataServiceImpl
     * constructor, so the TaskOwner used does not have access to managers or
     * to the full AppContext.
     */
    private static class DelegatingScheduler implements Scheduler {

	/** The task scheduler. */
	private final TaskScheduler taskScheduler;

	/** The task owner. */
	private final Identity taskOwner;

	DelegatingScheduler(TaskScheduler taskScheduler, Identity taskOwner) {
	    this.taskScheduler = taskScheduler;
	    this.taskOwner = taskOwner;
	}

	public TaskHandle scheduleRecurringTask(Runnable task, long period) {
	    return new Handle(task, period);
	}

	/** Implementation of task handle. */
	private class Handle implements TaskHandle, KernelRunnable {
	    private final Runnable task;
	    private final long period;
	    private final RecurringTaskHandle handle;

	    Handle(Runnable task, long period) {
		this.task = task;
		this.period = period;
		handle = taskScheduler.scheduleRecurringTask(
		    this, taskOwner, System.currentTimeMillis() + period,
		    period);
		handle.start();
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
		handle.cancel();
	    }
	}
    }

    /**
     * Creates an instance of this class configured with the specified
     * properties and services.  See the {@link DataServiceImpl class
     * documentation} for the list of supported properties.
     *
     * @param	properties the properties for configuring this service
     * @param	systemRegistry the registry of available system components
     * @param	txnProxy the transaction proxy
     * @throws	IllegalArgumentException if the <code>com.sun.sgs.app.name
     *		</code> property is not specified, if the value of the <code>
     *		com.sun.sgs.impl.service.data.DataServiceImpl.debug.check.interval
     *		</code> property is not a valid integer, or if the data store
     *		constructor detects an illegal property value
     * @throws	Exception if a problem occurs creating the service
     */
    public DataServiceImpl(Properties properties,
			   ComponentRegistry systemRegistry,
			   TransactionProxy txnProxy)
	throws Exception
    {
	if (logger.isLoggable(Level.CONFIG)) {
	    logger.log(Level.CONFIG,
		       "Creating DataServiceImpl properties:{0}, " +
		       "systemRegistry:{1}, txnProxy:{2}",
		       properties, systemRegistry, txnProxy);
	}
	try {
	    PropertiesWrapper wrappedProps = new PropertiesWrapper(properties);
	    appName = wrappedProps.getProperty(StandardProperties.APP_NAME);
	    if (appName == null) {
		throw new IllegalArgumentException(
		    "The " + StandardProperties.APP_NAME +
		    " property must be specified");
	    } else if (systemRegistry == null) {
		throw new NullPointerException(
		    "The systemRegistry argument must not be null");
	    } else if (txnProxy == null) {
		throw new NullPointerException(
		    "The txnProxy argument must not be null");
	    }
	    debugCheckInterval = wrappedProps.getIntProperty(
		DEBUG_CHECK_INTERVAL_PROPERTY, Integer.MAX_VALUE);
	    detectModifications = wrappedProps.getBooleanProperty(
		DETECT_MODIFICATIONS_PROPERTY, Boolean.TRUE);
	    String dataStoreClassName = wrappedProps.getProperty(
		DATA_STORE_CLASS_PROPERTY);
	    TaskScheduler taskScheduler =
		systemRegistry.getComponent(TaskScheduler.class);
	    Identity taskOwner = txnProxy.getCurrentOwner();
	    scheduler = new DelegatingScheduler(taskScheduler, taskOwner);
	    boolean serverStart = wrappedProps.getBooleanProperty(
		StandardProperties.SERVER_START, true);
	    if (dataStoreClassName != null) {
		store = wrappedProps.getClassInstanceProperty(
		    DATA_STORE_CLASS_PROPERTY, DataStore.class,
		    new Class[] { Properties.class }, properties);
		logger.log(Level.CONFIG, "Using data store {0}", store);
	    } else if (serverStart) {
		store = new DataStoreImpl(properties, scheduler);
	    } else {
		store = new DataStoreClient(properties);
	    }
	    classesTable = new ClassesTable(store);
	    synchronized (contextMapLock) {
		if (contextMap == null) {
		    contextMap = new ContextMap(txnProxy);
		}
	    }
	    contextFactory = new ContextFactory(contextMap);
	    synchronized (stateLock) {
		state = State.RUNNING;
	    }
	    systemRegistry.getComponent(TransactionScheduler.class).runTask(
		    new AbstractKernelRunnable() {
			public void run() {
			    DataServiceHeader header;
			    try {
				header = (DataServiceHeader) 
				    getServiceBinding(CLASSNAME + ".header");
				logger.log(Level.CONFIG,
					   "Found existing header {0}",
					   header);
			    } catch (NameNotBoundException e) {
				header = new DataServiceHeader(appName);
				setServiceBinding(
				    CLASSNAME + ".header", header);
				logger.log(Level.CONFIG,
					   "Created new header {0}", header);
			    }
			}
		    },
		taskOwner);
	} catch (RuntimeException e) {
	    getExceptionLogger(e).logThrow(
		Level.SEVERE, e, "DataService initialization failed");
	    throw e;
	} catch (Error e) {
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
    public void ready() { }

    /* -- Implement DataManager -- */

    /** {@inheritDoc} */
    public ManagedObject getBinding(String name) {
	return getBindingInternal(name, false);
    }

    /** {@inheritDoc} */
     public void setBinding(String name, Object object) {
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
    public void removeObject(Object object) {
	Context context = null;
	ManagedReferenceImpl<?> ref = null;
	try {
	    checkManagedObject(object);
	    context = getContext();
	    ref = context.findReference(object);
	    if (ref != null) {
		if (object instanceof ManagedObjectRemoval) {
		    context.removingObject((ManagedObjectRemoval) object);
		    /*
		     * Get the context again in case something changed as a
		     * result of the call to removingObject.
		     */
		    getContext();
		}
		ref.removeObject();
	    }
	    if (logger.isLoggable(Level.FINEST)) {
		logger.log(
		    Level.FINEST,
		    "removeObject tid:{0,number,#}, type:{1}," +
		    " oid:{2,number,#} returns",
		    contextTxnId(context), typeName(object), refId(ref));
	    }
	} catch (RuntimeException e) {
	    LoggerWrapper exceptionLogger = getExceptionLogger(e);
	    if (exceptionLogger.isLoggable(Level.FINEST)) {
		exceptionLogger.logThrow(
		    Level.FINEST, e,
		    "removeObject tid:{0,number,#}, type:{1}," +
		    " oid:{2,number,#} throws",
		    contextTxnId(context), typeName(object), refId(ref));
	    }
	    throw e;
	}
    }

    /** {@inheritDoc} */
    public void markForUpdate(Object object) {
	Context context = null;
	ManagedReferenceImpl<?> ref = null;
	try {
	    checkManagedObject(object);
	    context = getContext();
	    ref = context.findReference(object);
	    if (ref != null) {
		ref.markForUpdate();
	    }
	    if (logger.isLoggable(Level.FINEST)) {
		logger.log(
		    Level.FINEST,
		    "markForUpdate tid:{0,number,#}, type:{1}," +
		    " oid:{2,number,#} returns",
		    contextTxnId(context), typeName(object), refId(ref));
	    }
	} catch (RuntimeException e) {
	    LoggerWrapper exceptionLogger = getExceptionLogger(e);
	    if (exceptionLogger.isLoggable(Level.FINEST)) {
		exceptionLogger.logThrow(
		    Level.FINEST, e,
		    "markForUpdate tid:{0,number,#}, type:{1}," +
		    " oid:{2,number,#} throws",
		    contextTxnId(context), typeName(object), refId(ref));
	    }
	    throw e;
	}
    }

    /** {@inheritDoc} */
    public <T> ManagedReference<T> createReference(T object) {
	Context context = null;
	try {
	    checkManagedObject(object);
	    context = getContext();
	    ManagedReference<T> result = context.getReference(object);
	    if (logger.isLoggable(Level.FINEST)) {
		logger.log(
		    Level.FINEST,
		    "createReference tid:{0,number,#}, type:{1}" +
		    " returns oid:{2,number,#}",
		    contextTxnId(context), typeName(object), refId(result));
	    }
	    return result;
	} catch (RuntimeException e) {
	    LoggerWrapper exceptionLogger = getExceptionLogger(e);
	    if (exceptionLogger.isLoggable(Level.FINEST)) {
		exceptionLogger.logThrow(
		    Level.FINEST, e,
		    "createReference tid:{0,number,#}, type:{1} throws",
		    contextTxnId(context), typeName(object));
	    }
	    throw e;
	}
    }

    /* -- Implement DataService -- */

    /** {@inheritDoc} */
    public ManagedObject getServiceBinding(String name) {
	return getBindingInternal(name, true);
    }

    /** {@inheritDoc} */
     public void setServiceBinding(String name, Object object) {
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
    public ManagedReference<?> createReferenceForId(BigInteger id) {
	Context context = null;
	try {
	    context = getContext();
	    ManagedReference<?> result = context.getReference(getOid(id));
	    if (logger.isLoggable(Level.FINEST)) {
		logger.log(Level.FINEST,
			   "createReferenceForId tid:{0,number,#}," +
			   " oid:{1,number,#} returns",
			   contextTxnId(context), id);
	    }
	    return result;
	} catch (RuntimeException e) {
	    LoggerWrapper exceptionLogger = getExceptionLogger(e);
	    if (exceptionLogger.isLoggable(Level.FINEST)) {
		exceptionLogger.logThrow(
		    Level.FINEST, e,
		    "createReferenceForId tid:{0,number,#}," +
		    " oid:{1,number,#} throws",
		    contextTxnId(context), id);
	    }
	    throw e;
	}
    }

    /** {@inheritDoc} */
    public BigInteger nextObjectId(BigInteger objectId) {
	try {
	    long oid = (objectId == null) ? -1 : getOid(objectId);
	    Context context = getContext();
	    long nextOid = context.nextObjectId(oid);
	    BigInteger result =
		(nextOid == -1) ? null : BigInteger.valueOf(nextOid);
	    if (logger.isLoggable(Level.FINEST)) {
		logger.log(
		    Level.FINEST, "nextObjectId objectId:{0} returns {1}",
		    objectId, result);
	    }
	    return result;
	} catch (RuntimeException e) {
	    getExceptionLogger(e).logThrow(
		Level.FINEST, e, "nextObjectId objectId:{0} throws", objectId);
	    throw e;
	}
    }

    /* -- Generic binding methods -- */

    /** Implement getBinding and getServiceBinding. */
    private ManagedObject getBindingInternal(
	String name, boolean serviceBinding)
    {
	Context context = null;
	try {
	    if (name == null) {
		throw new NullPointerException("The name must not be null");
	    }
	    context = getContext();
	    ManagedObject result;
	    try {
		result = context.getBinding(
		    getInternalName(name, serviceBinding));
	    } catch (NameNotBoundException e) {
		throw new NameNotBoundException(
		    "Name '" + name + "' is not bound");
	    }
	    if (logger.isLoggable(Level.FINEST)) {
		logger.log(
		    Level.FINEST,
		    "{0} tid:{1,number,#}, name:{2} returns type:{3}," +
		    " oid:{4,number,#}",
		    serviceBinding ? "getServiceBinding" : "getBinding",
		    contextTxnId(context), name, typeName(result),
		    objectId(context, result));
	    }
	    return result;
	} catch (RuntimeException e) {
	    LoggerWrapper exceptionLogger = getExceptionLogger(e);
	    if (exceptionLogger.isLoggable(Level.FINEST)) {
		exceptionLogger.logThrow(
		    Level.FINEST, e,
		    "{0} tid:{1,number,#}, name:{2} throws",
		    serviceBinding ? "getServiceBinding" : "getBinding",
		    contextTxnId(context), name);
	    }
	    throw e;
	}
    }

    /** Implement setBinding and setServiceBinding. */
    private void setBindingInternal(
	String name, Object object, boolean serviceBinding)
    {
	Context context = null;
	try {
	    if (name == null) {
		throw new NullPointerException("The name must not be null");
	    }
	    checkManagedObject(object);
	    context = getContext();
	    context.setBinding(getInternalName(name, serviceBinding), object);
	    if (logger.isLoggable(Level.FINEST)) {
		logger.log(
		    Level.FINEST,
		    "{0} tid:{1,number,#}, name:{2}, type:{3}," +
		    " oid:{4,number,#} returns",
		    serviceBinding ? "setServiceBinding" : "setBinding",
		    contextTxnId(context), name, typeName(object),
		    objectId(context, object));
	    }
	} catch (RuntimeException e) {
	    LoggerWrapper exceptionLogger = getExceptionLogger(e);
	    if (exceptionLogger.isLoggable(Level.FINEST)) {
		exceptionLogger.logThrow(
		    Level.FINEST, e,
		    "{0} tid:{1,number,#}, name:{2}, type:{3}," +
		    " oid:{4,number,#} throws",
		    serviceBinding ? "setServiceBinding" : "setBinding",
		    contextTxnId(context), name, typeName(object),
		    objectId(context, object));
	    }
	    throw e;
	}
    }

    /** Implement removeBinding and removeServiceBinding. */
    private void removeBindingInternal(String name, boolean serviceBinding) {
	Context context = null;
	try {
	    if (name == null) {
		throw new NullPointerException("The name must not be null");
	    }
	    context = getContext();
	    try {
		context.removeBinding(getInternalName(name, serviceBinding));
	    } catch (NameNotBoundException e) {
		throw new NameNotBoundException(
		    "Name '" + name + "' is not bound");
	    }
	    if (logger.isLoggable(Level.FINEST)) {
		logger.log(
		    Level.FINEST, "{0} tid:{1,number,#}, name:{2} returns",
		    serviceBinding ? "removeServiceBinding" : "removeBinding",
		    contextTxnId(context), name);
	    }
	} catch (RuntimeException e) {
	    LoggerWrapper exceptionLogger = getExceptionLogger(e);
	    if (exceptionLogger.isLoggable(Level.FINEST)) {
		exceptionLogger.logThrow(
		    Level.FINEST, e, "{0} tid:{1,number,#}, name:{2} throws",
		    serviceBinding ? "removeServiceBinding" : "removeBinding",
		    contextTxnId(context), name);
	    }
	    throw e;
	}
    }

    /** Implement nextBoundName and nextServiceBoundName. */
    private String nextBoundNameInternal(String name, boolean serviceBinding) {
	Context context = null;
	try {
	    context = getContext();
	    String result = getExternalName(
		context.nextBoundName(getInternalName(name, serviceBinding)),
		serviceBinding);
	    if (logger.isLoggable(Level.FINEST)) {
		logger.log(
		    Level.FINEST, "{0} tid:{1,number,#}, name:{2} returns {3}",
		    serviceBinding ? "nextServiceBoundName" : "nextBoundName",
		    contextTxnId(context), name, result);
	    }
	    return result;
	} catch (RuntimeException e) {
	    LoggerWrapper exceptionLogger = getExceptionLogger(e);
	    if (exceptionLogger.isLoggable(Level.FINEST)) {
		exceptionLogger.logThrow(
		    Level.FINEST, e, "{0} tid:{1,number,#}, name:{2} throws",
		    serviceBinding ? "nextServiceBoundName" : "nextBoundName",
		    contextTxnId(context), name);
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
    public void setProfileRegistrar(ProfileCollector profileCollector) {
        if (store instanceof ProfileProducer)
            ((ProfileProducer) store).setProfileRegistrar(profileCollector);
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
	Context context = getContextFactory().joinTransaction();
	context.maybeCheckReferenceTable();
	return context;
    }

    /**
     * Returns the transaction context factory, first checking the state of the
     * service.
     */
    private TransactionContextFactory<Context> getContextFactory() {
	checkState();
	return contextFactory;
    }

    /**
     * Returns the transaction context map, checking to be sure it has been
     * initialized.
     */
    private static TransactionContextMap<Context> getContextMap() {
	synchronized (contextMapLock) {
	    if (contextMap == null) {
		throw new IllegalStateException("Service is not configured");
	    }
	    return contextMap;
	}
    }

    /** Checks that the current state is RUNNING or SHUTTING_DOWN. */
    void checkState() {
	synchronized (stateLock) {
	    switch (state) {
	    case UNINITIALIZED:
		throw new IllegalStateException("Service is not constructed");
	    case RUNNING:
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
	getContextMap().checkContext(context);
    }

    /**
     * Obtains the currently active context, throwing
     * TransactionNotActiveException if none is active.  Does not join the
     * transaction.
     */
    static Context getContextNoJoin() {
	return getContextMap().getContext();
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
     * Converts a BigInteger object ID into a long, throwing an exception if
     * the argument is invalid.
     */
    private static long getOid(BigInteger objectId) {
	if (objectId == null) {
	    throw new NullPointerException("The object ID must not be null");
	} else if (objectId.bitLength() > 63 || objectId.signum() < 0) {
	    throw new IllegalArgumentException(
		"The object ID is invalid: " + objectId);
	}
	return objectId.longValue();
    }

    /**
     * Returns the transaction ID associated with the context, or null if the
     * context is null.
     */
    private static BigInteger contextTxnId(Context context) {
	return (context != null) ? context.getTxnId() : null;
    }

    /**
     * Returns the object ID for the reference, or null if the reference is
     * null.
     */
    private static BigInteger refId(ManagedReference<?> ref) {
	return (ref != null) ? ref.getId() : null;
    }

    /** Returns the type name of the object. */
    static String typeName(Object object) {
	return (object == null) ? "null" : object.getClass().getName();
    }

    /**
     * Returns the object ID of the object, or null if the object is null or
     * not assigned an ID.  Returns an ID even if the object is removed.
     */
    private static BigInteger objectId(Context context, Object object) {
	return refId(context.safeFindReference(object));
    }

    /**
     * Checks that the argument is a legal managed object: non-null,
     * serializable, and implements ManagedObject.
     */
    private static void checkManagedObject(Object object) {
	if (object == null) {
	    throw new NullPointerException("The object must not be null");
	} else if (!(object instanceof Serializable)) {
	    throw new IllegalArgumentException(
		"The object must be serializable: " + object);
	} else if (!(object instanceof ManagedObject)) {
	    throw new IllegalArgumentException(
		"The object must implement ManagedObject: " + object);
	}
    }

    /**
     * Returns the logger that should be used to log the specified exception.
     * In particular, use the abortLogger for TransactionAbortedException, and
     * the class logger for other runtime exceptions.
     */
    static LoggerWrapper getExceptionLogger(RuntimeException exception) {
	return exception instanceof TransactionAbortedException
	    ? abortLogger : logger;
    }
}
