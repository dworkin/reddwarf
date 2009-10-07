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
import com.sun.sgs.service.Transaction;

/**
 * Records information about an entity that requests locks from a {@link
 * TxnLockManager} as part of a transaction.
 *
 * @param	<K> the type of key
 */
public class TxnLocker<K> extends BasicLocker<K> {

    /** The associated transaction. */
    protected final Transaction txn;

    /**
     * The time in milliseconds when the associated transaction was originally
     * requested to start.
     */
    protected final long requestedStartTime;

    /**
     * A conflict that should cause this transaction's request to be
     * denied, or {@code null}.  This value is cleared after the conflict
     * has been reported unless the conflict is a deadlock.  Synchronize on
     * this locker when accessing this field.
     */
    private LockConflict<K> conflict;

    /* -- Constructor -- */

    /**
     * Creates an instance of this class.
     *
     * @param	lockManager the lock manager for this locker
     * @param	txn the associated transaction
     * @param	requestedStartTime the time in milliseconds that the task
     *		associated with the transaction was originally
     *		requested to start
     * @throws	IllegalArgumentException if {@code requestedStartTime}
     *		is less than {@code 0}
     */
    public TxnLocker(TxnLockManager<K> lockManager,
		     Transaction txn,
		     long requestedStartTime)
    {
	super(lockManager);
	checkNull("txn", txn);
	if (requestedStartTime < 0) {
	    throw new IllegalArgumentException(
		"The requestedStartTime must not be less than 0");
	}
	this.txn = txn;
	this.requestedStartTime = requestedStartTime;
    }

    /* -- Public methods -- */

    /**
     * Returns the transaction associated with this locker.
     *
     * @return	the transaction associated with this locker
     */
    public Transaction getTransaction() {
	return txn;
    }

    /**
     * Returns the time in milliseconds that the transaction associated with
     * this locker was originally requested to start, as specified in the
     * constructor.
     *
     * @return	the requested start time in milliseconds
     */
    public long getRequestedStartTime() {
	return requestedStartTime;
    }

    /* -- Protected methods -- */

    /**
     * {@inheritDoc} <p>
     *
     * This implementation stops the lock attempt when the transaction ends.
     */
    @Override
    protected long getLockTimeoutTime(long now, long lockTimeout) {
	return Math.min(
	    addCheckOverflow(now, lockTimeout),
	    addCheckOverflow(txn.getCreationTime(), txn.getTimeout()));
    }

    /**
     * {@inheritDoc} <p>
     *
     * This implementation returns the conflict recorded for this locker.
     *
     * @throws	IllegalStateException {@inheritDoc}
     */
    @Override
    protected LockConflict<K> getConflict() {
	assert checkAllowSync();
	synchronized (this) {
	    return conflict;
	}
    }

    /**
     * {@inheritDoc} <p>
     *
     * This implementation checks for deadlocks and notifies waiters.
     *
     * @throws	IllegalStateException {@inheritDoc}
     */
    @Override
    protected void clearConflict() {
	assert checkAllowSync();
	synchronized (this) {
	    if (conflict != null) {
		if (conflict.type == LockConflictType.DEADLOCK) {
		    throw new IllegalStateException(
			"Transaction " + this +
			" must abort due to conflict: " + conflict);
		}
		conflict = null;
	    }
	    notifyAll();
	}
    }

    /**
     * Specifies a conflict that should cause this locker's current request to
     * be denied, and notifies waiters.
     *
     * @param	conflict the conflicting request
     * @throws	IllegalStateException if a conflicting request has already been
     *		specified
     */
    protected void setConflict(LockConflict<K> conflict) {
	assert checkAllowSync();
	synchronized (this) {
	    if (this.conflict != null) {
		throw new IllegalStateException(
		    "This locker already has a conflict");
	    }
	    this.conflict = conflict;
	    notifyAll();
	}
    }
}
