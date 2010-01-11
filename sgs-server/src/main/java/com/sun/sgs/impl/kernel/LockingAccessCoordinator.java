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
import com.sun.sgs.impl.util.lock.LockConflict;
import com.sun.sgs.impl.util.lock.LockConflictType;
import com.sun.sgs.impl.util.lock.LockManager;
import com.sun.sgs.impl.util.lock.LockRequest;
import com.sun.sgs.impl.util.lock.TxnLockManager;
import com.sun.sgs.impl.util.lock.TxnLocker;
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
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;
import static java.util.logging.Level.CONFIG;
import static java.util.logging.Level.FINER;
import java.util.logging.Logger;

/**
 * An implementation of {@link AccessCoordinator} that uses locking to handle
 * conflicts. <p>
 *
 * This implementation checks for deadlock whenever an access request is
 * blocked due to a conflict.  It selects the youngest transaction as the
 * deadlock victim, determining the age using the originally requested start
 * time for the task associated with the transaction.  The implementation does
 * not deny requests that would not result in deadlock.  When requests block,
 * it services the requests in the order that they arrive. <p>
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
 * <li> {@link Level#CONFIG CONFIG} - Creating an instance
 * <li> {@link Level#FINER FINER} - Beginning and ending transactions
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
    private final ConcurrentMap<Transaction, LockerImpl> txnMap =
	new ConcurrentHashMap<Transaction, LockerImpl>();

    /** The lock manager. */
    private final TxnLockManager<Key> lockManager;

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
	    TransactionCoordinator.TXN_TIMEOUT_PROPERTY,
	    TransactionCoordinatorImpl.BOUNDED_TIMEOUT_DEFAULT);
	long defaultLockTimeout = Math.max(
	    1L, (long) (txnTimeout * DEFAULT_LOCK_TIMEOUT_PROPORTION));
	long lockTimeout = wrappedProps.getLongProperty(
	    LOCK_TIMEOUT_PROPERTY, defaultLockTimeout, 1, Long.MAX_VALUE);
	int numKeyMaps = wrappedProps.getIntProperty(
	    NUM_KEY_MAPS_PROPERTY, NUM_KEY_MAPS_DEFAULT, 1, Integer.MAX_VALUE);
	lockManager = new TxnLockManager<Key>(lockTimeout, numKeyMaps);
	if (logger.isLoggable(CONFIG)) {
	    logger.log(CONFIG,
		       "Created LockingAccessCoordinator with properties:" +
		       "\n  txn timeout: " + txnTimeout +
		       "\n  lock timeout: " + lockTimeout +
		       "\n  num key maps: " + numKeyMaps);
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
	LockerImpl locker =
	    new LockerImpl(lockManager, txn, requestedStartTime);
	LockerImpl existing = txnMap.putIfAbsent(txn, locker);
	if (existing != null) {
	    throw new IllegalStateException("Transaction already started");
	}
	if (logger.isLoggable(FINER)) {
	    logger.log(FINER, "begin {0}, requestedStartTime:{1,number,#}",
		       locker, requestedStartTime);
	}
	txn.registerListener(new TxnListener(txn));
    }

    /* -- Other methods -- */

    /**
     * Returns the locker associated with a transaction.
     *
     * @param	txn the transaction
     * @return	the locker
     * @throws	IllegalArgumentException if the transaction is not active
     */
    LockerImpl getLocker(Transaction txn) {
	checkNull("txn", txn);
	LockerImpl locker = txnMap.get(txn);
	if (locker == null) {
	    throw new IllegalArgumentException(
		"Transaction not active: " + txn);
	}
	return locker;
    }

    /**
     * Releases the locks for the transaction and reports object accesses to
     * the profiling system.
     *
     * @param	txn the finished transaction
     */
    private void endTransaction(Transaction txn) {
	LockerImpl locker = getLocker(txn);
	logger.log(FINER, "end {0}", locker);
	locker.releaseAll();
	txnMap.remove(txn);
	profileCollectorHandle.setAccessedObjectsDetail(locker);
    }

    /* -- Other classes -- */

    /**
     * Define a locker that records information about the transaction
     * requesting locks, and descriptions.
     */
    public static class LockerImpl extends TxnLocker<Key>
	implements AccessedObjectsDetail
    {
	/** The lock requests made by this transaction. */
	private final List<AccessedObjectImpl> requests =
	    new ArrayList<AccessedObjectImpl>();

	/** A map from keys to descriptions, or {@code null}. */
	private Map<Key, Object> keyToDescriptionMap = null;

	/**
	 * Whether the transaction has ended.  Used when assertions are enabled
	 * to check the thread safety of accesses to the requests field.
	 * Synchronize on the requests field, rather than the locker object
	 * itself, when accessing this field, to avoid lock ordering problems.
	 */
	private boolean ended = false;

	/**
	 * Creates an instance of this class.
	 *
	 * @param	lockManager the lock manager for this locker
	 * @param	txn the associated transaction
	 * @param	requestedStartTime the time milliseconds that the task
	 *		associated with the transaction was originally
	 *		requested to start
	 * @throws	IllegalArgumentException if {@code requestedStartTime}
	 *		is less than {@code 0}
	 */
	LockerImpl(TxnLockManager<Key> lockManager,
		   Transaction txn,
		   long requestedStartTime)
	{
	    super(lockManager, txn, requestedStartTime);
	}

	/**
	 * {@inheritDoc} <p>
	 *
	 * This implementation records the new request and uses a local
	 * class.
	 */
	@Override
	protected LockRequest<Key> newLockRequest(
	    Key key, boolean forWrite, boolean upgrade)
	{
	    assert !getEnded();
	    AccessedObjectImpl request =
		new AccessedObjectImpl(this, key, forWrite, upgrade);
	    requests.add(request);
	    return request;
	}

	/** Release all locks. */
	void releaseAll() {
	    assert setEnded();
	    LockManager<Key> lockManager = getLockManager();
	    for (LockRequest<Key> request : requests) {
		lockManager.releaseLock(this, request.getKey());
	    }
	}

	/**
	 * Returns a string representation of this object.  This implementation
	 * prints the associated transaction, for debugging.
	 *
	 * @return	a string representation of this object
	 */
	@Override
	public String toString() {
	    return txn.toString();
	}

	/* -- Implement AccessedObjectsDetail -- */

	/** {@inheritDoc} */
	public List<AccessedObject> getAccessedObjects() {
	    return Collections.<AccessedObject>unmodifiableList(requests);
	}

	/** {@inheritDoc} */
	public ConflictType getConflictType() {
	    LockConflict<Key> conflict = getConflict();
	    if (conflict == null) {
		return ConflictType.NONE;
	    } else if (conflict.getType() == LockConflictType.DEADLOCK) {
		return ConflictType.DEADLOCK;
	    } else {
		return ConflictType.ACCESS_NOT_GRANTED;
	    }
	}

	/** {@inheritDoc} */
	public byte[] getConflictingId() {
	    LockConflict<Key> conflict = getConflict();
	    if (conflict != null) {
		LockerImpl conflictingLocker =
		    (LockerImpl) conflict.getConflictingLocker();
	      return conflictingLocker.getTransaction().getId();
	    } else {
		return null;
	    }
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
	 * Sets the specified conflict if this locker does not have a conflict
	 * set.
	 */
	synchronized void setConflictIfNeeded(LockConflict<Key> conflict) {
	    if (getConflict() == null) {
		setConflict(conflict);
	    }
	}

	/**
	 * Marks the transaction as ended.
	 *
	 * @return	whether the transaction is newly ended
	 */
	private boolean setEnded() {
	    synchronized (requests) {
		if (ended) {
		    return false;
		} else {
		    ended = true;
		    return true;
		}
	    }
	}

	/** Returns whether the transaction has ended. */
	private boolean getEnded() {
	    synchronized (requests) {
		return ended;
	    }
	}
    }

    /** Implement {@code AccessedObject}. */
    private static class AccessedObjectImpl extends LockRequest<Key>
	implements AccessedObject
    {
	/**
	 * Creates an instance of this class.
	 *
	 * @param	locker the locker that requested the lock
	 * @param	key the key identifying the lock
	 * @param	forWrite whether a write lock was requested
	 * @param	upgrade whether an upgrade was requested
	 */
	AccessedObjectImpl(LockerImpl locker,
			   Key key,
			   boolean forWrite,
			   boolean upgrade)
	{
	    super(locker, key, forWrite, upgrade);
	}

	/* -- Implement AccessedObject -- */

	/** {@inheritDoc} */
	public String getSource() {
	    return getKey().source;
	}

	/** {@inheritDoc} */
	public Object getObjectId() {
	    return getKey().objectId;
	}

	/** {@inheritDoc} */
	public AccessType getAccessType() {
	    return getForWrite() ? AccessType.WRITE : AccessType.READ;
	}

	/** {@inheritDoc} */
	public Object getDescription() {
	    return ((LockerImpl) getLocker()).getDescription(getKey());
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
	    } else if (object instanceof AccessedObjectImpl) {
		AccessedObjectImpl other = (AccessedObjectImpl) object;
		return getKey().equals(other.getKey()) &&
		    getForWrite() == other.getForWrite();
	    } else {
		return false;
	    }
	}

	@Override
	public int hashCode() {
	    return getKey().hashCode() ^ (getForWrite() ? 1 : 0);
	}

	/* -- Other methods -- */

	/** Print fields, for debugging. */
	@Override
	public String toString() {
	    return "AccessedObjectImpl[" + getLocker() + ", " +
		getKey() + ", " +
		(getForWrite() ? "WRITE" : getUpgrade() ? "UPGRADE" : "READ") +
		"]";
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
	    LockerImpl locker = getLocker(txn);
	    Key key = new Key(source, objectId);
	    if (description != null) {
		locker.setDescription(key, description);
	    }
	    LockConflict<Key> conflict =
		lockManager.lock(locker, key, type == AccessType.WRITE);
	    if (conflict != null) {
		locker.setConflictIfNeeded(conflict);
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
		LockerImpl conflictingLocker =
		    (LockerImpl) conflict.getConflictingLocker();
		String conflictMsg = ", with conflicting transaction " +
		    conflictingLocker.getTransaction();
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
	    LockerImpl locker = getLocker(txn);
	    if (description == null) {
		checkNull("objectId", objectId);
	    } else {
		locker.setDescription(new Key(source, objectId), description);
	    }
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
