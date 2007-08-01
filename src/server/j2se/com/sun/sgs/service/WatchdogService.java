/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.service;

import java.util.Iterator;

/**
 * The {@code WatchdogService} monitors the health of server nodes and
 * notifies registered listeners of node status change events.
 */
public interface WatchdogService extends Service {

    /**
     * Returns the node ID for the local node.
     *
     * @return the node ID for the local node
     */
    long getLocalNodeId();

    /**
     * Returns {@code true} if the local node is considered alive,
     * otherwise returns {@code false}.  If {@code checkTransactionally}
     * is {@code true}, the check happens in the current transaction,
     * otherwise this method returns the most recent information known.
     *
     * @param 	checkTransactionally if {@code true}, the check happens
     * 		in the current transaction, otherwise the most recent
     * 		information is returned
     * @return	{@code true} if the local node is considered alive, and
     * 		{@code false} otherwise
     * @throws 	TransactionException if {@code checkTransactionally} is
     * 		{@code true} and there is a problem with the current
     *		transaction
     */
    boolean isLocalNodeAlive(boolean checkTransactionally);

    /**
     * Returns an iterator for the set of nodes that this service
     * monitors.  The {@code remove} operation of the returned
     * iterator is not supported and will throw {@code
     * UnsupportedOperationException} if invoked.  This method should
     * only be called within a transaction, and the returned iterator
     * should only be used within that transaction.
     *
     * @return	an iterator for the set of nodes that this service
     *		monitors
     * @throws 	TransactionException if there is a problem with the
     *		current transaction
     */
    Iterator<Node> getNodes();

    /**
     * Returns node status information for the node with the specified
     * {@code nodeId}.  This method should only be called within a
     * transaction.
     *
     * @param	nodeId	a node ID
     * @return	node status information for the specified {@code
     * 		nodeId}
     * @throws	IllegalArgumentException if the specified {@code nodeId}
     *		is not within the range of valid IDs
     * @throws 	TransactionException if there is a problem with the
     *		current transaction
     */
    Node getNode(long nodeId);

    /**
     * Registers a {@code listener} to be notified when any node that
     * this service monitors starts or fails.  Registered listeners
     * are notified outside of a transaction.
     *
     * @param	listener a node listener
     */
    void addNodeListener(NodeListener listener);

    /**
     * Registers a {@code listener} to be notified when the node with
     * the specified {@code nodeId} starts or fails.  Registered
     * listeners are notified outside of a transaction.
     *
     * @param	nodeId a node ID
     * @param	listener a node listener
     */
    void addNodeListener(long nodeId, NodeListener listener);
}
