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

import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.ManagedObjectRemoval;
import com.sun.sgs.app.ManagedReference;
import com.sun.sgs.app.ObjectNotFoundException;
import com.sun.sgs.app.annotation.ReadOnly;
import com.sun.sgs.impl.service.data.store.DataStore;
import com.sun.sgs.impl.sharedutil.LoggerWrapper;
import com.sun.sgs.impl.util.MaybeRetryableTransactionNotActiveException;
import com.sun.sgs.impl.util.TransactionContext;
import com.sun.sgs.kernel.AccessReporter;
import com.sun.sgs.kernel.AccessReporter.AccessType;
import com.sun.sgs.service.Transaction;
import com.sun.sgs.service.TransactionParticipant;
import java.math.BigInteger;
import java.util.IdentityHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Stores information for a specific transaction. */
final class Context extends TransactionContext {

    /** The logger for the data service class. */
    private static final LoggerWrapper logger =
	new LoggerWrapper(Logger.getLogger(DataServiceImpl.class.getName()));

    /** The data service. */
    private final DataServiceImpl service;

    /** The data store. */
    final DataStore store;

    /**
     * The read-only data cache for those ManagedObject instances
     * marked with the ReadOnly annotation
     */
    final DataCache cache;

    /**
     * The wrapped transaction, to be passed to the data store.  The wrapping
     * allows the data service to manage the data store's transaction
     * participation by itself, rather than revealing it to the transaction
     * coordinator.
     */
    final TxnTrampoline txn;

    /**
     * The number of operations to skip between checks of the consistency of
     * the reference table.
     */
    private final int debugCheckInterval;

    /** Whether to detect modifications. */
    final boolean detectModifications;

    /** Controls serializing classes. */
    final ClassSerialization classSerial;

    /**
     * The number of operations performed -- used to determine when to make
     * checks on the reference table.
     */
    private int count = 0;

    /**
     * The participant object for the data store, or null if the data store has
     * not yet joined the transaction.
     */
    TransactionParticipant storeParticipant;

    /**
     * Stores information about managed references.  This field is logically
     * part of the ManagedReferenceImpl class.
     */
    final ReferenceTable refs = new ReferenceTable();

    /** The proxy for notifying of object accesses. */
    final AccessReporter<BigInteger> oidAccesses;

    /**
     * A table for recording data for all the {@link
     * ReadOnlyReference} instances created during the context of the
     * current transaction.
     */
    final ReadOnlyReferenceTable readOnlyRefs = new ReadOnlyReferenceTable();

    /**
     * A map that records all managed objects that are currently having
     * ManagedObjectRemoval.removingObject called on them, to detect recursion,
     * or null.  Uses identity comparison to avoid confusion by value-based
     * equals methods.
     */
    private IdentityHashMap<ManagedObjectRemoval, Boolean> removing = null;

    /** Creates an instance of this class. */
    Context(DataServiceImpl service,
	    DataStore store,
	    DataCache cache,
	    Transaction txn,
	    int debugCheckInterval,
	    boolean detectModifications,
	    ClassesTable classesTable,
	    AccessReporter<BigInteger> oidAccesses)
    {
	super(txn);
	assert service != null && store != null && txn != null &&
	    classesTable != null && oidAccesses != null;
	this.service = service;
	this.store = store;
	this.cache = cache;
	this.txn = new TxnTrampoline(txn);
	this.debugCheckInterval = debugCheckInterval;
	this.detectModifications = detectModifications;
	this.oidAccesses = oidAccesses;
	classSerial = classesTable.createClassSerialization(this.txn);
	if (logger.isLoggable(Level.FINER)) {
	    logger.log(Level.FINER, "join tid:{0,number,#}, thread:{1}",
		       getTxnId(), Thread.currentThread().getName());
	}
    }

    /**
     * Defines a transaction that forwards all operations to another
     * transaction, except for join, which records the participant.  Pass
     * instances of this class to the DataStore in order to mediate its
     * participation in the transaction.
     */
    final class TxnTrampoline implements Transaction {

	/** The original transaction. */
	final Transaction originalTxn;

	/** Whether this transaction is inactive. */
	private boolean inactive;

	/** Creates an instance. */
	TxnTrampoline(Transaction originalTxn) {
	    this.originalTxn = originalTxn;
	}

	/* -- Implement Transaction -- */

	public byte[] getId() {
	    return originalTxn.getId();
	}

	public long getCreationTime() {
	    return originalTxn.getCreationTime();
	}

	public long getTimeout() {
	    return originalTxn.getTimeout();
	}

	public void checkTimeout() {
	    originalTxn.checkTimeout();
	}

	public void join(TransactionParticipant participant) {
	    if (originalTxn.isAborted()) {
		throw new MaybeRetryableTransactionNotActiveException(
		    "Transaction is not active", originalTxn.getAbortCause());
	    } else if (inactive) {
		throw new IllegalStateException(
		    "Attempt to join a transaction that is not active");
	    } else if (participant == null) {
		throw new NullPointerException("Participant must not be null");
	    } else if (storeParticipant == null) {
		storeParticipant = participant;
	    } else if (!storeParticipant.equals(participant)) {
		throw new IllegalStateException(
		    "Attempt to join with different participant");
	    }
	}

	public void abort(Throwable cause) {
	    originalTxn.abort(cause);
	}

	public boolean isAborted() {
	    return originalTxn.isAborted();
	}

	public Throwable getAbortCause() {
	    return originalTxn.getAbortCause();
	}

	/* -- Object methods -- */

	public boolean equals(Object object) {
	    return object instanceof TxnTrampoline &&
		originalTxn.equals(((TxnTrampoline) object).originalTxn);
	}

	public int hashCode() {
	    return originalTxn.hashCode();
	}

	public String toString() {
	    return "TxnTrampoline[originalTxn:" + originalTxn + "]";
	}

	/* -- Other methods -- */

	/** Notes that this transaction is inactive. */
	void setInactive() {
	    inactive = true;
	}
    }

    /* -- Methods for obtaining references -- */

    /** Obtains the reference associated with the specified object. */
    <T> InternalManagedReference<T> getReference(T object) {
	return (isReadOnly(object))
	    ? ReadOnlyReference.getOrCreateIfNotPresent(this, object)
	    : ManagedReferenceImpl.getReference(this, object);
    }

    /**
     * Finds the existing reference associated with the specified
     * object, returning null if it is not found.  Throws
     * ObjectNotFoundException if the object has already been removed
     * during the current transaction.
     *
     * @throws ObjectNotFoundException if the object has been removed.
     */
    <T> InternalManagedReference<T> findReference(T object) {
	if (isReadOnly(object)) {
	    ReadOnlyReference<T> ref = 
		ReadOnlyReference.getReference(this, object);
	    if (ref != null && ref.isRemoved())
		throw new ObjectNotFoundException("object has already " + 
						  "been removed");
	    return ref;
	}
	else
	    return ManagedReferenceImpl.findReference(this, object);
    }

    /**
     * Finds the existing reference associated with the specified object,
     * returning null if it is not found or has been removed.
     */
    <T> InternalManagedReference<T> safeFindReference(T object) {

	if (isReadOnly(object)) {
	    ReadOnlyReference<T> ref = 
		ReadOnlyReference.getReference(this, object);
	    return ref;
	}

	return ManagedReferenceImpl.safeFindReference(this, object);
    }

    /** 
     * Obtains the reference associated with the specified ID.  Note that this
     * method will eagerly load the object from the data store.
     */
    InternalManagedReference<?> getReference(long oid) {

	// Given that we know nothing about the type of the object, we require
	// obtaining an instance of it to determine which type of reference to
	// return
	InternalManagedReference<?> ref;

	// first check whether the oid is already contained within any of the
	// reference tables.  We have to do this before trying to load the
	// object from memory because if the object was created within the
	// current transaction it will not be in the backing data store
	if ((ref = refs.find(oid)) != null)
	    return ref;
	else if ((ref = readOnlyRefs.get(oid)) != null)
	    return ref;       

	// Otherwise, the object has not been created or loading during the
	// current transaction so we have to pay the penalty of getting the
	// object up front in order to ensure that we return the right type of
	// reference.
	//
	// In practice we assume that this will not actually be much additonal
	// overhead since the only caller of this method is when a
	// ManagedReference is being created by an oid.  Most often in this
	// case, the caller will immediately call .get() on the reference and
	// so the penalty will be offset.
	Object o = SerialUtil.deserialize(store.getObject(txn, oid, false),
					  classSerial);
	oidAccesses.reportObjectAccess(BigInteger.valueOf(oid), 
				       AccessType.READ);
	ref = (isReadOnly(o))
	    ? ReadOnlyReference.getOrCreateIfNotPresent(this, oid)
	    : ManagedReferenceImpl.getReference(this, oid);	    

	// Once the right type of reference has been created, set the value
	// contained by that reference.  This avoid having the reference
	// re-fetch the item from the data store when .get() is called.
	ref.setObject((ManagedObject)o);
 
	return ref;
    }

    /**
     * Returns {@code true} if the provided object is annotated with a
     * read-only marker.
     */
    private static boolean isReadOnly(Object o) {
	return o.getClass().getAnnotation(ReadOnly.class) != null;
    }


    /* -- Methods for bindings -- */

    /** Obtains the object associated with the specified internal name. */
    ManagedObject getBinding(String internalName) {
	long id = store.getBinding(txn, internalName);
	assert id >= 0 : "Object ID must not be negative";
	return (ManagedObject) getReference(id).get();
    }

    /** Sets the object associated with the specified internal name. */
    void setBinding(String internalName, Object object) {
	store.setBinding(txn, internalName, getReference(object).getOid());
    }

    /** Removes the object associated with the specified internal name. */
    void removeBinding(String internalName) {
	store.removeBinding(txn, internalName);
    }

    /** Returns the next bound name. */
    String nextBoundName(String internalName) {
	return store.nextBoundName(txn, internalName);
    }

    /* -- Methods for object IDs -- */

    /**
     * Returns the next object ID, or -1 if there are no more objects.  Does
     * not return IDs for removed objects.  Specifying -1 requests the first
     * ID.
     */
    long nextObjectId(long oid) {
	return ManagedReferenceImpl.nextObjectId(this, oid);
    }

    /* -- Methods for TransactionContext -- */

    @Override
    public boolean prepare() throws Exception {
	try {
	    isPrepared = true;
	    txn.setInactive();
	    ManagedReferenceImpl.flushAll(this);
	    readOnlyRefs.validate();
            // TEST
            service.accessCoordinator.validate(txn.originalTxn);
	    boolean result;
	    if (storeParticipant == null) {
		isCommitted = true;
		result = true;
	    } else {
		result = storeParticipant.prepare(txn);
	    }
	    if (logger.isLoggable(Level.FINER)) {
		logger.log(Level.FINER, "prepare tid:{0,number,#} returns {1}",
			   getTxnId(), result);
	    }
	    return result;
	} catch (Exception e) {
	    if (logger.isLoggable(Level.FINER)) {
		logger.logThrow(Level.FINER, e,
				"prepare tid:{0,number,#} throws", getTxnId());
	    }
	    throw e;
	}
    }

    @Override
    public void commit() {
	try {
	    isCommitted = true;
	    txn.setInactive();
	    if (storeParticipant != null) {
		storeParticipant.commit(txn);
	    }
	    if (logger.isLoggable(Level.FINER)) {
		logger.log(Level.FINER, "commit tid:{0,number,#} returns",
			   getTxnId());
	    }
	} catch (RuntimeException e) {
	    if (logger.isLoggable(Level.FINER)) {
		logger.logThrow(Level.FINER, e,
				"commit tid:{0,number,#} throws", getTxnId());
	    }
	    throw e;
	}
    }

    @Override
    public void prepareAndCommit() throws Exception {
	try {
	    isCommitted = true;
	    txn.setInactive();
	    ManagedReferenceImpl.flushAll(this);
	    readOnlyRefs.validate();
            // TEST
            service.accessCoordinator.validate(txn.originalTxn);
	    if (storeParticipant != null) {
		storeParticipant.prepareAndCommit(txn);
	    }
	    if (logger.isLoggable(Level.FINER)) {
		logger.log(Level.FINER,
			   "prepareAndCommit tid:{0,number,#} returns",
			   getTxnId());
	    }
	} catch (RuntimeException e) {
	    if (logger.isLoggable(Level.FINER)) {
		logger.logThrow(Level.FINER, e,
				"prepareAndCommit tid:{0,number,#} throws",
				getTxnId());
	    }
	    throw e;
	}
    }

    @Override
    public void abort(boolean retryable) {
	try {
	    txn.setInactive();
	    if (storeParticipant != null) {
		storeParticipant.abort(txn);
	    }
	    if (logger.isLoggable(Level.FINER)) {
		logger.log(Level.FINER, "abort tid:{0,number,#} returns",
			   getTxnId());
	    }
	} catch (RuntimeException e) {
	    if (logger.isLoggable(Level.FINER)) {
		logger.logThrow(Level.FINER, e,
				"abort tid:{0,number,#} throws", getTxnId());
	    }
	    throw e;
	}
    }

    /* -- Other methods -- */

    /**
     * Checks the consistency of the reference table if the operation count
     * equals the check interval.  Throws an IllegalStateException if it
     * encounters a problem.
     */
    void maybeCheckReferenceTable() {
	if (++count > debugCheckInterval) {
	    count = 0;
	    ManagedReferenceImpl.checkAllState(this);
	}
    }

    /** Checks that the service is running or shutting down. */
    void checkState() {
	service.checkState();
    }

    /** Calls removingObject on the argument, and checks for recursion. */
    void removingObject(ManagedObjectRemoval object) {
	if (removing == null) {
	    removing = new IdentityHashMap<ManagedObjectRemoval, Boolean>();
	}
	if (removing.containsKey(object)) {
	    throw new IllegalStateException(
		"Attempt to remove object recursively: " + object);
	}
	try {
	    removing.put(object, Boolean.TRUE);
	    object.removingObject();
	} finally {
	    removing.remove(object);
	}
    }

    /** Returns the ID of the associated transaction as a BigInteger. */
    BigInteger getTxnId() {
	return new BigInteger(1, txn.getId());
    }
}
