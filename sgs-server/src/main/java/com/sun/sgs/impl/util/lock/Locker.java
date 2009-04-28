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

import static com.sun.sgs.impl.sharedutil.Objects.checkNull;

/**
 * Records information about an entity that requests locks of a {@link
 * LockManager}.
 *
 * @param	<K> the type of key
 * @param	<L> the type of locker
 */
public class Locker<K, L extends Locker<K, L>> {

    /** The lock manager for this locker. */
    final LockManager<K, L> lockManager;

    /**
     * The time in milliseconds when the task associated with this
     * transaction was originally requested to start.
     */
    final long requestedStartTime;

    /**
     * The result of the lock request that this transaction is waiting for,
     * or {@code null} if it is not waiting.  Synchronize on this locker
     * when accessing this field.
     */
    private LockAttemptResult<K, L> waitingFor;

    /**
     * A conflict that should cause this transaction's request to be
     * denied, or {@code null}.  This value is cleared after the conflict
     * has been reported unless the conflict is a deadlock.  Synchronize on
     * this locker when accessing this field.
     */
    private LockConflict<K, L> conflict;

    /**
     * Creates an instance of this class.
     *
     * @param	lockManager the lock manager for this locker
     * @param	requestedStartTime the time milliseconds that the task
     *		associated with the transaction was originally
     *		requested to start
     * @throws	IllegalArgumentException if {@code requestedStartTime}
     *		is less than {@code 0}
     */
    public Locker(LockManager<K, L> lockManager, long requestedStartTime) {
	checkNull("lockManager", lockManager);
	if (requestedStartTime < 0) {
	    throw new IllegalArgumentException(
		"The requestedStartTime must not be less than 0");
	}
	this.lockManager = lockManager;
	this.requestedStartTime = requestedStartTime;
    }

    /**
     * Returns the lock manager for this locker.
     *
     * @return	the lock manager for this locker.
     */
    public LockManager<K, L> getLockManager() {
	return lockManager;
    }

    /**
     * Returns the time when a lock attempt should stop, given the current time
     * and the lock timeout time supplied by the caller.  Subclasses can
     * override this method, for example to enforce a transaction timeout.
     *
     * @param	now the current time in milliseconds
     * @param	lockTimeoutTime the time in milliseconds when the lock timeout
     *		will cause the lock attempt to timeout
     * @return	the time in milliseconds when the lock attempt should timeout
     */
    protected long getLockTimeoutTime(long now, long lockTimeoutTime) {
	return lockTimeoutTime;
    }

    /**
     * Creates an object to represent a new lock request.
     *
     * @param	key the key that identifies the lock
     * @param	forWrite whether the request is for write
     * @param	upgrade whether the request is for an upgrade
     */
    protected LockRequest<K, L> newLockRequest(
	K key, boolean forWrite, boolean upgrade)
    {
	return new LockRequest<K, L>(this, key, forWrite, upgrade);
    }

    /**
     * Checks if there is a conflict that should cause this locker's
     * request to be denied.  This value can be cleared to permit a new
     * request unless the conflict is a deadlock.
     *
     * @return	the conflicting request or {@code null}
     */
    protected synchronized LockConflict<K, L> getConflict() {
	assert checkAllowSync();
	return conflict;
    }

    /**
     * Requests that this locker request be denied because of a conflict
     * with the specified request.
     */
    synchronized void setConflict(LockConflict<K, L> conflict) {
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
    synchronized LockAttemptResult<K, L> getWaitingFor() {
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
    synchronized void setWaitingFor(LockAttemptResult<K, L> waitingFor) {
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
	Locker<K, L> locker = lockManager.currentLockerSync.get();
	if (locker != null && locker != this) {
	    throw new AssertionError(
		"Attempt to synchronize on locker " + this +
		", but already synchronized on " + locker);
	}
	Lock.checkNoSync(lockManager);
	return true;
    }

    /**
     * Notes the start of synchronization on this locker.  Throws {@link
     * AssertionError} if already synchronized on any locker or lock,
     * otherwise returns {@code true}.
     */
    boolean noteSync() {
	Locker<K, L> locker = lockManager.currentLockerSync.get();
	if (locker != null) {
	    throw new AssertionError(
		"Attempt to synchronize on locker " + this +
		", but already synchronized on " + locker);
	}
	Lock.checkNoSync(lockManager);
	lockManager.currentLockerSync.set(this);
	return true;
    }

    /**
     * Notes the end of synchronization on this locker.  Throws {@link
     * AssertionError} if not already synchronized on this locker,
     * otherwise returns {@code true}.
     */
    boolean noteUnsync() {
	Locker<K, L> locker = lockManager.currentLockerSync.get();
	if (locker == null) {
	    throw new AssertionError(
		"Attempt to unsynchronize on locker " + this +
		", but not currently synchronized on a locker");
	} else if (locker != this) {
	    throw new AssertionError(
		"Attempt to unsynchronize on locker " + this +
		", but currently synchronized on " + locker);
	}
	lockManager.currentLockerSync.remove();
	return true;
    }
}
