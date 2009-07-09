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

/**
 *
 * Interface for node assignment.  I expect that we'll replace
 * these every so often, so the actual policy to be used is configurable
 * in the node mapping server.
 * <p>
 * This will probably morph into node assignment plus node balancing policy.
 * 
 */
interface NodeAssignPolicy {
    
    /**
     *  An id representing the server node.
     */
    long SERVER_NODE = -1L;
    
    /**
     * Choose a node to assign the identity to.  It is assumed that
     * we've already checked to see if the identity is in the map
     * before calling this method.
     *
     * @param id the identity which needs an assignment.
     * @param requestingNode the id of the node making the request, or 
     *         {@code SERVER_NODE} if the system is making the request
     * @return the chosen node's id
     *
     * @throws NoNodesAvailableException if there are no live nodes to assign to
     */
    long chooseNode(Identity id, long requestingNode) 
            throws NoNodesAvailableException;

    /**
     * Indicates whether any nodes are available for assignment.
     *
     * @return {@code true} if a node is available for assignment, otherwise
     * {@code false}
     */
    boolean nodesAvailable();

    /**
     * Returns {@code true} of the specified node is available for assignment.
     *
     * @param nodeId a node ID
     * @return {@code true} of the specified node is available for assignment
     */
    boolean isNodeAvailable(long nodeId);

    /**
     * Inform the policy that a node is now available.
     *
     * @param nodeId the available node
     */
    void nodeAvailable(long nodeId);
    
    /**
     * Inform the policy that a node is no longer available.
     *
     * @param nodeId  the unavailable node
     */
    void nodeUnavailable(long nodeId);
    
    /**
     * Reset the policy, in particular its idea of what nodes are available.
     */
    void reset();
}
