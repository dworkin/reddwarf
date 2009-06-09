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
 * Graph builder interface.  Graph builder objects take task access information
 * and create a graph from it, and can return the graph.
 * <p>
 * The returned graph vertices are identities, and the edges are the 
 * object references the vertices have in common.  The edges can be either
 * weighted or parallel (both are being used for experiments).
 * <p>
 * All graph builders support the following properties:
 * <p>
 * <dl style="margin-left: 1em">
 *
 * <dt>	<i>Property:</i> <code><b>
 *	com.sun.sgs.impl.service.nodemap.affinity.GraphBuilder.snapshot.period
 *	</b></code><br>
 *	<i>Default:</i> {@code 300000} (5 minutes)<br>
 *
 * <dd style="padding-top: .5em">The amount of time, in milliseconds, for
 *      each snapshot of retained data.  Older snapshots are discarded as
 *      time goes on. A longer snapshot period gives us more history, but
 *      also longer compute times to use that history, as more data must
 *      be processed.<p>
 *
 * <dt>	<i>Property:</i> <code><b>
 *	com.sun.sgs.impl.service.nodemap.affinity.GraphBuilder.snapshot.count
 *	</b></code><br>
 *	<i>Default:</i> {@code 1}
 *
 * <dd style="padding-top: .5em">The number of snapshots to retain.  A
 *       larger value means more history will be retained.  Using a smaller
 *       snapshot period with a larger count means more total history will be
 *       retained, with a smaller amount discarded at the start of each
 *       new snapshot.<p>
 * </dl>
 */
public interface GraphBuilder {

    /** The base name for graph builder properties. */
    String PROP_BASE = GraphBuilder.class.getName();

    /** The property controlling time snapshots, in milliseconds. */
    String PERIOD_PROPERTY = PROP_BASE + ".snapshot.period";

    /** The default time snapshot period. */
    long DEFAULT_PERIOD = 1000 * 60 * 5;

    /** The property controlling how many past snapshots to retain. */
    String PERIOD_COUNT_PROPERTY = PROP_BASE + ".snapshot.count";

    /** The default snapshot count. */
    int DEFAULT_PERIOD_COUNT = 1;
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
    Graph<Identity, ? extends WeightedEdge> getAffinityGraph();
}
