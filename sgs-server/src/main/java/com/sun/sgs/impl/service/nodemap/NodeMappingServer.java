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
 */

package com.sun.sgs.impl.service.nodemap;

import com.sun.sgs.auth.Identity;
import com.sun.sgs.service.NodeMappingService;
import java.io.IOException;
import java.rmi.Remote;

/**
 * Defines the service interface for the global portion of the
 * {@link NodeMappingService}.
 */
interface NodeMappingServer extends Remote {

    /**
     * Assigns the identity to a node and indicates that the service class
     * believes this identity is active.
     * If the id has already been assigned to a node, simply
     * return that assignment.  If the id is assigned to a node that's not 
     * alive, reassign it.
     *
     * @param service the class of the calling service
     * @param id the identity to assign
     *    <b> should this be a set? </b>
     * @param requestingNode the id of the node requesting assignment
     *
     * @throws	IOException if a communication problem occurs while
     *          invoking this method
     */
    void assignNode(Class service, Identity id, long requestingNode) 
        throws IOException;
    
    /**
     * The identity's reference count has gone to zero, so it can be
     * removed. 
     *
     * @param id the identity which can be removed from the map
     * @throws	IOException if a communication problem occurs while
     *          invoking this method 
     */
    void canRemove(Identity id) throws IOException;
    
    /**
     * All {@code IdentityRelocationListener}s have completed their work,
     * so it's safe to move the identity.
     * @param id the identity which can now be moved
     * @throws IOException if a communication problem occurs while
     *         invoking this method
     */
    void canMove(Identity id) throws IOException;
    
    /**
     * Register a {@link NotifyClient} object to be called when changes
     * occur on a particular node.  Only one listener object can be 
     * registered per {@code nodeId}, and registering a listener a 
     * {@code nodeId} will clear any previously registered listener for
     * that {@code nodeId}.
     *
     * @param client the callback client
     * @param nodeId the node which {@code client} is interested in changes to
     * @throws	IOException if a communication problem occurs while
     *          invoking this method 
     */
    void registerNodeListener(NotifyClient client, long nodeId) 
        throws IOException;
    
    /**
     * Unregister the {@link NotifyClient} object for a node if one
     * is registered.
     *
     * @param nodeId the node
     * @throws	IOException if a communication problem occurs while
     *          invoking this method 
     */
    void unregisterNodeListener(long nodeId) throws IOException;
    
    /**
     * Check the validity of the data store for a particular identity.
     * Used for testing.
     *
     * @param identity the identity
     * @return {@code true} if all is well, {@code false} if there is a problem
     *
     * @throws Exception if any error occurs
     */
    boolean assertValid(Identity identity) throws Exception;
}
