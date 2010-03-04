/*
 * This material is distributed under the GNU General Public License
 * Version 2. You may review the terms of this license at
 * http://www.gnu.org/licenses/gpl-2.0.html 
 *
 * Copyright (c) 2007-2010, Oracle and/or its affiliates.
 *
 * All rights reserved.
 */

package com.sun.sgs.impl.util.lock;

/**
 * Records information about an entity that requests locks from a {@link
 * LockManager} and that permits only a single active lock request.
 *
 * @param	<K> the type of key
 */
public class BasicLocker<K> extends Locker<K> {

    /**
     * The result of the lock request that this locker is waiting for, or
     * {@code null} if it is not waiting.  Synchronize on this locker when
     * accessing this field.
     */
    private LockAttemptResult<K> waitingFor;

    /* -- Constructor -- */

    /**
     * Creates an instance of this class.
     *
     * @param	lockManager the lock manager for this locker
     */
    public BasicLocker(LockManager<K> lockManager) {
	super(lockManager);
    }

    /* -- Package access methods -- */

    /**
     * {@inheritDoc} <p>
     *
     * This implementation returns the lock attempt request associated with
     * this locker, if any.
     */
    @Override
    LockAttemptResult<K> getWaitingFor() {
	assert checkAllowSync();
	synchronized (this) {
	    return waitingFor;
	}
    }

    /**
     * {@inheritDoc} <p>
     *
     * This implementation sets the lock attempt request associated with this
     * locker.
     *
     * @throws	IllegalArgumentException {@inheritDoc}
     */
    @Override
    void setWaitingFor(LockAttemptResult<K> waitingFor) {
	assert checkAllowSync();
	if (waitingFor != null && waitingFor.conflict == null) {
	    throw new IllegalArgumentException(
		"Attempt to specify a lock attempt result that is not a" +
		" conflict: " + waitingFor);
	}
	synchronized (this) {
	    this.waitingFor = waitingFor;
	}
    }
}
