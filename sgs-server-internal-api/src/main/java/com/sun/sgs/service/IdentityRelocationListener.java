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

import com.sun.sgs.auth.Identity;

/**
 * A service can register an {@code IdentityRelocationListener} to be notified
 * when an identity is about to be relocated from the local node if it needs
 * to perform any work in advance of the identity relocation.
 *
 * @see 
 * NodeMappingService#addIdentityRelocationListener(IdentityRelocationListener)
 */
public interface IdentityRelocationListener {

    /**
     * Notifies this listener that the specified {@code id} has been
     * selected for relocation to {@code newNodeId} and that this listener
     * needs to prepare for that move. This method is invoked outside of 
     * a transaction.
     * <p>
     * When the listener has completed preparations for the identity relocation,
     * the {@link SimpleCompletionHandler#completed completed} method of the
     * specified {@code handler} must be invoked.  After all listeners have
     * noted they are ready for the move, the identity will be relocated and
     * the new node will be returned from
     * {@link NodeMappingService#getNode(Identity)} calls.
     * <p>
     * The implementation of this method should be idempotent
     * because it may be invoked multiple times.  If it is invoked multiple
     * times, the {@link SimpleCompletionHandler#completed completed} method
     * must be called for each {@code handler} provided.
     * 
     * @param id        the identity which has been selected for relocation
     * @param newNodeId the identity of the node that {@code id} will move to
     * @param handler   a handler to notify when relocation preparations are
     *                  complete
     */
    void prepareToRelocate(Identity id, long newNodeId, 
                           SimpleCompletionHandler handler);
}
