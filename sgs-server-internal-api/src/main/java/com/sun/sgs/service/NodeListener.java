/*
 * Copyright 2007-2009 Sun Microsystems, Inc.
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
 *
 * Sun designates this particular file as subject to the "Classpath"
 * exception as provided by Sun in the LICENSE file that accompanied
 * this code.
 */

package com.sun.sgs.service;

/**
 * A listener that can be registered with the {@link WatchdogService}
 * to be notified of node status change events.  Invocations to the
 * methods of a {@code NodeListener} are made outside of a
 * transaction.
 *
 * <p>The implementations for the methods of this interface should be
 * idempotent because they may be invoked multiple times.  However,
 * for any given node, the {@link #nodeStarted nodeStarted} method
 * will never be invoked after the {@link #nodeFailed nodeFailed}
 * method is invoked for that given node.
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
