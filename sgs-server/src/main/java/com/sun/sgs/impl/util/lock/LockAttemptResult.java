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
