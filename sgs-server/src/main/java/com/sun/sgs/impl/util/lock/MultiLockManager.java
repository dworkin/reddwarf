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

package com.sun.sgs.impl.util.lock;

import static java.util.logging.Level.FINER;

/* FIXME: Implement releasing all locks by marking lockers as dead? */

/**
 * A class for managing lock conflicts where locks are not held by transactions
 * and the locker can make simultaneous request from multiple threads.
 *
 * @param	<K> the type of key
 * @param	<L> the type of locker
 */
public final class MultiLockManager<K, L extends MultiLocker<K, L>>
    extends LockManager<K, L>
{
    /**
     * A thread local that stores the result of the lock request that the
     * current thread is waiting for, or {@code null} if it is not waiting.
     */
    private final ThreadLocal<LockAttemptResult<K, L>> waitingFor =
	new ThreadLocal<LockAttemptResult<K, L>>();

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
     * Downgrades a lock held by a locker from write to read access.  This
     * method does nothing if the lock is not held for write.
     *
     * @param	locker the locker holding the lock
     * @param	key the key identifying the lock
     * @throws	IllegalArgumentException if {@code locker} has a different lock
     *		manager 
     */
    public void downgradeLock(L locker, K key) {
	if (logger.isLoggable(FINER)) {
	    logger.log(FINER, "downgrade {0} {1}", locker, key);
	}
	releaseLockInternal(locker, key, true);
    }

    /* -- Other methods -- */

    /**
     * Checks if this thread is waiting for a lock.
     *
     * @return	the result of the lock request this thread is waiting
     *		for or {@code null} if it is not waiting
     */
    LockAttemptResult<K, L> getWaitingFor() {
	return waitingFor.get();
    }

    /**
     * Sets the lock that this thread is waiting for, or marks that it is not
     * waiting if the argument is {@code null}.  If {@code waitingFor} is not
     * {@code null}, then it should represent a conflict, and it's {@code
     * conflict} field must not be {@code null}.
     *
     * @param	waitingFor the lock or {@code null}
     */
    void setWaitingFor(LockAttemptResult<K, L> waitingFor) {
	this.waitingFor.set(waitingFor);
    }
}
