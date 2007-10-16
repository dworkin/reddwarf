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

import java.util.Iterator;

import com.sun.sgs.app.TransactionException;

/**
 * The {@code WatchdogService} monitors the health of server nodes and
 * notifies registered listeners of node status change events.
 */
public interface WatchdogService extends Service {

    /**
     * Returns the node ID for the local node.  The node ID for a node
     * remains fixed for the lifetime of the node (i.e., until it
     * fails).
     *
     * @return the node ID for the local node
     */
    long getLocalNodeId();

    /**
     * Returns {@code true} if the local node is considered alive,
     * otherwise returns {@code false}.  This method should only be
     * called from within a transaction.
     *
     * @return	{@code true} if the local node is considered alive, and
     * 		{@code false} otherwise
     * @throws 	TransactionException if there is a problem with the
     *		current transaction
     */
    boolean isLocalNodeAlive();

    /**
     * Returns {@code true} if the local node is considered alive,
     * otherwise returns {@code false}.  This method returns the most
     * recent information known to this service and may not be
     * definitive.  For definitive information, use the {@link
     * #isLocalNodeAlive isLocalNodeAlive} method.
     *
     * @return	{@code true} if the local node is considered alive, and
     * 		{@code false} otherwise
     */
    boolean isLocalNodeAliveNonTransactional();

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
     * {@code nodeId}, or {@code null} if the node is unknown.  This
     * method should only be called within a transaction.
     *
     * @param	nodeId	a node ID
     * @return	node status information for the specified {@code nodeId},
     *		or {@code null}
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
}
