/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.service;

/**
 * The {@code RecoveryService} provides information and notification
 * services about node backup assignment and recovery.
 */
public interface RecoveryService extends Service {

    /**
     * Returns the node that is designated as the backup for the node
     * with the specified {@code nodeId}, or {@code null} if no backup
     * is currently designated.  This method must be called within a
     * transaction.
     *
     * <p>Note: this method should be moved to the Node interface.
     *
     * @param	nodeId a node ID
     * @return	a backup node, or {@code null}
     * @throws 	TransactionException if there is a problem with the
     *		current transaction
     */
    Node getBackup(long nodeId);

    /**
     * Adds the specified recovery {@code listener} for the local
     * node.  If the local node is designated as a backup for a node
     * that fails, the specified {@code listener} will be notified
     * (outside of a transaction) by having its {@link
     * RecoveryListener#recover recover} method invoked, passing the
     * failed node and a {@link RecoveryCompleteFuture} whose {@link
     * RecoveryCompleteFuture#done done} method must be invoked when
     * the recovery operations initiated by the {@code listener} are
     * complete.
     *
     * <p>This method should be called outside of a transaction.
     *
     * @param	listener a recovery listener
     */
    void addRecoveryListener(RecoveryListener listener);

}
