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

import java.util.ArrayList;
import java.util.List;

/**
 * Abstract node policy class. This class manages the list of nodes that
 * are available for assignment. Subclasses must implement the logic for
 * making the assignments.
 * @see NodeAssignPolicy#chooseNode(com.sun.sgs.auth.Identity, long)
 */
public abstract class AbstractNodePolicy implements NodeAssignPolicy {

    /**
     * The list of available nodes.
     */
    protected final List<Long> availableNodes = new ArrayList<Long>();

    /** 
     * Creates a new instance of the AbstractNodePolicy.
     */
    public AbstractNodePolicy() {          
    }

    /** {@inheritDoc} */
    public synchronized boolean nodesAvailable() {
        return !availableNodes.isEmpty();
    }
    
    /** {@inheritDoc} */
    public synchronized void nodeAvailable(long nodeId) {
        if (!availableNodes.contains(nodeId)) {
            availableNodes.add(nodeId);
        }
    }

    /** {@inheritDoc} */
    public synchronized void nodeUnavailable(long nodeId) {
        availableNodes.remove(nodeId);
    }
    
    /** {@inheritDoc} */
    public synchronized void reset() {
        availableNodes.clear();
    }
}
