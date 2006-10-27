package com.sun.sgs.impl.service.data;

import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.impl.service.data.store.DataStore;
import com.sun.sgs.service.Transaction;
import com.sun.sgs.service.TransactionParticipant;

/** Stores information for a specific transaction. */
final class Context {

    /** The data store. */
    final DataStore store;

    /**
     * The wrapped transaction, to be passed to the data store.  The wrapping
     * allows the data service to manage the data store's participation by
     * itself, rather than revealing it to the transaction coordinator.
     */
    final TxnTrampoline txn;

    /**
     * The number of operations between making checks on the reference table.
     */
    private final int debugCheckInterval;

    /** Whether to detect modifications. */
    final boolean detectModifications;

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

    /** Stores information about managed references. */
    final ReferenceTable refs = new ReferenceTable();

    /** Creates an instance of this class. */
    Context(DataStore store,
	    Transaction txn,
	    int debugCheckInterval,
	    boolean detectModifications)
    {
	assert store != null && txn != null : "Store or txn is null";
	this.store = store;
	this.txn = new TxnTrampoline(txn);
	this.debugCheckInterval = debugCheckInterval;
	this.detectModifications = detectModifications;
    }

    /**
     * Defines a transaction that forwards all operations to another
     * transaction, except for join, which it ignores.  Use this implementation
     * for transactions passed to the DataStore in order to mediate its
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

	public void join(TransactionParticipant participant) {
	    if (inactive) {
		throw new IllegalStateException(
		    "Attempt to join a transaction that is not active");
	    } else if (storeParticipant == null) {
		storeParticipant = participant;
	    } else if (!storeParticipant.equals(participant)) {
		throw new IllegalStateException(
		    "Attempt to join with different participant");
	    }
	}

	public void abort() {
	    originalTxn.abort();
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

	/**
	 * Checks that the specified transaction equals the original one, and
	 * throw IllegalStateException if not.
	 */
	void check(Transaction otherTxn) {
	    if (!originalTxn.equals(otherTxn)) {
		throw new IllegalStateException(
		    "Wrong transaction: Expected " + originalTxn +
		    ", found " + otherTxn);
	    }
	}

	/** Notes that this transaction is inactive. */
	void setInactive() {
	    inactive = true;
	}
    }

    /* -- Methods for obtaining references -- */

    /** Obtains the reference associated with the specified object. */
    <T extends ManagedObject> ManagedReferenceImpl<T> getReference(T object) {
	return ManagedReferenceImpl.getReference(this, object);
    }

    /**
     * Finds the existing reference associated with the specified object,
     * returning null if it is not found.
     */
    <T extends ManagedObject> ManagedReferenceImpl<T> findReference(T object) {
	return ManagedReferenceImpl.findReference(this, object);
    }

    /** Obtains the reference associated with the specified ID. */
    private ManagedReferenceImpl<? extends ManagedObject> getReference(
	long oid)
    {
	return ManagedReferenceImpl.getReference(this, oid);
    }

    /* -- Methods for bindings -- */

    /** Obtains the object associated with the specified internal name. */
    <T extends ManagedObject> T getBinding(
	String internalName, Class<T> type)
    {
	return type.cast(
	    getReference(store.getBinding(txn, internalName)).get());
    }

    /** Sets the object associated with the specified internal name. */
    <T extends ManagedObject> void setBinding(String internalName, T object) {
	store.setBinding(txn, internalName, getReference(object).oid);
    }

    /** Removes the object associated with the specified internal name. */
    void removeBinding(String internalName) {
	store.removeBinding(txn, internalName);
    }

    /* -- Methods for TransactionParticipant -- */

    boolean prepare() throws Exception {
	txn.setInactive();
	flushChanges();
	return storeParticipant.prepare(txn);
    }

    void commit() {
	txn.setInactive();
	storeParticipant.commit(txn);
    }

    void prepareAndCommit() throws Exception {
	txn.setInactive();
	flushChanges();
	storeParticipant.prepareAndCommit(txn);
    }

    void abort() {
	txn.setInactive();
	storeParticipant.abort(txn);
    }

    /* -- Other methods -- */

    /**
     * Checks the consistency of the reference table if the operation count
     * equals the check interval.  Throws an IllegalStateException if it
     * encounters a problem.
     */
    void maybeCheckReferenceTable() {
	if (++count >= debugCheckInterval) {
	    count = 0;
	    refs.checkState();
	}
    }

    /**
     * Check that the specified transaction equals the original one, and throw
     * IllegalStateException if not.
     */
    void checkTxn(Transaction otherTxn) {
	txn.check(otherTxn);
    }

    /** Stores all object modifications in the data store. */
    private void flushChanges() {
	for (ManagedReferenceImpl ref : refs.getReferences()) {
	    ref.flush();
	}
    }
}
