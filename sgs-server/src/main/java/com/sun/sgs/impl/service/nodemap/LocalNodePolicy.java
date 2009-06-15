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
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

/**
 *  The simpliest node policy possible: always assign to the local requesting
 *  node.  Round robin assignment is used when the server is making the 
 *  request due to node failure.
 */
public class LocalNodePolicy extends AbstractNodePolicy {
    
    private final AtomicInteger nextNode = new AtomicInteger();

    /** 
     * Creates a new instance of the LocalNodePolicy, which always assigns
     * an identity to the node which requested an assignment be made.
     * @param props service properties
     * @param server node mapping server which is using this policy
     */
    public LocalNodePolicy(Properties props, NodeMappingServerImpl server) {
       super();
    }
    
    /** 
     * {@inheritDoc} 
     * <p>
     * This implementation simply assigns to the node making the request.
     * Round robin assignment is used if the server is making the request
     * (due to reassigning identities from a failed node).
     */
    public long chooseNode(Identity id, long requestingNode) 
            throws NoNodesAvailableException 
    {
        if (requestingNode == NodeAssignPolicy.SERVER_NODE) {
            // A node has failed, we need to pick a new one from the live nodes.
            synchronized (availableNodes) {
                if (availableNodes.size() < 1) {
                    // We don't have any live nodes to assign to.
                    // Let the caller figure it out.
                    throw 
                      new NoNodesAvailableException("no live nodes available");
                }  
                return availableNodes.get(
                        nextNode.getAndIncrement() % availableNodes.size());
            }
        }
        return requestingNode;
    }
}
