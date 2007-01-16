package com.sun.sgs.impl.service.data.store;

import com.sun.sgs.app.NameNotBoundException;
import com.sun.sgs.app.ObjectNotFoundException;
import com.sun.sgs.app.TransactionAbortedException;
import com.sun.sgs.impl.service.data.DataServiceImpl;
import com.sun.sgs.service.Transaction;

/**
 * Defines the interface to the underlying persistence mechanism that {@link
 * DataServiceImpl} uses to store byte data. <p>
 *
 * Objects are identified by object IDs, which are positive
 * <code>long</code>s.  Names are mapped to object IDs.
 */
public interface DataStore {

    /**
     * Reserves an object ID for a new object.  Note that calling other
     * operations using this ID are not required to find the object until
     * {@link #setObject setObject} is called.  Aborting a transaction is also
     * not required to unassign the ID so long as other operations treat it as
     * a non-existent object.
     *
     * @param	txn the transaction under which the operation should take place
     * @return	the new object ID
     * @throws	TransactionAbortedException if the transaction was aborted due
     *		to a lock conflict or timeout
     * @throws	IllegalStateException if the operation failed because of a
     *		problem with the current transaction
     */
    long createObject(Transaction txn);

    /**
     * Notifies the <code>DataStore</code> that an object is going to be
     * modified.  The implementation can use this information to obtain an
     * exclusive lock on the object in order avoid contention when the object
     * is modified.
     *
     * @param	txn the transaction under which the operation should take place
     * @param	oid the object ID
     * @throws	IllegalArgumentException if <code>oid</code> is negative
     * @throws	ObjectNotFoundException if the object is not found
     * @throws	TransactionAbortedException if the transaction was aborted due
     *		to a lock conflict or timeout
     * @throws	IllegalStateException if the operation failed because of a
     *		problem with the current transaction
     */
    void markForUpdate(Transaction txn, long oid);

    /**
     * Obtains the data associated with an object ID.  If the
     * <code>forUpdate</code> parameter is <code>true</code>, the caller is
     * stating its intention to modify the object.  The implementation can use
     * that information to obtain an exclusive lock on the object in order
     * avoid contention when the object is modified.
     *
     * @param	txn the transaction under which the operation should take place
     * @param	oid the object ID
     * @param	forUpdate whether the caller intends to modify the object
     * @return	the data associated with the object ID
     * @throws	IllegalArgumentException if <code>oid</code> is negative
     * @throws	ObjectNotFoundException if the object is not found
     * @throws	TransactionAbortedException if the transaction was aborted due
     *		to a lock conflict or timeout
     * @throws	IllegalStateException if the operation failed because of a
     *		problem with the current transaction
     */
    byte[] getObject(Transaction txn, long oid, boolean forUpdate);

    /**
     * Specifies data to associate with an object ID.
     *
     * @param	txn the transaction under which the operation should take place
     * @param	oid the object ID
     * @param	data the data
     * @throws	IllegalArgumentException if <code>oid</code> is negative, or if
     *		<code>data</code> is empty
     * @throws	TransactionAbortedException if the transaction was aborted due
     *		to a lock conflict or timeout
     * @throws	IllegalStateException if the operation failed because of a
     *		problem with the current transaction
     */
    void setObject(Transaction txn, long oid, byte[] data);

    /**
     * Removes the object with the specified object ID.  The implementation
     * will make an effort to flag subsequent references to the removed object
     * by throwing {@link ObjectNotFoundException}, although this behavior is
     * not guaranteed.
     *
     * @param	txn the transaction under which the operation should take place
     * @param	oid the object ID
     * @throws	IllegalArgumentException if <code>oid</code> is negative
     * @throws	ObjectNotFoundException if the object is not found
     * @throws	TransactionAbortedException if the transaction was aborted due
     *		to a lock conflict or timeout
     * @throws	IllegalStateException if the operation failed because of a
     *		problem with the current transaction
     */
    void removeObject(Transaction txn, long oid);

    /**
     * Obtains the object ID bound to a name.
     *
     * @param	txn the transaction under which the operation should take place
     * @param	name the name
     * @return	the object ID
     * @throws	NameNotBoundException if no object ID is bound to the name
     * @throws	TransactionAbortedException if the transaction was aborted due
     *		to a lock conflict or timeout
     * @throws	IllegalStateException if the operation failed because of a
     *		problem with the current transaction
     */
    long getBinding(Transaction txn, String name);

    /**
     * Binds an object ID to a name.
     *
     * @param	txn the transaction under which the operation should take place
     * @param	name the name
     * @param	oid the object ID
     * @throws	IllegalArgumentException if <code>oid</code> is negative
     * @throws	TransactionAbortedException if the transaction was aborted due
     *		to a lock conflict or timeout
     * @throws	IllegalStateException if the operation failed because of a
     *		problem with the current transaction
     */
    void setBinding(Transaction txn, String name, long oid);

    /**
     * Removes the binding for a name.
     *
     * @param	txn the transaction under which the operation should take place
     * @param	name the name
     * @throws	NameNotBoundException if the name is not bound
     * @throws	TransactionAbortedException if the transaction was aborted due
     *		to a lock conflict or timeout
     * @throws	IllegalStateException if the operation failed because of a
     *		problem with the current transaction
     */
    void removeBinding(Transaction txn, String name);

    /**
     * Returns the next name after the specified name that has a binding, or
     * <code>null</code> if there are no more bound names.  If
     * <code>name</code> is <code>null</code>, then the search starts at the
     * beginning.
     *
     * @param	txn the transaction under which the operation should take place
     * @param	name the name to search after, or <code>null</code> to start
     *		at the beginning
     * @return	the next name with a binding following <code>name</code>, or
     *		<code>null</code> if there are no more bound names
     * @throws	TransactionAbortedException if the transaction was aborted due
     *		to a lock conflict or timeout
     * @throws	IllegalStateException if the operation failed because of a
     *		problem with the current transaction
     */
    String nextBoundName(Transaction txn, String name);

    /** 
     * Attempts to shut down this data store, returning a value that specifies
     * whether the attempt was successful. <p>
     *
     * @return	<code>true</code> if the shut down was successful, else
     *		<code>false</code>
     * @throws	IllegalStateException if the <code>shutdown</code> method has
     *		already been called and returned <code>true</code>
     */
    boolean shutdown();
}
