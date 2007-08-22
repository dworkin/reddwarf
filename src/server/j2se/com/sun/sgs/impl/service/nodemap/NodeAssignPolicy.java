/*
 * Copyright 2007 Sun Microsystems, Inc.  All rights reserved.
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
     * Choose a node to assign the identity to.  It is assumed that
     * we've already checked to see if the identity is in the map
     * before calling this method.
     *
     * @param id the identity which needs an assignment.
     * @return the chosen node's id
     *
     * @throws NoNodesAvailableException if there are no live nodes to assign to
     */
    long chooseNode(Identity id) throws NoNodesAvailableException;
    
    /**
     * Inform the policy that a node is now available.
     *
     * @param nodeId the started node
     */
    void nodeStarted(long nodeId);
    
    /**
     * Inform the policy that a node has stopped.
     * @param nodeId  the stopped node
     */
    void nodeStopped(long nodeId);
    
    /**
     * Reset the policy, in particular its idea of what nodes have
     * started and stopped.
     */
    void reset();
}
