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
 * --
 */

package com.sun.sgs.impl.service.nodemap.policy;

import com.sun.sgs.impl.service.nodemap.NoNodesAvailableException;
import com.sun.sgs.impl.service.nodemap.NodeAssignPolicy;
import java.util.Properties;

/**
 *  The simpliest node policy possible: always assign to the local requesting
 *  node.  Round robin assignment is used when the server is making the 
 *  request due to node failure.
 */
public class LocalNodePolicy extends RoundRobinPolicy {
    
    /** 
     * Creates a new instance of the LocalNodePolicy, which always assigns
     * an identity to the node which requested an assignment be made.
     * @param props service properties
     */
    public LocalNodePolicy(Properties props) {
        super(props);
    }
    
    /** 
     * {@inheritDoc} 
     * <p>
     * This implementation simply assigns to the node making the request.
     * Round robin assignment is used if the server is making the request
     * (due to reassigning identities from a failed node).
     */
    public long chooseNode(long requestingNode)
        throws NoNodesAvailableException 
    {
        if (requestingNode == NodeAssignPolicy.SERVER_NODE) {

            // A node has failed, we need to pick a new one from the live nodes.
            return super.chooseNode(requestingNode);
        }
        return requestingNode;
    }
}
