/*
 * This material is distributed under the GNU General Public License
 * Version 2. You may review the terms of this license at
 * http://www.gnu.org/licenses/gpl-2.0.html 
 *
 * Copyright (c) 2007-2010, Oracle and/or its affiliates.
 *
 * All rights reserved.
 */

package com.sun.sgs.impl.service.nodemap;

import com.sun.sgs.auth.Identity;

/**
 * Interface for node assignment. The actual policy to be used is configurable
 * in the node mapping server. A class implementing the {@code NodeAssignPolicy}
 * interface must have a public constructor that takes the following argument:
 *
 * <ul>
 * <li>{@link java.util.Properties}</li>
 * </ul>
 * 
 */
public interface NodeAssignPolicy {
    
    /**
     *  An id representing the server node.
     */
    long SERVER_NODE = -1L;
   
    /**
     * Choose a node to assign an identity, or set of identities to.
     *
     * @param requestingNode the id of the node making the request, or 
     *         {@code SERVER_NODE} if the system is making the request
     *
     * @return the chosen node's id
     *
     * @throws NoNodesAvailableException if there are no live nodes to assign to
     */
    long chooseNode(long requestingNode) throws NoNodesAvailableException;

    /**
     * Choose a node to assign an identity to.
     *
     * @param requestingNode the id of the node making the request, or
     *         {@code SERVER_NODE} if the system is making the request
     * @param id the identity which needs an assignment.
     *
     * @return the chosen node's id
     *
     * @throws NoNodesAvailableException if there are no live nodes to assign to
     */
    long chooseNode(long requestingNode, Identity id)
            throws NoNodesAvailableException;
    
    /**
     * Inform the policy that a node is now available.
     *
     * @param nodeId the node ID
     */
    void nodeAvailable(long nodeId);
    
    /**
     * Inform the policy that a node is no longer available.
     *
     * @param nodeId  the node ID
     */
    void nodeUnavailable(long nodeId);
    
    /**
     * Reset the policy, informing it that no nodes are available.
     */
    void reset();
}
