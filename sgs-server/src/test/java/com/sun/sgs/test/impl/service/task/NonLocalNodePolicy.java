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

package com.sun.sgs.test.impl.service.task;

import com.sun.sgs.auth.Identity;
import com.sun.sgs.impl.service.nodemap.NoNodesAvailableException;
import com.sun.sgs.impl.service.nodemap.NodeAssignPolicy;
import com.sun.sgs.impl.service.nodemap.NodeMappingServerImpl;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * This is a {@link NodeAssignPolicy} used for testing purposes that always
 * chooses a node that is not the requesting node when assigning an
 * {@code Identity} to a node.  The exception is when there is only one node
 * available in which case there is no choice but to choose the single node.
 */
public class NonLocalNodePolicy implements NodeAssignPolicy {

    private final List<Long> liveNodes = new ArrayList<Long>();

    /**
     * Creates a new instance of the NonLocalNodePolicy, which always assigns
     * an identity to a node other than the requesting node.
     * @param props service properties
     * @param server node mapping server which is using this policy
     */
    public NonLocalNodePolicy(Properties props, NodeMappingServerImpl server) {

    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation simply assigns to any node not making the request.
     */
    public long chooseNode(Identity id, long requestingNode)
            throws NoNodesAvailableException
    {
        for (Long nodeId : liveNodes) {
            if (nodeId != requestingNode) {
                return nodeId;
            }
        }

        // if there are no other nodes, just return the requesting node
        return requestingNode;
    }

    /** {@inheritDoc} */
    public synchronized void nodeStarted(long nodeId) {
        liveNodes.add(nodeId);
    }

    /** {@inheritDoc} */
    public synchronized void nodeStopped(long nodeId) {
        liveNodes.remove(nodeId);
    }

    /** {@inheritDoc} */
    public synchronized void reset() {
        liveNodes.clear();
    }

}
