/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.impl.service.data;

import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.impl.service.data.store.DataStore;
import com.sun.sgs.impl.util.MaybeRetryableTransactionNotActiveException;
import com.sun.sgs.impl.util.TransactionContext;
import com.sun.sgs.service.Transaction;
import com.sun.sgs.service.TransactionParticipant;

/** Stores information for a specific transaction. */
final class Context extends TransactionContext {

    /** The data service. */
    private final DataServiceImpl service;

    /** The data store. */
    final DataStore store;

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

    /** Creates an instance of this class. */
    Context(DataServiceImpl service,
	    DataStore store,
	    Transaction txn,
	    int debugCheckInterval,
	    boolean detectModifications,
	    ClassesTable classesTable)
    {
	super(txn);
	assert service != null && store != null && txn != null &&
	    classesTable != null;
	this.service = service;
	this.store = store;
	this.txn = new TxnTrampoline(txn);
	this.debugCheckInterval = debugCheckInterval;
	this.detectModifications = detectModifications;
	classSerial = classesTable.createClassSerialization(this.txn);
    }

    /**
     * Defines a transaction that forwards all operations to another
     * transaction, except for join, which records the participant.  Pass
     * instances of this class to the DataStore in order to mediate its
     * participation in the transaction.
     */
    private final class TxnTrampoline implements Transaction {

	/** The original transaction. */
	private final Transaction originalTxn;

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
    ManagedReferenceImpl getReference(ManagedObject object) {
	return ManagedReferenceImpl.getReference(this, object);
    }

    /**
     * Finds the existing reference associated with the specified object,
     * returning null if it is not found.
     */
    ManagedReferenceImpl findReference(ManagedObject object) {
	return ManagedReferenceImpl.findReference(this, object);
    }

    /** Obtains the reference associated with the specified ID. */
    ManagedReferenceImpl getReference(long oid) {
	return ManagedReferenceImpl.getReference(this, oid);
    }

    /* -- Methods for bindings -- */

    /** Obtains the object associated with the specified internal name. */
    <T> T getBinding(String internalName, Class<T> type) {
	long id = store.getBinding(txn, internalName);
	assert id >= 0 : "Object ID must not be negative";
	return getReference(id).get(type, false);
    }

    /** Sets the object associated with the specified internal name. */
    void setBinding(String internalName, ManagedObject object) {
	store.setBinding(txn, internalName, getReference(object).oid);
    }

    /** Removes the object associated with the specified internal name. */
    void removeBinding(String internalName) {
	store.removeBinding(txn, internalName);
    }

    /** Returns the next bound name. */
    String nextBoundName(String internalName) {
	return store.nextBoundName(txn, internalName);
    }

    /* -- Methods for TransactionContext -- */

    @Override
    public boolean prepare() throws Exception {
	isPrepared = true;
	txn.setInactive();
	ManagedReferenceImpl.flushAll(this);
	if (storeParticipant == null) {
	    isCommitted = true;
	    return true;
	} else {
	    return storeParticipant.prepare(txn);
	}
    }

    @Override
    public void commit() {
	isCommitted = true;
	txn.setInactive();
	if (storeParticipant != null) {
	    storeParticipant.commit(txn);
	}
    }

    @Override
    public void prepareAndCommit() throws Exception {
	isCommitted = true;
	txn.setInactive();
	ManagedReferenceImpl.flushAll(this);
	if (storeParticipant != null) {
	    storeParticipant.prepareAndCommit(txn);
	}
    }

    @Override
    public void abort(boolean retryable) {
	txn.setInactive();
	if (storeParticipant != null) {
	    storeParticipant.abort(txn);
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
}
