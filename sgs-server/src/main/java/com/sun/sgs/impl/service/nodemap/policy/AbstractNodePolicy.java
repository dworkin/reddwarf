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

import com.sun.sgs.auth.Identity;
import com.sun.sgs.impl.service.nodemap.NoNodesAvailableException;
import com.sun.sgs.impl.service.nodemap.NodeAssignPolicy;
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
