package com.sun.sgs.impl.service.data;

import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.ManagedReference;
import com.sun.sgs.app.NameNotBoundException;
import com.sun.sgs.app.TransactionNotActiveException;
import com.sun.sgs.impl.service.data.store.DataStoreImpl;
import com.sun.sgs.service.DataService;
import com.sun.sgs.service.Transaction;
import com.sun.sgs.service.TransactionParticipant;
import com.sun.sgs.service.TransactionProxy;
import java.util.Properties;

public class DataServiceImpl implements DataService, TransactionParticipant {

    private static final String APP_NAME_PROPERTY = "com.sun.sgs.appName";

    private static final String DEBUG_CHECK_INTERVAL_PROPERTY =
	DataServiceImpl.class.getName() + ".debugCheckInterval";

    private static final ThreadLocal<Context> currentContext =
	new ThreadLocal<Context>();

    private final String appName;

    private final int debugCheckInterval;

    private final DataStoreImpl store;

    private TransactionProxy txnProxy;

    public DataServiceImpl(Properties properties) {
	appName = properties.getProperty(APP_NAME_PROPERTY);
	if (appName == null) {
	    throw new IllegalArgumentException(
		"The " + APP_NAME_PROPERTY + " property must be specified");
	}
	debugCheckInterval = Util.getIntProperty(
	    properties, DEBUG_CHECK_INTERVAL_PROPERTY, Integer.MAX_VALUE);
	store = new DataStoreImpl(properties);
    }

    public void configure(TransactionProxy txnProxy) {
	if (this.txnProxy != null) {
	    throw new IllegalStateException("Already configured");
	}
	this.txnProxy = txnProxy;
    }

    /* -- Implement DataManager -- */

    public <T extends ManagedObject> T getBinding(
	 String name, Class<T> type)
    {
	if (name == null || type == null) {
	    throw new NullPointerException("The arguments must not be null");
	}
	String internalName = "u" + name;
	Context context = checkContext();
	try {
	    return context.getBinding(internalName, type);
	} catch (NameNotBoundException e) {
	    throw new NameNotBoundException(
		"Name '" + name + "' is not bound");
	}
    }

     public void setBinding(String name, ManagedObject object) {
	String internalName = "u" + name;
	Context context = checkContext();
	context.setBinding(name, object);
    }

    public void removeBinding(String name) {
	String internalName = "u" + name;
	Context context = checkContext();
	try {
	    context.removeBinding(internalName);
	} catch (NameNotBoundException e) {
	    throw new NameNotBoundException(
		"Name '" + name + "' is not bound");
	}
    }

    public void removeObject(ManagedObject object) {
	Context context = checkContext();
	ManagedReferenceImpl<? extends ManagedObject> ref =
	    context.findReference(object);
	if (ref != null) {
	    ref.removeObject();
	}
    }

    public void markForUpdate(ManagedObject object) {
	Context context = checkContext();
	ManagedReferenceImpl<? extends ManagedObject> ref =
	    context.findReference(object);
	if (ref != null) {
	    ref.markForUpdate();
	}
    }

    public <T extends ManagedObject> ManagedReference<T> createReference(
	T object)
    {
	Context context = checkContext();
	return context.getReference(object);
    }

    /* -- Implement DataService -- */

    public <T extends ManagedObject> T getServiceBinding(
	String name, Class<T> type)
    {
	String internalName = "s" + name;
	Context context = checkContext();
	try {
	    return context.getBinding(internalName, type);
	} catch (NameNotBoundException e) {
	    throw new NameNotBoundException(
		"Name '" + name + "' has no service binding");
	}
    }

     public void setServiceBinding(String name, ManagedObject object) {
	String internalName = "s" + name;
	Context context = checkContext();
	context.setBinding(name, object);
    }

    public void removeServiceBinding(String name) {
	String internalName = "s" + name;
	Context context = checkContext();
	try {
	    context.removeBinding(internalName);
	} catch (NameNotBoundException e) {
	    throw new NameNotBoundException(
		"Name '" + name + "' has no service binding");
	}
    }

    /* -- Implement TransactionParticipant -- */

    public String getIdentifier() {
	return toString();
    }

    public boolean prepare(Transaction txn) {
	Context context = checkContext();
	context.setInactive();
	context.flushChanges();
	return store.prepare(txn);
    }

    public void commit(Transaction txn) {
	checkContext();
	currentContext.remove();
	store.commit(txn);
    }

    public void prepareAndCommit(Transaction txn) {
	Context context = checkContext();
	context.setInactive();
	context.flushChanges();
	currentContext.remove();
	store.prepareAndCommit(txn);
    }

    public void abort(Transaction txn) {
	Context context = checkContext();
	context.setInactive();
	currentContext.remove();
	store.abort(txn);
    }

    /* -- Other methods -- */

    public String toString() {
	return "DataServiceImpl[appName:" + appName + "]";
    }

    /**
     * Obtains information associated with the current transaction, throwing an
     * exception if that transaction is not active.
     */
    Context checkContext() {
	if (txnProxy == null) {
	    throw new IllegalStateException("Not configured");
	}
	Transaction txn = txnProxy.getCurrentTransaction();
	if (txn == null) {
	    throw new TransactionNotActiveException(
		"No transaction is active");
	}
	Context context = currentContext.get();
	if (context != null && !txn.equals(context.txn)) {
	    currentContext.remove();
	    throw new IllegalStateException("Wrong transaction");
	}
	try {
	    txn.join(this);
	} catch (IllegalStateException e) {
	    throw new TransactionNotActiveException(
		"No transaction is active");
	}
	context = new Context(store, txn, debugCheckInterval);
	currentContext.set(context);
	context.maybeCheckReferenceTable();
	return context;
    }

    /** Checks that the specified context is currently active. */
    static void checkContext(Context context) {
	if (context != currentContext.get()) {
	    throw new TransactionNotActiveException(
		"No transaction is active");
	}
	context.maybeCheckReferenceTable();
    }

    /**
     * Obtains the currently active context.
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
}
