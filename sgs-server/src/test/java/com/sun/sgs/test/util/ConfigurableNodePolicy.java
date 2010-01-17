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

package com.sun.sgs.test.util;

import com.sun.sgs.impl.service.nodemap.NoNodesAvailableException;
import com.sun.sgs.impl.service.nodemap.NodeAssignPolicy;
import com.sun.sgs.impl.service.nodemap.policy.RoundRobinPolicy;
import java.util.Properties;

/**
 * A configurable (at runtime) node policy.  By default, it always assigns
 * to the local requesting node (i.e., LocalNodePolicy) and round robin
 * assignment is used when the server is making the request due to node
 * failure. <p>
 *
 * Use the {@link #setRoundRobinPolicy} and {@link #setLocalNodePolicy}
 * methods to modify the policy.
 */
public class ConfigurableNodePolicy extends RoundRobinPolicy {

    private static volatile boolean isRoundRobin = false;
    
    /** 
     * Creates a new instance with the default local node policy.
     * Constructing an instance of this class resets the default policy for
     * all instances of this class.
     *
     * @param props service properties
     */
    public ConfigurableNodePolicy(Properties props) {
        super(props);
	isRoundRobin = false;
    }

    /**
     * Sets the policy to round robin assignment.
     */
    public static void setRoundRobinPolicy() {
	isRoundRobin = true;
    }

    /**
     * Sets the policy to local node assignment.
     */
    public static void setLocalNodePolicy() {
	isRoundRobin = false;
    }
    
    /** 
     * {@inheritDoc} 
     * <p>
     * This implementation assigns to the node depending on how this
     * instance is configured. By default, the policy is the "local node"
     * policy. 
     */
    public long chooseNode(long requestingNode)
        throws NoNodesAvailableException 
    {
        if (requestingNode == NodeAssignPolicy.SERVER_NODE || isRoundRobin) {

            // A node has failed, we need to pick a new one from the live nodes.
            return super.chooseNode(requestingNode);
        }
        return requestingNode;
    }
}
