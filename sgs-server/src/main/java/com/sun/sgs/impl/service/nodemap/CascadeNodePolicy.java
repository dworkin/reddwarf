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

import java.util.Properties;

/**
 * Node selection policy that assigns to a single node until that node is
 * no longer available. When a node that has been used for assignments becomes
 * unavailable, the next node in the list of available nodes is chosen for
 * future assignments.
 */
public class CascadeNodePolicy extends AbstractNodePolicy {

    // index to walk through available list
    private int nextNode = 0;

    // Current node we are assigning. The initial valuse of -1 indicates that a
    // new node must be selected. (Not to be confused with
    // NodeAssignPolicy.SERVER_NODE)
    private long currentNode = -1L;

    /** 
     * Creates a new instance of the {@code CascadeNodePolicy}.
     * @param props service properties
     * @param server node mapping server which is using this policy
     */
    public CascadeNodePolicy(Properties props, NodeMappingServerImpl server) {
       super();
    }
    
    @Override
    public synchronized long chooseNode(long requestingNode)
            throws NoNodesAvailableException 
    {
        // Be optimistic and assume the current node is available
        // If the current node is no longer available, get a new one, if any
        if (!availableNodes.contains(currentNode)) {
            if (availableNodes.isEmpty()) {

                // We don't have any nodes to assign to.
                // Let the caller figure it out.
                throw new NoNodesAvailableException("no nodes available");
            }
            currentNode =
                    availableNodes.get(nextNode++ % availableNodes.size());
        }
        return currentNode;
    }
}
