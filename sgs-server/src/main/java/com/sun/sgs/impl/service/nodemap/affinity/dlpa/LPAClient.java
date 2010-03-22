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

package com.sun.sgs.impl.service.nodemap.affinity.dlpa;

import com.sun.sgs.impl.service.nodemap.affinity.AffinityGroup;
import java.io.IOException;
import java.rmi.Remote;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The label propagation algorithm clients, which can be called by
 * the {@code LPAServer} to coordinate runs of the algorithm.
 */
public interface LPAClient extends Remote {
    /**
     * A new run of the algorithm is about to start, so the client nodes
     * should do whatever is necessary to set up for that run.  When
     * prepared, {@link LPAServer#readyToBegin} should be called.
     * Called by the LPAServer.
     *
     * @param runNumber the number of this algorithm run
     * @throws IOException if there is a communication problem
     */
    void prepareAlgorithm(long runNumber) throws IOException;

    /**
     * Start an iteration of the algorithm.  When finished,
     * {@link LPAServer#finishedIteration} should be called.
     * Called by the LPAServer.
     *
     * @param iteration the iteration number
     * @throws IOException if there is a communication problem
     */
    void startIteration(int iteration) throws IOException;

    /**
     * Returns the affinity groups found on this node.  An empty set is returned
     * if there are no affinity groups on this node.
     * Called by the LPAServer.  The LPAServer must call this with
     * {@code done} set to {@code true} if it intends to start another algorithm
     * run at any time in the future, even if the current run fails.
     *
     * @param runNumber the run number provided to the last
     *                  {@link #prepareAlgorithm} call
     * @param done {@code true} if all iterations are done, allowing cleanup
     * @return the affinity groups on this node
     * @throws IllegalArgumentException if runNumber does not match the last
     *         call to {@code prepareAlgorithm}
     * @throws IOException if there is a communication problem
     */
    Set<AffinityGroup> getAffinityGroups(long runNumber, boolean done)
            throws IOException;

    /**
     * Indicates that the given node probably contains edges to the graph on
     * the local node.  This informs the local node where non-local neighbors
     * might reside. If there are no endpoints for the edges on this node,
     * nothing is done.
     * Called by other LPAClients.
     *
     * @param objIds the collection of objects, representing edges, that
     *               probably have endpoints to vertices on this node
     * @param nodeId the node with vertices attached to the edges
     * @throws IOException if there is a communication problem
     */
    void notifyCrossNodeEdges(Collection<Object> objIds, long nodeId)
            throws IOException;

    /**
     * Get the labels for all vertices in our affinity graph for identities
     * that have used the given objects. If no such vertices exist, nothing
     * is done and an empty map is returned.
     * Called by other LPAClients.
     * 
     * @param objIds the collection of objects, representing potential graph
     *               edges, that we want neighbor node information for
     * @return a map of Objects (one for each element of {@code objIds}) to
     *               neighbor labels, with a count of each use
     * @throws IOException if there is a communication problem
     */
    Map<Object, Map<Integer, List<Long>>> getRemoteLabels(
        Collection<Object> objIds)
            throws IOException;

    /**
     * Indicates that the affinity group finding system is shutting down,
     * and all local resources should be cleaned up.
     * 
     * @throws IOException if there is a communication problem
     */
    void shutdown() throws IOException;

    /**
     * Indicates that the affinity group finding system is disabled,
     * so no new data should be collected (old can be disgarded).
     *
     * @throws IOException if there is a communication problem
     */
    void disable() throws IOException;

    /**
     * Indicates that the affinity group finding system is enabled,
     * so new data should be collected again.
     *
     * @throws IOException if there is a communication problem
     */
    void enable() throws IOException;
}
