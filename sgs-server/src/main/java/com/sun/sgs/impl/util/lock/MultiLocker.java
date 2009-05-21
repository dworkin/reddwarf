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
 * Records information about an entity that requests locks of a {@link
 * MultiLockManager}.
 *
 * @param	<K> the type of key
 * @param	<L> the type of locker
 */
public abstract class MultiLocker<K, L extends MultiLocker<K, L>>
    extends Locker<K, L>
{
    /* -- Constructor -- */

    /**
     * Creates an instance of this class.
     *
     * @param	lockManager the lock manager for this locker
     */
    protected MultiLocker(MultiLockManager<K, L> lockManager) {
	super(lockManager);
    }

    /* -- Protected methods -- */

    /**
     * {@inheritDoc} <p>
     *
     * This implementation always returns {@code null} because the {@link
     * MultiLockManager} does not detect deadlocks or other conflicts from
     * another thread.
     */
    @Override
    protected LockConflict<K, L> getConflict() {
	return null;
    }

    /* -- Package access methods -- */

    /**
     * {@inheritDoc} <p>
     *
     * This implementation throws {@link UnsupportedOperationException} because
     * the {@link MultiLockManager} does not detect deadlocks or other
     * conflicts from another thread.
     */
    @Override
    void setConflict(LockConflict<K, L> conflict) {
	throw new UnsupportedOperationException(
	    "The MultiLocker class does not support the setConflict method");
    }

    /**
     * {@inheritDoc} <p>
     *
     * This implementation returns the value for the current thread obtained
     * from the {@link MultiLockManager}.
     */
    @Override
    LockAttemptResult<K, L> getWaitingFor() {
	return ((MultiLockManager<K, L>) lockManager).getWaitingFor();
    }

    /**
     * {@inheritDoc} <p>
     *
     * This implementation sets the value for the current thread in the {@link
     * MultiLockManager}.
     */
    @Override
    void setWaitingFor(LockAttemptResult<K, L> waitingFor) {
	((MultiLockManager<K, L>) lockManager).setWaitingFor(waitingFor);
    }
}
