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
 * A service can register a {@code RecoveryListener} to be notified
 * when that service on the local node needs to recover for the
 * service on a failed node.
 *
 * @see WatchdogService#addRecoveryListener(RecoveryListener)
 */
public interface RecoveryListener {

    /**
     * Notifies this listener that the specified {@code node} has
     * failed and that this listener needs to orchestrate recovery.
     * This method is invoked outside of a transaction.
     *
     * <p>When recovery for this listener for the specified {@code
     * node} is complete, the {@link RecoveryCompleteFuture#done done}
     * method of the specified {@code future} must be invoked.
     *
     * <p>Recovery does not need to be performed in this method, but
     * may be performed asynchronously.
     *
     * <p>The implementation of this method should be idempotent
     * because it may be invoked multiple times.
     *
     * @param	node a failed node to recover
     * @param	future a future to notify when recovery is complete
     */
    void recover(Node node, RecoveryCompleteFuture future);
}
