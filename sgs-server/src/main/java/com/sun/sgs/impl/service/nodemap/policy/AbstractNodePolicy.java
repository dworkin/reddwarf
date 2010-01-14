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

package com.sun.sgs.impl.service.nodemap.policy;

import com.sun.sgs.auth.Identity;
import com.sun.sgs.impl.service.nodemap.NoNodesAvailableException;
import com.sun.sgs.impl.service.nodemap.NodeAssignPolicy;
import com.sun.sgs.service.Node.Health;
import java.util.ArrayList;
import java.util.List;

/**
 * Abstract node policy class. This class manages the list of nodes that
 * are available for assignment. Subclasses must implement the logic for
 * making the assignments.
 * 
 * @see NodeAssignPolicy#chooseNode(long)
 * @see NodeAssignPolicy#chooseNode(long, com.sun.sgs.auth.Identity)
 */
public abstract class AbstractNodePolicy implements NodeAssignPolicy {

    /**
     * The list of available nodes. Access to this list must be synchronized.
     */
    protected final List<Long> availableNodes = new ArrayList<Long>();

    /** 
     * Creates a new instance of the AbstractNodePolicy.
     */
    protected AbstractNodePolicy() {
    }

    /**
     * {@inheritDoc}
     * This implementation simply calls {@link #chooseNode(long)}, ignoring
     * the {@code id} parameter.
     */
    public long chooseNode(long requestingNode, Identity id)
        throws NoNodesAvailableException
    {
        return chooseNode(requestingNode);
    }

    /**
     * {@inheritDoc}
     * This implementation only makes available nodes whose health
     * is {@code GREEN}.
     * Subclass should override this method to implement other policies
     * for which nodes are made available for assignment.
     */
    public synchronized void nodeUpdate(long nodeId, Health health) {
        if (nodeId <= 0) {
            throw new IllegalArgumentException("nodeID can not be <= 0");
        }
        if (health == null) {
            throw new NullPointerException("health can not be null");
        }
        if (health.equals(Health.GREEN)) {
            if (!availableNodes.contains(nodeId)) {
                availableNodes.add(nodeId);
            }
        } else {
            nodeUnavailable(nodeId);
        }
    }

    /** {@inheritDoc} */
    public synchronized void nodeUnavailable(long nodeId) {
        if (nodeId <= 0) {
            throw new IllegalArgumentException("nodeID can not be <= 0");
        }
        availableNodes.remove(nodeId);
    }

    /** {@inheritDoc} */
    public synchronized boolean isNodeAvailable(long nodeId) {
        return availableNodes.contains(nodeId);
    }

    /** {@inheritDoc} */
    public synchronized boolean nodesAvailable() {
        return !availableNodes.isEmpty();
    }

    /** {@inheritDoc} */
    public synchronized void reset() {
        availableNodes.clear();
    }
}
