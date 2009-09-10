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
 *
 * Sun designates this particular file as subject to the "Classpath"
 * exception as provided by Sun in the LICENSE file that accompanied
 * this code.
 */

package com.sun.sgs.service.store;

import com.sun.sgs.app.NameNotBoundException;
import com.sun.sgs.app.ObjectNotFoundException;
import com.sun.sgs.app.TransactionAbortedException;
import com.sun.sgs.app.TransactionNotActiveException;
import com.sun.sgs.kernel.AccessReporter;
import com.sun.sgs.service.Transaction;
import java.io.ObjectStreamClass;

/**
 * Defines the interface to the underlying persistence mechanism to 
 * store byte data. <p>
 *
 * Objects are identified by object IDs, which are positive
 * <code>long</code>s.  Names are mapped to object IDs.
 */
public interface DataStore {

    /**
     * Notifies the data store that services associated with the application
     * have been successfully created.  If the method throws an exception, then
     * the application should be shutdown.
     *
     * @throws Exception if an error occurs
     */
    void ready() throws Exception;

    /**
     * Returns the node ID for the local node.
     *
     * @return	the node ID for the local node
     */
    long getLocalNodeId();

    /**
     * Reserves an object ID for a new object.  Note that calling other
     * operations using this ID are not required to find the object until
     * {@link #setObject setObject} or {@link #setObjects setObjects} is
     * called.  Aborting a transaction is also not required to unassign the ID
     * so long as other operations treat it as a non-existent object.
     *
     * @param	txn the transaction under which the operation should take place
     * @return	the new object ID
     * @throws	TransactionAbortedException if the transaction was aborted due
     *		to a lock conflict or timeout
     * @throws	TransactionNotActiveException if the transaction is not active
     * @throws	IllegalStateException if the operation failed because of a
     *		problem with the current transaction
     */
    long createObject(Transaction txn);

    /**
     * Notifies the <code>DataStore</code> that an object is going to be
     * modified.  The implementation can use this information to obtain an
     * exclusive lock on the object in order to avoid contention when the
     * object is modified.  This method does nothing if the object does not
     * exist.
     *
     * @param	txn the transaction under which the operation should take place
     * @param	oid the object ID
     * @throws	IllegalArgumentException if <code>oid</code> is negative
     * @throws	TransactionAbortedException if the transaction was aborted due
     *		to a lock conflict or timeout
     * @throws	TransactionNotActiveException if the transaction is not active
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
     * @throws	TransactionNotActiveException if the transaction is not active
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
     * @throws	IllegalArgumentException if <code>oid</code> is negative
     * @throws	TransactionAbortedException if the transaction was aborted due
     *		to a lock conflict or timeout
     * @throws	TransactionNotActiveException if the transaction is not active
     * @throws	IllegalStateException if the operation failed because of a
     *		problem with the current transaction
     */
    void setObject(Transaction txn, long oid, byte[] data);

    /** 
     * Specifies data to associate with a series of object IDs.
     *
     * @param	txn the transaction under which the operation should take place
     * @param	oids the object IDs
     * @param	dataArray the associated data values
     * @throws	IllegalArgumentException if <code>oids</code> and
     *		<code>data</code> are not the same length, or if
     *		<code>oids</code> contains a value that is negative
     * @throws	TransactionAbortedException if the transaction was aborted due
     *		to a lock conflict or timeout
     * @throws	TransactionNotActiveException if the transaction is not active
     * @throws	IllegalStateException if the operation failed because of a
     *		problem with the current transaction
     */
    void setObjects(Transaction txn, long[] oids, byte[][] dataArray);

    /**
     * Removes the object with the specified object ID.  The implementation
     * will make an effort to flag subsequent references to the removed object
     * by throwing {@link ObjectNotFoundException}, although this behavior is
     * not guaranteed.  The implementation is not required to check that the
     * object is an externally visible object rather than one used internally
     * by the implementation.
     *
     * @param	txn the transaction under which the operation should take place
     * @param	oid the object ID
     * @throws	IllegalArgumentException if <code>oid</code> is negative
     * @throws	ObjectNotFoundException if the object is not found
     * @throws	TransactionAbortedException if the transaction was aborted due
     *		to a lock conflict or timeout
     * @throws	TransactionNotActiveException if the transaction is not active
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
     * @throws	TransactionNotActiveException if the transaction is not active
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
     * @throws	TransactionNotActiveException if the transaction is not active
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
     * @throws	TransactionNotActiveException if the transaction is not active
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
     * @throws	TransactionNotActiveException if the transaction is not active
     * @throws	IllegalStateException if the operation failed because of a
     *		problem with the current transaction
     */
    String nextBoundName(Transaction txn, String name);

    /** 
     * Shuts down this data store. This method will block until the shutdown
     * is complete.<p>
     */
    void shutdown();

    /**
     * Returns the class ID to represent classes with the specified class
     * information.  Obtains an existing ID for the class information if
     * present; otherwise, stores the information and returns the new ID
     * associated with it.  Class IDs are always greater than {@code 0}.  The
     * class information is the serialized form of the {@link
     * ObjectStreamClass} instance that serialization uses to represent the
     * class.
     *
     * @param	txn the transaction under which the operation should take place
     * @param	classInfo the class information
     * @return	the associated class ID
     * @throws	TransactionAbortedException if the transaction was aborted due
     *		to a lock conflict or timeout
     * @throws	TransactionNotActiveException if the transaction is not active
     * @throws	IllegalStateException if the operation failed because of a
     *		problem with the current transaction
     */
    int getClassId(Transaction txn, byte[] classInfo);

    /**
     * Returns the class information associated with the specified class ID.
     * The class information is the serialized form of the {@link
     * ObjectStreamClass} instance that serialization uses to represent the
     * class.
     *
     * @param	txn the transaction under which the operation should take place
     * @param	classId the class ID
     * @return	the associated class information
     * @throws	IllegalArgumentException if {@code classId} is not greater than
     *		{@code 0}
     * @throws	ClassInfoNotFoundException if the ID is not found
     * @throws	TransactionAbortedException if the transaction was aborted due
     *		to a lock conflict or timeout
     * @throws	IllegalStateException if the operation failed because of a
     *		problem with the transaction
     */
    byte[] getClassInfo(Transaction txn, int classId)
	throws ClassInfoNotFoundException;

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
     * Applications should not assume that objects associated with the IDs
     * returned by this method, but which cannot be reached by traversing
     * object field references starting with an object associated with a name
     * binding, will continue to be retained by the data store.
     *
     * @param	txn the transaction under which the operation should take place
     * @param	oid the identifier of the object to search after, or
     *		{@code -1} to request the first object
     * @return	the identifier of the next object following the object with
     *		identifier {@code oid}, or {@code -1} if there are no more
     *		objects
     * @throws	IllegalArgumentException if the argument is less than {@code -1}
     * @throws	TransactionAbortedException if the transaction was aborted due
     *		to a lock conflict or timeout
     * @throws	IllegalStateException if the operation failed because of a
     *		problem with the current transaction
     */
    long nextObjectId(Transaction txn, long oid);

    /**
     * Associates a description with an object ID, for use in describing object
     * accesses.  The {@code description} should provide a meaningful {@code
     * toString} method.
     *
     * @param	txn the transaction under which the operation should take place
     * @param	oid the object ID
     * @param	description the description
     * @throws	IllegalArgumentException if <code>oid</code> is negative
     * @throws	IllegalStateException if the operation failed because of a
     *		problem with the current transaction
     * @see	AccessReporter#setObjectDescription
     *		AccessReporter.setObjectDescription
     */
    void setObjectDescription(Transaction txn, long oid, Object description);

    /**
     * Associates a description with a bound name, for use in describing name
     * accesses.  The {@code description} should provide a meaningful {@code
     * toString} method.
     *
     * @param	txn the transaction under which the operation should take place
     * @param	name the name
     * @param	description the description
     * @throws	IllegalStateException if the operation failed because of a
     *		problem with the current transaction
     * @see	AccessReporter#setObjectDescription
     *		AccessReporter.setObjectDescription
     */
    void setBindingDescription(
	Transaction txn, String name, Object description);
}
