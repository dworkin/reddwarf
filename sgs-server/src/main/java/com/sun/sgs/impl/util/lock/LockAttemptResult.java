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
 * The result of attempting to request a lock.
 *
 * @param	<K> the type of key
 * @see		LockManager
 */
final class LockAttemptResult<K> {

    /** The lock request. */
    final LockRequest<K> request;

    /**
     * A conflicting locker, if the request was not granted, or {@code
     * null}.
     */
    final Locker<K> conflict;

    /**
     * Creates an instance of this class.
     *
     * @param	request the lock request
     * @param	conflict a conflicting locker or {@code null}
     */
    LockAttemptResult(LockRequest<K> request, Locker<K> conflict) {
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
