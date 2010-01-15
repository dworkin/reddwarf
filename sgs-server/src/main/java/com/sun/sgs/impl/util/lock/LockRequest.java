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
 * A class representing a request for a lock made to a {@link LockManager}.
 *
 * @param	<K> the type of key
 */
public class LockRequest<K> {

    /** Types of requests. */
    private enum Type { READ, WRITE, UPGRADE; }

    /** The locker that requested the lock. */
    private final Locker<K> locker;

    /** The key identifying the lock. */
    private final K key;

    /** The request type. */
    private final Type type;

    /**
     * Creates a lock request.
     *
     * @param	locker the locker that requested the lock
     * @param	key the key identifying the lock
     * @param	forWrite whether a write lock was requested
     * @param	upgrade whether an upgrade was requested
     */
    public LockRequest(
	Locker<K> locker, K key, boolean forWrite, boolean upgrade)
    {
	assert locker != null;
	assert key != null;
	assert !upgrade || forWrite : "Upgrade implies forWrite";
	this.locker = locker;
	this.key = key;
	type = !forWrite ? Type.READ
	    : !upgrade ? Type.WRITE
	    : Type.UPGRADE;
    }

    /**
     * Print fields, for debugging.
     *
     * @return	a string representation of this instance
     */
    @Override
    public String toString() {
	return "LockRequest[" + locker + ", " + key +
	    ", type:" + type + "]";
    }

    /**
     * Returns the locker that requested the lock.
     *
     * @return	the locker that requested the lock.
     */
    public Locker<K> getLocker() {
	return locker;
    }

    /**
     * Returns the key identifying the lock.
     *
     * @return	the key identifying the lock
     */
    public K getKey() {
	return key;
    }

    /**
     * Returns whether the request was for write.
     *
     * @return	whether the request was for write
     */
    public boolean getForWrite() {
	return type != Type.READ;
    }

    /**
     * Returns whether the request was for an upgrade.
     *
     * @return	whether the request was for an upgrade
     */
    public boolean getUpgrade() {
	return type == Type.UPGRADE;
    }
}
