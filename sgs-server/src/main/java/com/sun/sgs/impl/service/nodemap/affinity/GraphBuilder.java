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

import com.sun.sgs.auth.Identity;
import com.sun.sgs.profile.AccessedObjectsDetail;
import edu.uci.ics.jung.graph.Graph;

/**
 *
 * Graph builder interface.  Graph builder objects take task access information
 * and create a graph from it, and can return the graph.
 * 
 * The returned graph vertices are identities, and the edges are the 
 * object references the vertices have in common.  The edges can be either
 * weighted or parallel (both are being used for experiments).
 */
public interface GraphBuilder {

    // the base name for properties
    static final String PROP_BASE = GraphBuilder.class.getName();

    // property controlling our time snapshots, in milliseconds
    static final String PERIOD_PROPERTY = PROP_BASE + ".snapshot.period";

    // default:  5 minutes
    // a longer snapshot gives us more history but also potentially bigger
    // graphs
    static final long DEFAULT_PERIOD = 1000 * 60 * 5;

    // property controlling how many past time periods we should retain
    static final String PERIOD_COUNT_PROPERTY = PROP_BASE + ".snapshot.count";

    // default:  1
    // a greater number holds more data, perhaps increasing value of data?
    // a smaller number purges older data quickly, making graphs smaller
    static final int DEFAULT_PERIOD_COUNT = 1;
    /**
     * Update the graph based on the objects accessed in a task.
     *
     * @param owner  the task owner (the object making the accesses)
     * @param detail detailed information about the object accesses, including
     * a list of the accessed objects
     */
    void updateGraph(Identity owner, AccessedObjectsDetail detail);

    /**
     * Get the task which prunes the graph.
     * 
     * @return the runnable which prunes the graph.
     */
    Runnable getPruneTask();

    /**
     * Returns the current graph, with identities as vertices, and
     * edges representing each object accessed by both identity
     * endpoints.
     *
     * @return the graph of access information
     */
    Graph<Identity, WeightedEdge> getAffinityGraph();
}
