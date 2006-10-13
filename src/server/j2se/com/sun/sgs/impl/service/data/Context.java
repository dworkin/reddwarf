package com.sun.sgs.impl.service.data;

import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.impl.service.data.store.DataStore;
import com.sun.sgs.service.Service;
import com.sun.sgs.service.Transaction;

/** Stores information specific to a specific transaction. */
final class Context {

    /** The data store. */
    final DataStore store;

    /** The current transaction. */
    final Transaction txn;

    private final int debugCheckInterval;

    private int count = 0;

    /** Stores information about managed references. */
    final ReferenceTable refs = new ReferenceTable();

    /** Creates an instance of this class. */
    Context(DataStore store, Transaction txn, int debugCheckInterval) {
	assert store != null && txn != null;
	this.store = store;
	this.txn = new TxnTrampoline(txn);
	this.debugCheckInterval = debugCheckInterval;
    }

    /**
     * Defines a transaction that forwards all operations to another
     * transaction, except for join, which it ignores.  Use this implementation
     * for transactions passed to the DataStore in order to mediate its
     * participation in the transaction.
     */
    private static final class TxnTrampoline implements Transaction {
	private final Transaction txn;
	TxnTrampoline(Transaction txn) { this.txn = txn; }
	public byte[] getId() { return txn.getId(); }
	public long getTimeStamp() { return txn.getTimeStamp(); }
	public void join(Service service) { /* Ignore */ }
	public void commit() { txn.commit(); }
	public void abort() { txn.abort(); }
	public boolean equals(Object object) {
	    return object instanceof TxnTrampoline &&
		txn.equals(((TxnTrampoline) object).txn);
	}
	public int hashCode() { return txn.hashCode(); }
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
	return type.cast(getReference(store.getBinding(txn, internalName)));
    }

    /** Sets the object associated with the specified internal name. */
    <T extends ManagedObject> void setBinding(String internalName, T object) {
	store.setBinding(txn, internalName, getReference(object).id);
    }

    /** Removes the object associated with the specified internal name. */
    void removeBinding(String internalName) {
	store.removeBinding(txn, internalName);
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
}
