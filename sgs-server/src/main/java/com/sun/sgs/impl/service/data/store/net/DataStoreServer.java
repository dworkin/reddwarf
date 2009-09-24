/*
 * Copyright 2007-2009 Sun Microsystems, Inc.
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

package com.sun.sgs.impl.service.data.store.net;

import com.sun.sgs.app.NameNotBoundException;
import com.sun.sgs.app.ObjectNotFoundException;
import com.sun.sgs.app.TransactionAbortedException;
import com.sun.sgs.app.TransactionNotActiveException;
import com.sun.sgs.impl.service.data.store.BindingValue;
import com.sun.sgs.service.store.ClassInfoNotFoundException;
import java.io.IOException;
import java.io.ObjectStreamClass;
import java.rmi.Remote;

/** Defines the network interface for the data store server. */
public interface DataStoreServer extends Remote {

    /**
     * Returns a new node ID for use with a newly started node.
     *
     * @return	the new node ID
     * @throws	IOException if a network problem occurs
     */
    long newNodeId() throws IOException;

    /**
     * Reserves an object ID for a new object.  Note that calling other
     * operations using this ID are not required to find the objects until
     * {@link #setObject setObject} is called.  Aborting a transaction is also
     * not required to unassign any of the IDs so long as other operations
     * treat them as non-existent objects.
     *
     * @param	tid the ID of the transaction under which the operation should
     *		take place
     * @return	the new object ID
     * @throws	IllegalArgumentException if {@code tid} is negative
     * @throws	TransactionAbortedException if the transaction was aborted due
     *		to a lock conflict or timeout
     * @throws	TransactionNotActiveException if the transaction is not active
     * @throws	IllegalStateException if the operation failed because of a
     *		problem with the current transaction
     * @throws	IOException if a network problem occurs
     */
    long createObject(long tid) throws IOException;

    /**
     * Notifies the server that an object is going to be modified.
     *
     * @param	tid the ID of the transaction under which the operation should
     *		take place
     * @param	oid the object ID
     * @throws	IllegalArgumentException if {@code tid} or {@code oid} is
     *		negative
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
     * @throws	IllegalArgumentException if {@code tid} or {@code oid} is
     *		negative
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
     * @throws	IllegalArgumentException if {@code tid} or {@code oid} is
     *		negative, or if {@code data} is empty
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
     * @throws	IllegalArgumentException if {@code tid} is negative, if
     *		{@code oids} and {@code data} are not the same length, or if
     *		{@code oids} contains a value that is negative
     * @throws	TransactionAbortedException if the transaction was aborted due
     *		to a lock conflict or timeout
     * @throws	TransactionNotActiveException if the transaction is not active
     * @throws	IllegalStateException if the operation failed because of a
     *		problem with the current transaction
     * @throws	IOException if a network problem occurs
     */
    void setObjects(long tid, long[] oids, byte[][] dataArray)
	throws IOException;

    /**
     * Removes the object with the specified object ID.
     *
     * @param	tid the ID of the transaction under which the operation should
     *		take place
     * @param	oid the object ID
     * @throws	IllegalArgumentException if {@code tid} or {@code oid} is
     *		negative
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
     * Obtains the object ID bound to a name.  If the name is bound, the return
     * value contains the object ID that the name is bound to and a next name
     * of {@code null}.  If the name is not bound, the return value contains an
     * object ID of {@code -1} and the next name found, which may be {@code
     * null}.
     *
     * @param	tid the ID of the transaction under which the operation should
     *		take place
     * @param	name the name
     * @return	information about the object ID and the next name
     * @throws	IllegalArgumentException if {@code tid} is negative
     * @throws	TransactionAbortedException if the transaction was aborted due
     *		to a lock conflict or timeout
     * @throws	TransactionNotActiveException if the transaction is not active
     * @throws	IllegalStateException if the operation failed because of a
     *		problem with the current transaction
     * @throws	IOException if a network problem occurs
     */
    BindingValue getBinding(long tid, String name) throws IOException;

    /**
     * Binds an object ID to a name.  If the name is bound, the return value
     * contains an arbitrary non-negative object ID and a next name of {@code
     * null}.  If the name is not bound, the return value contains an object ID
     * of {@code -1} and the next name found, which may be {@code null}.
     *
     * @param	tid the ID of the transaction under which the operation should
     *		take place
     * @param	name the name
     * @param	oid the object ID
     * @return	information about the object ID and the next name
     * @throws	IllegalArgumentException if {@code tid} or {@code oid} is
     *		negative
     * @throws	TransactionAbortedException if the transaction was aborted due
     *		to a lock conflict or timeout
     * @throws	TransactionNotActiveException if the transaction is not active
     * @throws	IllegalStateException if the operation failed because of a
     *		problem with the current transaction
     * @throws	IOException if a network problem occurs
     */
    BindingValue setBinding(long tid, String name, long oid)
	throws IOException;

    /**
     * Removes the binding for a name.  If the name is bound, the return value
     * contains an arbitrary non-negative object ID, otherwise it contains
     * {@code -1}.  In all cases, the return value contains the next name
     * found, which may be {@code null}.
     *
     * @param	tid the ID of the transaction under which the operation should
     *		take place
     * @param	name the name
     * @return	information about the object ID and the next name
     * @throws	IllegalArgumentException if {@code tid} is negative
     * @throws	TransactionAbortedException if the transaction was aborted due
     *		to a lock conflict or timeout
     * @throws	TransactionNotActiveException if the transaction is not active
     * @throws	IllegalStateException if the operation failed because of a
     *		problem with the current transaction
     * @throws	IOException if a network problem occurs
     */
    BindingValue removeBinding(long tid, String name) throws IOException;

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
     * @throws	IllegalArgumentException if {@code tid} is negative
     * @throws	TransactionAbortedException if the transaction was aborted due
     *		to a lock conflict or timeout
     * @throws	TransactionNotActiveException if the transaction is not active
     * @throws	IllegalStateException if the operation failed because of a
     *		problem with the current transaction
     * @throws	IOException if a network problem occurs
     */
    String nextBoundName(long tid, String name) throws IOException;

    /**
     * Returns the class ID to represent classes with the specified class
     * information.  Obtains an existing ID for the class information if
     * present; otherwise, stores the information and returns the new ID
     * associated with it.  Class IDs are always greater than {@code 0}.  The
     * class information is the serialized form of the {@link
     * ObjectStreamClass} instance that serialization uses to represent the
     * class.
     *
     * @param	tid the ID of the transaction under which the operation should
     *		take place
     * @param	classInfo the class information
     * @return	the associated class ID
     * @throws	IllegalArgumentException if {@code tid} is negative
     * @throws	TransactionAbortedException if the transaction was aborted due
     *		to a lock conflict or timeout
     * @throws	TransactionNotActiveException if the transaction is not active
     * @throws	IllegalStateException if the operation failed because of a
     *		problem with the current transaction
     * @throws	IOException if a network problem occurs
     */
    int getClassId(long tid, byte[] classInfo) throws IOException;

    /**
     * Returns the class information associated with the specified class ID.
     * The class information is the serialized form of the {@link
     * ObjectStreamClass} instance that serialization uses to represent the
     * class.
     *
     * @param	tid the ID of the transaction under which the operation should
     *		take place
     * @param	classId the class ID
     * @return	the associated class information
     * @throws	IllegalArgumentException if {@code tid} is negative, or if
     *		{@code classId} is not greater than {@code 0}
     * @throws	ClassInfoNotFoundException if the ID is not found
     * @throws	TransactionAbortedException if the transaction was aborted due
     *		to a lock conflict or timeout
     * @throws	IllegalStateException if the operation failed because of a
     *		problem with the transaction
     * @throws	IOException if a network problem occurs
     */
    byte[] getClassInfo(long tid, int classId)
	throws ClassInfoNotFoundException, IOException;

    /**
     * Returns the object ID for the next object after the object with the
     * specified ID, or {@code -1} if there are no more objects.  If {@code
     * objectId} is {@code -1}, then returns the ID of the first object.  The
     * IDs returned by this method will not include ones for objects that have
     * already been removed, and may not include identifiers for objects
     * created after an iteration has begun.  It is not an error for the object
     * associated with the specified identifier to have already been
     * removed. <p>
     *
     * @param	tid the ID of the transaction
     * @param	oid the identifier of the object to search after, or
     *		{@code -1} to request the first object
     * @return	the identifier of the next object following the object with
     *		identifier {@code oid}, or {@code -1} if there are no more
     *		objects
     * @throws	IllegalArgumentException if {@code tid} or {@code oid} is
     *		negative
     * @throws	TransactionAbortedException if the transaction was aborted due
     *		to a lock conflict or timeout
     * @throws	TransactionNotActiveException if the transaction is not active
     * @throws	IllegalStateException if the operation failed because of a
     *		problem with the current transaction
     * @throws	IOException if a network problem occurs
     */
    long nextObjectId(long tid, long oid) throws IOException;

    /** 
     * Creates a new transaction, and returns the associated ID, which will not
     * be negative.
     *
     * @param	timeout the number of milliseconds the resulting transaction
     *		should be allowed to run before it times out
     * @return	the ID of the new transaction
     * @throws	IllegalArgumentException if the argument is less than or equal
     *		to {@code 0}
     * @throws	IOException if a network problem occurs
     */
    long createTransaction(long timeout) throws IOException;

    /**
     * Prepares the transaction to commit.  Returns {@code true} when no state
     * was modified, and neither {@code commit} or {@code abort} should be
     * called.
     *
     * @param	tid the ID of the transaction
     * @return	{@code true} if this participant is read-only, otherwise
     *		{@code false}
     * @throws	IllegalArgumentException if {@code tid} is negative
     * @throws	IllegalStateException if the transaction has been prepared,
     *		committed, or aborted, or if the transaction is not known
     * @throws	IOException if a network problem occurs
     */
    boolean prepare(long tid) throws IOException;

    /**
     * Commits the transaction.
     *
     * @param	tid the ID of the transaction
     * @throws	IllegalArgumentException if {@code tid} is negative
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
     * @throws	IllegalArgumentException if {@code tid} is negative
     * @throws	IllegalStateException if the transaction has been prepared,
     *		committed, or aborted, or if the transaction is not known
     * @throws	IOException if a network problem occurs
     */
    void prepareAndCommit(long tid) throws IOException;

    /**
     * Aborts the transaction.
     *
     * @param	tid the ID of the transaction
     * @throws	IllegalArgumentException if {@code tid} is negative
     * @throws	IllegalStateException if the transaction has been committed or
     *		aborted, or if the transaction is not known
     * @throws	IOException if a network problem occurs
     */
    void abort(long tid) throws IOException;
}
