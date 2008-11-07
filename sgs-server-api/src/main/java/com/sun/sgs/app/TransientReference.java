/*
 * Copyright 2007-2008 Sun Microsystems, Inc.
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

package com.sun.sgs.app;

/**
 * Represents a reference to an object that is only valid within the same run
 * of the task in which it was created, otherwise returning {@code null}.
 * Transient references should not be serializable.  Applications should also
 * insure that a single transient reference is not shared by multiple managed
 * objects.
 *
 * @param	<T> the type of the referenced object
 * @see		DataManager#createTransientReference
 *		DataManager.createTransientReference 
 */
public interface TransientReference<T> {

    /**
     * Returns the object associated with this reference, or {@code null} if
     * the reference was created in another task, including another run of the
     * same task.
     *
     * @return	the object or {@code null}
     * @throws	TransactionException if the operation failed because of a
     *		problem with the current transaction
     */
    T get();
}
