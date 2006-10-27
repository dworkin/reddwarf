package com.sun.sgs.impl.service.data;

import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.ManagedReference;
import com.sun.sgs.app.NameNotBoundException;
import com.sun.sgs.app.TransactionNotActiveException;
import com.sun.sgs.impl.service.data.store.DataStore;
import com.sun.sgs.impl.service.data.store.DataStoreImpl;
import com.sun.sgs.impl.util.LoggerWrapper;
import com.sun.sgs.impl.util.PropertiesUtil;
import com.sun.sgs.service.DataService;
import com.sun.sgs.service.Transaction;
import com.sun.sgs.service.TransactionParticipant;
import com.sun.sgs.service.TransactionProxy;
import java.io.Serializable;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/* XXX: Add header? */
/** Provides an implementation of <code>DataService</code>. */
public class DataServiceImpl implements DataService, TransactionParticipant {

    /** The property that specifies the application name. */
    public static final String APP_NAME_PROPERTY = "com.sun.sgs.appName";

    /** The name of this class. */
    private static final String CLASSNAME = DataServiceImpl.class.getName();

    /**
     * The property that specifies after how many operations to check the
     * consistency of the managed references table.
     */
    public static final String DEBUG_CHECK_INTERVAL_PROPERTY =
	CLASSNAME + ".debugCheckInterval";

    /**
     * The property that specifies whether to automatically detect
     * modifications to objects.
     */
    public static final String DETECT_MODIFICATIONS_PROPERTY =
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
     * Synchronize on this object before accessing the txnProxy,
     * debugCheckInterval, or detectModifications fields.
     */
    private final Object lock = new Object();

    /** The transaction proxy, or null if configure has not been called. */
    private TransactionProxy txnProxy;

    /**
     * The number of operations between checking the consistency of the managed
     * reference table.
     */
    private int debugCheckInterval;

    /** Whether to detect object modification automatically. */
    private boolean detectModifications;

    /**
     * Creates an instance of this class configured with the specified
     * properties.
     *
     * @param	properties the properties for configuring this service
     * @throws	IllegalArgumentException if the <code>APP_NAME_PROPERTY</code>
     *		is not specified, if the value of the
     *		<code>DEBUG_CHECK_INTERVAL_PROPERTY</code> is not a valid
     *		integer, or if the data store constructor detects an illegal
     *		property value
     */
    public DataServiceImpl(Properties properties) {
	logger.log(Level.CONFIG, "Creating DataServiceImpl properties:{0}",
		   properties);
	try {
	    appName = properties.getProperty(APP_NAME_PROPERTY);
	    if (appName == null) {
		throw new IllegalArgumentException(
		    "The " + APP_NAME_PROPERTY +
		    " property must be specified");
	    }
	    debugCheckInterval = PropertiesUtil.getIntProperty(
		properties, DEBUG_CHECK_INTERVAL_PROPERTY, Integer.MAX_VALUE);
	    detectModifications = PropertiesUtil.getBooleanProperty(
		properties, DETECT_MODIFICATIONS_PROPERTY, Boolean.TRUE);
	    store = new DataStoreImpl(properties);
	} catch (RuntimeException e) {
	    logger.log(Level.SEVERE, "DataService initialization failed", e);
	    throw e;
	}
    }

    /**
     * Configures this service with the specified transaction proxy.
     *
     * @param	txnProxy the transaction proxy
     * @throws	IllegalStateException if this method has already been called
     */
    public void configure(TransactionProxy txnProxy) {
	if (txnProxy == null) {
	    throw new NullPointerException("The argument must not be null");
	}
	synchronized (lock) {
	    if (this.txnProxy != null) {
		throw new IllegalStateException("Already configured");
	    }
	    this.txnProxy = txnProxy;
	}
    }

    /* -- Implement DataManager -- */

    /** {@inheritDoc} */
    public <T extends ManagedObject> T getBinding(
	 String name, Class<T> type)
    {
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
    public void removeObject(ManagedObject object) {
	try {
	    if (object == null) {
		throw new NullPointerException("The argument must not be null");
	    } else if (!(object instanceof Serializable)) {
		throw new IllegalArgumentException(
		    "The object must be serializable");
	    }
	    Context context = checkContext();
	    ManagedReferenceImpl<? extends ManagedObject> ref =
		context.findReference(object);
	    if (ref != null) {
		ref.removeObject();
	    }
	    logger.log(
		Level.FINEST, "removeObject object:{0} returns", object);
	} catch (RuntimeException e) {
	    logger.logThrow(
		Level.FINEST, "removeObject object:{0} fails", e, object);
	    throw e;
	}
    }

    /** {@inheritDoc} */
    public void markForUpdate(ManagedObject object) {
	try {
	    if (object == null) {
		throw new NullPointerException("The argument must not be null");
	    } else if (!(object instanceof Serializable)) {
		throw new IllegalArgumentException(
		    "The object must be serializable");
	    }
	    Context context = checkContext();
	    ManagedReferenceImpl<? extends ManagedObject> ref =
		context.findReference(object);
	    if (ref != null) {
		ref.markForUpdate();
	    }
	    logger.log(
		Level.FINEST, "markForUpdate object:{0} returns", object);
	} catch (RuntimeException e) {
	    logger.logThrow(
		Level.FINEST, "markForUpdate object:{0} fails", e, object);
	    throw e;
	}
    }

    /** {@inheritDoc} */
    public <T extends ManagedObject> ManagedReference<T> createReference(
	T object)
    {
	try {
	    if (object == null) {
		throw new NullPointerException("The argument must not be null");
	    } else if (!(object instanceof Serializable)) {
		throw new IllegalArgumentException(
		    "The object must be serializable");
	    }
	    Context context = checkContext();
	    ManagedReference<T> result = context.getReference(object);
	    if (logger.isLoggable(Level.FINEST)) {
		logger.log(
		    Level.FINEST, "createReference object:{0} returns {1}",
		    object, result);
	    }
	    return result;
	} catch (RuntimeException e) {
	    logger.logThrow(
		Level.FINEST, "createReference object:{0} fails", e, object);
	    throw e;
	}
    }

    /* -- Implement DataService -- */

    /** {@inheritDoc} */
    public <T extends ManagedObject> T getServiceBinding(
	String name, Class<T> type)
    {
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

    /* -- Implement TransactionParticipant -- */

    /** {@inheritDoc} */
    public String getName() {
	return toString();
    }

    /** {@inheritDoc} */
    public boolean prepare(Transaction txn) throws Exception {
	try {
	    if (txn == null) {
		throw new TransactionNotActiveException(
		    "No transaction is active");
	    }
	    Context context = currentContext.get();
	    if (context == null) {
		throw new IllegalStateException("No context");
	    }
	    context.checkTxn(txn);
	    boolean result = context.prepare();
	    if (logger.isLoggable(Level.FINE)) {
		logger.log(Level.FINER, "prepare txn:{0} returns {1}",
			   txn, result);
	    }
	    return result;
	} catch (RuntimeException e) {
	    logger.logThrow(Level.FINER, "prepare txn:{0} fails", e, txn);
	    throw e;
	}
    }

    /** {@inheritDoc} */
    public void commit(Transaction txn) {
	try {
	    if (txn == null) {
		throw new IllegalStateException("No transaction");
	    }
	    Context context = currentContext.get();
	    if (context == null) {
		throw new IllegalStateException("Not joined");
	    }
	    context.checkTxn(txn);
	    currentContext.set(null);
	    context.commit();
	    logger.log(Level.FINER, "commit txn:{0} returns", txn);
	} catch (RuntimeException e) {
	    logger.logThrow(Level.FINER, "commit txn:{0} fails", e, txn);
	    throw e;
	}
    }

    /** {@inheritDoc} */
    public void prepareAndCommit(Transaction txn) throws Exception {
	try {
	    if (txn == null) {
		throw new IllegalStateException("No transaction");
	    }
	    Context context = currentContext.get();
	    if (context == null) {
		throw new IllegalStateException("Not joined");
	    }
	    context.checkTxn(txn);
	    currentContext.set(null);
	    context.prepareAndCommit();
	    logger.log(Level.FINER, "prepareAndCommit txn:{0} returns", txn);
	} catch (RuntimeException e) {
	    logger.logThrow(
		Level.FINER, "prepareAndCommit txn:{0} fails", e, txn);
	    throw e;
	}
    }

    /** {@inheritDoc} */
    public void abort(Transaction txn) {
	try {
	    if (txn == null) {
		throw new IllegalStateException("No transaction");
	    }
	    Context context = currentContext.get();
	    if (context == null) {
		throw new IllegalStateException("Not joined");
	    }
	    context.checkTxn(txn);
	    currentContext.set(null);
	    context.abort();
	    logger.log(Level.FINER, "abort txn:{0} returns", txn);
	} catch (RuntimeException e) {
	    logger.logThrow(Level.FINER, "abort txn:{0} fails", e, txn);
	    throw e;
	}
    }

    /* -- Generic binding methods -- */

    /** Implement getBinding and getServiceBinding. */
    private <T extends ManagedObject> T getBindingInternal(
	 String name, Class<T> type, boolean serviceBinding)
    {
	try {
	    if (name == null || type == null) {
		throw new NullPointerException(
		    "The arguments must not be null");
	    }
	    Context context = checkContext();
	    T result;
	    try {
		result = context.getBinding(
		    getInternalName(name, serviceBinding), type);
	    } catch (NameNotBoundException e) {
		throw new NameNotBoundException(
		    "Name '" + name + "' is not bound", e);
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
		    Level.FINEST, "{0} name:{1}, type:{2} fails", e,
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
	    Context context = checkContext();
	    context.setBinding(getInternalName(name, serviceBinding), object);
	    if (logger.isLoggable(Level.FINEST)) {
		logger.log(Level.FINEST, "{0} name:{1}, object:{2} returns",
			   serviceBinding ? "setServiceBinding" : "setBinding",
			   name, object);
	    }
	} catch (RuntimeException e) {
	    if (logger.isLoggable(Level.FINEST)) {
		logger.logThrow(
		    Level.FINEST, "{0} name:{1}, object:{2} fails", e,
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
		throw new NullPointerException(
		    "The argument must not be null");
	    }
	    Context context = checkContext();
	    try {
		context.removeBinding(getInternalName(name, serviceBinding));
	    } catch (NameNotBoundException e) {
		throw new NameNotBoundException(
		    "Name '" + name + "' is not bound", e);
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
		    Level.FINEST, "{0} name:{1} fails", e,
		    serviceBinding ? "removeServiceBinding" : "removeBinding",
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
	return "DataServiceImpl[appName:\"" + appName + "\"]";
    }

    /**
     * Specifies after how many operations to check the consistency of the
     * managed references table.
     *
     * @param	debugCheckInterval the number of operations between consistency
     *		checks
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
	Context context = currentContext.get();
	if (context == null) {
	    logger.log(Level.FINER, "join txn:{0}", txn);
	    txn.join(this);
	    context = new Context(
		store, txn, debugCheckInterval, detectModifications);
	    currentContext.set(context);
	} else {
	    context.checkTxn(txn);
	}
	context.maybeCheckReferenceTable();
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
	context.maybeCheckReferenceTable();
    }

    /**
     * Obtains the currently active context, throwing
     * TransactionNotActiveException if none is active.
     */
    static Context getContext() {
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
     * binding.
     */
    private static String getInternalName(
	String name, boolean serviceBinding)
    {
	return (serviceBinding ? "s" : "a") + name;
    }
}
