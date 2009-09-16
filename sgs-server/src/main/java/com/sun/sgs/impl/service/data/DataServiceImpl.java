/*
 * Copyright 2007-2009 Sun Microsystems, Inc.
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
import com.sun.sgs.app.TransactionNotActiveException;
import com.sun.sgs.impl.kernel.StandardProperties;
import com.sun.sgs.impl.service.data.store.DataStoreImpl;
import com.sun.sgs.impl.service.data.store.DataStoreProfileProducer;
import com.sun.sgs.impl.service.data.store.net.DataStoreClient;
import com.sun.sgs.impl.sharedutil.LoggerWrapper;
import com.sun.sgs.impl.sharedutil.PropertiesWrapper;
import com.sun.sgs.impl.util.AbstractKernelRunnable;
import com.sun.sgs.impl.util.TransactionContextFactory;
import com.sun.sgs.impl.util.TransactionContextMap;
import com.sun.sgs.kernel.ComponentRegistry;
import com.sun.sgs.kernel.NodeType;
import com.sun.sgs.kernel.TransactionScheduler;
import com.sun.sgs.profile.ProfileCollector;
import com.sun.sgs.service.DataService;
import com.sun.sgs.service.Transaction;
import com.sun.sgs.service.TransactionProxy;
import com.sun.sgs.service.store.DataStore;
import java.io.Serializable;
import java.math.BigInteger;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.management.JMException;

/**
 * Provides an implementation of <code>DataService</code> based on {@link
 * DataStoreImpl}. <p>
 *
 * The {@link #DataServiceImpl constructor} requires the <a
 * href="../../../impl/kernel/doc-files/config-properties.html#com.sun.sgs.app.name">
 * <code>com.sun.sgs.app.name</code></a> property, and supports both these
 * public configuration <a
 * href="../../../impl/kernel/doc-files/config-properties.html#DataService">
 * properties</a> and the following additional properties: <p>
 *
 * <dl style="margin-left: 1em">
 *
 * <dt> <i>Property:</i> <code><b>{@value #DATA_STORE_CLASS_PROPERTY}
 *	</b></code> <br>
 *	<i>Default:</i>
 *	<code>com.sun.sgs.impl.service.data.store.net.DataStoreClient</code> 
 *      unless the {@code com.sun.sgs.node.type} property is {@code singleNode},
 *      which defaults to 
 *      <code>com.sun.sgs.impl.service.data.store.DataStoreImpl</code>
 *
 * <dd style="padding-top: .5em">The name of the class that implements {@link
 *	DataStore}.  The class should be public, not abstract, and should
 *	provide a public constructor with {@link Properties}, {@link
 *	ComponentRegistry}, and {@link TransactionProxy} parameters. <p>
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
 * <dt> <i>Property:</i> <code><b>{@value #OPTIMISTIC_WRITE_LOCKS}
 *	</b></code><br>
 *	<i>Default:</i> <code>false</code>
 *
 * <dd style="padding-top: .5em">Whether to wait until commit time to obtain
 *	write locks.  If <code>false</code>, which is the default, the service
 *	acquires write locks as soon as it knows that an object is being
 *	modified.  If <code>true</code>, the service delays obtaining write
 *	locks until commit time, which may improve performance in some cases,
 *	typically when there is low contention.  Note that setting this flag to
 *	<code>true</code> does not delay write locks when removing objects.<p>
 *
 * <dt> <i>Property:</i> <code><b>{@value #TRACK_STALE_OBJECTS_PROPERTY}
 *	</b></code> <br>
 *	<i>Default:</i> <code>false</code>
 *
 * <dd style="padding-top: .5em">Whether to track references to stale managed
 *	objects.  If <code>true</code>, the <code>DataService</code> keeps
 *	track of persistent or removed objects from completed transactions and
 *	throws {@link TransactionNotActiveException} if the application refers
 *	to those objects from another transaction. <p>
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
public final class DataServiceImpl implements DataService {

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
     * {@link DataStore}.
     */
    public static final String DATA_STORE_CLASS_PROPERTY =
	CLASSNAME + ".data.store.class";

    /** The property that specifies to use optimistic write locking. */
    public static final String OPTIMISTIC_WRITE_LOCKS =
	CLASSNAME + ".optimistic.write.locks";

    /** The property that specifies whether to track stale objects. */
    public static final String TRACK_STALE_OBJECTS_PROPERTY =
	CLASSNAME + ".track.stale.objects";

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

    /** The underlying data store. */
    private final DataStore store;

    /** The local node ID. */
    private final long nodeId;

    /** Table that stores information about classes used in serialization. */
    private final ClassesTable classesTable;

    /** The transaction context factory. */
    private final TransactionContextFactory<Context> contextFactory;

    /** Whether to delay obtaining write locks. */
    final boolean optimisticWriteLocks;

    /** Whether to track stale objects. */
    private final boolean trackStaleObjects;

    /** The data service profiling information. */
    private final DataServiceStats serviceStats;
    
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
		detectModifications, classesTable, trackStaleObjects);
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
     *        </code> property is not specified, if the value of the <code>
     *        com.sun.sgs.impl.service.data.DataServiceImpl.debug.check.interval
     *	      </code> property is not a valid integer, or if the data store
     *	      constructor detects an illegal property value
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
	DataStore storeToShutdown = null;
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
	    optimisticWriteLocks = wrappedProps.getBooleanProperty(
		OPTIMISTIC_WRITE_LOCKS, Boolean.FALSE);
	    trackStaleObjects = wrappedProps.getBooleanProperty(
		TRACK_STALE_OBJECTS_PROPERTY, Boolean.FALSE);
            NodeType nodeType = 
                wrappedProps.getEnumProperty(StandardProperties.NODE_TYPE, 
                                             NodeType.class, 
                                             NodeType.singleNode);

	    DataStore baseStore;
	    if (dataStoreClassName != null) {
		baseStore = wrappedProps.getClassInstanceProperty(
		    DATA_STORE_CLASS_PROPERTY, DataStore.class,
		    new Class[] { Properties.class, ComponentRegistry.class,
				  TransactionProxy.class },
		    properties, systemRegistry, txnProxy);
		logger.log(Level.CONFIG, "Using data store {0}", baseStore);
	    } else if (nodeType == NodeType.singleNode) {
		baseStore = new DataStoreImpl(
		    properties, systemRegistry, txnProxy);
	    } else {
		baseStore = new DataStoreClient(
		    properties, systemRegistry, txnProxy);
	    }
            storeToShutdown = baseStore;
            ProfileCollector collector = 
		systemRegistry.getComponent(ProfileCollector.class);
	    store = new DataStoreProfileProducer(baseStore, collector);
	    nodeId = store.getLocalNodeId();
            
            // create our service profiling info and register our MBean
            serviceStats = new DataServiceStats(collector);
            try {
                collector.registerMBean(serviceStats,
                                        DataServiceStats.MXBEAN_NAME);
            } catch (JMException e) {
                logger.logThrow(Level.CONFIG, e, "Could not register MBean");
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
		    new AbstractKernelRunnable("BindDataServiceHeader") {
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
		    txnProxy.getCurrentOwner());
	    storeToShutdown = null;
	} catch (RuntimeException e) {
	    getExceptionLogger(e).logThrow(
		Level.SEVERE, e, "DataService initialization failed");
	    throw e;
	} catch (Error e) {
	    logger.logThrow(
		Level.SEVERE, e, "DataService initialization failed");
	    throw e;
	} finally {
	    if (storeToShutdown != null) {
		storeToShutdown.shutdown();
	    }
	}
    }

    /* -- Implement Service -- */

     /** {@inheritDoc} */
    public String getName() {
	return toString();
    }

    /** {@inheritDoc} */
    public void ready() throws Exception {
	store.ready();
    }

    /* -- Implement DataManager -- */

    /** {@inheritDoc} */
    public ManagedObject getBinding(String name) {
        serviceStats.getBindingOp.report();
	return getBindingInternal(name, false, false, "getBinding");
    }

    /** {@inheritDoc} */
    public ManagedObject getBindingForUpdate(String name) {
	serviceStats.getBindingForUpdateOp.report();
	return getBindingInternal(name, false, true, "getBindingForUpdate");
    }

    /** {@inheritDoc} */
     public void setBinding(String name, Object object) {
         serviceStats.setBindingOp.report();
	 setBindingInternal(name, object, false);
    }

    /** {@inheritDoc} */
    public void removeBinding(String name) {
        serviceStats.removeBindingOp.report();
	removeBindingInternal(name, false);
    }

    /** {@inheritDoc} */
    public String nextBoundName(String name) {
        serviceStats.nextBoundNameOp.report();
	return nextBoundNameInternal(name, false);
    }

    /** {@inheritDoc} */
    public void removeObject(Object object) {
        serviceStats.removeObjOp.report();
	Context context = null;
	ManagedReferenceImpl<?> ref = null;
	try {
	    checkManagedObject(object);
	    context = getContext();
	    ref = context.findReference(object);
	    if (object instanceof ManagedObjectRemoval) {
		context.removingObject((ManagedObjectRemoval) object);
		/*
		 * Get the context again in case something changed as a
		 * result of the call to removingObject.
		 */
		getContext();
	    }
	    if (ref != null) {
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
        serviceStats.markForUpdateOp.report();
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
        serviceStats.createRefOp.report();
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

    /** {@inheritDoc} */
    public BigInteger getObjectId(Object object) {
	serviceStats.getObjectIdOp.report();
	Context context = null;
	try {
	    checkManagedObject(object);
	    context = getContext();
	    BigInteger result = context.getReference(object).getId();
	    if (logger.isLoggable(Level.FINEST)) {
		logger.log(Level.FINEST,
			   "getObjectId tid:{0,number,#}, type:{1}" +
			   " returns oid:{2,number,#}",
			   contextTxnId(context), typeName(object), result);
	    }
	    return result;
	} catch (RuntimeException e) {
	    LoggerWrapper exceptionLogger = getExceptionLogger(e);
	    if (exceptionLogger.isLoggable(Level.FINEST)) {
		exceptionLogger.logThrow(
		    Level.FINEST, e,
		    "getObjectId tid:{0,number,#}, type:{1} throws",
		    contextTxnId(context), typeName(object));
	    }
	    throw e;
	}
    }

    /* -- Implement DataService -- */

    /** {@inheritDoc} */
    public long getLocalNodeId() {
	serviceStats.getLocalNodeIdOp.report();
	return nodeId;
    }

    /** {@inheritDoc} */
    public ManagedObject getServiceBinding(String name) {
        serviceStats.getServiceBindingOp.report();
	return getBindingInternal(name, true, false, "getServiceBinding");
    }

    /** {@inheritDoc} */
    public ManagedObject getServiceBindingForUpdate(String name) {
        serviceStats.getServiceBindingForUpdateOp.report();
	return getBindingInternal(
	    name, true, true, "getServiceBindingForUpdate");
    }

    /** {@inheritDoc} */
     public void setServiceBinding(String name, Object object) {
         serviceStats.setServiceBindingOp.report();
	 setBindingInternal(name, object, true);
    }

    /** {@inheritDoc} */
    public void removeServiceBinding(String name) {
       serviceStats.removeServiceBindingOp.report();
       removeBindingInternal(name, true);
    }

    /** {@inheritDoc} */
    public String nextServiceBoundName(String name) {
        serviceStats.nextServiceBoundNameOp.report();
	return nextBoundNameInternal(name, true);
    }

    /** {@inheritDoc} */
    public ManagedReference<?> createReferenceForId(BigInteger id) {
        serviceStats.createRefForIdOp.report();
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
        serviceStats.nextObjIdOp.report();
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

    /**
     * Implement getBinding, getBindingForUpdate, getServiceBinding, and
     * getServiceBindingForUpdate.
     */
    private ManagedObject getBindingInternal(String name,
					     boolean serviceBinding,
					     boolean forUpdate,
					     String methodName)
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
		    getInternalName(name, serviceBinding), forUpdate);
	    } catch (NameNotBoundException e) {
		throw new NameNotBoundException(
		    "Name '" + name + "' is not bound");
	    }
	    if (logger.isLoggable(Level.FINEST)) {
		logger.log(
		    Level.FINEST,
		    "{0} tid:{1,number,#}, name:{2} returns type:{3}," +
		    " oid:{4,number,#}",
		    methodName, contextTxnId(context), name, typeName(result),
		    objectId(context, result));
	    }
	    return result;
	} catch (RuntimeException e) {
	    LoggerWrapper exceptionLogger = getExceptionLogger(e);
	    if (exceptionLogger.isLoggable(Level.FINEST)) {
		exceptionLogger.logThrow(
		    Level.FINEST, e,
		    "{0} tid:{1,number,#}, name:{2} throws",
		    methodName, contextTxnId(context), name);
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

    /* -- Other methods -- */

    /** 
     * {@inheritDoc}
     */
    public void shutdown() {
        synchronized (stateLock) {
            while (state == State.SHUTTING_DOWN) {
                try {
                    stateLock.wait();
                } catch (InterruptedException e) {
                    // loop until shutdown is complete
                    logger.log(Level.FINEST, "DataService shutdown " +
                            "interrupt ignored");
                }
            }
            if (state == State.SHUTDOWN) {
                return; // return silently
            }
            state = State.SHUTTING_DOWN;
        }

        store.shutdown();
        synchronized (stateLock) {
            state = State.SHUTDOWN;
            stateLock.notifyAll();
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
     * has not assigned an ID, or if the context is null.  Returns an ID even
     * if the object is removed.
     */
    private static BigInteger objectId(Context context, Object object) {
	return (context != null) ? refId(context.safeFindReference(object))
	    : null;
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
