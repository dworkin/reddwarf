/*
 * Copyright 2007-2010 Sun Microsystems, Inc.
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
 *
 * --
 */

package com.sun.sgs.service;

import com.sun.sgs.app.ExceptionRetryStatus;
import com.sun.sgs.auth.Identity;

/**
 * A listener that can be registered with the {@link NodeMappingService}
 * to be notified of identities being added to or removed from the local node.
 * <p>
 * Invocations of the {@code NodeMappingListener} methods are made outside
 * of a transaction, but modifications to the map will have been performed
 * inside a transaction.
 * <p>
 * The implementations for the methods of this interface should be
 * idempotent because they may be invoked multiple times if the implementation
 * throws an exception which implements {@link ExceptionRetryStatus} and 
 * {@code shouldRetry} returns {@code true}.
 * 
 * @see NodeMappingService#addNodeMappingListener(NodeMappingListener)
 */
public interface NodeMappingListener {
    /**
     * Notifies this listener that an identity has been added to this node.
     *
     * @param id the added identity
     * @param oldNode the last node the identity was assigned to, or 
     *                {@code null} if this is the identity's first assignment
     */
    void mappingAdded(Identity id, Node oldNode);
    
    /**
     * Notifies this listener that an identity has been removed from this node.
     *
     * @param id the removed identity
     * @param newNode the new node assignment for the identity, or {@code null} 
     *                if the identity has been removed from the system
     */
    void mappingRemoved(Identity id, Node newNode);
}
