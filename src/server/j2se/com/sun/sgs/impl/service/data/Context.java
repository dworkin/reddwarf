package com.sun.sgs.impl.service.data;

import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.impl.service.data.store.DataStore;
import com.sun.sgs.service.Service;
import com.sun.sgs.service.Transaction;
import com.sun.sgs.service.TransactionParticipant;

/** Stores information specific to a specific transaction. */
final class Context {

    /** The data store. */
    final DataStore store;

    /** The original transaction. */
    final Transaction originalTxn;

    /** The wrapped transaction. */
    final TxnTrampoline txn;

    private final int debugCheckInterval;

    final boolean detectModifications;

    private int count = 0;

    private boolean inactive;

    private TransactionParticipant storeParticipant;

    /** Stores information about managed references. */
    final ReferenceTable refs = new ReferenceTable();

    /** Creates an instance of this class. */
    Context(DataStore store,
	    Transaction txn,
	    int debugCheckInterval,
	    boolean detectModifications)
    {
	assert store != null && txn != null;
	this.store = store;
	this.originalTxn = txn;
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
    final class TxnTrampoline implements Transaction {
	private final Transaction txn;
	TxnTrampoline(Transaction txn) { this.txn = txn; }
	public byte[] getId() { return txn.getId(); }
	public long getTimeStamp() { return txn.getTimeStamp(); }
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
	public void abort() { txn.abort(); }
	public boolean equals(Object object) {
	    return object instanceof TxnTrampoline &&
		txn.equals(((TxnTrampoline) object).txn);
	}
	public int hashCode() { return txn.hashCode(); }
	public String toString() {
	    return "TxnTrampoline[txn:" + txn + "]";
	}
    }

    /* -- References -- */

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
    ManagedReferenceImpl<? extends ManagedObject> getReference(long id) {
	return ManagedReferenceImpl.getReference(this, id);
    }

    /* -- Bindings -- */

    /** Obtains the object associated with the specified internal name. */
    <T extends ManagedObject> T getBinding(
	String internalName, Class<T> type)
    {
	return type.cast(
	    getReference(store.getBinding(txn, internalName)).get());
    }

    /** Sets the object associated with the specified internal name. */
    <T extends ManagedObject> void setBinding(String internalName, T object) {
	store.setBinding(txn, internalName, getReference(object).id);
    }

    /** Removes the object associated with the specified internal name. */
    void removeBinding(String internalName) {
	store.removeBinding(txn, internalName);
    }

    /* -- Methods on transaction participant -- */

    boolean prepare() {
	return storeParticipant.prepare(txn);
    }

    void commit() {
	storeParticipant.commit(txn);
    }

    void prepareAndCommit() {
	storeParticipant.prepareAndCommit(txn);
    }

    void abort() {
	storeParticipant.commit(txn);
    }

    /* -- Other methods -- */

    /** Stores all object modifications in the data store. */
    void flushChanges() {
	for (ManagedReferenceImpl<? extends ManagedObject> ref :
		 refs.getReferences())
	{
	    ref.flush();
	}
    }

    void maybeCheckReferenceTable() {
	if (++count >= debugCheckInterval) {
	    count = 0;
	    refs.checkState();
	}
    }

    void setInactive() {
	inactive = true;
    }
}
