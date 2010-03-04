/*
 * This material is distributed under the GNU General Public License
 * Version 2. You may review the terms of this license at
 * http://www.gnu.org/licenses/gpl-2.0.html 
 *
 * Copyright (c) 2007-2010, Oracle and/or its affiliates.
 *
 * All rights reserved.
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
