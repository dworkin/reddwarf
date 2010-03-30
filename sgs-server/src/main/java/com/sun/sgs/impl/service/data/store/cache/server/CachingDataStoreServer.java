/*
 * Copyright 2007-2010 Sun Microsystems, Inc.
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
 * --
 */

package com.sun.sgs.impl.service.data.store.cache.server;

import com.sun.sgs.app.TransactionAbortedException;
import com.sun.sgs.impl.service.data.store.cache.CacheConsistencyException;
import com.sun.sgs.impl.service.data.store.cache.CallbackServer;
import static com.sun.sgs.impl.sharedutil.Objects.checkNull;
import java.io.IOException;
import java.io.ObjectStreamClass;
import java.io.Serializable;
import java.rmi.Remote;

/** Defines the network interface for the caching data store server. */
public interface CachingDataStoreServer extends Remote {

    /**
     * Registers a node with the server, supplying a callback server that the
     * server can use to make callback requests.  Returns the ID for the new
     * node and the socket port that the node should connect to for sending
     * updates to the server's update queue.
     *
     * @param	callbackServer the callback server
     * @return	the node ID and update queue port
     * @throws	IllegalArgumentException if {@code nodeId} has already been
     *		registered
     * @throws	IOException if a network problem occurs
     */
    RegisterNodeResult registerNode(CallbackServer callbackServer)
	throws IOException;

    /** The results of a call to {@link #registerNode}. */
    final class RegisterNodeResult implements Serializable {

	/** The version of the serialized form. */
	private static final long serialVersionUID = 1;

	/** The node ID for the new node. */
	public final long nodeId;

	/** The port for connecting to the update queue. */
	public final int updateQueuePort;

	/**
	 * Creates an instance of this class.
	 *
	 * @param	nodeId the node ID for the new node
	 * @param	updateQueuePort the update queue port
	 */
	public RegisterNodeResult(long nodeId, int updateQueuePort) {
	    this.nodeId = nodeId;
	    this.updateQueuePort = updateQueuePort;
	}

	@Override
	public String toString() {
	    return "RegisterNodeResult[" +
		"nodeId:" + nodeId +
		", updateQueuePort:" + updateQueuePort +
		"]";
	}
    }

    /* -- Object methods -- */

    /**
     * Reserves a block of object IDs to use for allocating new objects.
     *
     * @param	numIds the number of object IDs to reserve
     * @return	the first new object ID
     * @throws	IllegalArgumentException if {@code numIds} is less than
     *		{@code 1}
     * @throws	IOException if a network problem occurs
     * @throws	TransactionAbortedException if the transaction performed by the
     *		server was aborted due to a lock conflict or timeout
     */
    long newObjectIds(int numIds) throws IOException;

    /**
     * Obtains read access to an object.  If the return value is not {@code
     * null} and its {@code callbackEvict} field is {@code true}, then the
     * caller must evict the requested object after using it.
     *
     * @param	nodeId the ID of the requesting node
     * @param	oid the object ID
     * @return	information about the object, or {@code null} if the object is
     *		not found
     * @throws	IllegalArgumentException if {@code nodeId} has not been
     *		registered or if {@code oid} is negative
     * @throws	IOException if a network problem occurs
     * @throws	TransactionAbortedException if the transaction performed by the
     *		server was aborted due to a lock conflict or timeout
     */
    GetObjectResults getObject(long nodeId, long oid) throws IOException;

    /** The results of a call to {@link #getObject}. */
    class GetObjectResults implements Serializable {

	/** The version of the serialized form. */
	private static final long serialVersionUID = 1;

	/** The object data. */
	public final byte[] data;

	/** Whether to evict the object. */
	public final boolean callbackEvict;

	/**
	 * Creates an instance of this class.
	 *
	 * @param	data the object data
	 * @param	callbackEvict whether to evict the object
	 */
	public GetObjectResults(byte[] data, boolean callbackEvict) {
	    checkNull("data", data);
	    this.data = data;
	    this.callbackEvict = callbackEvict;
	}

	@Override
	public String toString() {
	    return "GetObjectResults[" +
		"data:" +
		(data == null ? "null" : "byte[" + data.length + "]") +
		", callbackEvict:" + callbackEvict +
		"]";
	}
    }

    /**
     * Obtains write access to an object.  If the return value is not {@code
     * null} and its {@code callbackEvict} field is {@code true}, then the
     * caller must evict the requested object after using it.  If the return
     * value is not {@code null} and its {@code callbackDowngrade} field is
     * {@code true}, then the caller must downgrade the requested object to
     * read access after using it.
     *
     * @param	nodeId the ID of the requesting node
     * @param	oid the object ID
     * @return	information about the object, or {@code null} if the object is
     *		not found
     * @throws	IllegalArgumentException if {@code nodeId} has not been
     *		registered or if {@code oid} is negative
     * @throws	IOException if a network problem occurs
     * @throws	TransactionAbortedException if the transaction performed by the
     *		server was aborted due to a lock conflict or timeout
     */
    GetObjectForUpdateResults getObjectForUpdate(long nodeId, long oid)
	throws IOException;

    /** The results of a call to {@link #getObjectForUpdate}. */
    final class GetObjectForUpdateResults extends GetObjectResults {

	/** The version of the serialized form. */
	private static final long serialVersionUID = 1;

	/** Whether to downgrade the object. */
	public final boolean callbackDowngrade;

	/**
	 * Creates an instance of this class.
	 *
	 * @param	data the object data
	 * @param	callbackEvict whether to evict the object
	 * @param	callbackDowngrade whether to downgrade the object
	 */
	public GetObjectForUpdateResults(
	    byte[] data, boolean callbackEvict, boolean callbackDowngrade)
	{
	    super(data, callbackEvict);
	    this.callbackDowngrade = callbackDowngrade;
	}

	@Override
	public String toString() {
	    return "GetObjectForUpdateResults[" +
		"data:" +
		(data == null ? "null" : "byte[" + data.length + "]") +
		", callbackEvict:" + callbackEvict +
		", callbackDowngrade:" + callbackDowngrade +
		"]";
	}
    }

    /**
     * Upgrades access to an object from read to write access.  If the return
     * value's {@code callbackEvict} field is {@code true}, then the caller
     * must evict the requested object after using it.  If the return value's
     * {@code callbackDowngrade} field is {@code true}, then the caller must
     * downgrade the requested object to read access after using it.
     *
     * @param	nodeId the ID of the requesting node
     * @param	oid the object ID
     * @return	information about the object
     * @throws	CacheConsistencyException if the requesting node does not have
     *		read access for the object ID
     * @throws	IllegalArgumentException if {@code nodeId} has not been
     *		registered or if {@code oid} is negative
     * @throws	IOException if a network problem occurs
     * @throws	TransactionAbortedException if the transaction performed by the
     *		server was aborted due to a lock conflict or timeout
     */
    UpgradeObjectResults upgradeObject(long nodeId, long oid)
	throws CacheConsistencyException, IOException;

    /** The results of a call to {@link #upgradeObject}. */
    final class UpgradeObjectResults implements Serializable {

	/** The version of the serialized form. */
	private static final long serialVersionUID = 1;

	/** Whether to evict the object. */
	public final boolean callbackEvict;

	/** Whether to downgrade the object. */
	public final boolean callbackDowngrade;

	/**
	 * Creates an instance of this class.
	 *
	 * @param	callbackEvict whether to evict the object
	 * @param	callbackDowngrade whether to downgrade the object
	 */
	public UpgradeObjectResults(boolean callbackEvict,
				    boolean callbackDowngrade)
	{
	    this.callbackEvict = callbackEvict;
	    this.callbackDowngrade = callbackDowngrade;
	}

	@Override
	public String toString() {
	    return "UpgradeObjectResults[" +
		"callbackEvict:" + callbackEvict +
		", callbackDowngrade:" + callbackDowngrade +
		"]";
	}
    }

    /**
     * Returns information about the next object after the object with the
     * specified ID, obtaining read access to the next object if it is found.
     * If {@code oid} is {@code -1}, then returns information about the first
     * object.  The results returned by this method will not refer to objects
     * that have already been removed, and may not refer to objects created
     * after an iteration has begun.  It is not an error to specify the
     * identifier for an object that has already been removed.  If the return
     * value is not {@code null} and its {@code callbackEvict} field is {@code
     * true}, then the caller must evict the next object after using it.
     *
     * @param	nodeId the ID of the requesting node
     * @param	oid the identifier of the object to search after, or
     *		{@code -1} to request the first object
     * @return	information about the next object, or {@code null} if there are
     *		no more objects
     * @throws	IllegalArgumentException if {@code nodeId} has not been
     *		registered or if {@code oid} is less than {@code -1}
     * @throws	IOException if a network problem occurs
     * @throws	TransactionAbortedException if the transaction performed by the
     *		server was aborted due to a lock conflict or timeout
     */
    NextObjectResults nextObjectId(long nodeId, long oid) throws IOException;

    /** The results of a call to {@link #nextObjectId}. */
    final class NextObjectResults implements Serializable {

	/** The version of the serialized form. */
	private static final long serialVersionUID = 1;

	/** The ID of the next object. */
	public final long oid;

	/** The object data associated with the next object. */
	public final byte[] data;

	/** Whether to evict the next object. */
	public final boolean callbackEvict;

	/**
	 * Creates an instance of this class.
	 *
	 * @param	oid the next object ID
	 * @param	data the object data
	 * @param	callbackEvict whether to evict the next object
	 */
	public NextObjectResults(
	    long oid, byte[] data, boolean callbackEvict) {
	    if (oid < 0) {
		throw new IllegalArgumentException(
		    "The oid argument must not be negative");
	    }
	    checkNull("data", data);
	    this.oid = oid;
	    this.data = data;
	    this.callbackEvict = callbackEvict;
	}

	@Override
	public String toString() {
	    return "NextObjectResults[" +
		"oid:" + oid +
		", data:" +
		(data == null ? "null" : "byte[" + data.length + "]") +
		", callbackEvict:" + callbackEvict +
		"]";
	}
    }

    /* -- Name binding methods -- */

    /**
     * Obtains read access to a name binding.  If the name is not bound,
     * obtains read access to the next name binding instead.  If the return
     * value's {@code callbackEvict} field is {@code true}, then the caller
     * must evict after using it either the requested name, if the return
     * value's {@code found} field is {@code true}, or else the next name, if
     * the {@code found} field is {@code false}.
     *
     * @param	nodeId the ID of the requesting node
     * @param	name the name
     * @return	information about the name binding
     * @throws	IllegalArgumentException if {@code nodeId} has not been
     *		registered
     * @throws	IOException if a network problem occurs
     * @throws	TransactionAbortedException if the transaction performed by the
     *		server was aborted due to a lock conflict or timeout
     */
    GetBindingResults getBinding(long nodeId, String name) throws IOException;

    /**
     * The results of a call to {@link #getBinding}. <p>
     *
     * If {@link #found} is {@code true}, then the name is bound.  In that
     * case, {@link #nextName} is {@code null}, {@link #oid} is the object ID
     * that is bound to the name, and {@link #callbackEvict} records whether to
     * evict the requested name. <p>
     *
     * If {@code found} is {@code false}, then the name was not bound.  In that
     * case, {@code nextName} is the next bound name following the name, or
     * {@code null} if there is no next bound name, {@code oid} is the object
     * ID that is bound to that next name, or {@code -1} if {@code nextName} is
     * {@code null}, and {@code callbackEvict} records whether to evict the
     * next name.
     */
    class GetBindingResults implements Serializable {

	/** The version of the serialized form. */
	private static final long serialVersionUID = 1;

	/** Whether the requested name is bound. */
	public final boolean found;

	/**
	 * The next bound name after the requested name, or {@code null} if
	 * either there is no next bound name or {@code found} is {@code true}.
	 */
	public final String nextName;

	/**
	 * The object ID associated with the requested name, if {@code found}
	 * is {@code true}, else the object ID of the next name, if {@code
	 * nextName} is not {@code null}, else {@code -1}.
	 */
	public final long oid;

	/** Whether to evict the requested or next name. */
	public final boolean callbackEvict;

	/**
	 * Creates an instance of this class.
	 *
	 * @param	found whether the requested name was found
	 * @param	nextName the next bound name or {@code null}
	 * @param	oid the object ID or {@code -1}
	 * @param	callbackEvict whether to evict the requested or next
	 *		name
	 */
	public GetBindingResults(
	    boolean found, String nextName, long oid, boolean callbackEvict)
	{
	    this.found = found;
	    this.nextName = nextName;
	    this.oid = oid;
	    this.callbackEvict = callbackEvict;
	}

	@Override
	public String toString() {
	    return "GetBindingResults[" +
		"found:" + found +
		", nextName:" + nextName +
		", oid:" + oid +
		", callbackEvict:" + callbackEvict +
		"]";
	}
    }

    /**
     * Obtains write access to a name binding.  If the name is not bound,
     * obtains write access to the next name binding instead.  If the return
     * value's {@code callbackEvict} field is {@code true}, then the caller
     * must evict after using it either the requested name, if the return
     * value's {@code found} field is {@code true}, or else the next name, if
     * the {@code found} field is {@code false}.  If the return value's {@code
     * callbackDowngrade} field is {@code true}, then the caller must
     * downgrade to read access after using it either the requested name, if
     * the return value's {@code found} field is {@code true}, or else the next
     * name, if the {@code found} field is {@code false}.
     *
     * @param	nodeId the ID of the requesting node
     * @param	name the name
     * @return	information about the name binding
     * @throws	IllegalArgumentException if {@code nodeId} has not been
     *		registered
     * @throws	IOException if a network problem occurs
     * @throws	TransactionAbortedException if the transaction performed by the
     *		server was aborted due to a lock conflict or timeout
     */
    GetBindingForUpdateResults getBindingForUpdate(long nodeId, String name)
	throws IOException;

    /** The results of a call to {@link #getBindingForUpdate}. */
    final class GetBindingForUpdateResults extends GetBindingResults {

	/** The version of the serialized form. */
	private static final long serialVersionUID = 1;

	/** Whether the requested or next name must be downgraded. */
	public final boolean callbackDowngrade;

	/**
	 * Creates an instance of this class.
	 *
	 * @param	found whether the requested name was found
	 * @param	nextName the next bound name
	 * @param	oid the object ID or {@code -1}
	 * @param	callbackEvict whether to evict the requested or next
	 *		name
	 * @param	callbackDowngrade whether to downgrade the requested or
	 *		next name
	 */
	public GetBindingForUpdateResults(boolean found,
					  String nextName,
					  long oid,
					  boolean callbackEvict,
					  boolean callbackDowngrade)
	{
	    super(found, nextName, oid, callbackEvict);
	    this.callbackDowngrade = callbackDowngrade;
	}

	@Override
	public String toString() {
	    return "GetBindingForUpdateResults[" +
		"found:" + found +
		", nextName:" + nextName +
		", oid:" + oid +
		", callbackEvict:" + callbackEvict +
		", callbackDowngrade:" + callbackDowngrade +
		"]";
	}
    }

    /**
     * Obtains access needed to remove a name binding, including write access
     * to the name binding if it is bound.  If the name is bound, obtains write
     * access to the next bound name; if it is not bound, obtains read access
     * to the next bound name.  If the return value's {@code callbackEvict}
     * field is {@code true}, then the caller must evict the requested name
     * after using it.  If the return value's {@code callbackDowngrade} field
     * is {@code true}, then the caller must downgrade the requested name to
     * read access after using it.  If the return value's {@code
     * nextCallbackEvict} field is {@code true}, then the caller must evict the
     * next name after using it.  If the return value's {@code
     * nextCallbackDowngrade} field is {@code true}, then the caller must
     * downgrade the next name to read access after using it.
     *
     * @param	nodeId the ID of the requesting node
     * @param	name the name
     * @return	information about the object ID and the next name
     * @throws	IllegalArgumentException if {@code nodeId} has not been
     *		registered
     * @throws	IOException if a network problem occurs
     * @throws	TransactionAbortedException if the transaction performed by the
     *		server was aborted due to a lock conflict or timeout
     */
    GetBindingForRemoveResults getBindingForRemove(long nodeId, String name)
	throws IOException;

    /**
     * The results of a call to {@link #getBindingForRemove}. <p>
     *
     * In all cases, {@link #nextName} is the next bound name following the
     * requested name, or {@code null} if there is no next bound name, {@link
     * #nextOid} is the object ID that is bound to that next name, or {@code
     * -1} if {@code nextName} is {@code null}, and {@link #nextCallbackEvict}
     * records whether to evict the next name. <p>
     *
     * If {@link #found} is {@code true}, then the name is bound.  In that
     * case, {@link #oid} is the object ID that is bound to the requested name,
     * {@link #callbackEvict} records whether to evict the requested name,
     * {@link #callbackDowngrade} records whether to downgrade the name to read
     * access, and {@link #nextCallbackDowngrade} records whether to downgrade
     * the next name to read access. <p>
     *
     * If {@code found} is {@code false}, then the name is not bound.  In that
     * case, {@code oid} is {@code -1}, and {@code callbackEvict}, {@code
     * callbackDowngrade}, and {@code nextCallbackDowngrade} are {@code false}.
     */
    final class GetBindingForRemoveResults implements Serializable {

	/** The version of the serialized form. */
	private static final long serialVersionUID = 1;

	/** Whether the requested name is bound. */
	public final boolean found;

	/**
	 * The next bound name after the requested one, or {@code null} if
	 * there is no next bound name.
	 */
	public final String nextName;

	/**
	 * The object ID associated with the requested name, if {@code found}
	 * is {@code true}, else {@code -1}.
	 */
	public final long oid;

	/** Whether to evict the requested name. */
	public final boolean callbackEvict;

	/** Whether to downgrade the requested name. */
	public final boolean callbackDowngrade;

	/**
	 * The object ID associated with the next bound name or {@code -1} if
	 * there is no next bound name.
	 */
	public final long nextOid;

	/** Whether to evict the next name. */
	public final boolean nextCallbackEvict;

	/** Whether to downgrade the next name. */
	public final boolean nextCallbackDowngrade;

	/**
	 * Creates an instance of this class.
	 *
	 * @param	found whether the name is bound
	 * @param	nextName the next bound name
	 * @param	oid the object ID
	 * @param	callbackEvict whether to evict the name
	 * @param	callbackDowngrade whether to downgrade the name
	 * @param	nextOid the object ID for the next bound name
	 * @param	nextCallbackEvict whether to evict the next name
	 * @param	nextCallbackDowngrade whether to downgrade the next
	 *		name
	 */
	public GetBindingForRemoveResults(boolean found,
					  String nextName,
					  long oid,
					  boolean callbackEvict,
					  boolean callbackDowngrade,
					  long nextOid,
					  boolean nextCallbackEvict,
					  boolean nextCallbackDowngrade)
	{
	    this.found = found;
	    this.nextName = nextName;
	    this.oid = oid;
	    this.callbackEvict = callbackEvict;
	    this.callbackDowngrade = callbackDowngrade;
	    this.nextOid = nextOid;
	    this.nextCallbackEvict = nextCallbackEvict;
	    this.nextCallbackDowngrade = nextCallbackDowngrade;
	}

	@Override
	public String toString() {
	    return "GetBindingForRemoveResults[" +
		"found:" + found +
		", nextName:" + nextName +
		", oid:" + oid +
		", callbackEvict:" + callbackEvict +
		", callbackDowngrade:" + callbackDowngrade +
		", nextOid:" + nextOid +
		", nextCallbackEvict:" + nextCallbackEvict +
		", nextCallbackDowngrade:" + nextCallbackDowngrade +
		"]";
	}
    }

    /**
     * Returns information about the next name after the specified name that
     * has a binding, or {@code null} if there are no more bound names.  If
     * {@code name} is {@code null}, then the search starts at the beginning.
     * If the return value's {@code callbackEvict} field is {@code true}, the
     * caller must evict the next name after using it.
     *
     * @param	nodeId the ID of the requesting node
     * @param	name the name
     * @return	information about the next bound name
     * @throws	IllegalArgumentException if {@code nodeId} has not been
     *		registered
     * @throws	IOException if a network problem occurs
     * @throws	TransactionAbortedException if the transaction performed by the
     *		server was aborted due to a lock conflict or timeout
     */
    NextBoundNameResults nextBoundName(long nodeId, String name)
	throws IOException;

    /**
     * The results of a call to {@link #nextBoundName}. <p>
     *
     * The {@link #nextName} field is the next bound name following the
     * requested name, or {@code null} if there is no next bound name.  The
     * {@link #oid} field is the object ID that is bound to that next name, or
     * {@code -1} if {@code nextName} is {@code null}.  The {@link
     * #callbackEvict} field records whether to evict the next name.
     */
    final class NextBoundNameResults implements Serializable {

	/** The version of the serialized form. */
	private static final long serialVersionUID = 1;

	/**
	 * The next bound name after the requested one, or {@code null} if
	 * there is no next bound name.
	 */
	public final String nextName;

	/**
	 * The object ID associated with the next name, or {@code -1} if {@code
	 * nextName} is {@code null}.
	 */
	public final long oid;

	/** Whether to evict the next name. */
	public final boolean callbackEvict;

	/**
	 * Creates an instance of this class.
	 *
	 * @param	nextName the next name or {@code null}
	 * @param	oid the object ID or {@code -1}
	 * @param	callbackEvict whether to evict the next name
	 */
	public NextBoundNameResults(
	    String nextName, long oid, boolean callbackEvict)
	{
	    this.nextName = nextName;
	    this.oid = oid;
	    this.callbackEvict = callbackEvict;
	}

	@Override
	public String toString() {
	    return "NextBoundNameResults[" +
		"nextName:" + nextName +
		", oid:" + oid +
		", callbackEvict:" + callbackEvict +
		"]";
	}
    }

    /* -- Class info methods -- */

    /**
     * Returns the class ID to represent classes with the specified class
     * information.  Obtains an existing ID for the class information if
     * present; otherwise, stores the information and returns the new ID
     * associated with it.  Class IDs are always greater than {@code 0}.  The
     * class information is the serialized form of the {@link
     * ObjectStreamClass} instance that serialization uses to represent the
     * class.
     *
     * @param	classInfo the class information
     * @return	the associated class ID
     * @throws	IOException if a network problem occurs
     * @throws	TransactionAbortedException if the transaction performed by the
     *		server was aborted due to a lock conflict or timeout
     */
    int getClassId(byte[] classInfo) throws IOException;

    /**
     * Returns the class information associated with the specified class ID.
     * The class information is the serialized form of the {@link
     * ObjectStreamClass} instance that serialization uses to represent the
     * class.
     *
     * @param	classId the class ID
     * @return	the associated class information or {@code null} if the ID is
     *		not found
     * @throws	IllegalArgumentException if {@code classId} is not greater
     *		than {@code 0}
     * @throws	IOException if a network problem occurs
     * @throws	TransactionAbortedException if the transaction performed by the
     *		server was aborted due to a lock conflict or timeout
     */
    byte[] getClassInfo(int classId) throws IOException;
}
