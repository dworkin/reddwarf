/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.impl.service.data.store.net;

import com.sun.sgs.app.TransactionAbortedException;
import com.sun.sgs.app.TransactionNotActiveException;
import java.io.IOException;
import java.rmi.Remote;

/** Defines the network interface for the data store server. */
public interface DataStoreServer extends Remote {

    /**
     * Reserves a batch of object IDs for allocating new objects.  This
     * operation is performed in its own transaction.
     *
     * @param	count the number of object IDs to reserve
     * @return	the next available object ID
     * @throws	IllegalArgumentException if {@code count} is less than
     *		{@code 1}
     * @throws	IOException if a network problem occurs
     */
    long allocateObjects(int count) throws IOException;

    /**
     * Notifies the server that an object is going to be modified.
     *
     * @param	tid the ID of the transaction under which the operation should
     *		take place
     * @param	oid the object ID
     * @throws	IllegalArgumentException if {@code oid} is negative
     * @throws	ObjectNotFoundException if the object is not found
     * @throws	TransactionAbortedException if the transaction was aborted due
     *		to a lock conflict or timeout
     * @throws	TransactionNotActiveException if the transaction is not active
     * @throws	IllegalStateException if the operation failed because of a
     *		problem with the current transaction
     * @throws	IOException if a network problem occurs
     */
    void markForUpdate(long tid, long oid) throws IOException;

    /**
     * Obtains the data associated with an object ID.  If the {@code forUpdate}
     * parameter is {@code true}, the caller is stating its intention to modify
     * the object.
     *
     * @param	tid the ID of the transaction under which the operation should
     *		take place
     * @param	oid the object ID
     * @param	forUpdate whether the caller intends to modify the object
     * @return	the data associated with the object ID
     * @throws	IllegalArgumentException if {@code oid} is negative
     * @throws	ObjectNotFoundException if the object is not found
     * @throws	TransactionAbortedException if the transaction was aborted due
     *		to a lock conflict or timeout
     * @throws	TransactionNotActiveException if the transaction is not active
     * @throws	IllegalStateException if the operation failed because of a
     *		problem with the current transaction
     * @throws	IOException if a network problem occurs
     */
    byte[] getObject(long tid, long oid, boolean forUpdate)
	throws IOException;

    /**
     * Specifies data to associate with an object ID.
     *
     * @param	tid the ID of the transaction under which the operation should
     *		take place
     * @param	oid the object ID
     * @param	data the data
     * @throws	IllegalArgumentException if {@code oid} is negative, or if
     *		{@code data} is empty
     * @throws	TransactionAbortedException if the transaction was aborted due
     *		to a lock conflict or timeout
     * @throws	TransactionNotActiveException if the transaction is not active
     * @throws	IllegalStateException if the operation failed because of a
     *		problem with the current transaction
     * @throws	IOException if a network problem occurs
     */
    void setObject(long tid, long oid, byte[] data) throws IOException;

    /** 
     * Specifies data to associate with a series of object IDs.
     *
     * @param	tid the ID of the transaction under which the operation should
     *		take place
     * @param	oids the object IDs
     * @param	dataArray the associated data values
     * @throws	IllegalArgumentException if {@code oids} and {@code data} are
     *		not the same length, or if {@code oids} contains a value that
     *		is negative
     * @throws	TransactionAbortedException if the transaction was aborted due
     *		to a lock conflict or timeout
     * @throws	TransactionNotActiveException if the transaction is not active
     * @throws	IllegalStateException if the operation failed because of a
     *		problem with the current transaction
     */
    void setObjects(long tid, long[] oids, byte[][] dataArray)
	throws IOException;

    /**
     * Removes the object with the specified object ID.
     *
     * @param	tid the ID of the transaction under which the operation should
     *		take place
     * @param	oid the object ID
     * @throws	IllegalArgumentException if {@code oid} is negative
     * @throws	ObjectNotFoundException if the object is not found
     * @throws	TransactionAbortedException if the transaction was aborted due
     *		to a lock conflict or timeout
     * @throws	TransactionNotActiveException if the transaction is not active
     * @throws	IllegalStateException if the operation failed because of a
     *		problem with the current transaction
     * @throws	IOException if a network problem occurs
     */
    void removeObject(long tid, long oid) throws IOException;

    /**
     * Obtains the object ID bound to a name.
     *
     * @param	tid the ID of the transaction under which the operation should
     *		take place
     * @param	name the name
     * @return	the object ID
     * @throws	NameNotBoundException if no object ID is bound to the name
     * @throws	TransactionAbortedException if the transaction was aborted due
     *		to a lock conflict or timeout
     * @throws	TransactionNotActiveException if the transaction is not active
     * @throws	IllegalStateException if the operation failed because of a
     *		problem with the current transaction
     * @throws	IOException if a network problem occurs
     */
    long getBinding(long tid, String name) throws IOException;

    /**
     * Binds an object ID to a name.
     *
     * @param	tid the ID of the transaction under which the operation should
     *		take place
     * @param	name the name
     * @param	oid the object ID
     * @throws	IllegalArgumentException if {@code oid} is negative
     * @throws	TransactionAbortedException if the transaction was aborted due
     *		to a lock conflict or timeout
     * @throws	TransactionNotActiveException if the transaction is not active
     * @throws	IllegalStateException if the operation failed because of a
     *		problem with the current transaction
     * @throws	IOException if a network problem occurs
     */
    void setBinding(long tid, String name, long oid) throws IOException;

    /**
     * Removes the binding for a name.
     *
     * @param	tid the ID of the transaction under which the operation should
     *		take place
     * @param	name the name
     * @throws	NameNotBoundException if the name is not bound
     * @throws	TransactionAbortedException if the transaction was aborted due
     *		to a lock conflict or timeout
     * @throws	TransactionNotActiveException if the transaction is not active
     * @throws	IllegalStateException if the operation failed because of a
     *		problem with the current transaction
     * @throws	IOException if a network problem occurs
     */
    void removeBinding(long tid, String name) throws IOException;

    /**
     * Returns the next name after the specified name that has a binding, or
     * {@code null} if there are no more bound names.  If {@code name} is
     * {@code null}, then the search starts at the beginning.
     *
     * @param	tid the ID of the transaction under which the operation should
     *		take place
     * @param	name the name to search after, or {@code null} to start at the
     *		beginning
     * @return	the next name with a binding following {@code name}, or
     *		{@code null} if there are no more bound names
     * @throws	TransactionAbortedException if the transaction was aborted due
     *		to a lock conflict or timeout
     * @throws	TransactionNotActiveException if the transaction is not active
     * @throws	IllegalStateException if the operation failed because of a
     *		problem with the current transaction
     * @throws	IOException if a network problem occurs
     */
    String nextBoundName(long tid, String name) throws IOException;

    /** 
     * Creates a new transaction and returns the associated ID.
     *
     * @return	the ID of the new transaction
     * @throws	IOException if a network problem occurs
     */
    long createTransaction() throws IOException;

    /**
     * Prepares the transaction to commit.  Returns {@code true} when no state
     * was modified, and neither {@code commit} or {@code abort} should be
     * called.
     *
     * @param	tid the ID of the transaction
     * @return	{@code true} if this participant is read-only, otherwise
     *		{@code false}
     * @throws	Exception if there are any failures in preparing
     * @throws	IllegalStateException if the transaction has been prepared,
     *		committed, or aborted, or if the transaction is not known
     * @throws	IOException if a network problem occurs
     */
    boolean prepare(long tid) throws IOException;

    /**
     * Commits the transaction.
     *
     * @param	tid the ID of the transaction
     * @throws	IllegalStateException if the transaction has not been
     *		prepared, if it has been committed or aborted, or if the
     *		transaction is not known
     * @throws	IOException if a network problem occurs
     */
    void commit(long tid) throws IOException;

    /**
     * Prepares and commits the transaction.
     *
     * @param	tid the ID of the transaction
     * @throws	IllegalStateException if the transaction has been prepared,
     *		committed, or aborted, or if the transaction is not known
     * @throws	IOException if a network problem occurs
     */
    void prepareAndCommit(long tid) throws IOException;

    /**
     * Aborts the transaction.
     *
     * @param	tid the ID of the transaction
     * @throws	IllegalStateException if the transaction has been committed or
     *		aborted, or if the transaction is not known
     * @throws	IOException if a network problem occurs
     */
    void abort(long tid) throws IOException;
}
