/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.service;

/**
 * A service can register a {@code RecoveryListener} to be notified
 * when that service on the local node needs to recover for the
 * service on a failed node.
 *
 * @see RecoveryService#addRecoveryListener(RecoveryListener)
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
