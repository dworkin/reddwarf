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
 * A class for representing a conflict resulting from a lock request made to a
 * {@link LockManager}.
 *
 * @param	<K> the type of key
 */
public final class LockConflict<K> {

    /** The type of conflict. */
    final LockConflictType type;

    /** A locker that caused the conflict. */
    final Locker<K> conflictingLocker;

    /**
     * Creates an instance of this class.
     *
     * @param	type the type of conflict
     * @param	conflictingLocker a locker that caused the conflict
     */
    public LockConflict(LockConflictType type,
			Locker<K> conflictingLocker)
    {
	assert type != null;
	assert conflictingLocker != null;
	this.type = type;
	this.conflictingLocker = conflictingLocker;
    }

    /**
     * Returns the type of conflict.
     *
     * @return	the type of conflict
     */
    public LockConflictType getType() {
	return type;
    }

    /**
     * Returns a transaction that caused the conflict.
     *
     * @return	a transaction that caused the conflict
     */
    public Locker<K> getConflictingLocker() {
	return conflictingLocker;
    }

    /**
     * Returns a string representation of this instance, for debugging.
     *
     * @return	a string representation of this instance
     */
    @Override
    public String toString() {
	return "LockConflict[type:" + type +
	    ", conflict:" + conflictingLocker + "]";
    }
}
