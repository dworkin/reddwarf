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
