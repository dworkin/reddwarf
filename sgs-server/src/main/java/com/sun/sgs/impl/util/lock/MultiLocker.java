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
 * MultiLockManager}.
 *
 * @param	<K> the type of key
 */
public class MultiLocker<K> extends Locker<K> {

    /* -- Constructor -- */

    /**
     * Creates an instance of this class.
     *
     * @param	lockManager the lock manager for this locker
     */
    public MultiLocker(MultiLockManager<K> lockManager) {
	super(lockManager);
    }

    /* -- Package access methods -- */

    /**
     * {@inheritDoc} <p>
     *
     * This implementation returns the value for the current thread obtained
     * from the {@link MultiLockManager}.
     */
    @Override
    LockAttemptResult<K> getWaitingFor() {
	return ((MultiLockManager<K>) lockManager).getWaitingFor();
    }

    /**
     * {@inheritDoc} <p>
     *
     * This implementation sets the value for the current thread in the {@link
     * MultiLockManager}.
     *
     * @throws	IllegalArgumentException {@inheritDoc}
     */
    @Override
    void setWaitingFor(LockAttemptResult<K> waitingFor) {
	if (waitingFor != null && waitingFor.conflict == null) {
	    throw new IllegalArgumentException(
		"Attempt to specify a lock attempt result that is not a" +
		" conflict: " + waitingFor);
	}
	((MultiLockManager<K>) lockManager).setWaitingFor(waitingFor);
    }
}
