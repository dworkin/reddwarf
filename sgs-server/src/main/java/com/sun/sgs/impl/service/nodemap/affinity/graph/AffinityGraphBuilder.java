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

package com.sun.sgs.impl.service.nodemap.affinity.graph;

import com.sun.sgs.auth.Identity;
import com.sun.sgs.impl.service.nodemap.affinity.LPAAffinityGroupFinder;
import com.sun.sgs.profile.AccessedObjectsDetail;
import edu.uci.ics.jung.graph.UndirectedGraph;

/**
 * Graph builder interface.  Graph builder objects take task access information
 * and create a graph from it, and can return the graph.  Builders are also
 * responsible for instantiating the objects which will consume the graph.
 * <p>
 * The returned graph vertices are identities, and the edges are the
 * object references the vertices have in common.  Edge weights refer to the
 * number of common references of all objects (e.g. if obj1 is used
 * twice by both objects, the weight is 2.  If both objects then use obj2,
 * the weight becomes 3).  Parallel edges are not supported.
 * <p>
 * Graphs are pruned as time goes by, both to ensure that endless resources
 * are not consumed, and to ensure that while the graph contains enough history
 * to be useful, it doesn't contain too much very old history.
 * <p>
 * The graphs contain only application information (no system service
 * information) to limit the size of the graphs.
 * <p>
 * Graph builders are typically instantiated by the node {@link GraphListener}.
 * In order to be instantiated by the {@code GraphListener}, they should
 * implement a constructor taking the arguments
 * {@code (Properties, ComponentRegistry, TransactionProxy)}.
 * The transaction proxy can be {@code null} for testing outside of the
 * Darkstar service framework.
 */
public interface AffinityGraphBuilder { 
    /**
     * Update the graph based on the objects accessed in a task. If the
     * builder is disabled, does nothing.
     *
     * @param owner  the task owner (the object making the accesses)
     * @param detail detailed information about the object accesses, including
     *               a list of the accessed objects
     * @throws UnsupportedOperationException if this builder cannot access
     *      the affinity graph.  Typically, this occurs because the builder
     *      itself is distributed.
     * @throws IllegalStateException if the builder is shut down
     */
    void updateGraph(Identity owner, AccessedObjectsDetail detail);

    /**
     * Returns the current graph, with identities as vertices, and
     * edges representing each object accessed by both identity
     * endpoints. An empty graph will be returned if there is no affinity
     * data collected.
     * <p>
     * The returned graph can not be modified.
     *
     * @return the graph of access information
     * @throws UnsupportedOperationException if this builder cannot access
     *      the affinity graph.  Typically, this occurs because the builder
     *      itself is distributed.
     */
    UndirectedGraph<LabelVertex, WeightedEdge> getAffinityGraph();

    /**
     * Enables this builder.  Enabled builders can be disabled or shutdown.
     * Multiple calls to enable are allowed.
     * @throws IllegalStateException if the builder has been shut down
     */
    void enable();

    /**
     * Disables this builder. Disabled builders can be enabled or shutdown.
     * Multiple calls to disable are allowed.
     * <p>
     * While disabled, no new graph updates are applied, but the graph
     * pruners continue to discard old data.
     * @throws IllegalStateException if the builder has been shut down
     */
    void disable();

    /**
     * Shuts down this builder. Once shut down, a builder cannot be enabled
     * or disabled.  Multiple calls to shutdown are allowed.
     */
    void shutdown();

    /**
     * Gets the graph vertex for the given {@code Identity}.
     * @param id an identity
     * @return the graph vertex for the identity, or {@code null} if
     *          there is no such vertex
     */
    LabelVertex getVertex(Identity id);

    /**
     * Returns the affinity group finder created by this builder,
     * or {@code null} if none was created.  Some algorithms only create
     * the finder on the server node.
     *
     * @return the affinity group finder or {@code null}
     */
    LPAAffinityGroupFinder getAffinityGroupFinder();
}
