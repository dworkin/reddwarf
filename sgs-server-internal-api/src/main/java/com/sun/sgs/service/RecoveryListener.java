/*
 * This material is distributed under the GNU General Public License
 * Version 2. You may review the terms of this license at
 * http://www.gnu.org/licenses/gpl-2.0.html 
 *
 * Copyright (c) 2007-2010, Oracle and/or its affiliates.
 *
 * All rights reserved.
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
     * Notifies this listener that the specified {@code node} has failed
     * and that this listener needs to orchestrate recovery.  This method
     * is invoked outside of a transaction.
     *
     * <p>When recovery for this listener for the specified {@code node} is
     * complete, the {@link SimpleCompletionHandler#completed completed}
     * method of the specified {@code handler} must be invoked.
     *
     * <p>Recovery does not need to be performed in this method, but may be
     * performed asynchronously.
     *
     * <p>The implementation of this method should be idempotent because it
     * may be invoked multiple times.  If it is invoked multiple times, the
     * {@link SimpleCompletionHandler#completed completed} method must be
     * called for each {@code handler} provided.
     *
     * @param	node a failed node to recover
     * @param	handler a handler to notify when recovery is complete
     */
    void recover(Node node, SimpleCompletionHandler handler);
}
