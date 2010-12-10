/*
 * Copyright 2010 The RedDwarf Authors.  All rights reserved
 * Portions of this file have been modified as part of RedDwarf
 * The source code is governed by a GPLv2 license that can be found
 * in the LICENSE file.
 */
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
     * The type of lock conflict, or {@code null} if and only if {@link
     * #conflict} is {@code null}.
     */
    final LockConflictType conflictType;

    /**
     * Creates an instance of this class, with the specified conflict type.
     * The {@code conflicct} and {@code conflictType} must either both be
     * {@code null} or both not {@code null}.
     *
     * @param	request the lock request
     * @param	conflict a conflicting locker or {@code null}
     * @param	conflictType the conflict type or {@code null}
     */
    LockAttemptResult(LockRequest<K> request,
		      Locker<K> conflict,
		      LockConflictType conflictType)
    {
	assert request != null;
	assert (conflict == null) == (conflictType == null);
	this.request = request;
	this.conflict = conflict;
	this.conflictType = conflictType;
    }

    /** Print fields, for debugging. */
    @Override
    public String toString() {
	return "LockAttemptResult[" + request +
	    ", conflict:" + conflict +
	    (conflictType != null ? ", conflictType:" + conflictType : "") +
	    "]";
    }
}
