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
import static com.sun.sgs.impl.util.Numbers.addCheckOverflow;

/**
 * Records information about an entity that requests locks from a {@link
 * LockManager}.
 *
 * @param	<K> the type of key
 */
public abstract class Locker<K> {

    /** The lock manager for this locker. */
    final LockManager<K> lockManager;

    /* -- Constructor -- */

    /**
     * Creates an instance of this class.
     *
     * @param	lockManager the lock manager for this locker
     */
    protected Locker(LockManager<K> lockManager) {
	checkNull("lockManager", lockManager);
	this.lockManager = lockManager;
    }

    /* -- Public methods -- */

    /**
     * Returns the lock manager for this locker.
     *
     * @return	the lock manager for this locker.
     */
    public LockManager<K> getLockManager() {
	return lockManager;
    }

    /* -- Protected methods -- */

    /**
     * Returns the time when a lock attempt should stop, given the current time
     * and the lock timeout supplied by the caller.  Subclasses can override
     * this method, for example to enforce a transaction timeout.
     *
     * @param	now the current time in milliseconds
     * @param	lockTimeout the amount of time in milliseconds to wait for a
     *		lock
     * @return	the time in milliseconds when the lock attempt should timeout
     */
    protected long getLockTimeoutTime(long now, long lockTimeout) {
	return addCheckOverflow(now, lockTimeout);
    }

    /**
     * Creates a new lock request. <p>
     *
     * The default implementation creates and returns an instance of {@link
     * LockRequest}.
     *
     * @param	key the key that identifies the lock
     * @param	forWrite whether the request is for write
     * @param	upgrade whether the request is for an upgrade
     * @return	the lock request
     */
    protected LockRequest<K> newLockRequest(
	K key, boolean forWrite, boolean upgrade)
    {
	return new LockRequest<K>(this, key, forWrite, upgrade);
    }

    /**
     * Checks if there is a conflict that should cause this locker's
     * current request to be denied.  Returns {@code null} if there was no
     * conflict. <p>
     *
     * The default implementation of this method always returns {@code null}.
     *
     * @return	the conflicting request or {@code null}
     */
    protected LockConflict<K> getConflict() {
	return null;
    }

    /**
     * Clears the conflict that should cause this locker's current request to
     * be denied.  If there is no conflict, then this method has no effect.  If
     * the conflict is a deadlock, represented by a non-{@code null} return
     * value from {@link #getConflict getConflict} with a {@code type} field
     * equal to {@link LockConflictType#DEADLOCK DEADLOCK}, then the conflict
     * cannot be cleared and {@code IllegalStateException} will be thrown. <p>
     *
     * The default implementation of this method does nothing.
     *
     * @throws	IllegalStateException if the conflict is a deadlock
     */
    protected void clearConflict() { }

    /* -- Package access methods -- */

    /**
     * Checks if this locker is waiting for a lock.
     *
     * @return	the result of the attempt to request a lock that this locker
     *		is waiting for, or {@code null} if it is not waiting
     */
    abstract LockAttemptResult<K> getWaitingFor();

    /**
     * Records that this locker is waiting for a lock, or marks that it is not
     * waiting if the argument is {@code null}.  If {@code waitingFor} is not
     * {@code null}, then it should represent a conflict, and it's {@code
     * conflict} field must not be {@code null}.
     *
     * @param	waitingFor the lock or {@code null}
     * @throws	IllegalArgumentException if {@code waitingFor} is not {@code
     *		null} and its {@code conflict} field is {@code null}
     */
    abstract void setWaitingFor(LockAttemptResult<K> waitingFor);

    /**
     * Checks that the current thread is permitted to synchronize on this
     * locker.  Throws an {@link AssertionError} if already synchronized on
     * a locker other than this one or any lock, otherwise returns {@code
     * true}.
     */
    boolean checkAllowSync() {
	Locker<K> locker = lockManager.currentLockerSync.get();
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
	Locker<K> locker = lockManager.currentLockerSync.get();
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
	Locker<K> locker = lockManager.currentLockerSync.get();
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
