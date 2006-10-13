package com.sun.sgs.impl.service.data.store;

import com.sun.sgs.service.Transaction;

/**
 * Defines the interface to the underlying persistence mechanism that {@link
 * DataServiceImpl} uses to store byte data. <p>
 *
 * Objects are identified by object IDs, which are positive <code>long</code>s.
 */
public interface DataStore {

    /**
     * Reserves an object ID for a new object.
     *
     * @param	txn the transaction under which the operation should take place
     * @return	the new object ID
     * @throws	TransactionException if the operation failed because of a
     *		problem with the current transaction
     */
    long createObject(Transaction txn);

    /**
     * Notifies the <code>DataStore</code> that an object is going to be
     * modified.
     *
     * @param	txn the transaction under which the operation should take place
     * @param	id the object ID
     * @throws	IllegalArgumentException if <code>id</code> is negative
     * @throws	ObjectNotFoundException if the object is not found
     * @throws	TransactionException if the operation failed because of a
     *		problem with the current transaction
     */
    void markForUpdate(Transaction txn, long id);

    /**
     * Obtains the data associated with an object ID.  If the
     * <code>forUpdate</code> parameter is <code>true</code>, the caller is
     * stating its intention to modify the object.  The implementation can use
     * the information to obtain an exclusive lock on the object in order avoid
     * contention when the object is modified.
     *
     * @param	txn the transaction under which the operation should take place
     * @param	id the object ID
     * @param	forUpdate whether the caller intends to modify the object
     * @return	the data associated with the object ID
     * @throws	IllegalArgumentException if <code>id</code> is negative
     * @throws	ObjectNotFoundException if the object is not found
     * @throws	TransactionException if the operation failed because of a
     *		problem with the current transaction
     */
    byte[] getObject(Transaction txn, long id, boolean forUpdate);

    /**
     * Specifies data to associate with an object ID.
     *
     * @param	txn the transaction under which the operation should take place
     * @param	id the object ID
     * @param	data the data
     * @throws	IllegalArgumentException if <code>id</code> is negative
     * @throws	ObjectNotFoundException if the object is not found
     * @throws	TransactionException if the operation failed because of a
     *		problem with the current transaction
     */
    void setObject(Transaction txn, long id, byte[] data);

    /**
     * Removes the object with the specified object ID.  The implementation
     * will make an effort to flag subsequent references to the removed object
     * by throwing {@link ObjectNotFoundException}, although this behavior is
     * not guaranteed.
     *
     * @param	txn the transaction under which the operation should take place
     * @param	id the object ID
     * @throws	IllegalArgumentException if <code>id</code> is negative
     * @throws	ObjectNotFoundException if the object is not found
     * @throws	TransactionException if the operation failed because of a
     *		problem with the current transaction
     */
    void removeObject(Transaction txn, long id);

    /**
     * Obtains the object ID bound to a name.
     *
     * @param	txn the transaction under which the operation should take place
     * @param	name the name
     * @return	the object ID
     * @throws	NameNotBoundException if no object ID is bound to the name
     * @throws	TransactionException if the operation failed because of a
     *		problem with the current transaction
     */
    long getBinding(Transaction txn, String name);

    /**
     * Binds an object ID to a name.
     *
     * @param	txn the transaction under which the operation should take place
     * @param	name the name
     * @param	the object ID
     * @throws	IllegalArgumentException if <code>id</code> is negative
     * @throws	TransactionException if the operation failed because of a
     *		problem with the current transaction
     */
    void setBinding(Transaction txn, String name, long id);

    /**
     * Removes the binding for a name.
     *
     * @param	txn the transaction under which the operation should take place
     * @param	name the name
     * @throws	NameNotBoundException if the name is not bound
     * @throws	TransactionException if the operation failed because of a
     *		problem with the current transaction
     */
    void removeBinding(Transaction txn, String name);
}
