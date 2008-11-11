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

package com.sun.sgs.service;

import java.io.IOException;
import java.util.Iterator;
import com.sun.sgs.app.TransactionException;

/**
 * The {@code WatchdogService} monitors the health of server nodes and
 * notifies registered listeners of node status change events. It also
 * provides information and notification services about node backup and
 * recovery.
 */
public interface WatchdogService extends Service {

    /**
     * Failure constants to use when reporting issues to the Watchdog
     */
    public enum FailureLevel {
	/** Fatal error which should prompt the shutdown of the node */
	FATAL,
	/** A severe error which likely requires the node to shutdown */
	SEVERE,
	/** A medium error which might require the node to shutdown (I/O?) */
	MEDIUM,
	/** A minor error that may not require node shutdown */
	MINOR
    }

    /**
     * Returns the node ID for the local node. The node ID for a node remains
     * fixed for the lifetime of the node (i.e., until it fails).
     * 
     * @return the node ID for the local node
     */
    long getLocalNodeId();

    /**
     * Returns {@code true} if the local node is considered alive, otherwise
     * returns {@code false}. This method should only be called from within a
     * transaction.
     * 
     * @return {@code true} if the local node is considered alive, and
     * {@code false} otherwise
     * @throws TransactionException if there is a problem with the current
     * transaction
     */
    boolean isLocalNodeAlive();

    /**
     * Returns {@code true} if the local node is considered alive, otherwise
     * returns {@code false}. This method returns the most recent information
     * known to this service and may not be definitive. For definitive
     * information, use the {@link #isLocalNodeAlive isLocalNodeAlive} method.
     * 
     * @return {@code true} if the local node is considered alive, and
     * {@code false} otherwise
     */
    boolean isLocalNodeAliveNonTransactional();

    /**
     * Returns an iterator for the set of nodes that this service monitors.
     * The {@code remove} operation of the returned iterator is not supported
     * and will throw {@code UnsupportedOperationException} if invoked. This
     * method should only be called within a transaction, and the returned
     * iterator should only be used within that transaction.
     * 
     * @return an iterator for the set of nodes that this service monitors
     * @throws TransactionException if there is a problem with the current
     * transaction
     */
    Iterator<Node> getNodes();

    /**
     * Returns node status information for the node with the specified
     * {@code nodeId}, or {@code null} if the node is unknown. This method
     * should only be called within a transaction.
     * 
     * @param nodeId a node ID
     * @return node status information for the specified {@code nodeId}, or
     * {@code null}
     * @throws IllegalArgumentException if the specified {@code nodeId} is not
     * within the range of valid IDs
     * @throws TransactionException if there is a problem with the current
     * transaction
     */
    Node getNode(long nodeId);

    /**
     * Returns the node that is designated as the backup for the node with the
     * specified {@code nodeId}, or {@code null} if no backup is currently
     * designated. This method must be called within a transaction.
     * <p>
     * <b>Note: this method should probably be moved to the Node interface, or
     * it should throw an exception in the case where there is no existing
     * node corresponding to {@code nodeId}.</b>
     * 
     * @param nodeId a node ID
     * @return a backup node, or {@code null}
     * @throws IllegalArgumentException if the specified {@code nodeId} is not
     * within the range of valid IDs
     * @throws TransactionException if there is a problem with the current
     * transaction
     */
    Node getBackup(long nodeId);

    /**
     * Registers a {@code listener} to be notified when any node that this
     * service monitors starts or fails. Registered listeners are notified
     * outside of a transaction.
     * 
     * @param listener a node listener
     */
    void addNodeListener(NodeListener listener);

    /**
     * Adds the specified recovery {@code listener} for the local node. If the
     * local node is designated as a backup for a node that fails, the
     * specified {@code listener} will be notified (outside of a transaction)
     * by having its {@link RecoveryListener#recover recover} method invoked,
     * passing the failed node and a {@link RecoveryCompleteFuture} whose
     * {@link RecoveryCompleteFuture#done done} method must be invoked when
     * the recovery operations initiated by the {@code listener} are complete.
     * <p>
     * This method should be called outside of a transaction.
     * 
     * @param listener a recovery listener
     */
    void addRecoveryListener(RecoveryListener listener);

    /**
     * A hook for services to call when there is a known problem that requires
     * the watchdog to shut down the node.
     * 
     * @param className the class name of the service that failed
     * @param severity the severity of the failure; values can be
     * {@code FailureLevel.MINOR}, {@code FailureLevel.FAILURE_MEDIUM},
     * {@code FailureLevel.FAILURE_SEVERE}, or
     * {@code FailureLevel.FAILURE_FATAL}
     */
    void reportFailure(String className, FailureLevel severity);

    /**
     * A hook for servers to call when there is a known problem on a remote node
     * that requires the watchdog to shut down the node. This method is
     * called through the {@code WatchdogService} interface when the node is known
     * to be remote.
     * 
     * @param nodeId the id of the node to shutdown
     * @param className the class name of the service that failed
     * @param severity the severity of the failure; values can be
     * {@code FailureLevel.MINOR}, {@code FailureLevel.FAILURE_MEDIUM},
     * {@code FailureLevel.FAILURE_SEVERE}, or
     * {@code FailureLevel.FAILURE_FATAL}
     */
    void reportFailure(long nodeId, String className, FailureLevel severity)
	    throws IOException;
}
