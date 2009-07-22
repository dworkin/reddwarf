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

/**
 * Records information about an entity that requests locks from a {@link
 * TxnLockManager} as part of a transaction.
 *
 * @param	<K> the type of key
 * @param	<L> the type of locker
 */
public abstract class TxnLocker<K, L extends TxnLocker<K, L>>
    extends Locker<K, L>
{
    /**
     * The time in milliseconds when the task associated with this
     * transaction was originally requested to start.
     */
    private final long requestedStartTime;

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

    /* -- Constructor -- */

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
    protected TxnLocker(
	TxnLockManager<K, L> lockManager, long requestedStartTime)
    {
	super(lockManager);
	if (requestedStartTime < 0) {
	    throw new IllegalArgumentException(
		"The requestedStartTime must not be less than 0");
	}
	this.requestedStartTime = requestedStartTime;
    }

    /* -- Public methods -- */

    /**
     * {@inheritDoc} <p>
     *
     * This implementation returns the value specified in the constructor.
     */
    public long getRequestedStartTime() {
	return requestedStartTime;
    }

    /* -- Protected methods -- */

    /**
     * {@inheritDoc} <p>
     *
     * This implementation returns the conflict recorded for this locker.
     */
    @Override
    protected LockConflict<K, L> getConflict() {
	assert checkAllowSync();
	synchronized (this) {
	    return conflict;
	}
    }

    /* -- Package access methods -- */

    @Override
    void setConflict(LockConflict<K, L> conflict) {
	assert checkAllowSync();
	synchronized (this) {
	    this.conflict = conflict;
	    notify();
	}
    }

    @Override
    LockAttemptResult<K, L> getWaitingFor() {
	assert checkAllowSync();
	synchronized (this) {
	    return waitingFor;
	}
    }

    @Override
    void setWaitingFor(LockAttemptResult<K, L> waitingFor) {
	assert checkAllowSync();
	synchronized (this) {
	    assert waitingFor == null || waitingFor.conflict != null;
	    this.waitingFor = waitingFor;
	}
    }
}
