/*
 * Copyright 2007-2008 Sun Microsystems, Inc.
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

package com.sun.sgs.impl.kernel;

import com.sun.sgs.impl.profile.ProfileCollectorHandle;
import com.sun.sgs.impl.sharedutil.LoggerWrapper;
import com.sun.sgs.impl.sharedutil.PropertiesWrapper;
import com.sun.sgs.kernel.AccessCoordinator;
import com.sun.sgs.kernel.AccessReporter;
import com.sun.sgs.kernel.AccessReporter.AccessType;
import com.sun.sgs.kernel.AccessedObject;
import com.sun.sgs.profile.AccessedObjectsDetail;
import com.sun.sgs.profile.AccessedObjectsDetail.ConflictType;
import com.sun.sgs.service.Transaction;
import com.sun.sgs.service.TransactionParticipant;
import com.sun.sgs.service.TransactionProxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * An implementation of {@link AccessCoordinator} that uses locking to handle
 * conflicts. <p>
 *
 * The methods that this class provides to implement {@code AccessCoordinator}
 * are not thread safe, and should either be called from a single thread or
 * else protected with external synchronization.
 */
public class LockingAccessCoordinator extends AbstractAccessCoordinator
    implements AccessCoordinatorHandle, TransactionParticipant
{
    /**
     * The property for specifying the maximum number of milliseconds to wait
     * for obtaining a lock.
     */
    public static final String LOCK_TIMEOUT_PROPERTY =
	"com.sun.sgs.lock.timeout";

    /** The default number of milliseconds to wait for obtaining a lock. */
    public static final long LOCK_TIMEOUT_DEFAULT = 10;

    /**
     * The property for specifying the number of maps to use for associating
     * keys and maps.  The number of maps controls the amount of concurrency.
     */
    public static final String NUM_KEY_MAPS_PROPERTY =
	"com.sun.sgs.impl.kernel.LockingAccessCoordinator.num.key.maps";

    /** The default number of key maps. */
    public static final int NUM_KEY_MAPS_DEFAULT = 8;

    /** An empty array of lock requests. */
    static final LockRequest[] NO_LOCK_REQUESTS = { };

    /** The logger for this class. */
    static final LoggerWrapper logger = new LoggerWrapper(
	Logger.getLogger(LockingAccessCoordinator.class.getName()));

    /** Maps transactions to lockers. */
    private final ConcurrentMap<Transaction, Locker> txnMap =
	new ConcurrentHashMap<Transaction, Locker>();

    /**
     * The maximum number of milliseconds to spend attempting to acquiring a
     * lock.
     */
    private final long lockTimeout;

    /**
     * The number of separate maps to use for storing keys in order to support
     * concurrent access.
     */
    private final int numKeyMaps;

    /**
     * An array of maps from key to lock.  The map to use is chosen by using
     * the key's hash code mod the number of key maps.  Synchronization for
     * locks is based on locking the associated key map.  Locks should not be
     * used without synchronizing on the associated key map lock.
     */
    private final Map<Key, Lock>[] keyMaps;

    /* -- Public constructor -- */

    /**
     * Creates an instance of this class.
     *
     * @param	properties the configuration properties
     * @param	txnProxy the transaction proxy
     * @param	profileCollectorHandle the profile collector handle
     * @throws	IllegalArgumentException if the values of the configuration
     *		properties are illegal
     */
    public LockingAccessCoordinator(
	Properties properties,
	TransactionProxy txnProxy,
	ProfileCollectorHandle profileCollectorHandle)
    {
	super(txnProxy, profileCollectorHandle);
	PropertiesWrapper wrappedProps = new PropertiesWrapper(properties);
	lockTimeout = wrappedProps.getLongProperty(
	    LOCK_TIMEOUT_PROPERTY, LOCK_TIMEOUT_DEFAULT, 0, Long.MAX_VALUE);
	numKeyMaps = wrappedProps.getIntProperty(
	    NUM_KEY_MAPS_PROPERTY, NUM_KEY_MAPS_DEFAULT, 1, Integer.MAX_VALUE);
	keyMaps = createKeyMaps(numKeyMaps);
	for (int i = 0; i < numKeyMaps; i++) {
	    keyMaps[i] = new HashMap<Key, Lock>();
	}
    }

    /* -- Implement AccessCoordinator -- */

    /** {@inheritDoc} */
    public <T> AccessReporter<T> registerAccessSource(
	String sourceName, Class<T> objectIdType)
    {
	checkNonNull(objectIdType, "objectIdType");
	return new AccessReporterImpl<T>(sourceName);
    }

    /** {@inheritDoc} */
    public Transaction getConflictingTransaction(Transaction txn) {
	checkNonNull(txn, "txn");
	return null;
    }

    /* -- Implement AccessCoordinatorHandle -- */

    /** {@inheritDoc} */
    public void notifyNewTransaction(
	Transaction txn, long requestedStartTime, int tryCount)
    {
	checkNonNull(txn, "txn");
	if (requestedStartTime < 0 || tryCount < 1) {
	    throw new IllegalArgumentException();
	}
	Locker locker = new Locker(txn, requestedStartTime);
	Locker existing = txnMap.putIfAbsent(txn, locker);
	if (existing != null) {
	    throw new IllegalStateException("Transaction already started");
	}
	if (logger.isLoggable(Level.FINER)) {
	    logger.log(Level.FINER,
		       "Start {0}, requestedStartTime:{1,number,#}",
		       locker, requestedStartTime);
	}
	txn.join(this);
    }

    /* -- Implement TransactionParticipant -- */

    /** {@inheritDoc} */
    public boolean prepare(Transaction txn) {
	return false;
    }

    /** {@inheritDoc} */
    public void commit(Transaction txn) {
	reportDetail(txn);
    }

    /** {@inheritDoc} */
    public void prepareAndCommit(Transaction txn) {
	reportDetail(txn);
    }

    /** {@inheritDoc} */
    public void abort(Transaction txn) {
	reportDetail(txn);
    }

    /** {@inheritDoc} */
    public String getTypeName() {
        return getClass().getName();
    }

    /* -- Other public methods -- */

    /**
     * Attempts to acquire a lock, waiting if needed.  Returns information
     * about conflicts that occurred while attempting to acquire the lock that
     * prevented the lock from being acquired, or else {@code null} if the lock
     * was acquired.  If the {@code type} field of the return value is {@link
     * LockConflictType#DEADLOCK DEADLOCK}, then the caller should call {@link
     * #releaseAll releaseAll} to end the transaction, and any other lock or
     * wait requests will throw {@link IllegalStateException}.
     *
     * @param	txn the transaction requesting the lock
     * @param	source the source of the object
     * @param	objectId the ID of the object
     * @param	forWrite whether to request a write lock
     * @param	description a description of the object being accessed, or
     *		{@code null}
     * @return	lock conflict information, or {@code null} if there was no
     *		conflict
     * @throws	IllegalStateException if {@link #notifyNewTransaction
     *		notifyNewTransaction} has not be called for {@code txn}, or if
     *		an earlier lock attempt for this transaction produced a
     *		deadlock
     */
    public LockConflict lock(Transaction txn,
			     String source,
			     Object objectId,
			     boolean forWrite,
			     Object description)
    {
	Locker locker = getLocker(txn);
	checkLockerNotAborted(locker);
	LockConflict conflict = lockNoWaitInternal(
	    locker, new Key(source, objectId), forWrite, description);
	return (conflict == null) ? null : waitForLockInternal(locker);
    }

    /**
     * Attempts to acquire a lock, returning immediately.  Returns information
     * about any conflict that occurred while attempting to acquire the lock,
     * or else {@code null} if the lock was acquired.  If the attempt to
     * acquire the lock was blocked, returns a value with a {@code type} field
     * of {@link LockConflictType#BLOCKED BLOCKED} rather than waiting.  If the
     * {@code type} field of the return value is {@link
     * LockConflictType#DEADLOCK DEADLOCK}, then the caller should call {@link
     * #releaseAll releaseAll} to end the transaction, and any other lock or
     * wait requests will throw {@link IllegalStateException}.
     *
     * @param	txn the transaction requesting the lock
     * @param	source the source of the object
     * @param	objectId the ID of the object
     * @param	forWrite whether to request a write lock
     * @param	description a description of the object being accessed, or
     *		{@code null}
     * @return	lock conflict information, or {@code null} if there was no
     *		conflict
     * @throws	IllegalStateException if {@link #notifyNewTransaction
     *		notifyNewTransaction} has not be called for {@code txn}, or if
     *		an earlier lock attempt for this transaction produced a
     *		deadlock
     */
    public LockConflict lockNoWait(Transaction txn,
				   String source,
				   Object objectId,
				   boolean forWrite,
				   Object description)
    {
	Locker locker = getLocker(txn);
	checkLockerNotAborted(locker);	
	return lockNoWaitInternal(
	    locker, new Key(source, objectId), forWrite, description);
    }

    /**
     * Waits for a previous attempt to obtain a lock that blocked.  Returns
     * information about any conflict that occurred while attempting to acquire
     * the lock, or else {@code null} if the lock was acquired or the
     * transaction was not waiting.  If the {@code type} field of the return
     * value is {@link LockConflictType#DEADLOCK DEADLOCK}, then the caller
     * should call {@link #releaseAll releaseAll} to end the transaction, and
     * any other lock or wait requests will throw {@link
     * IllegalStateException}.
     *
     * @param	txn the transaction requesting the lock
     * @return	lock conflict information, or {@code null} if there was no
     *		conflict
     * @throws	IllegalStateException if {@link #notifyNewTransaction
     *		notifyNewTransaction} has not be called for {@code txn}, or if
     *		an earlier lock attempt for this transaction produced a
     *		deadlock
     */
    public LockConflict waitForLock(Transaction txn) {
	Locker locker = getLocker(txn);
	checkLockerNotAborted(locker);
	return waitForLockInternal(locker);
    }

    public LockConflict checkLock(Transaction txn) {
	Locker locker = getLocker(txn);
	LockConflict lockConflict = locker.getConflict();
	if (lockConflict != null) {
	    return lockConflict;
	}
	Lock lock = locker.getWaitingFor();
	if (lock == null) {
	    return null;
	}
	Key key = lock.key;
	Map<Key, Lock> keyMap = getKeyMap(key);
	LockRequest request;
	synchronized (keyMap) {
	    //XXX
	    request = lock.getFirstOwner();
	}
	Transaction conflictingTxn = request.locker.txn;
	long now = System.currentTimeMillis();
	long stop = Math.min(now + lockTimeout, locker.stopTime);
	if (now > stop) {
	    synchronized (keyMap) {
		lock.flushWaiter(locker);
	    }
	    locker.setWaitingFor(null);
	    return new LockConflict(
		LockConflictType.TIMEOUT, conflictingTxn);
	} else {
	    return new LockConflict(
		LockConflictType.BLOCKED, conflictingTxn);
	}
    }

    /**
     * Releases all of the locks associated with the specified transaction and
     * returns information about the locks requested during that transaction.
     *
     * @param	txn the transaction
     * @return	information about object accesses by this transaction
     * @throws	IllegalStateException if {@link #notifyNewTransaction
     *		notifyNewTransaction} has not be called, or {@link #releaseAll
     *		releaseAll} has already been called, for {@code txn}
     */
    public AccessedObjectsDetail releaseAll(Transaction txn) {
	Locker locker = getLocker(txn);
	for (LockRequest request : locker.requests) {
	    Key key = request.key;
	    Map<Key, Lock> keyMap = getKeyMap(key);
	    Set<Locker> newOwners;
	    synchronized (keyMap) {
		Lock lock = getLock(key, keyMap);
		newOwners = lock.release(locker);
		if (!lock.inUse()) {
		    keyMap.remove(key);
		}
	    }
	    for (Locker newOwner : newOwners) {
		synchronized (newOwner) {
		    newOwner.notify();
		}
	    }
	}
	txnMap.remove(txn);
	return locker;
    }

    /* -- Public classes -- */

    /** The type of lock conflict. */
    public enum LockConflictType {

	/** The request is currently blocked. */
	BLOCKED,

	/** The request timed out. */
	TIMEOUT,

	/** The request was denied. */
	DENIED,

	/** The request resulted in deadlock and was chosen to be aborted. */
	DEADLOCK;
    }

    /**
     * A class for representing a conflict resulting from requesting a lock.
     */
    public static final class LockConflict {

	/** The type of conflict. */
	private final LockConflictType type;

	/** A transaction that caused the conflict. */
	private final Transaction conflictingTxn;

	/**
	 * Creates an instance of this class.
	 *
	 * @param	type the type of conflict
	 * @param	conflictingTxn a transaction that caused the conflict
	 */
	public LockConflict(
	    LockConflictType type, Transaction conflictingTxn)
	{
	    assert type != null;
	    assert conflictingTxn != null;
	    this.type = type;
	    this.conflictingTxn = conflictingTxn;
	}

	/**
	 * Returns the type of conflict.
	 *
	 * @return	the type of conflict
	 */
	public LockConflictType getType() {
	    return type;
	}

	/**
	 * Returns a transaction that caused the conflict.
	 *
	 * @return	a transaction that caused the conflict
	 */
	public Transaction getConflictingTxn() {
	    return conflictingTxn;
	}

	/** Print fields, for debugging. */
	@Override
	public String toString() {
	    return "LockConflict[type:" + type +
		", conflictingTxn:" + conflictingTxn + "]";
	}
    }

    /* -- Other methods -- */

    /**
     * Returns the locker associated with a transaction.
     *
     * @param	txn the transaction
     * @return	the locker
     * @throws	IllegalStateException if the transaction is not active
     */
    Locker getLocker(Transaction txn) {
	checkNonNull(txn, "txn");
	Locker locker = txnMap.get(txn);
	if (locker == null) {
	    throw new IllegalStateException("Transaction not active: " + txn);
	}
	return locker;
    }

    /**
     * Returns the key map that should be used for the specified key.
     *
     * @param	key the key
     * @return	the associated key map
     */
    private Map<Key, Lock> getKeyMap(Key key) {
	return keyMaps[(key.hashCode() & Integer.MAX_VALUE) % numKeyMaps];
    }

    /**
     * Returns the lock associated with the specified key from the key map,
     * which should be the one returned by calling {@link #getKeyMap
     * getKeyMap}.  The lock on {@code keyMap} should be held.
     *
     * @param	key the key
     * @param	keyMap the keyMap
     * @return	the associated lock
     */
    private static Lock getLock(Key key, Map<Key, Lock> keyMap) {
	assert Thread.holdsLock(keyMap);
	Lock lock = keyMap.get(key);
	if (lock == null) {
	    lock = new Lock(key);
	    keyMap.put(key, lock);
	}
	return lock;
    }

    /** Creates the key maps array. */
    @SuppressWarnings("unchecked")
    private Map<Key, Lock>[] createKeyMaps(int n) {
	return new Map[n];
    }

    /** Checks if the locker has been requested to abort. */
    private static void checkLockerNotAborted(Locker locker) {
	LockConflict lockConflict = locker.getConflict();
	if (lockConflict != null &&
	    lockConflict.type == LockConflictType.DEADLOCK)
	{
	    throw new IllegalStateException(
		"Transaction must abort: " + locker.txn);
	}
    }

    /** 
     * Releases the locks for the transaction and reports object accesses to
     * the profiling system.
     *
     * @param	txn the finished transaction
     */
    private void reportDetail(Transaction txn) {
        profileCollectorHandle.setAccessedObjectsDetail(releaseAll(txn));
    }

    /** Attempts to acquire a lock, returning immediately. */
    private LockConflict lockNoWaitInternal(
	Locker locker, Key key, boolean forWrite, Object description)
    {
	if (description != null) {
	    locker.setDescription(key, description);
	}
	Map<Key, Lock> keyMap = getKeyMap(key);
	Transaction conflictingTxn;
	Lock lock;
	synchronized (keyMap) {
	    lock = getLock(key, keyMap);
	    LockAttemptResult result = lock.lock(locker, forWrite);
	    if (result == null) {
		if (logger.isLoggable(Level.FINER)) {
		    logger.log(Level.FINER,
			       "Lock {0}, {1}, forWrite:{2}" +
			       "\n  returns null (already granted)",
			locker, key, forWrite);
		}
		return null;
	    } else if (result.conflict == null) {
		locker.requests.add(result.request);
		if (logger.isLoggable(Level.FINER)) {
		    logger.log(Level.FINER,
			       "Lock {0}, {1}, forWrite:{2}" +
			       "\n  returns null (new)",
			locker, key, forWrite);
		}
		return null;
	    } else {
		conflictingTxn = result.conflict.txn;
	    }
	}
	if (logger.isLoggable(Level.FINEST)) {
	    logger.log(Level.FINEST, "Lock {0}, {1}, forWrite:{2}\n  blocked",
		       locker, key, forWrite);
	}
	LockConflict conflict = new DeadlockChecker().check(locker, key);
	if (conflict == null) {
	    conflict =
		new LockConflict(LockConflictType.BLOCKED, conflictingTxn);
	}
	if (logger.isLoggable(Level.FINER)) {
	    logger.log(Level.FINER,
		       "Lock {0}, {1}, forWrite:{2}\n  returns {3}",
			locker, key, forWrite, conflict);
	}
	locker.setWaitingFor(lock);
	return conflict;
    }

    /** Attempts to acquire a lock, waiting if needed. */
    private LockConflict waitForLockInternal(Locker locker) {
	Lock lock = locker.getWaitingFor();
	if (lock == null) {
	    logger.log(Level.FINER,
		       "Wait for lock {0}\n  returns null (already granted)",
			locker);
	    return null;
	}
	Key key = lock.key;
	Map<Key, Lock> keyMap = getKeyMap(key);
	LockRequest request;
	synchronized (keyMap) {
	    //XXX
	    request = lock.getFirstOwner();
	}
	Transaction conflictingTxn = request.locker.txn;
	long now = System.currentTimeMillis();
	long stop = Math.min(now + lockTimeout, locker.stopTime);
	if (logger.isLoggable(Level.FINER)) {
	    logger.log(Level.FINER, "Wait for lock {0}, stop:{1,number,#}",
		       locker, stop);
	}
	while (true) {
	    if (now > stop) {
		synchronized (keyMap) {
		    lock.flushWaiter(locker);
		}
		locker.setWaitingFor(null);
		LockConflict conflict = new LockConflict(
		    LockConflictType.TIMEOUT, conflictingTxn);
		if (logger.isLoggable(Level.FINER)) {
		    logger.log(Level.FINER, "Wait for lock {0}\n  returns {1}",
			       locker, conflict);
		}
		return conflict;
	    }
	    try {
		synchronized (locker) {
		    locker.wait(stop - now);
		}
	    } catch (InterruptedException e) {
		throw new RuntimeException("Unexpected interrupt");
	    }
	    synchronized (keyMap) {
		if (lock.isOwner(locker, request.getForWrite())) {
		    return null;
		}
	    }
	    LockConflict conflict = locker.getConflict();
	    if (conflict != null) {
		synchronized (keyMap) {
		    lock.flushWaiter(locker);
		}
		locker.setWaitingFor(null);
		if (logger.isLoggable(Level.FINER)) {
		    logger.log(Level.FINER, "Wait for lock {0}\n  returns {1}",
			       locker, conflict);
		}
		return conflict;
	    }
	    now = System.currentTimeMillis();
	}
    }

    /* -- Other classes -- */

    /** Records information about a transaction requesting locks. */
    static class Locker implements AccessedObjectsDetail {

	/** The transaction. */
	final Transaction txn;

	/**
	 * The time in milliseconds when the task associated with this
	 * transaction was originally requested to start.
	 */
	final long requestedStartTime;

	/** The time in milliseconds when this transaction times out. */
	final long stopTime;

	/** The lock requests made by this transaction. */
	final List<LockRequest> requests = new ArrayList<LockRequest>();

	/** A map from keys to descriptions, or {@code null}. */
	private Map<Key, Object> keyToDescriptionMap = null;

	/**
	 * The lock that this transaction is waiting for, or {@code null} if it
	 * is not waiting.  Synchronize on this locker when accessing this
	 * field.
	 */
	private Lock waitingFor;

	/**
	 * A conflict that should cause the locker's current request to be
	 * denied, or {@code null}.  Synchronize on this locker when accessing
	 * this field.
	 */
	private LockConflict conflict;

	/**
	 * Creates an instance of this class.
	 *
	 * @param	txn the associated transaction
	 * @param	requestedStartTime the time milliseconds that the task
	 *		associated with the transaction was originally
	 *		requested to start
	 */
	Locker(Transaction txn, long requestedStartTime) {
	    this.txn = txn;
	    this.stopTime = System.currentTimeMillis() + txn.getTimeout();
	    this.requestedStartTime = requestedStartTime;
	}

	/* -- Implement AccessedObjectsDetail -- */

	/** {@inheritDoc} */
	public List<? extends AccessedObject> getAccessedObjects() {
	    return Collections.unmodifiableList(requests);
	}

	/** {@inheritDoc} */
	public ConflictType getConflictType() {
	    if (conflict == null) {
		return ConflictType.NONE;
	    } else if (conflict.type == LockConflictType.DEADLOCK) {
		return ConflictType.DEADLOCK;
	    } else {
		return ConflictType.ACCESS_NOT_GRANTED;
	    }
	}

	/** {@inheritDoc} */
	public byte[] getConflictingId() {
	    return txn.getId();
	}

	/* -- Other methods -- */

	/**
	 * Sets the description associated with a key for this locker.  The
	 * description should not be {@code null}.  Does not replace an
	 * existing description.
	 *
	 * @param	key the key
	 * @param	description the description
	 */
	void setDescription(Key key, Object description) {
	    assert key != null;
	    assert description != null;
	    if (keyToDescriptionMap == null) {
		keyToDescriptionMap = new HashMap<Key, Object>();
	    }
	    if (!keyToDescriptionMap.containsKey(key)) {
		keyToDescriptionMap.put(key, description);
	    }
	}

	/**
	 * Gets the description associated with a key for this locker.
	 *
	 * @param	key the key
	 * @return	the description or {@code null}
	 */
	Object getDescription(Key key) {
	    return (keyToDescriptionMap == null)
		? null : keyToDescriptionMap.get(key);
	}

	/**
	 * Checks if there is a conflict that should cause this locker's
	 * request to be denied
	 *
	 * @return	the conflicting request or {@code null}
	 */
	synchronized LockConflict getConflict() {
	    return conflict;
	}

	/**
	 * Requests that this locker request be denied because of a conflict
	 * with the specified request.
	 */
	synchronized void setConflict(LockConflict conflict) {
	    this.conflict = conflict;
	    notify();
	}

	/**
	 * Checks if this locker is waiting for a lock.
	 *
	 * @return	the lock this locker is waiting for or {@code null} if
	 *		it is not waiting
	 */
	synchronized Lock getWaitingFor() {
	    return waitingFor;
	}

	/**
	 * Sets the lock that this locker is waiting for, or marks that it is
	 * not waiting if the argument is {@code null}.
	 *
	 * @param	waitingFor the lock or {@code null}
	 */
	synchronized void setWaitingFor(Lock waitingFor) {
	    this.waitingFor = waitingFor;
	}

	/** Print fields, for debugging. */
	@Override
	public String toString() {
	    return "Locker[txn:" + txn + "]";
	}
    }

    /** Represents an object as identified by a source and an object ID. */
    private static final class Key {

	/** The source. */
	final String source;

	/** The object ID. */
	final Object objectId;

	/**
	 * Creates an instance of this class.
	 *
	 * @param	source the source of the object
	 * @param	objectId the object ID of the object
	 */
	Key(String source, Object objectId) {
	    checkNonNull(source, "source");
	    checkNonNull(objectId, "objectId");
	    this.source = source;
	    this.objectId = objectId;
	}

	/* -- Compare source and object ID -- */

	@Override
	public boolean equals(Object object) {
	    if (object == this) {
		return true;
	    } else if (object instanceof Key) {
		Key key = (Key) object;
		return source.equals(key.source) &&
		    objectId.equals(key.objectId);
	    } else {
		return false;
	    }
	}

	@Override
	public int hashCode() {
	    return source.hashCode() ^ objectId.hashCode();
	}

	/** Print fields, for debugging. */
	@Override
	public String toString() {
	    return "Key[source:" + source + ", objectId:" + objectId + "]";
	}
    }

    /**
     * A class used to represent locks. <p>
     *
     * Callers should only call non-Object methods on instances of this class
     * if they hold the lock on the key map associated with the instance.
     */
    private static class Lock {

	/** The key that identifies this lock. */
	final Key key;

	/** The requests that currently own this lock. */
	private final List<LockRequest> owners = new ArrayList<LockRequest>();

	/** The requests that are waiting for this lock. */
	private final List<LockRequest> waiters = new ArrayList<LockRequest>();

	/**
	 * Creates a lock.
	 *
	 * @param	key the key that identifies this lock
	 */
	Lock(Key key) {
	    checkNonNull(key, "key");
	    this.key = key;
	}

	/**
	 * Attempts to obtain this lock.  Adds the locker as an owner of the
	 * lock if the lock was obtained.  Returns {@code null} if the locker
	 * already owned this lock.  Otherwise, returns a {@code
	 * LockAttemptResult} containing the {@link LockRequest} and with the
	 * {@code granted} field set to {@code true} if the lock was acquired,
	 * else {@code false} if the lock could not be obtained.
	 *
	 * @param	locker the locker requesting the lock
	 * @param	forWrite whether a write lock is requested
	 * @return	a {@code LockAttemptResult} or {@code null}
	 */
	LockAttemptResult lock(Locker locker, boolean forWrite) {
	    if (owners.isEmpty()) {
		LockRequest request =
		    new LockRequest(locker, key, forWrite, false);
		owners.add(request);
		return new LockAttemptResult(request, null);
	    }
	    boolean upgrade = false;
	    Locker conflict = null;
	    for (LockRequest ownerRequest : owners) {
		if (locker == ownerRequest.locker) {
		    if (!forWrite || ownerRequest.getForWrite()) {
			return null;
		    } else {
			upgrade = true;
		    }
		} else if (forWrite || ownerRequest.getForWrite()) {
		    conflict = ownerRequest.locker;
		}
	    }
	    LockRequest request =
		new LockRequest(locker, key, forWrite, upgrade);
	    if (conflict == null) {
		if (upgrade) {
		    for (Iterator<LockRequest> i = owners.iterator();
			 i.hasNext(); )
		    {
			LockRequest ownerRequest = i.next();
			if (locker == ownerRequest.locker) {
			    i.remove();
			    break;
			}
		    }
		}
		owners.add(request);
		return new LockAttemptResult(request, null);
	    }
	    addWaiter(request);
	    return new LockAttemptResult(request, conflict);
	}

	/**
	 * Add a lock request to the list of requests waiting for this lock.
	 * If this is an upgrade request, puts the request after any other
	 * upgrade requests, but before other requests.  Otherwise, puts the
	 * request at the end of the list.
	 */
	private void addWaiter(LockRequest request) {
	    if (waiters.isEmpty() || !request.getUpgrade()) {
		waiters.add(request);
	    } else {
		/*
		 * Add upgrade requests after any existing ones, but before
		 * other requests.
		 */
		for (int i = 0; i < waiters.size(); i++) {
		    if (!waiters.get(i).getUpgrade()) {
			waiters.add(i, request);
			break;
		    }
		}
	    }
	}

	/**
	 * Releases the ownership of this lock by the locker.  Returns the set
	 * of lockers who have been newly made owners of this lock, if any.
	 *
	 * @param	locker the locker whose ownership will be released
	 * @return	the newly added owners
	 */
	Set<Locker> release(Locker locker) {
	    boolean owned = false;
	    for (Iterator<LockRequest> i = owners.iterator(); i.hasNext(); ) {
		LockRequest ownerRequest = i.next();
		if (locker == ownerRequest.locker) {
		    i.remove();
		    owned = true;
		    break;
		}
	    }
	    Set<Locker> lockersToNotify = Collections.emptySet();
	    if (owned && !waiters.isEmpty()) {
		boolean found = false;
		for (int i = 0; i < waiters.size(); i++) {
		    LockRequest waiter = waiters.get(i);
		    LockAttemptResult result =
			lock(waiter.locker, waiter.getForWrite());
		    if (result != null && result.conflict != null) {
			break;
		    }
		    waiters.remove(i--);
		    if (!found) {
			found = true;
			lockersToNotify = new HashSet<Locker>();
		    }
		    lockersToNotify.add(waiter.locker);
		}
	    }
	    return lockersToNotify;
	}

	/** Checks if this lock has any owners or waiters. */
	boolean inUse() {
	    return !owners.isEmpty() || !waiters.isEmpty();
	}

	/**
	 * Returns the lock request associated with the first owner, assuming
	 * that the lock has at least one owner.
	 */
	LockRequest getFirstOwner() {
	    assert !owners.isEmpty();
	    return owners.get(0);
	}

	/** Returns a copy of the locker requests for the owners. */
	LockRequest[] copyOwners() {
	    if (owners.isEmpty()) {
		return NO_LOCK_REQUESTS;
	    } else {
		return owners.toArray(new LockRequest[owners.size()]);
	    }
	}

	/** Removes a locker from the list of waiters for this lock. */
	void flushWaiter(Locker locker) {
	    for (int i = 0; i < waiters.size(); i++) {
		LockRequest request = waiters.get(i);
		if (request.locker == locker) {
		    waiters.remove(i);
		    break;
		}
	    }
	}

	/**
	 * Checks if the specified locker owns this lock, requiring write
	 * ownership if {@code forWrite} is true.
	 */
	boolean isOwner(Locker locker, boolean forWrite) {
	    for (Iterator<LockRequest> i = owners.iterator(); i.hasNext(); ) {
		LockRequest request = i.next();
		if (request.locker == locker) {
		    return !forWrite || request.getForWrite();
		}
	    }
	    return false;
	}

	/** Print fields, for debugging. */
	@Override
	public String toString() {
	    return "Lock[key:" + key + "]";
	}
    }

    /** The result of attempting to request a lock. */
    private static class LockAttemptResult {

	/** The lock request. */
	final LockRequest request;

	/** A conflicting locker, if the request was not granted, or null. */
	final Locker conflict;

	/**
	 * Creates an instance of this class.
	 *
	 * @param	request the lock request
	 * @param	conflict a conflicting locker or null
	 */
	LockAttemptResult(LockRequest request, Locker conflict) {
	    assert request != null;
	    this.request = request;
	    this.conflict = conflict;
	}
    }

    /** Implement {@link AccessReporter}. */
    private class AccessReporterImpl<T> extends AbstractAccessReporter<T> {

	/**
	 * Creates an instance of this class.
	 *
	 * @param	source the source of the objects managed by this
	 *		reporter
	 */
	AccessReporterImpl(String source) {
	    super(source);
	}

	/* -- Implement AccessReporter -- */

	/** {@inheritDoc} */
	public void reportObjectAccess(
	    Transaction txn, T objectId, AccessType type, Object description)
	{
	    checkNonNull(type, "type");
	    lock(txn, source, objectId, type == AccessType.WRITE, description);
	}

	/** {@inheritDoc} */
	public void setObjectDescription(
	    Transaction txn, T objectId, Object description)
	{
	    if (description != null) {
		getLocker(txn).setDescription(
		    new Key(source, objectId), description);
	    }
	}
    }

    /** A class representing a request for a lock. */
    private static final class LockRequest implements AccessedObject {

	/** Types of requests. */
	private enum Type { READ, WRITE, UPGRADE; }

	/** The locker that requested the lock. */
	final Locker locker;

	/** The key identifying the lock. */
	final Key key;

	/** The request type. */
	private final Type type;

	/**
	 * Creates a lock request.
	 *
	 * @param	locker the locker that requested the lock
	 * @param	key the key identifying the lock
	 * @param	forWrite whether a write lock was requested
	 * @param	upgrade whether an upgrade was requested
	 */
	LockRequest(
	    Locker locker, Key key, boolean forWrite, boolean upgrade)
	{
	    assert locker != null;
	    assert key != null;
	    assert !upgrade || forWrite : "Upgrade implies forWrite";
	    this.locker = locker;
	    this.key = key;
	    type = !forWrite ? Type.READ
		: !upgrade ? Type.WRITE
		: Type.UPGRADE;
	}

	/* -- Implement AccessedObject -- */

	/** {@inheritDoc} */
	public String getSource() {
	    return key.source;
	}

	/** {@inheritDoc} */
	public Object getObjectId() {
	    return key.objectId;
	}

	/** {@inheritDoc} */
	public AccessType getAccessType() {
	    return (type == Type.READ) ? AccessType.READ : AccessType.WRITE;
	}

	/** {@inheritDoc} */
	public Object getDescription() {
	    return locker.getDescription(key);
	}

	/* -- Other methods -- */

	/** Print fields, for debugging. */
	@Override
	public String toString() {
	    return "LockRequest[" + locker + ", " + key +
		", type:" + type + "]";
	}

	/** Returns whether the request was for write. */
	boolean getForWrite() {
	    return type != Type.READ;
	}

	/** Returns whether the request was for an upgrade. */
	boolean getUpgrade() {
	    return type == Type.UPGRADE;
	}
    }

    /** Utility class for detecting deadlocks. */
    private class DeadlockChecker {

	/** All blocked owners found so far. */
	private final Set<Locker> allOwners = new HashSet<Locker>();

	/** The locker that was found in a circular reference. */
	private Locker cycleBoundary;

	/** The current choice of a locker to abort. */
	private Locker victim;

	/** Another locker in the deadlock. */
	private Locker conflict;

	/** Creates an instance of this class. */
	DeadlockChecker() { }

	/**
	 * Checks for a deadlock starting with a locker blocked requesting the
	 * lock for a key.
	 *
	 * @param	locker the locker
	 * @param	key the key
	 * @return	a lock conflict if a deadlock was found, else {@code
	 *		null} 
	 */
	LockConflict check(Locker locker, Key key) {
	    allOwners.add(locker);
	    if (!checkInternal(locker, key)) {
		logger.log(Level.FINEST, "No deadlock found");
		return null;
	    }
	    LockConflict lockConflict =
		new LockConflict(LockConflictType.DEADLOCK, conflict.txn);
	    victim.setConflict(lockConflict);
	    if (victim == locker) {
		return lockConflict;
	    } else {
		return new LockConflict(
		    LockConflictType.BLOCKED, conflict.txn);
	    }
	}
		    
	/**
	 * Checks for deadlock starting with a locker blocked requesting the
	 * lock for a key, given the previously found lockers that are
	 * blocked.  Returns whether the deadlock was found.
	 */
	private boolean checkInternal(Locker locker, Key key) {
	    Map<Key, Lock> keyMap = getKeyMap(key);
	    LockRequest[] owners;
	    synchronized (keyMap) {
		owners = getLock(key, keyMap).copyOwners();
	    }
	    if (logger.isLoggable(Level.FINEST)) {
		logger.log(Level.FINEST,
			   "Check deadlock {0}, {1}" +
			   "\n  owners:{2}" +
			   "\n  allOwners:{3}",
			   locker, key, Arrays.toString(owners), allOwners);
	    }
	    for (LockRequest ownerRequest : owners) {
		Locker owner = ownerRequest.locker;
		if (owner == locker) {
		    continue;
		}
		if (allOwners.contains(owner)) {
		    /* Found a deadlock! */
		    cycleBoundary = owner;
		    victim = owner;
		    logger.log(Level.FINEST,
			       "Check deadlock found victim:{0}",
			       victim);
		    return true;
		}
		Lock ownerWaitingFor = owner.getWaitingFor();
		if (logger.isLoggable(Level.FINEST)) {
		    logger.log(Level.FINEST,
			       "Check owner:{0}, waitingFor:{1}",
			       owner, ownerWaitingFor);
		}
		if (ownerWaitingFor != null) {
		    allOwners.add(owner);
		    boolean deadlock =
			checkInternal(owner, ownerWaitingFor.key);
		    if (deadlock) {
			maybeUpdateVictim(owner);
		    }
		    return deadlock;
		}
	    }
	    return false;
	}

	/**
	 * Updates the victim and conflict fields given an additional locker in
	 * the chain of lockers checked.
	 */
	private void maybeUpdateVictim(Locker locker) {
	    if (conflict == null) {
		conflict = locker;
	    }
	    if (locker == cycleBoundary) {
		/* We've gone all the way around the circle, so we're done */
		cycleBoundary = null;
	    } else if (cycleBoundary != null &&
		       locker.requestedStartTime > victim.requestedStartTime)
	    {
		/*
		 * We're not done and this locker started later than the
		 * current victim, so use it instead.
		 */
		victim = locker;
		logger.log(Level.FINEST,
			   "Check deadlock new victim:{0}", victim);
	    }
	}
    }
}
