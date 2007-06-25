/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.impl.service.watchdog;

import com.sun.sgs.app.TransactionNotActiveException;
import com.sun.sgs.auth.Identity;
import com.sun.sgs.kernel.ComponentRegistry;
import com.sun.sgs.kernel.KernelAppContext;
import com.sun.sgs.kernel.TaskOwner;
import com.sun.sgs.service.Service;
import com.sun.sgs.service.Transaction;
import com.sun.sgs.service.TransactionProxy;
import java.lang.Math;
import java.util.HashMap;
import java.util.Map;
import java.util.MissingResourceException;

/**
 * An implementation of {@code TransactionProxy} for the Watchdog
 * server.
 */

class TransactionProxyImpl implements TransactionProxy, ComponentRegistry {
    /** Stores information about the transaction for the current thread. */
    private final ThreadLocal<Transaction> threadTxn =
	new ThreadLocal<Transaction>();

    /** The task owner. */
    private final TaskOwner taskOwner = new TaskOwnerImpl();

    /** Mapping from type to service. */
    private final Map<Class<? extends Service>, Service> services =
	new HashMap<Class<? extends Service>, Service>();

    /** Creates an instance of this class. */
    public TransactionProxyImpl() { }

    /* -- Implement TransactionProxy -- */

    /** {@inheritDoc} */
    public Transaction getCurrentTransaction() {
	Transaction txn = threadTxn.get();
	if (txn != null) {
	    return txn;
	} else {
	    throw new TransactionNotActiveException(
		"No transaction is active");
	}
    }

    /** {@inheritDoc} */
    public TaskOwner getCurrentOwner() {
	return taskOwner;
    }

    /** {@inheritDoc} */
    public <T extends Service> T getService(Class<T> type) {
	Object service = services.get(type);
	if (service == null) {
	    throw new MissingResourceException(
		"Service of type " + type + " was not found",
		type.getName(), "Service");
	}
	return type.cast(service);
    }

    /* -- Implement ComponentRegistry -- */

    /** {@inheritDoc} */
    public <T> T getComponent(Class<T> type) {
	Object component = services.get(type);
	if (component == null) {
	    throw new MissingResourceException(
		"Component of type " + type + " was not found",
		type.getName(), "Component");
	}
	return type.cast(component);
    }

    /* -- Other methods -- */

    /**
     * Specifies the transaction object that will be returned for the current
     * transaction, or null to specify that no transaction should be
     * associated.
     */
    void setCurrentTransaction(Transaction txn) {
	threadTxn.set(txn);
    }

    /**
     * Specifies the service that should be returned for an exact match for
     * the specified type.
     */
    <T extends Service> void setComponent(Class<T> type, T service) {
	if (type == null || service == null) {
	    throw new NullPointerException("Arguments must not be null");
	}
	services.put(type, service);
    }

    private static class TaskOwnerImpl implements TaskOwner {

	/** The identity. */
	private final Identity identity = new IdentityImpl();


	/** Creates an instance of this class. */
	public TaskOwnerImpl() { }

	/* -- Implement TaskOwner -- */

	public KernelAppContext getContext() {
	    // This should be OK since the watchdog server runs standalone.
	    return null;
	}

	public Identity getIdentity() {
	    return identity;
	}
    }

    private static class IdentityImpl implements Identity {
	
	public String getName() { return "WatchdogServerImpl"; }

	public void notifyLoggedIn() { }

	public void notifyLoggedOut() { }
    }
}
