/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.service;

/**
 * A listener that can be registered with the {@link WatchdogService}
 * to be notified of node status change events.  Invocations to the
 * methods of a {@code NodeListener} are made outside of a
 * transaction.
 *
 * @see WatchdogService#addNodeListener(NodeListener)
 */
public interface NodeListener {

    /**
     * Notifies this listener that the specified {@code node} started.
     *
     * @param	node	node status information 
     */
    void nodeStarted(Node node);
    
    /**
     * Notifies this listener that the specified {@code node} failed.
     *
     * @param	node	node status information 
     */
    void nodeFailed(Node node);
}
