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
 * A listener that can be registered with the {@link WatchdogService}
 * to be notified of node status events.  Invocations to the
 * methods of a {@code NodeListener} are made outside of a transaction. <p>
 *
 * The implementations for the methods of this interface should be
 * idempotent because they may be invoked multiple times.
 *
 * @see WatchdogService#addNodeListener(NodeListener)
 */
public interface NodeListener {
    
    /**
     * Notifies this listener that the specified {@code node}'s health has
     * been updated. The node's health can be obtained by calling
     * {@link Node#getHealth getHealth} on the node status information object.
     * Note that the node's health may not have changed since the
     * last update. <p>
     *
     * On node startup a health update will be made to indicate the initial
     * health of the node. <p>
     *
     * Once a node has failed, i.e. {@link Node#isAlive() node.isAlive()}
     * returns {@code false}, the node's health will not change.
     *
     * @param	node	node status information
     */
    void nodeHealthUpdate(Node node);
}
