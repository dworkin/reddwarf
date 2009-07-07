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

package com.sun.sgs.impl.service.nodemap.affinity;

import java.io.IOException;
import java.util.Collection;

/**
 * The label propagation algorithm clients, which can be called by
 * the {@code LPAServer} to coordinate runs of the algorithm.
 * 
 */
public interface LPAClient {

    /**
     * A new run of the algorithm is about to start, so the client nodes
     * should exchange any cross-node information in their graph.  When
     * finished, {@link LPAServer#readyToBegin} should be called.
     *
     * @throws IOException if there is a communication problem
     */
    void exchangeCrossNodeInfo() throws IOException;

    /**
     * Start an iteration of the algorithm.  When finished,
     * {@link LPAServer#finishedIteration} should be called.
     *
     * @param iteration the iteration number
     * @throws IOException if there is a communication problem
     */
    void startIteration(int iteration) throws IOException;

    /**
     * Returns the affinity groups found on this node.
     *
     * @return the affinity groups on this node
     * @throws IOException if there is a communication problem
     */
    Collection<AffinityGroup> affinityGroups() throws IOException;

    /**
     * Remove any cached information about a node.
     * @param nodeId the node to be removed
     * @throws IOException if there is a communication problem
     */
    void removeNode(long nodeId) throws IOException;
}
