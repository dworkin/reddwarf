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
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A very simple round robin assignment policy.
 */
public class RoundRobinPolicy extends AbstractNodePolicy {

    private final AtomicInteger nextNode = new AtomicInteger();
    
    /**
     * Creates a new instance of RoundRobinPolicy.
     *
     * @param props service properties
     */
    public RoundRobinPolicy(Properties props) {
        super();
    }
    
    /** {@inheritDoc} */
    public synchronized long chooseNode(long requestingNode) 
        throws NoNodesAvailableException 
    {
        if (availableNodes.size() < 1) {
            // We don't have any live nodes to assign to.
            // Let the caller figure it out.
            throw new NoNodesAvailableException("no live nodes available");
        }  
        
        return availableNodes.get(
                nextNode.getAndIncrement() % availableNodes.size());
    }
}
