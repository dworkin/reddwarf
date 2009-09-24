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

package com.sun.sgs.impl.service.data.store;

import com.sun.sgs.app.NameNotBoundException;
import com.sun.sgs.app.ObjectNotFoundException;
import com.sun.sgs.app.TransactionAbortedException;
import com.sun.sgs.app.TransactionNotActiveException;
import com.sun.sgs.impl.sharedutil.LoggerWrapper;
import static com.sun.sgs.impl.sharedutil.Objects.checkNull;
import com.sun.sgs.kernel.AccessCoordinator;
import com.sun.sgs.kernel.AccessReporter;
import com.sun.sgs.kernel.AccessReporter.AccessType;
import static com.sun.sgs.kernel.AccessReporter.AccessType.READ;
import static com.sun.sgs.kernel.AccessReporter.AccessType.WRITE;
import com.sun.sgs.kernel.ComponentRegistry;
import com.sun.sgs.service.Transaction;
import com.sun.sgs.service.TransactionParticipant;
import com.sun.sgs.service.store.ClassInfoNotFoundException;
import com.sun.sgs.service.store.DataStore;
import java.util.Arrays;
import java.util.logging.Level;
import static java.util.logging.Level.FINER;
import static java.util.logging.Level.FINEST;

/**
 * A skeletal implementation of {@code DataStore} that does logging, checks
 * arguments, reports object accesses, implements the next key locking scheme
 * for name bindings, and is a transaction participant.  Object and name
 * accesses are logged to {@link AccessReporter}s whose source name includes
 * the name of the concrete class. <p>
 *
 * This class uses a next key locking scheme when reporting accesses to name
 * bindings.  This scheme is a way to insure isolation when different
 * transactions are creating, removing, and iterating over name bindings at the
 * same time.  The idea is to use the next key after a given key as a proxy for
 * the existence of the given key even when that key does not exist.  That way,
 * iterators know when they lock the next key after a particular key that they
 * will prevent others from changing what that next key ought to be.  See the
 * individual methods for getting, setting, removing, and iterating over
 * bindings for details of how each operation fits into this scheme. <p>
 *
 * Note that this class does not perform next key locking for objects, because
 * the way objects are allocated and used makes that unnecessary.  Because
 * there is no method at the application level for obtaining objects by object
 * ID, applications can only officially obtain objects that are stored as the
 * value of a name binding, or that are reachable by navigation from a name
 * binding.  In addition, object IDs are never reused.  The combination of
 * these two factors means that applications cannot ask to see an object that
 * was created simultaneously by another transaction, which is what next key
 * locking is intended to prevent.  This lack of consistency enforcement for
 * objects does mean that services, which can access objects by ID, need to
 * make sure not to ask questions about objects that they don't have other
 * reasons to believe exist.  In particular, it means that object iteration may
 * return inconsistent results.
 */
public abstract class AbstractDataStore
    implements DataStore, TransactionParticipant
{
    /** The main logger for this class. */
    protected final LoggerWrapper logger;

    /** The logger for transaction abort exceptions. */
    protected final LoggerWrapper abortLogger;

    /** The reporter to notify of object accesses. */
    protected final AccessReporter<Long> objectAccesses;

    /** The reporter to notify of bound name accesses. */
    protected final AccessReporter<String> nameAccesses;

    /**
     * Creates an instance of this class.
     *
     * @param	systemRegistry the registry of available system components
     * @param	logger the main logger for this class
     * @param	abortLogger the logger for transaction abort exceptions
     */
    protected AbstractDataStore(ComponentRegistry systemRegistry,
				LoggerWrapper logger,
				LoggerWrapper abortLogger)
    {
	checkNull("logger", logger);
	checkNull("abortLogger", abortLogger);
	this.logger = logger;
	this.abortLogger = logger;
	String className = getClass().getName();
	AccessCoordinator accessCoordinator =
	    systemRegistry.getComponent(AccessCoordinator.class);
	objectAccesses = accessCoordinator.registerAccessSource(
	    className + ".objects", Long.class);
	nameAccesses = accessCoordinator.registerAccessSource(
	    className + ".names", String.class);
    }

    /* -- Implement DataStore -- */

    /**
     * {@inheritDoc} <p>
     *
     * This implementation does nothing.
     */
    public void ready() throws Exception { }

    /**
     * {@inheritDoc} <p>
     *
     * This implementation does logging and calls {@link
     * #getLocalNodeIdInternal} to perform the actual operation.
     */
    public long getLocalNodeId() {
	logger.log(FINEST, "getLocalNodeId");
	try {
	    long result = getLocalNodeIdInternal();
	    if (logger.isLoggable(FINEST)) {
		logger.log(
		    FINEST, "getLocalNodeId returns nodeId:{0}", result);
	    }
	    return result;
	} catch (RuntimeException e) {
	    throw handleException(null, FINEST, e, "getLocalNodeId");
	}
    }

    /**
     * Performs the actual operation for {@link #getLocalNodeId
     * getLocalNodeId}.
     *
     * @return	the local node ID
     */
    protected abstract long getLocalNodeIdInternal();

    /**
     * {@inheritDoc} <p>
     *
     * This implementation does logging and calls {@link #createObjectInternal
     * createObjectInternal} to perform the actual operation.
     */
    public long createObject(Transaction txn) {
	logger.log(FINEST, "createObject txn:{0}", txn);
	try {
	    long result = createObjectInternal(txn);
	    if (logger.isLoggable(FINEST)) {
		logger.log(
		    FINEST, "createObject txn:{0} returns oid:{1,number,#}",
		    txn, result);
	    }
	    return result;
	} catch (RuntimeException e) {
	    throw handleException(txn, FINEST, e, "createObject txn:" + txn);
	}
    }

    /**
     * Performs the actual operation for {@link #createObject createObject}.
     *
     * @param	txn the transaction under which the operation should take place
     * @return	the new object ID
     * @throws	TransactionAbortedException if the transaction was aborted due
     *		to a lock conflict or timeout
     * @throws	TransactionNotActiveException if the transaction is not active
     * @throws	IllegalStateException if the operation failed because of a
     *		problem with the current transaction
     */
    protected abstract long createObjectInternal(Transaction txn);

    /**
     * {@inheritDoc} <p>
     *
     * This implementation does logging, checks that {@code oid} is valid, 
     * reports object accesses, and calls {@link #markForUpdateInternal
     * markForUpdateInternal} to perform the actual operation.
     */
    public void markForUpdate(Transaction txn, long oid) {
	if (logger.isLoggable(FINEST)) {
	    logger.log(
		FINEST, "markForUpdate txn:{0}, oid:{1,number,#}", txn, oid);
	}
	try {
	    reportObjectAccess(txn, oid, WRITE);
	    markForUpdateInternal(txn, oid);
	    if (logger.isLoggable(FINEST)) {
		logger.log(FINEST,
			   "markForUpdate txn:{0}, oid:{1,number,#} returns",
			   txn, oid);
	    }
	} catch (RuntimeException e) {
	    throw handleException(
		txn, FINEST, e, "markForUpdate txn:" + txn + ", oid:" + oid);
	}		
    }

    /**
     * Performs the actual operation for {@link #markForUpdate markForUpdate}.
     *
     * @param	txn the transaction under which the operation should take place
     * @param	oid the object ID
     * @throws	TransactionAbortedException if the transaction was aborted due
     *		to a lock conflict or timeout
     * @throws	TransactionNotActiveException if the transaction is not active
     * @throws	IllegalStateException if the operation failed because of a
     *		problem with the current transaction
     */
    protected abstract void markForUpdateInternal(Transaction txn, long oid);

    /**
     * {@inheritDoc} <p>
     *
     * This implementation does logging, checks that {@code oid} is valid,
     * reports object accesses, and calls {@link #getObjectInternal
     * getObjectInternal} to perform the actual operation.
     */
    public byte[] getObject(Transaction txn, long oid, boolean forUpdate) {
	if (logger.isLoggable(FINEST)) {
	    logger.log(FINEST,
		       "getObject txn:{0}, oid:{1,number,#}, forUpdate:{2}",
		       txn, oid, forUpdate);
	}
	try {
	    reportObjectAccess(txn, oid, forUpdate ? WRITE : READ);
	    byte[] result = getObjectInternal(txn, oid, forUpdate);
	    if (logger.isLoggable(FINEST)) {
		logger.log(FINEST,
			   "getObject txn:{0}, oid:{1,number,#}" +
			   ", forUpdate:{2} returns",
			   txn, oid, forUpdate);
	    }
	    return result;
	} catch (RuntimeException e) {
	    throw handleException(txn, FINEST, e,
				  "getObject txn:" + txn + ", oid:" + oid +
				  ", forUpdate:" + forUpdate);

	}
    }

    /**
     * Performs the actual operation for {@link #getObject getObject}.
     *
     * @param	txn the transaction under which the operation should take place
     * @param	oid the object ID
     * @param	forUpdate whether the caller intends to modify the object
     * @return	the data associated with the object ID
     * @throws	ObjectNotFoundException if the object is not found
     * @throws	TransactionAbortedException if the transaction was aborted due
     *		to a lock conflict or timeout
     * @throws	TransactionNotActiveException if the transaction is not active
     * @throws	IllegalStateException if the operation failed because of a
     *		problem with the current transaction
     */
    protected abstract byte[] getObjectInternal(
	Transaction txn, long oid, boolean forUpdate);

    /**
     * {@inheritDoc} <p>
     *
     * This implementation does logging, checks that {@code oid} is valid and
     * {@code data} is not {@code null}, reports object accesses, and calls
     * {@link #setObjectInternal setObjectInternal} to perform the actual
     * operation.
     */
    public void setObject(Transaction txn, long oid, byte[] data) {
	if (logger.isLoggable(FINEST)) {
	    logger.log(
		FINEST, "setObject txn:{0}, oid:{1,number,#}", txn, oid);
	}
	try {
	    checkNull("data", data);
	    reportObjectAccess(txn, oid, WRITE);
	    setObjectInternal(txn, oid, data);
	    if (logger.isLoggable(FINEST)) {
		logger.log(FINEST,
			   "setObject txn:{0}, oid:{1,number,#} returns",
			   txn, oid);
	    }
	} catch (RuntimeException e) {
	    throw handleException(
		txn, FINEST, e, "setObject txn:" + txn + ", oid:" + oid);
	}
    }

    /**
     * Performs the actual operation for {@link #setObject setObject}.
     *
     * @param	txn the transaction under which the operation should take place
     * @param	oid the object ID
     * @param	data the data
     * @throws	TransactionAbortedException if the transaction was aborted due
     *		to a lock conflict or timeout
     * @throws	TransactionNotActiveException if the transaction is not active
     * @throws	IllegalStateException if the operation failed because of a
     *		problem with the current transaction
     */
    protected abstract void setObjectInternal(
	Transaction txn, long oid, byte[] data);

    /**
     * {@inheritDoc} <p>
     *
     * This implementation does logging, checks that {@code oids} is not {@code
     * null} and its elements are valid, that {@code dataArray} and its
     * elements are not {@code null}, and that {@code oids} and {@code
     * dataArray} have the same length, reports object accesses, and calls
     * {@link #setObjectsInternal setObjectsInternal} to perform the actual
     * operation.
     */
    public void setObjects(Transaction txn, long[] oids, byte[][] dataArray) {
	if (logger.isLoggable(FINEST)) {
	    logger.log(FINEST, "setObjects txn:{0}, oids:[{1}]",
		       txn, Arrays.toString(oids));
	}
	try {
	    for (long oid : oids) {
		reportObjectAccess(txn, oid, WRITE);
	    }
	    for (byte[] data : dataArray) {
		if (data == null) {
		    throw new NullPointerException(
			"The data must not be null");
		}
	    }
	    if (oids.length != dataArray.length) {
		throw new IllegalArgumentException(
		    "The oids and dataArray must be the same length");
	    }
	    setObjectsInternal(txn, oids, dataArray);
	    if (logger.isLoggable(FINEST)) {
		logger.log(FINEST, "setObjects txn:{0}, oids:[{1}] returns",
			   txn, Arrays.toString(oids));
	    }
	} catch (RuntimeException e) {
	    throw handleException(txn, FINEST, e,
				  "setObjects txn:" + txn +
				  ", oids:[" + Arrays.toString(oids) + "]");
	}
    }

    /**
     * Performs the actual operation for {@link #setObjects setObjects}.
     *
     * @param	txn the transaction under which the operation should take place
     * @param	oids the object IDs
     * @param	dataArray the associated data values
     * @throws	TransactionAbortedException if the transaction was aborted due
     *		to a lock conflict or timeout
     * @throws	TransactionNotActiveException if the transaction is not active
     * @throws	IllegalStateException if the operation failed because of a
     *		problem with the current transaction
     */
    protected abstract void setObjectsInternal(
	Transaction txn, long[] oids, byte[][] dataArray);

    /**
     * {@inheritDoc} <p>
     *
     * This implementation does logging, checks that {@code oid} is valid and
     * {@code data} is not {@code null}, reports object accesses, and calls
     * {@link #removeObjectInternal removeObjectInternal} to perform the actual
     * operation.
     */
    public void removeObject(Transaction txn, long oid) {
	if (logger.isLoggable(FINEST)) {
	    logger.log(
		FINEST, "removeObject txn:{0}, oid:{1,number,#}", txn, oid);
	}
	try {
	    reportObjectAccess(txn, oid, WRITE);
	    removeObjectInternal(txn, oid);
	    if (logger.isLoggable(FINEST)) {
		logger.log(FINEST,
			   "removeObject txn:{0}, oid:{1,number,#} returns",
			   txn, oid);
	    }
	} catch (RuntimeException e) {
	    throw handleException(
		txn, FINEST, e, "removeObject txn:" + txn + ", oid:" + oid);
	}
    }

    /**
     * Performs the actual operation for {@link #removeObject removeObject}.
     *
     * @param	txn the transaction under which the operation should take place
     * @param	oid the object ID
     * @throws	ObjectNotFoundException if the object is not found
     * @throws	TransactionAbortedException if the transaction was aborted due
     *		to a lock conflict or timeout
     * @throws	TransactionNotActiveException if the transaction is not active
     * @throws	IllegalStateException if the operation failed because of a
     *		problem with the current transaction
     */
    protected abstract void removeObjectInternal(Transaction txn, long oid);

    /**
     * {@inheritDoc} <p>
     *
     * This implementation does logging, checks that {@code name} is not {@code
     * null}, reports name accesses, and calls {@link #getBindingInternal
     * getBindingInternal} to perform the actual operation.
     */
    public long getBinding(Transaction txn, String name) {
	if (logger.isLoggable(FINEST)) {
	    logger.log(FINEST, "getBinding txn:{0}, name:{1}", txn, name);
	}
	try {
	    checkNull("name", name);
	    /*
	     * Read lock the name even though we don't know yet if it is bound.
	     * Doing this means that the value we obtain, if there is one, will
	     * be known to be correct.
	     */
	    reportNameAccess(txn, name, READ);
	    BindingValue result = getBindingInternal(txn, name);
	    if (!result.isNameBound()) {
		/*
		 * Read lock the next name if the requested name is not bound
		 * to prevent a concurrent transaction from observing a
		 * different next name than this transaction would.
		 */
		reportNextNameAccess(txn, name, result.getNextName(), READ);
		throw new NameNotBoundException("Name not bound: " + name);
	    }
	    if (logger.isLoggable(FINEST)) {
		logger.log(FINEST,
			   "getBinding txn:{0}, name:{1} returns " +
			   "oid:{2,number,#}",
			   txn, name, result.getObjectId());
	    }
	    return result.getObjectId();
	} catch (RuntimeException e) {
	    throw handleException(
		txn, FINEST, e, "getBinding txn:" + txn + ", name:" + name);
	}
    }

    /**
     * Performs the actual operation for {@link #getBinding getBinding}.  If
     * the name is bound, the return value contains the object ID that the name
     * is bound to and a next name of {@code null}.  If the name is not bound,
     * the return value contains an object ID of {@code -1} and the next name
     * found, which may be {@code null}.
     *
     * @param	txn the transaction under which the operation should take place
     * @param	name the name
     * @return	information about the object ID and the next name
     * @throws	TransactionAbortedException if the transaction was aborted due
     *		to a lock conflict or timeout
     * @throws	TransactionNotActiveException if the transaction is not active
     * @throws	IllegalStateException if the operation failed because of a
     *		problem with the current transaction
     */
    protected abstract BindingValue getBindingInternal(
	Transaction txn, String name);

    /**
     * {@inheritDoc} <p>
     *
     * This implementation does logging, checks that {@code name} is not {@code
     * null} and that {@code oid} is valid, reports name accesses, and calls
     * {@link #setBindingInternal setBindingInternal} to perform the actual
     * operation.
     */
    public void setBinding(Transaction txn, String name, long oid) {
	if (logger.isLoggable(FINEST)) {
	    logger.log(
		FINEST, "setBinding txn:{0}, name:{1}, oid:{2,number,#}",
		txn, name, oid);
	}
	try {
	    checkNull("name", name);
	    reportNameAccess(txn, name, WRITE);
	    BindingValue result = setBindingInternal(txn, name, oid);
	    if (!result.isNameBound()) {
		/*
		 * Write lock the next name if the requested name was unbound
		 * to prevent other transactions from binding it, from getting
		 * it's value, or even from determining if it is bound, until
		 * this transaction commits.
		 */
		reportNextNameAccess(txn, name, result.getNextName(), WRITE);
	    }
	    if (logger.isLoggable(FINEST)) {
		logger.log(
		    FINEST,
		    "setBinding txn:{0}, name:{1}, oid:{2,number,#} returns",
		    txn, name, oid);
	    }
	} catch (RuntimeException e) {
	    throw handleException(
		txn, FINEST, e,
		"setBinding txn:" + txn + ", name:" + name + ", oid:" + oid);
	}
    }

    /**
     * Performs the actual operation for {@link #setBinding setBinding}.  If
     * the name is bound, the return value contains an arbitrary non-negative
     * object ID and a next name of {@code null}.  If the name is not bound,
     * the return value contains an object ID of {@code -1} and the next name
     * found, which may be {@code null}.
     *
     * @param	txn the transaction under which the operation should take place
     * @param	name the name
     * @param	oid the object ID
     * @return	information about the old object ID and the next name
     * @throws	TransactionAbortedException if the transaction was aborted due
     *		to a lock conflict or timeout
     * @throws	TransactionNotActiveException if the transaction is not active
     * @throws	IllegalStateException if the operation failed because of a
     *		problem with the current transaction
     */
    protected abstract BindingValue setBindingInternal(
	Transaction txn, String name, long oid);

    /**
     * {@inheritDoc} <p>
     *
     * This implementation does logging, checks that {@code name} is not {@code
     * null}, reports name accesses, and calls {@link #removeBindingInternal
     * removeBindingInternal} to perform the actual operation.
     */
    public void removeBinding(Transaction txn, String name) {
	if (logger.isLoggable(FINEST)) {
	    logger.log(FINEST, "removeBinding txn:{0}, name:{1}", txn, name);
	}
	try {
	    checkNull("name", name);
	    /*
	     * Write lock the name even though we don't know yet if it is
	     * bound.  Doing this means that another transaction cannot change
	     * or observe the existence of the binding until this transaction
	     * commits.  Without performing this check first, we would need to
	     * recheck the existence after locking the next key.
	     */
	    reportNameAccess(txn, name, WRITE);
	    BindingValue result = removeBindingInternal(txn, name);
	    if (!result.isNameBound()) {
		/*
		 * Read lock the next name to prevent another transaction from
		 * creating the name.  We are locking the non-existent name
		 * within AbstractDataStore, which will lock out others locally
		 * from creating the name.  But the server side will only want
		 * to lock existing items, so it depends on locking the next
		 * name.
		 */
		reportNextNameAccess(txn, name, result.getNextName(), READ);
		throw new NameNotBoundException("Name not bound: " + name);
	    }
	    /*
	     * Write lock the next name if the binding is being removed to
	     * prevent other transactions from observing its status as the next
	     * binding until this transaction commits.
	     */
	    reportNextNameAccess(txn, name, result.getNextName(), WRITE);
	    if (logger.isLoggable(FINEST)) {
		logger.log(FINEST, "removeBinding txn:{0}, name:{1} returns",
			   txn, name);
	    }
	} catch (RuntimeException e) {
	    throw handleException(
		txn, FINEST, e, "removeBinding txn:" + txn + ", name:" + name);
	}
    }

    /**
     * Performs the actual operation for {@link #removeBinding removeBinding}.
     * If the name is bound, the return value contains an arbitrary
     * non-negative object ID, otherwise it contains {@code -1}.  In all cases,
     * the return value contains the next name found, which may be {@code
     * null}.
     *
     * @param	txn the transaction under which the operation should take place
     * @param	name the name
     * @return	information about the object ID and the next name
     * @throws	TransactionAbortedException if the transaction was aborted due
     *		to a lock conflict or timeout
     * @throws	TransactionNotActiveException if the transaction is not active
     * @throws	IllegalStateException if the operation failed because of a
     *		problem with the current transaction
     */
    protected abstract BindingValue removeBindingInternal(
	Transaction txn, String name);

    /**
     * {@inheritDoc} <p>
     *
     * This implementation does logging, reports name accesses, and calls
     * {@link #nextBoundNameInternal nextBoundNameInternal} to perform the
     * actual operation.
     */
    public String nextBoundName(Transaction txn, String name) {
	if (logger.isLoggable(FINEST)) {
	    logger.log(FINEST, "nextBoundName txn:{0}, name:{1}", txn, name);
	}
	try {
	    String result = reportNextNameAccess(
		txn, name, nextBoundNameInternal(txn, name), READ);
	    if (logger.isLoggable(FINEST)) {
		logger.log(FINEST,
			   "nextBoundName txn:{0}, name:{1} returns {2}",
			   txn, name, result);
	    }
	    return result;
	} catch (RuntimeException e) {
	    throw handleException(
		txn, FINEST, e, "nextBoundName txn:" + txn + ", name:" + name);
	}
    }

    /**
     * Performs the actual operation for {@link #nextBoundName nextBoundName}.
     *
     * @param	txn the transaction under which the operation should take place
     * @param	name the name to search after, or {@code null} to start
     *		at the beginning
     * @return	the next name with a binding following {@code name}, or
     *		{@code null} if there are no more bound names
     * @throws	TransactionAbortedException if the transaction was aborted due
     *		to a lock conflict or timeout
     * @throws	TransactionNotActiveException if the transaction is not active
     * @throws	IllegalStateException if the operation failed because of a
     *		problem with the current transaction
     */
    protected abstract String nextBoundNameInternal(
	Transaction txn, String name);

    /**
     * {@inheritDoc} <p>
     *
     * This implementation does logging and calls {@link #shutdownInternal
     * shutdownInternal} to perform the actual operation.
     */
    public void shutdown() {
	logger.log(FINER, "shutdown");
	try {
	    shutdownInternal();
	    logger.log(FINER, "shutdown complete");
	} catch (RuntimeException e) {
	    throw handleException(null, FINER, e, "shutdown");
	}
    }

    /** Performs the actual operation for {@link #shutdown shutdown}. */
    protected abstract void shutdownInternal();

    /**
     * {@inheritDoc} <p>
     *
     * This implementation does logging, checks that {@code classInfo} is not
     * {@code null}, and calls {@link #getClassIdInternal getClassIdInternal}
     * to perform the actual operation.
     */
    public int getClassId(Transaction txn, byte[] classInfo) {
	logger.log(FINER, "getClassId txn:{0}", txn);
	try {
	    checkNull("classInfo", classInfo);
	    int result = getClassIdInternal(txn, classInfo);
	    if (logger.isLoggable(FINER)) {
		logger.log(
		    FINER, "getClassId txn:{0} returns {1}", txn, result);
	    }
	    return result;
	} catch (RuntimeException e) {
	    throw handleException(txn, FINER, e, "getClassId txn:" + txn);
	}
    }	    

    /**
     * Performs the actual operation for {@link #getClassId getClassId}.
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
    protected abstract int getClassIdInternal(
	Transaction txn, byte[] classInfo);

    /**
     * {@inheritDoc} <p>
     *
     * This implementation does logging, checks that {@code classId} is valid,
     * and calls {@link #getClassInfoInternal getClassInfoInternal} to perform
     * the actual operation.
     */
    public byte[] getClassInfo(Transaction txn, int classId)
	throws ClassInfoNotFoundException
    {
	if (logger.isLoggable(FINER)) {
	    logger.log(FINER, "getClassInfo txn:{0}, classId:{1,number,#}",
		       txn, classId);
	}
	try {
	    if (classId < 1) {
		throw new IllegalArgumentException(
		    "The classId argument must be greater than 0");
	    }
	    byte[] result = getClassInfoInternal(txn, classId);
	    if (logger.isLoggable(FINER)) {
		logger.log(
		    FINER,
		    "getClassInfo txn:{0}, classId:{1,number,#} returns",
		    txn, classId);
	    }
	    return result;
	} catch (RuntimeException e) {
	    throw handleException(
		txn, FINER, e,
		"getClassInfo txn:" + txn + ",classId:" + classId);
	}
    }

    /**
     * Performs the actual operation for {@link #getClassInfo getClassInfo}.
     *
     * @param	txn the transaction under which the operation should take place
     * @param	classId the class ID
     * @return	the associated class information
     * @throws	ClassInfoNotFoundException if the ID is not found
     * @throws	TransactionAbortedException if the transaction was aborted due
     *		to a lock conflict or timeout
     * @throws	IllegalStateException if the operation failed because of a
     *		problem with the transaction
     */
    protected abstract byte[] getClassInfoInternal(
	Transaction txn, int classId)
	throws ClassInfoNotFoundException;

    /**
     * {@inheritDoc} <p>
     *
     * This implementation does logging, checks that {@code oid} is valid,
     * reports object accesses, and calls {@link #nextObjectIdInternal
     * nextObjectIdInternal} to perform the actual operation.
     */
    public long nextObjectId(Transaction txn, long oid) {
	if (logger.isLoggable(FINEST)) {
	    logger.log(FINEST, "nextObjectId txn:{0}, oid:{1,number,#}",
		       txn, oid);
	}
	try {
	    if (oid < -1) {
		throw new IllegalArgumentException(
		    "Invalid object ID: " + oid);
	    }
	    long result = nextObjectIdInternal(txn, oid);
	    if (result != -1) {
		/* Only report access to the object if it was found */
		reportObjectAccess(txn, result, READ);
	    }
	    if (logger.isLoggable(FINEST)) {
		logger.log(FINEST,
			   "nextObjectId txn:{0}, oid:{1,number,#} " +
			   "returns oid:{2,number,#}",
			   txn, oid, result);
	    }
	    return result;
	} catch (RuntimeException e) {
	    throw handleException(
		txn, FINEST, e, "nextObjectId txn:" + txn + ", oid:" + oid);
	}
    }

    /**
     * Performs the actual operation for {@link #nextObjectId nextObjectId}.
     *
     * @param	txn the transaction under which the operation should take place
     * @param	oid the identifier of the object to search after, or
     *		{@code -1} to request the first object
     * @return	the identifier of the next object following the object with
     *		identifier {@code oid}, or {@code -1} if there are no more
     *		objects
     * @throws	TransactionAbortedException if the transaction was aborted due
     *		to a lock conflict or timeout
     * @throws	IllegalStateException if the operation failed because of a
     *		problem with the current transaction
     */
    protected abstract long nextObjectIdInternal(Transaction txn, long oid);

    /** {@inheritDoc} */
    public void setObjectDescription(
	Transaction txn, long oid, Object description)
    {
	checkOid(oid);
	checkNull("description", description);
	try {
	    objectAccesses.setObjectDescription(txn, oid, description);
	} catch (IllegalArgumentException e) {
	    throw new IllegalStateException(
		"Problem with transaction " + txn + ": " + e.getMessage(), e);
	}
    }

    /** {@inheritDoc} */
    public void setBindingDescription(
	Transaction txn, String name, Object description)
    {
	checkNull("name", name);
	checkNull("description", description);
	try {
	    nameAccesses.setObjectDescription(
		txn, getNameForAccess(name), description);
	} catch (IllegalArgumentException e) {
	    throw new IllegalStateException(
		"Problem with transaction " + txn + ": " + e.getMessage(), e);
	}
    }

    /* -- Implement TransactionParticipant -- */

    /**
     * {@inheritDoc} <p>
     *
     * This implementation does logging and calls {@link #prepareInternal
     * prepareInternal} to perform the actual operation.
     */
    public boolean prepare(Transaction txn) {
	logger.log(FINER, "prepare txn:{0}", txn);
	try {
	    boolean result = prepareInternal(txn);
	    if (logger.isLoggable(FINER)) {
		logger.log(FINER, "prepare txn:{0} returns {1}", txn, result);
	    }
	    return result;
	} catch (RuntimeException e) {
	    throw handleException(txn, FINER, e, "prepare txn:" + txn);
	}
    }

    /**
     * Performs the actual operation for {@link #prepare prepare}.
     *
     * @param	txn the transaction
     * @return	{@code true} if this participant is read-only, otherwise {@code
     *		false}
     * @throws	IllegalStateException if this participant has already been
     *		prepared, committed, or aborted, or if this participant is not
     *		participating in the given transaction
     */
    protected abstract boolean prepareInternal(Transaction txn);

    /**
     * {@inheritDoc} <p>
     *
     * This implementation does logging and calls {@link #commitInternal
     * commitInternal} to perform the actual operation.
     */
    public void commit(Transaction txn) {
	logger.log(FINER, "commit txn:{0}", txn);
	try {
	    commitInternal(txn);
	    logger.log(FINER, "commit txn:{0} returns", txn);
	} catch (RuntimeException e) {
	    throw handleException(txn, FINER, e, "commit txn:" + txn);
	}
    }

    /**
     * Performs the actual operation for {@link #commit commit}.
     *
     * @param	txn the transaction
     * @throws	IllegalStateException if this participant was not previously
     *		prepared, or if this participant has already committed or
     *		aborted, or if this participant is not participating in the
     *		given transaction
     */
    protected abstract void commitInternal(Transaction txn);

    /**
     * {@inheritDoc} <p>
     *
     * This implementation does logging and calls {@link
     * #prepareAndCommitInternal prepareAndCommitInternal} to perform the
     * actual operation.
     */
    public void prepareAndCommit(Transaction txn) {
	logger.log(FINER, "prepareAndCommit txn:{0}", txn);
	try {
	    prepareAndCommitInternal(txn);
	    logger.log(FINER, "prepareAndCommit txn:{0} returns", txn);
	} catch (RuntimeException e) {
	    throw handleException(
		txn, FINER, e, "prepareAndCommit txn:" + txn);
	}
    }

    /**
     * Performs the actual operation for {@link #prepareAndCommit
     * prepareAndCommit}.
     *
     * @param	txn the transaction
     * @throws	IllegalStateException if this participant has already been
     *		prepared, committed, or aborted, or if this participant is not
     *		participating in the given transaction
     */
    protected abstract void prepareAndCommitInternal(Transaction txn);

    /**
     * {@inheritDoc} <p>
     *
     * This implementation does logging and calls {@link #abortInternal
     * abortInternal} to perform the actual operation.
     */
    public void abort(Transaction txn) {
	logger.log(FINER, "abort txn:{0}", txn);
	try {
	    abortInternal(txn);
	    logger.log(FINER, "abort txn:{0} returns", txn);
	} catch (RuntimeException e) {
	    throw handleException(txn, FINER, e, "abort txn:" + txn);
	}
    }
    
    /**
     * Performs the actual operation for {@link #abort abort}.
     *
     * @param	txn the transaction
     * @throws	IllegalStateException if this participant has already been
     *		aborted or committed, or if this participant is not
     *		participating in the given transaction
     */
    protected abstract void abortInternal(Transaction txn);

    /** {@inheritDoc} */
    public String getTypeName() {
        return getClass().getName();
    }

    /* -- Other methods -- */

    /**
     * Performs any operations needed when an exception is going to be thrown,
     * as well as allowing the implementation to replace the exception with a
     * different one. <p>
     *
     * This implementation does logging, and aborts the transaction if it is
     * not {@code null} and the exception is a {@link
     * TransactionAbortedException}.
     *
     * @param	txn the transaction or {@code null}
     * @param	level the logging level
     * @param	e the exception
     * @param	operation a description of the operation being performed
     * @return	the exception to throw
     */
    protected RuntimeException handleException(Transaction txn,
					       Level level,
					       RuntimeException e,
					       String operation)
    {
	boolean abort = (e instanceof TransactionAbortedException);
	if (abort && txn != null && !txn.isAborted()) {
	    txn.abort(e);
	}
	LoggerWrapper thisLogger = abort ? abortLogger : logger;
	thisLogger.logThrow(level, e, "{0} throws", operation);
	return e;
    }

    /**
     * Reports an object access.
     *
     * @param	txn the transaction
     * @param	oid the object ID
     * @param	type the type of access
     * @throws	IllegalArgumentException if {@code oid} is negative
     */
    protected void reportObjectAccess(
	Transaction txn, long oid, AccessType type)
    {
	checkOid(oid);
	try {
	    objectAccesses.reportObjectAccess(txn, oid, type);
	} catch (IllegalArgumentException e) {
	    throw new TransactionNotActiveException(
	        "Problem with transaction " + txn + ": " + e.getMessage(), e);
	}
    }

    /**
     * Reports a name access.
     *
     * @param	txn the transaction
     * @param	name the name
     * @param	type the type of access
     */
    protected void reportNameAccess(
	Transaction txn, String name, AccessType type)
    {
	try {
	    nameAccesses.reportObjectAccess(txn, getNameForAccess(name), type);
	} catch (IllegalArgumentException e) {
	    throw new TransactionNotActiveException(
	        "Problem with transaction " + txn + ": " + e.getMessage(), e);
	}
    }

    /**
     * Returns the name to use for reporting access to a name binding.  Uses
     * the value "z.end" instead of {@code null} to represent a name beyond the
     * last known name.  Add the prefix "z" to any name whose first character
     * is "z".  Note that this scheme does not preserve order, but access
     * reporting does not require that.
     *
     * @param	name the name or {@code null}
     * @return	the name to use for reporting object accesses
     */
    protected static String getNameForAccess(String name) {
	if (name == null) {
	    return "z.end";
	} else if (name.startsWith("z")) {
	    return 'z' + name;
	} else {
	    return name;
	}
    }

    /**
     * Checks that the object ID argument is not negative.
     *
     * @param	oid the object ID
     * @throws	IllegalArgumentException if {@code oid} is negative
     */
    public static void checkOid(long oid) {
	if (oid < 0) {
	    throw new IllegalArgumentException(
		"Object ID must not be negative");
	}
    }

    /**
     * Reports access to what should be the next name after a given name.
     * Confirms that the next name is correct after obtaining access, repeating
     * the operation if the name has changed, and returning actual next name.
     * Note that the next name may change if the one obtained prior to checking
     * access was created by a transaction that aborts.
     *
     * @param	txn the transaction under which the operation should take place
     * @param	name the name, which may be {@code null}
     * @param	next the next name after {@code name}, which may be
     *		{@code null}
     * @param	accessType the type of access to the next name to report
     * @return	the actual next name, which may be {@code null}
     */
    private String reportNextNameAccess(Transaction txn,
					String name,
					String next,
					AccessType accessType)
    {
	while (true) {
	    reportNameAccess(txn, next, accessType);
	    String check = nextBoundNameInternal(txn, name);
	    if (check == null ? next == null : check.equals(next)) {
		return next;
	    }
	    next = check;
	}
    }
}
