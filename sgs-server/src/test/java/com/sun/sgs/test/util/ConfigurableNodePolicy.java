/*
 * This material is distributed under the GNU General Public License
 * Version 2. You may review the terms of this license at
 * http://www.gnu.org/licenses/gpl-2.0.html 
 *
 * Copyright (c) 2007-2010, Oracle and/or its affiliates.
 *
 * All rights reserved.
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
