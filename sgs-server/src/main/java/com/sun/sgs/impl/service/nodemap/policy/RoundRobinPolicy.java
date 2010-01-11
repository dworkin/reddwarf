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
