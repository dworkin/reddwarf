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

package com.sun.sgs.impl.util.lock;

import java.util.logging.Level;
import static java.util.logging.Level.FINER;
import java.util.logging.Logger;

/**
 * A class for managing lock conflicts where locks are not held by transactions
 * and the locker can make simultaneous requests from multiple threads.  This
 * class does not detect deadlocks, but provides support for {@linkplain
 * #downgradeLock downgrading locks}.  All {@link Locker} objects supplied to
 * this class should be instances of {@link MultiLocker}. <p>
 *
 * This class and its {@linkplain LockManager superclass} use the {@link
 * Logger} named {@code com.sun.sgs.impl.util.lock} to log information at the
 * following logging levels: <p>
 *
 * <ul>
 * <li> {@link Level#FINER FINER} - Releasing locks; requesting, waiting for,
 *	and returning from lock requests
 * <li> {@link Level#FINEST FINEST} - Notifying new lock owners, results of
 *	requesting locks before waiting, releasing locks, results of attempting
 *	to assign locks to waiters
 * </ul>
 *
 * @param	<K> the type of key
 */
public final class MultiLockManager<K> extends LockManager<K> {

    /**
     * A thread local that stores the result of the lock request that the
     * current thread is waiting for, or {@code null} if it is not waiting.
     */
    private final ThreadLocal<LockAttemptResult<K>> waitingFor =
	new ThreadLocal<LockAttemptResult<K>>();

    /* -- Public constructor -- */

    /**
     * Creates an instance of this class.
     *
     * @param	lockTimeout the maximum number of milliseconds to acquire a
     *		lock
     * @param	numKeyMaps the number of separate maps to use for storing keys
     * @throws	IllegalArgumentException if {@code lockTimeout} or {@code
     *		numKeyMaps} is less than {@code 1}
     */
    public MultiLockManager(long lockTimeout, int numKeyMaps) {
	super(lockTimeout, numKeyMaps);
    }

    /* -- Public methods -- */

    /**
     * {@inheritDoc}
     *
     * @throws	IllegalArgumentException {@inheritDoc}, or if {@code locker} is
     *		not an instance of {@link MultiLocker}
     */
    @Override
    public LockConflict<K> lock(Locker<K> locker, K key, boolean forWrite) {
	checkMultiLocker(locker);
	return super.lock(locker, key, forWrite);
    }

    /**
     * {@inheritDoc}
     *
     * @throws	IllegalArgumentException {@inheritDoc}, or if {@code locker} is
     *		not an instance of {@link MultiLocker}
     */
    @Override
    public LockConflict<K> lockNoWait(
	Locker<K> locker, K key, boolean forWrite)
    {
	checkMultiLocker(locker);
	return super.lockNoWait(locker, key, forWrite);
    }

    /**
     * {@inheritDoc}
     *
     * @throws	IllegalArgumentException {@inheritDoc}, or if {@code locker} is
     *		not an instance of {@link MultiLocker}
     */
    public LockConflict<K> waitForLock(Locker<K> locker) {
	checkMultiLocker(locker);
	return super.waitForLock(locker);
    }

    /**
     * Downgrades a lock held by a locker from write to read access.  This
     * method does nothing if the lock is not held for write.
     *
     * @param	locker the locker holding the lock
     * @param	key the key identifying the lock
     * @throws	IllegalArgumentException if {@code locker} has a different lock
     *		manager, or if {@code locker} is not an instance of {@link
     *		MultiLocker}
     */
    public void downgradeLock(Locker<K> locker, K key) {
	if (logger.isLoggable(FINER)) {
	    logger.log(FINER, "downgrade {0} {1}", locker, key);
	}
	checkMultiLocker(locker);
	releaseLockInternal(locker, key, true);
    }

    /* -- Other methods -- */

    /**
     * Checks if this thread is waiting for a lock.
     *
     * @return	the result of the lock request this thread is waiting
     *		for or {@code null} if it is not waiting
     */
    LockAttemptResult<K> getWaitingFor() {
	return waitingFor.get();
    }

    /**
     * Records the lock that this thread is waiting for, or marks that it is
     * not waiting if the argument is {@code null}.
     *
     * @param	waitingFor the lock or {@code null}
     */
    void setWaitingFor(LockAttemptResult<K> waitingFor) {
	this.waitingFor.set(waitingFor);
    }

    /**
     * Throws IllegalArgumentException if the argument is not a MultiLocker.
     */
    private static void checkMultiLocker(Locker<?> locker) {
	if (locker != null && !(locker instanceof MultiLocker<?>)) {
	    throw new IllegalArgumentException("Locker is not a MultiLocker");
	}
    }
}
