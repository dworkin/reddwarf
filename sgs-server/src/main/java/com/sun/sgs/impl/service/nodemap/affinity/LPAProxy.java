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
import java.util.Map;
import java.util.Set;

/**
 * A proxy for other nodes running the label propagation algorithm, to deal
 * with graph edges that run between nodes.
 * 
 */
public interface LPAProxy {
    /**
     * Indicates that the given node probably contains edges to the graph on
     * the local node.  This informs the local node where non-local neighbors
     * might reside. If there are no endpoints for the edges on this node,
     * nothing is done.
     *
     * @param objIds the collection of objects, representing edges, that
     *               probably have endpoints to vertices on this node
     * @param nodeId the node with vertices attached to the edges
     * @throws IOException if there is a communication problem
     */
    void crossNodeEdges(Collection<Object> objIds, long nodeId)
            throws IOException;

    /**
     * Get the labels for the given edges.  If no vertex on this node is
     * a possible endpoint for an edge, ignore that edge.
     *
     * @param objIds the collection of objects, representing edges, that
     *               we want neighbor node information for
     * @return a map of Objects (one for each element of {@code objIds}) to
     *               neighbor labels
     * @throws IOException if there is a communication problem
     */
    Map<Object, Set<Integer>> getRemoteLabels(Collection<Object> objIds)
            throws IOException;
}
