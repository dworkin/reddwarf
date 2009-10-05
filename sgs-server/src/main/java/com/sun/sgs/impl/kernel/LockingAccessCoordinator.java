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

package com.sun.sgs.impl.kernel;

import com.sun.sgs.app.TransactionAbortedException;
import com.sun.sgs.app.TransactionConflictException;
import com.sun.sgs.app.TransactionTimeoutException;
import com.sun.sgs.impl.profile.ProfileCollectorHandle;
import com.sun.sgs.impl.service.transaction.TransactionCoordinator;
import com.sun.sgs.impl.service.transaction.TransactionCoordinatorImpl;
import com.sun.sgs.impl.sharedutil.LoggerWrapper;
import static com.sun.sgs.impl.sharedutil.Objects.checkNull;
import com.sun.sgs.impl.sharedutil.PropertiesWrapper;
import com.sun.sgs.kernel.AccessCoordinator;
import com.sun.sgs.kernel.AccessReporter;
import com.sun.sgs.kernel.AccessReporter.AccessType;
import com.sun.sgs.kernel.AccessedObject;
import com.sun.sgs.profile.AccessedObjectsDetail;
import com.sun.sgs.profile.AccessedObjectsDetail.ConflictType;
import com.sun.sgs.service.Transaction;
import com.sun.sgs.service.TransactionInterruptedException;
import com.sun.sgs.service.TransactionListener;
import com.sun.sgs.service.TransactionProxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;
import java.util.logging.Logger;


/*
 * TODO: Consider creating a base class that provides the lock and
 * lockNoWaitInternal level methods as a common utility.
 * -tjb@sun.com (03/10/2009)
 */

/**
 * An implementation of {@link AccessCoordinator} that uses locking to handle
 * conflicts. <p>
 *
 * This implementation checks for deadlock whenever an access request is
 * blocked due to a conflict.  It selects the youngest transaction as the
 * deadlock victim, determining the age using the originally requested start
 * time for the task associated with the transaction.  The implementation does
 * not deny requests that would not result in deadlock.  When requests block,
 * it services the requests in the order that they arrive, except for upgrade
 * requests, which it puts ahead of non-upgrade requests.  The justification
 * for special treatment of upgrade requests is that an upgrade request is
 * useless if a conflicting request goes first and causes the waiter to lose
 * its read lock. <p>
 *
 * The methods that this class provides to implement {@code AccessReporter} are
 * not thread safe, and should either be called from a single thread or else
 * protected with external synchronization. <p>
 *
 * The {@link #LockingAccessCoordinator constructor} supports the following
 * configuration properties: <p>
 *
 * <dl style="margin-left: 1em">
 *
 * <dt> <i>Property:</i> <b>{@value #LOCK_TIMEOUT_PROPERTY}</b> <br>
 *	<i>Default:</i> {@value #DEFAULT_LOCK_TIMEOUT_PROPORTION} times the
 *	value of the {@code com.sun.sgs.txn.timeout} property, if specified,
 *	otherwise times the value of the default transaction timeout.
 *
 * <dd style="padding-top: .5em">The maximum number of milliseconds to wait for
 *	obtaining a lock.  The value must be greater than {@code 0}, and should
 *	be less than the transaction timeout. <p>
 *
 * <dt> <i>Property:</i> <b>{@value #NUM_KEY_MAPS_PROPERTY}</b> <br>
 *	<i>Default:</i> {@value #NUM_KEY_MAPS_DEFAULT}
 *
 * <dd style="padding-top: .5em">The number of maps to use for associating keys
 *	and maps.  The number of maps controls the amount of concurrency, and
 *	should typically be set to a value to support concurrent access by the
 *	number of active threads.  The value must be greater than {@code
 *	0}. <p>
 *
 * </dl> <p>
 *
 * This class uses the {@link Logger} named {@code
 * com.sun.sgs.impl.kernel.LockingAccessCoordinator} to log information at the
 * following logging levels: <p>
 *
 * <ul>
 * <li> {@link Level#FINER FINER} - Beginning and ending transactions;
 *	requesting, waiting for, and returning from lock requests; deadlocks
 * <li> {@link Level#FINEST FINEST} - Notifying new lock owners, results of
 *	requesting locks before waiting, releasing locks, results of attempting
 *	to assign locks to waiters, details of checking deadlocks
 * </ul> <p>
 *
 * The implementation of this class uses the following thread synchronization
 * scheme to avoid internal deadlocks:
 *
 * <ul>
 *
 * <li>Synchronization is only used on {@link Locker} objects and on the {@code
 *     Map}s that hold {@code Lock} objects
 *
 * <li>A thread can synchronize on at most one locker and one lock at a time,
 *     always synchronizing on the locker first
 *
 * </ul>
 *
 * To make it easier to adhere to these rules, the implementation takes the
 * following steps:
 *
 * <ul>
 *
 * <li>The {@code Lock} class is not synchronized <p>
 *
 *     Callers of non-{@code Object} methods on the {@code Lock} class should
 *     make sure that they are synchronized on the associated key map.
 *
 * <li>The {@link Locker} class only uses synchronization for getter and setter
 *     methods
 *
 * <li>Blocks synchronized on a {@code Lock} should not synchronize on anything
 *     else <p>
 *
 *     The code enforces this requirement by having lock methods not make calls
 *     to other classes, and by performing minimal work while synchronized on
 *     the associated key map.
 *
 * <li>Blocks synchronized on a {@code Locker} should not synchronize on a
 *     different locker, but can synchronize on a {@code Lock}
 *
 *     In fact, only one method synchronizes on a {@code Locker} and on a
 *     {@code Lock} -- the {@link #waitForLockInternal waitForLockInternal}
 *     method.  That method also makes sure that the only synchronized {@code
 *     Locker} methods that it calls are on the locker it has already
 *     synchronized on.  The {@code DeadlockChecker} class also synchronizes on
 *     key maps and lockers, but is called outside of synchronized blocks and
 *     only synchronizes on one thing at a time.
 *
 * <li>Use assertions to check adherence to the scheme
 *
 * </ul>
 */
public class LockingAccessCoordinator extends AbstractAccessCoordinator {

    /** The class name. */
    private static final String CLASS =
	"com.sun.sgs.impl.kernel.LockingAccessCoordinator";

    /**
     * The property for specifying the maximum number of milliseconds to wait
     * for obtaining a lock.
     */
    public static final String LOCK_TIMEOUT_PROPERTY =
	CLASS + ".lock.timeout";

    /**
     * The proportion of the transaction timeout to use for the lock timeout if
     * no lock timeout is specified.
     */
    public static final double DEFAULT_LOCK_TIMEOUT_PROPORTION = 0.1;

    /**
     * The default value of the lock timeout property, if no transaction
     * timeout is specified.
     */
    public static final long DEFAULT_LOCK_TIMEOUT = 
	computeLockTimeout(TransactionCoordinatorImpl.BOUNDED_TIMEOUT_DEFAULT);

    /**
     * The property for specifying the number of maps to use for associating
     * keys and maps.  The number of maps controls the amount of concurrency.
     */
    public static final String NUM_KEY_MAPS_PROPERTY =
	CLASS + ".num.key.maps";

    /** The default number of key maps. */
    public static final int NUM_KEY_MAPS_DEFAULT = 8;

    /** The logger for this class. */
    static final LoggerWrapper logger = new LoggerWrapper(
	Logger.getLogger(LockingAccessCoordinator.class.getName()));

    /** Maps transactions to lockers. */
    private final ConcurrentMap<Transaction, Locker> txnMap =
	new ConcurrentHashMap<Transaction, Locker>();

    /**
     * The maximum number of milliseconds to spend attempting to acquire a
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
     * locks is based on locking the associated key map.  Non-{@code Object}
     * methods on locks should not be used without synchronizing on the
     * associated key map lock.
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
	long txnTimeout = wrappedProps.getLongProperty(
	    TransactionCoordinator.TXN_TIMEOUT_PROPERTY, -1);
	long defaultLockTimeout = (txnTimeout < 1)
	    ? DEFAULT_LOCK_TIMEOUT : computeLockTimeout(txnTimeout);
	lockTimeout = wrappedProps.getLongProperty(
	    LOCK_TIMEOUT_PROPERTY, defaultLockTimeout, 1, Long.MAX_VALUE);
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
	checkNull("objectIdType", objectIdType);
	return new AccessReporterImpl<T>(sourceName);
    }

    /**
     * {@inheritDoc} <p>
     *
     * This implementation does not record information about completed
     * transactions, so it always returns {@code null}.
     */
    public Transaction getConflictingTransaction(Transaction txn) {
	checkNull("txn", txn);
	return null;
    }

    /* -- Implement AccessCoordinatorHandle -- */

    /** {@inheritDoc} */
    public void notifyNewTransaction(
	Transaction txn, long requestedStartTime, int tryCount)
    {
	if (tryCount < 1) {
	    throw new IllegalArgumentException(
		"The tryCount must not be less than 1");
	}
	Locker locker = new Locker(txn, requestedStartTime);
	Locker existing = txnMap.putIfAbsent(txn, locker);
	if (existing != null) {
	    throw new IllegalStateException("Transaction already started");
	}
	if (logger.isLoggable(Level.FINER)) {
	    logger.log(Level.FINER,
		       "begin {0}, requestedStartTime:{1,number,#}",
		       locker, requestedStartTime);
	}
	txn.registerListener(new TxnListener(txn));
    }

    /* -- Other public methods -- */

    /**
     * Attempts to acquire a lock, waiting if needed.  Returns information
     * about conflicts that occurred while attempting to acquire the lock that
     * prevented the lock from being acquired, or else {@code null} if the lock
     * was acquired.  If the {@code type} field of the return value is {@link
     * LockConflictType#DEADLOCK DEADLOCK}, then the caller should abort the
     * transaction, and any subsequent lock or wait requests will throw {@code
     * IllegalStateException}.
     *
     * @param	txn the transaction requesting the lock
     * @param	source the source of the object
     * @param	objectId the ID of the object
     * @param	forWrite whether to request a write lock
     * @param	description a description of the object being accessed, or
     *		{@code null}
     * @return	lock conflict information, or {@code null} if there was no
     *		conflict
     * @throws	IllegalArgumentException if {@link #notifyNewTransaction
     *		notifyNewTransaction} has not been called for {@code txn}
     * @throws	IllegalStateException if an earlier lock attempt for this
     *		transaction produced a deadlock, or if still waiting for an
     *		earlier attempt to complete
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
     * LockConflictType#DEADLOCK DEADLOCK}, then the caller should abort the
     * transaction, and any subsequent lock or wait requests will throw {@code
     * IllegalStateException}.
     *
     * @param	txn the transaction requesting the lock
     * @param	source the source of the object
     * @param	objectId the ID of the object
     * @param	forWrite whether to request a write lock
     * @param	description a description of the object being accessed, or
     *		{@code null}
     * @return	lock conflict information, or {@code null} if there was no
     *		conflict
     * @throws	IllegalArgumentException if {@link #notifyNewTransaction
     *		notifyNewTransaction} has not been called for {@code txn}
     * @throws	IllegalStateException if an earlier lock attempt for this
     *		transaction produced a deadlock, or if still waiting for an
     *		earlier attempt to complete
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
     * should abort the transaction, and any subsequent lock or wait requests
     * will throw {@code IllegalStateException}.
     *
     * @param	txn the transaction requesting the lock
     * @return	lock conflict information, or {@code null} if there was no
     *		conflict
     * @throws	IllegalArgumentException if {@link #notifyNewTransaction
     *		notifyNewTransaction} has not been called for {@code txn}
     * @throws	IllegalStateException if an earlier lock attempt for this
     *		transaction produced a deadlock
     */
    public LockConflict waitForLock(Transaction txn) {
	Locker locker = getLocker(txn);
	checkLockerNotAborted(locker);
	return waitForLockInternal(locker);
    }

    /* -- Public classes -- */

    /** A class for representing a conflict resulting from a lock request. */
    public static final class LockConflict {

	/** The type of conflict. */
	final LockConflictType type;

	/** A transaction that caused the conflict. */
	final Transaction conflictingTxn;

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
	public Transaction getConflictingTransaction() {
	    return conflictingTxn;
	}

	/**
	 * Returns a string representation of this instance, for debugging.
	 *
	 * @return	a string representation of this instance
	 */
	@Override
	public String toString() {
	    return "LockConflict[type:" + type +
		", conflict:" + conflictingTxn + "]";
	}
    }

    /** The type of a lock conflict. */
    public enum LockConflictType {

	/** The request is currently blocked. */
	BLOCKED,

	/** The request timed out. */
	TIMEOUT,

	/** The request was denied. */
	DENIED,

	/** The request was interrupted. */
	INTERRUPTED,

	/** The request resulted in deadlock and was chosen to be aborted. */
	DEADLOCK;
    }

    /* -- Other methods -- */

    /**
     * Returns the locker associated with a transaction.
     *
     * @param	txn the transaction
     * @return	the locker
     * @throws	IllegalArgumentException if the transaction is not active
     */
    Locker getLocker(Transaction txn) {
	checkNull("txn", txn);
	Locker locker = txnMap.get(txn);
	if (locker == null) {
	    throw new IllegalArgumentException(
		"Transaction not active: " + txn);
	}
	return locker;
    }

    /**
     * Returns the key map to use for the specified key.
     *
     * @param	key the key
     * @return	the associated key map
     */
    Map<Key, Lock> getKeyMap(Key key) {
	/* Mask off the sign bit to get a positive value */
	int index = (key.hashCode() & Integer.MAX_VALUE) % numKeyMaps;
	return keyMaps[index];
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
    static Lock getLock(Key key, Map<Key, Lock> keyMap) {
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
    private void endTransaction(Transaction txn) {
	Locker locker = getLocker(txn);
	logger.log(Level.FINER, "end {0}", locker);
	for (LockRequest request : locker.requests) {
	    List<Locker> newOwners = Collections.emptyList();
	    Key key = request.key;
	    Map<Key, Lock> keyMap = getKeyMap(key);
	    assert Lock.noteSync(key);
	    try {
		synchronized (keyMap) {
		    /* Don't create the lock if it isn't present */
		    Lock lock = keyMap.get(key);
		    if (lock != null) {
			newOwners = lock.release(locker);
			if (!lock.inUse()) {
			    keyMap.remove(key);
			}
		    }
		}
	    } finally {
		assert Lock.noteUnsync(key);
	    }
	    for (Locker newOwner : newOwners) {
		logger.log(Level.FINEST, "notify new owner {0}", newOwner);
		assert newOwner.noteSync();
		try {
		    synchronized (newOwner) {
			newOwner.notify();
		    }
		} finally {
		    assert newOwner.noteUnsync();
		}
	    }
	}
	txnMap.remove(txn);
	profileCollectorHandle.setAccessedObjectsDetail(locker);
    }

    /** Attempts to acquire a lock, returning immediately. */
    private LockConflict lockNoWaitInternal(
	Locker locker, Key key, boolean forWrite, Object description)
    {
	if (locker.getWaitingFor() != null) {
	    throw new IllegalStateException(
		"Attempt to obtain a new lock while waiting");
	}
	LockConflict conflict = locker.getConflict();
	if (conflict != null) {
	    if (conflict.type == LockConflictType.DEADLOCK) {
		throw new IllegalStateException(
		    "Attempt to obtain a new lock after a deadlock");
	    } else {
		/* Ignoring the previous conflict */
		locker.setConflict(null);
	    }
	}
	if (description != null) {
	    locker.setDescription(key, description);
	}
	LockAttemptResult result;
	Map<Key, Lock> keyMap = getKeyMap(key);
	assert Lock.noteSync(key);
	try {
	    synchronized (keyMap) {
		Lock lock = getLock(key, keyMap);
		result = lock.lock(locker, forWrite, false);
	    }
	} finally {
	    assert Lock.noteUnsync(key);
	}
	if (result == null) {
	    if (logger.isLoggable(Level.FINER)) {
		logger.log(Level.FINER,
			   "lock {0}, {1}, forWrite:{2}" +
			   "\n  returns null (already granted)",
			   locker, key, forWrite);
	    }
	    return null;
	}
	locker.requests.add(result.request);
	if (result.conflict == null) {
	    if (logger.isLoggable(Level.FINER)) {
		logger.log(Level.FINER,
			   "lock {0}, {1}, forWrite:{2}" +
			   "\n  returns null (granted)",
			   locker, key, forWrite);
	    }
	    return null;
	}
	if (logger.isLoggable(Level.FINEST)) {
	    logger.log(Level.FINEST,
		       "lock attempt {0}, {1}, forWrite:{2}" +
		       "\n  returns blocked",
		       locker, key, forWrite);
	}
	locker.setWaitingFor(result);
	conflict = new DeadlockChecker(locker).check();
	if (conflict == null) {
	    conflict = new LockConflict(
		LockConflictType.BLOCKED, result.conflict.txn);
	}
	if (logger.isLoggable(Level.FINER)) {
	    logger.log(Level.FINER,
		       "lock {0}, {1}, forWrite:{2}\n  returns {3}",
			locker, key, forWrite, conflict);
	}
	return conflict;
    }

    /** Attempts to acquire a lock, waiting if needed. */
    private LockConflict waitForLockInternal(Locker locker) {
	assert locker.noteSync();
	try {
	    synchronized (locker) {
		LockAttemptResult result = locker.getWaitingFor();
		if (result == null) {
		    logger.log(
			Level.FINER,
			"lock {0}\n  returns null (not waiting)", locker);
		    return null;
		}
		Lock lock;
		Key key = result.request.key;
		Map<Key, Lock> keyMap = getKeyMap(key);
		assert Lock.noteSync(key);
		try {
		    synchronized (keyMap) {
			lock = getLock(key, keyMap);
		    }
		} finally {
		    assert Lock.noteUnsync(key);
		}
		long now = System.currentTimeMillis();
		long stop = Math.min(addCheckOverflow(now, lockTimeout),
				     locker.stopTime);
		LockConflict conflict = null;
		while (true) {
		    conflict = locker.getConflict();
		    if (conflict != null) {
			break;
		    } else if (now >= stop) {
			conflict = new LockConflict(
			    LockConflictType.TIMEOUT, result.conflict.txn);
			locker.setConflict(conflict);
			break;
		    }
		    boolean isOwner;
		    assert Lock.noteSync(key);
		    try {
			synchronized (keyMap) {
			    isOwner = lock.isOwner(result.request);
			}
		    } finally {
			assert Lock.noteUnsync(key);
		    }
		    if (isOwner) {
			locker.setWaitingFor(null);
			if (logger.isLoggable(Level.FINER)) {
			    logger.log(
				Level.FINER,
				"lock {0}, {1}, forWrite:{2}" +
				"\n  returns null (granted)",
				locker, key, result.request.getForWrite());
			}
			return null;
		    }
		    if (logger.isLoggable(Level.FINER)) {
			logger.log(Level.FINER,
				   "wait for lock {0}, {1}, forWrite:{2}" +
				   ", wait:{3,number,#}",
				   locker, key, result.request.getForWrite(),
				   stop - now);
		    }
		    try {
			locker.wait(stop - now);
		    } catch (InterruptedException e) {
			conflict = new LockConflict(
			    LockConflictType.INTERRUPTED, result.conflict.txn);
			locker.setConflict(conflict);
			break;
		    }
		    now = System.currentTimeMillis();
		}
		assert conflict != null;
		assert Lock.noteSync(key);
		try {
		    synchronized (keyMap) {
			lock.flushWaiter(locker);
		    }
		} finally {
		    assert Lock.noteUnsync(key);
		}
		locker.setWaitingFor(null);
		if (logger.isLoggable(Level.FINER)) {
		    logger.log(Level.FINER,
			       "lock {0}, {1}, forWrite:{2}\n  returns {3}",
			       locker, key, result.request.getForWrite(),
			       conflict);
		}
		return conflict;
	    }
	} finally {
	    assert locker.noteUnsync();
	}
    }

    /**
     * Computes the lock timeout based on the specified transaction timeout and
     * {@link #DEFAULT_LOCK_TIMEOUT_PROPORTION}.
     */
    private static long computeLockTimeout(long txnTimeout) {
	long result = (long) (txnTimeout * DEFAULT_LOCK_TIMEOUT_PROPORTION);
	/* Lock timeout should be at least 1 */
	if (result < 1) {
	    result = 1;
	}
	return result;
    }

    /** Add two non-negative longs and don't wrap around on overflow. */
    static long addCheckOverflow(long x, long y) {
	assert x >= 0 && y >= 0;
	long result = x + y;
	return (result >= 0) ? result : Long.MAX_VALUE;
    }

    /* -- Other classes -- */

    /** Records information about a transaction requesting locks. */
    static class Locker implements AccessedObjectsDetail {

	/**
	 * When assertions are enabled, holds the {@code Locker} that the
	 * current thread is synchronized on, if any.
	 */
	private static final ThreadLocal<Locker> currentSync =
	    new ThreadLocal<Locker>();

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
	 * The result of the lock request that this transaction is waiting for,
	 * or {@code null} if it is not waiting.  Synchronize on this locker
	 * when accessing this field.
	 */
	private LockAttemptResult waitingFor;

	/**
	 * A conflict that should cause this transaction's request to be
	 * denied, or {@code null}.  This value is cleared after the conflict
	 * has been reported unless the conflict is a deadlock.  Synchronize on
	 * this locker when accessing this field.
	 */
	private LockConflict conflict;

	/**
	 * Creates an instance of this class.
	 *
	 * @param	txn the associated transaction
	 * @param	requestedStartTime the time milliseconds that the task
	 *		associated with the transaction was originally
	 *		requested to start
	 * @throws	IllegalArgumentException if {@code requestedStartTime}
	 *		is less than {@code 0}
	 */
	Locker(Transaction txn, long requestedStartTime) {
	    checkNull("txn", txn);
	    if (requestedStartTime < 0) {
		throw new IllegalArgumentException(
		    "The requestedStartTime must not be less than 0");
	    }
	    this.txn = txn;
	    this.requestedStartTime = requestedStartTime;
	    this.stopTime = addCheckOverflow(
		System.currentTimeMillis(), txn.getTimeout());
	}

	/* -- Implement AccessedObjectsDetail -- */

	/** {@inheritDoc} */
	public List<AccessedObject> getAccessedObjects() {
	    return Collections.<AccessedObject>unmodifiableList(requests);
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
	    return (conflict != null)
		? conflict.getConflictingTransaction().getId() : null;
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
	 * request to be denied.  This value can be cleared to permit a new
	 * request unless the conflict is a deadlock.
	 *
	 * @return	the conflicting request or {@code null}
	 */
	synchronized LockConflict getConflict() {
	    assert checkAllowSync();
	    return conflict;
	}

	/**
	 * Requests that this locker request be denied because of a conflict
	 * with the specified request.
	 */
	synchronized void setConflict(LockConflict conflict) {
	    assert checkAllowSync();
	    this.conflict = conflict;
	    notify();
	}

	/**
	 * Checks if this locker is waiting for a lock.
	 *
	 * @return	the result of the lock request this locker is waiting
	 *		for or {@code null} if it is not waiting
	 */
	synchronized LockAttemptResult getWaitingFor() {
	    assert checkAllowSync();
	    return waitingFor;
	}

	/**
	 * Sets the lock that this locker is waiting for, or marks that it is
	 * not waiting if the argument is {@code null}.  If {@code waitingFor}
	 * is not {@code null}, then it should represent a conflict, and it's
	 * {@code conflict} field must not be {@code null}.
	 *
	 * @param	waitingFor the lock or {@code null}
	 */
	synchronized void setWaitingFor(LockAttemptResult waitingFor) {
	    assert checkAllowSync();
	    assert waitingFor == null || waitingFor.conflict != null;
	    this.waitingFor = waitingFor;
	}

	/**
	 * Checks that the current thread is permitted to synchronize on this
	 * locker.  Throws an {@link AssertionError} if already synchronized on
	 * a locker other than this one or any lock, otherwise returns {@code
	 * true}.
	 */
	boolean checkAllowSync() {
	    Locker locker = currentSync.get();
	    if (locker != null && locker != this) {
		throw new AssertionError(
		    "Attempt to synchronize on locker " + this +
		    ", but already synchronized on " + locker);
	    }
	    Lock.checkNoSync();
	    return true;
	}

	/**
	 * Notes the start of synchronization on this locker.  Throws {@link
	 * AssertionError} if already synchronized on any locker or lock,
	 * otherwise returns {@code true}.
	 */
	boolean noteSync() {
	    Locker locker = currentSync.get();
	    if (locker != null) {
		throw new AssertionError(
		    "Attempt to synchronize on locker " + this +
		    ", but already synchronized on " + locker);
	    }
	    Lock.checkNoSync();
	    currentSync.set(this);
	    return true;
	}

	/**
	 * Notes the end of synchronization on this locker.  Throws {@link
	 * AssertionError} if not already synchronized on this locker,
	 * otherwise returns {@code true}.
	 */
	boolean noteUnsync() {
	    Locker locker = currentSync.get();
	    if (locker == null) {
		throw new AssertionError(
		    "Attempt to unsynchronize on locker " + this +
		    ", but not currently synchronized on a locker");
	    } else if (locker != this) {
		throw new AssertionError(
		    "Attempt to unsynchronize on locker " + this +
		    ", but currently synchronized on " + locker);
	    }
	    currentSync.remove();
	    return true;
	}
	    
	/** Print the associated transaction, for debugging. */
	@Override
	public String toString() {
	    return txn.toString();
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
	    checkNull("source", source);
	    checkNull("objectId", objectId);
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
	    return source + ":" + objectId;
	}
    }

    /**
     * A class used to represent locks. <p>
     *
     * Callers should only call non-Object methods on instances of this class
     * if they hold the lock on the key map associated with the instance.
     */
    private static class Lock {

	/**
	 * When assertions are enabled, hold the {@code Key} whose associated
	 * {@code Map} the current thread is synchronized on, if any.
	 */
	private static final ThreadLocal<Key> currentSync =
	    new ThreadLocal<Key>();

	/** An empty array of lock requests. */
	private static final LockRequest[] NO_LOCK_REQUESTS = { };

	/** The key that identifies this lock. */
	final Key key;

	/**
	 * The requests that currently own this lock.  Use a small initial
	 * size, since the number of owners is typically small.
	 */
	private final List<LockRequest> owners = new ArrayList<LockRequest>(2);

	/**
	 * The requests that are waiting for this lock.  Use a small initial
	 * size, since the number of owners is typically small.
	 */
	private final List<LockRequest> waiters =
	    new ArrayList<LockRequest>(2);

	/**
	 * Creates a lock.
	 *
	 * @param	key the key that identifies this lock
	 */
	Lock(Key key) {
	    checkNull("key", key);
	    this.key = key;
	}

	/**
	 * Attempts to obtain this lock.  If {@code waiting} is {@code true},
	 * the locker is already waiting for the lock.  Adds the locker as an
	 * owner of the lock if the lock was obtained, and removes the locker
	 * from the waiters list if it was waiting.  Returns {@code null} if
	 * the locker already owned this lock.  Otherwise, returns a {@code
	 * LockAttemptResult} containing the {@link LockRequest}, and with the
	 * {@code conflict} field set to {@code null} if the lock was acquired,
	 * else set to a conflicting transaction if the lock could not be
	 * obtained.
	 *
	 * @param	locker the locker requesting the lock
	 * @param	forWrite whether a write lock is requested
	 * @param	waiting whether the locker is already a waiter
	 * @return	a {@code LockAttemptResult} or {@code null}
	 */
	LockAttemptResult lock(
	    Locker locker, boolean forWrite, boolean waiting)
	{
	    assert checkSync();
	    if (owners.isEmpty()) {
		LockRequest request =
		    new LockRequest(locker, key, forWrite, false);
		owners.add(request);
		if (waiting) {
		    flushWaiter(locker);
		}
		assert validateInUse();
		return new LockAttemptResult(request, null);
	    }
	    boolean upgrade = false;
	    Locker conflict = null;
	    for (LockRequest ownerRequest : owners) {
		if (locker == ownerRequest.locker) {
		    if (!forWrite || ownerRequest.getForWrite()) {
			if (waiting) {
			    flushWaiter(locker);
			}
			assert validateInUse();
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
		    boolean found = false;
		    for (Iterator<LockRequest> i = owners.iterator();
			 i.hasNext(); )
		    {
			LockRequest ownerRequest = i.next();
			if (locker == ownerRequest.locker) {
			    assert !ownerRequest.getForWrite()
				: "Should own for read when upgrading";
			    i.remove();
			    found = true;
			    break;
			}
		    }
		    assert found : "Should own when upgrading";
		}
		owners.add(request);
		if (waiting) {
		    flushWaiter(locker);
		}
		assert validateInUse();
		return new LockAttemptResult(request, null);
	    }
	    if (!waiting) {
		addWaiter(request);
	    }
	    assert validateInUse();
	    return new LockAttemptResult(request, conflict);
	}

	/**
	 * Adds a lock request to the list of requests waiting for this lock.
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
		boolean added = false;
		for (int i = 0; i < waiters.size(); i++) {
		    if (!waiters.get(i).getUpgrade()) {
			waiters.add(i, request);
			added = true;
			break;
		    }
		}
		if (!added) {
		    waiters.add(request);
		}
	    }
	}

	/**
	 * Releases the ownership of this lock by the locker.  Attempts to
	 * acquire locks for current waiters, removing the ones that acquire
	 * the lock from the waiters list, adding them to the owners, and
	 * returning them.
	 *
	 * @param	locker the locker whose ownership will be released
	 * @return	the newly added owners
	 */
	List<Locker> release(Locker locker) {
	    assert checkSync();
	    if (logger.isLoggable(Level.FINEST)) {
		logger.log(Level.FINEST, "release {0}, {1}", locker, this);
	    }
	    boolean owned = false;
	    for (Iterator<LockRequest> i = owners.iterator(); i.hasNext(); ) {
		LockRequest ownerRequest = i.next();
		if (locker == ownerRequest.locker) {
		    i.remove();
		    owned = true;
		    break;
		}
	    }
	    List<Locker> lockersToNotify = Collections.emptyList();
	    if (owned && !waiters.isEmpty()) {
		boolean found = false;
		for (int i = 0; i < waiters.size(); i++) {
		    LockRequest waiter = waiters.get(i);
		    LockAttemptResult result =
			lock(waiter.locker, waiter.getForWrite(), true);
		    if (logger.isLoggable(Level.FINEST)) {
			logger.log(
			    Level.FINEST,
			    "attempt to lock waiter {0} returns {1}",
			    waiter, result);
		    }
		    /*
		     * Stop when the first conflict is detected.  This
		     * implementation always services waiters in order, so
		     * there is no reason to look further after we've found a
		     * waiter that is still blocked.
		     */
		    if (result != null && result.conflict != null) {
			break;
		    }
		    /* Back up because this waiter was removed */
		    i--;
		    if (!found) {
			found = true;
			lockersToNotify = new ArrayList<Locker>();
		    }
		    lockersToNotify.add(waiter.locker);
		}
	    }
	    assert !inUse() || validateInUse();
	    return lockersToNotify;
	}

	/**
	 * Validates the state of this lock, given that it is known to be in
	 * use. <p>
	 *
	 * Locks that are in use must satisfy the following consistency
	 * constraints:
	 * <ul>
	 * <li>at least one owner
	 * <li>all upgrade waiters precede other waiters
	 * <li>locker appears once in owners and waiters <em>except</em> that
	 *     an upgrade request can be a waiter if a read request is an owner
	 * <li>no upgrade waiters if the locker is not a read owner
	 * </ul>
	 *
	 * @return	{@code true} if the state is valid, otherwise throws
	 *		{@link AssertionError}
	 */
	private boolean validateInUse() {
	    int numWaiters = waiters.size();
	    int numOwners = owners.size();
	    if (numOwners == 0) {
		throw new AssertionError("No owners: " + this);
	    }
	    boolean seenNonUpgrade = false;
	    for (int i = 0; i < numWaiters - 1; i++) {
		LockRequest waiter = waiters.get(i);
		if (!waiter.getUpgrade()) {
		    seenNonUpgrade = true;
		} else if (seenNonUpgrade) {
		    throw new AssertionError(
			"Upgrade waiter follows non-upgrade: " + this +
			", waiters: " + waiters);
		}
		for (int j = i + 1; j < numWaiters; j++) {
		    LockRequest waiter2 = waiters.get(j);
		    if (waiter.locker == waiter2.locker) {
			throw new AssertionError(
			    "Locker waits twice: " + this + ", " +
			    waiter + ", " + waiter2);
		    }
		}
		boolean foundOwner = false;
		for (int j = 0; j < numOwners; j++) {
		    LockRequest owner = owners.get(j);
		    if (waiter.locker == owner.locker) {
			foundOwner = true;
			if (!waiter.getUpgrade()) {
			    throw new AssertionError(
				"Locker owns and waits, but not for" +
				" upgrade: " + this + ", owner:" + owner +
				", waiter:" + waiter);
			} else if (owner.getForWrite()) {
			    throw new AssertionError(
				"Locker owns for write but waits for" +
				" upgrade: " + this + ", owner:" + owner +
				", waiter:" + waiter);
			}
		    }
		}
		if (waiter.getUpgrade() && !foundOwner) {
		    throw new AssertionError(
			"Waiting for upgrade but not owner: " + this +
			", waiter: " + waiter);
		}
	    }
	    return true;
	}

	/**
	 * Returns whether this lock is currently in use, or whether it is not
	 * in use and can be removed from the lock table.  Locks are in use if
	 * they have any owners or waiters.
	 */
	boolean inUse() {
	    assert checkSync();
	    return !owners.isEmpty() || !waiters.isEmpty();
	}

	/** Returns a copy of the locker requests for the owners. */
	LockRequest[] copyOwners() {
	    assert checkSync();
	    if (owners.isEmpty()) {
		return NO_LOCK_REQUESTS;
	    } else {
		return owners.toArray(new LockRequest[owners.size()]);
	    }
	}

	/** Removes a locker from the list of waiters for this lock. */
	void flushWaiter(Locker locker) {
	    assert checkSync();
	    for (Iterator<LockRequest> iter = waiters.iterator();
		 iter.hasNext(); )
	    {
		LockRequest request = iter.next();
		if (request.locker == locker) {
		    iter.remove();
		    break;
		}
	    }
	}

	/**
	 * Checks if the lock is already owned in a way the satisfies the
	 * specified request.
	 */
	boolean isOwner(LockRequest request) {
	    assert checkSync();
	    for (LockRequest owner : owners) {
		if (request.locker == owner.locker) {
		    return !request.getForWrite() || owner.getForWrite();
		}
	    }
	    return false;
	}

	/**
	 * Notes the start of synchronization on the map associated with {@code
	 * key}.  Throws {@link AssertionError} if already synchronized on a
	 * key, otherwise returns {@code true}.
	 */
	static boolean noteSync(Key key) {
	    Key currentKey = currentSync.get();
	    if (currentKey != null) {
		throw new AssertionError(
		    "Attempt to synchronize on map for key " + key +
		    ", but already synchronized on " + currentKey);
	    }
	    currentSync.set(key);
	    return true;
	}

	/**
	 * Notes the end of synchronization on the map associated with {@code
	 * key}.  Throws {@link AssertionError} if not already synchronized on
	 * {@code key}, otherwise returns {@code true}.
	 */
	static boolean noteUnsync(Key key) {
	    Key currentKey = currentSync.get();
	    if (currentKey == null) {
		throw new AssertionError(
		    "Attempt to unsynchronize on map for key " + key +
		    ", but not currently synchronized on a key");
	    } else if (!currentKey.equals(key)) {
		throw new AssertionError(
		    "Attempt to unsynchronize on map for key " + key +
		    ", but currently synchronized on " + currentKey);
	    }
	    currentSync.remove();
	    return true;
	}

	/**
	 * Checks that the current thread is not synchronized on the map
	 * associated with a key, throwing {@link AssertionError} if it is, and
	 * otherwise returning {@code true}.
	 */
	static boolean checkNoSync() {
	    Key currentKey = currentSync.get();
	    if (currentKey != null) {
		throw new AssertionError(
		    "Currently synchronized on key " + currentKey);
	    }
	    return true;
	}

	/**
	 * Checks that the current thread is synchronized on the map associated
	 * with this lock, throwing {@link AssertionError} if it is not, and
	 * otherwise returning {@code true}.
	 */
	boolean checkSync() {
	    Key currentKey = currentSync.get();
	    if (currentKey == null) {
		throw new AssertionError(
		    "Currently not synchronized on a key");
	    } else if (!currentKey.equals(key)) {
		throw new AssertionError(
		    "Should be synchronized on " + key +
		    ", but currently synchronized on " + currentKey);
	    }
	    return true;
	}

	/** Print fields, for debugging. */
	@Override
	public String toString() {
	    return "Lock[" + key + "]";
	}
    }

    /** The result of attempting to request a lock. */
    private static class LockAttemptResult {

	/** The lock request. */
	final LockRequest request;

	/**
	 * A conflicting locker, if the request was not granted, or {@code
	 * null}.
	 */
	final Locker conflict;

	/**
	 * Creates an instance of this class.
	 *
	 * @param	request the lock request
	 * @param	conflict a conflicting locker or {@code null}
	 */
	LockAttemptResult(LockRequest request, Locker conflict) {
	    assert request != null;
	    this.request = request;
	    this.conflict = conflict;
	}

	/** Print fields, for debugging. */
	@Override
	public String toString() {
	    return "LockAttemptResult[" + request +
		", conflict:" + conflict + "]";
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
	    checkNull("type", type);
	    LockConflict conflict = lock(
		txn, source, objectId, type == AccessType.WRITE, description);
	    if (conflict != null) {
		String descriptionMsg = "";
		if (description != null) {
		    try {
			descriptionMsg = ", description:" + description;
		    } catch (RuntimeException e) {
		    }
		}
		String accessMsg = "Access txn:" + txn +
		    ", type:" + type +
		    ", source:" + source +
		    ", objectId:" + objectId +
		    descriptionMsg +
		    " failed: ";
		String conflictMsg = ", with conflicting transaction " +
		    conflict.getConflictingTransaction();
		TransactionAbortedException exception;
		switch (conflict.getType()) {
		case TIMEOUT:
		    exception = new TransactionTimeoutException(
			accessMsg + "Transaction timed out" + conflictMsg);
		    break;
		case DENIED:
		    exception = new TransactionConflictException(
			accessMsg + "Access denied" + conflictMsg);
		    break;
		case INTERRUPTED:
		    exception = new TransactionInterruptedException(
			accessMsg + "Transaction interrupted" + conflictMsg);
		    break;
		case DEADLOCK:
		    exception = new TransactionConflictException(
			accessMsg + "Transaction deadlock" + conflictMsg);
		    break;
		default:
		    throw new AssertionError(
			"Should not be " + conflict.getType());
		}
		txn.abort(exception);
		throw exception;
	    }
	}

	/** {@inheritDoc} */
	public void setObjectDescription(
	    Transaction txn, T objectId, Object description)
	{
	    Locker locker = getLocker(txn);
	    if (description == null) {
		checkNull("objectId", objectId);
	    } else {
		locker.setDescription(
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

	/**
	 * Two instances are equal if they are instances of this class, and
	 * have the same source, object ID, and access type.
	 *
	 * @param	object the object to compare with
	 * @return	whether this instance equals the argument
	 */
	@Override
	public boolean equals(Object object) {
	    if (object == this) {
		return true;
	    } else if (object instanceof LockRequest) {
		LockRequest request = (LockRequest) object;
		return key.equals(request.key) &&
		    getForWrite() == request.getForWrite();
	    } else {
		return false;
	    }
	}

	@Override
	public int hashCode() {
	    return key.hashCode() ^ (getForWrite() ? 1 : 0);
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

	/**
	 * Maps lockers to information about lockers they are waiting for.
	 * This map serves as a cache for information about lock owners, to
	 * avoid the synchronization needed to retrieve it again when checking
	 * for multiple deadlocks.
	 */
	private final Map<Locker, WaiterInfo> waiterMap =
	    new HashMap<Locker, WaiterInfo>();

	/** The top level locker we are checking for deadlocks. */
	private final Locker rootLocker;

	/**
	 * The pass number of the current deadlock check.  There could be
	 * multiple deadlocks active simultaneously, so deadlock checking is
	 * repeated until no deadlocks are found.
	 */
	private int pass;

	/** The locker that was found in a circular reference. */
	private Locker cycleBoundary;

	/** The current choice of a locker to abort. */
	private Locker victim;

	/** Another locker in the deadlock. */
	private Locker conflict;

	/**
	 * Creates an instance of this class.
	 *
	 * @param	locker the locker to check
	 */
	DeadlockChecker(Locker locker) {
	    assert locker != null;
	    rootLocker = locker;
	}

	/**
	 * Checks for a deadlock starting with the root locker.
	 *
	 * @return	a lock conflict if a deadlock was found, else {@code
	 *		null}
	 */
	LockConflict check() {
	    LockConflict result = null;
	    for (pass = 1; true; pass++) {
		if (!checkInternal(rootLocker, getWaiterInfo(rootLocker))) {
		    if (result == null) {
			logger.log(Level.FINEST,
				   "check deadlock {0}: no deadlock",
				   rootLocker);
			return null;
		    } else {
			return result;
		    }
		}
		if (logger.isLoggable(Level.FINER)) {
		    logger.log(Level.FINER, "check deadlock {0}: victim {1}",
			       rootLocker, victim);
		}
		LockConflict deadlock =
		    new LockConflict(LockConflictType.DEADLOCK, conflict.txn);
		getWaiterInfo(victim).waitingFor = null;
		victim.setConflict(deadlock);
		if (victim == rootLocker) {
		    return deadlock;
		} else {
		    result = new LockConflict(
			LockConflictType.BLOCKED, conflict.txn);
		}
	    }
	}

	/**
	 * Checks for deadlock starting with the specified locker and
	 * information about its waiters.  Returns whether a deadlock was
	 * found.
	 */
	private boolean checkInternal(Locker locker, WaiterInfo waiterInfo) {
	    waiterInfo.pass = pass;
	    for (LockRequest request : waiterInfo.waitingFor) {
		Locker owner = request.locker;
		if (owner == locker) {
		    if (logger.isLoggable(Level.FINEST)) {
			logger.log(Level.FINEST,
				   "checking deadlock {0}, pass {1}:" +
				   " locker {2}, waiting for {3}:" +
				   " ignore self-reference",
				   rootLocker, pass, locker, request);
		    }
		} else {
		    WaiterInfo ownerInfo = getWaiterInfo(owner);
		    if (ownerInfo.waitingFor == null) {
			if (logger.isLoggable(Level.FINEST)) {
			    logger.log(Level.FINEST,
				       "checking deadlock {0}, pass {1}:" +
				       " locker {2}, waiting for {3}:" +
				       " ignore not waiting",
				       rootLocker, pass, locker, request);
			}
		    } else if (ownerInfo.pass == pass) {
			/* Found a deadlock! */
			cycleBoundary = owner;
			victim = owner;
			if (logger.isLoggable(Level.FINEST)) {
			    logger.log(Level.FINEST,
				       "checking deadlock {0}, pass {1}:" +
				       " locker {2}, waiting for {3}:" +
				       " deadlock",
				       rootLocker, pass, locker, request);
			}
			return true;
		    } else {
			if (logger.isLoggable(Level.FINEST)) {
			    logger.log(Level.FINEST,
				       "checking deadlock {0}, pass {1}:" +
				       " locker {2}, waiting for {3}:" +
				       " recurse",
				       rootLocker, pass, locker, request);
			}
			if (checkInternal(owner, ownerInfo)) {
			    maybeUpdateVictim(owner);
			    return true;
			}
		    }
		}
	    }
	    return false;
	}

	/**
	 * Returns information about the lockers that the specified locker is
	 * waiting for.
	 */
	private WaiterInfo getWaiterInfo(Locker locker) {
	    WaiterInfo waiterInfo = waiterMap.get(locker);
	    if (waiterInfo == null) {
		LockRequest[] waitingFor;
		LockAttemptResult result = locker.getWaitingFor();
		if (result == null || locker.getConflict() != null) {
		    waitingFor = null;
		} else {
		    Key key = result.request.key;
		    Map<Key, Lock> keyMap = getKeyMap(key);
		    assert Lock.noteSync(key);
		    try {
			synchronized (keyMap) {
			    waitingFor = getLock(key, keyMap).copyOwners();
			}
		    } finally {
			assert Lock.noteUnsync(key);
		    }
		}
		waiterInfo = new WaiterInfo(waitingFor);
		waiterMap.put(locker, waiterInfo);
	    }
	    return waiterInfo;
	}

	/**
	 * Updates the victim and conflict fields to reflect an additional
	 * locker in the deadlock chain.  Use the argument as the victim if it
	 * has a newer requested start time than the previously selected
	 * victim.
	 */
	private void maybeUpdateVictim(Locker locker) {
	    assert locker != null;
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
		 * We're still within the cycle and this locker started later
		 * than the current victim, so use it instead.
		 */
		if (conflict == locker) {
		    conflict = victim;
		}
		victim = locker;
		logger.log(Level.FINEST,
			   "checking deadlock {0}, pass {1}: new victim: {2}",
			   rootLocker, pass, victim);
	    }
	}
    }

    /**
     * Provides information about the requests a locker is waiting for.  Used
     * in deadlock detection.
     */
    private static class WaiterInfo {

	/**
	 * The requests the locker is waiting for, or {@code null} if not
	 * waiting.
	 */
	LockRequest[] waitingFor;

	/**
	 * The pass in which the locker was checked.  If we encounter an
	 * instance with the current pass number, then we've found a cycle.
	 */
	int pass = 0;

	/**
	 * Creates an instance of this class.
	 *
	 * @param	waitingFor the requests the locker is waiting for
	 */
	WaiterInfo(LockRequest[] waitingFor) {
	    this.waitingFor = waitingFor;
	}
    }

    /**
     * A transaction listener that calls {@link #endTransaction} when called
     * after the transaction completes.  Use a listener instead of a
     * transaction participant to make sure that locks are released only after
     * all of the transaction participants have finished their work.
     */
    private class TxnListener implements TransactionListener {

	/** The transaction. */
	private final Transaction txn;

	/**
	 * Creates an instance of this class.
	 *
	 * @param	txn the transaction we're listening for
	 */
	TxnListener(Transaction txn) {
	    this.txn = txn;
	}

	/**
	 * {@inheritDoc} <p>
	 *
	 * This implementation does nothing.
	 */
	public void beforeCompletion() { }

	/**
	 * {@inheritDoc} <p>
	 *
	 * This implementation calls {@link #endTransaction}.
	 */
	public void afterCompletion(boolean committed) {
	    endTransaction(txn);
	}

        /** {@inheritDoc} */
        public String getTypeName() {
            return TxnListener.class.getName();
        }
    }
}
