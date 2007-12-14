/*
 * Copyright 2007 Sun Microsystems, Inc.
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

package com.sun.sgs.service;

/**
 * A future to be notified when recovery operations for an associated
 * {@link RecoveryListener} are complete.
 *
 * @see RecoveryListener#recover(Node,RecoveryCompleteFuture)
 */
public interface RecoveryCompleteFuture {

    /**
     * Notifies this future that the recovery operations initiated by
     * the {@link RecoveryListener} associated with this future are
     * complete.  This method is idempotent and can be called multiple times.
     */
    void done();

    /**
     * Returns {@code true} if the {@link #done done} method of this
     * future has been invoked, and {@code false} otherwise.
     *
     * @return	{@code true} if {@code done} has been invoked, and
     *		{@code false} otherwise 
     */
    boolean isDone();
}
