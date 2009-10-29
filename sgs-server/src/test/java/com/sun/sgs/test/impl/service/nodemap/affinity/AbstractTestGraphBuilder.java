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

package com.sun.sgs.test.impl.service.nodemap.affinity;

import com.sun.sgs.auth.Identity;
import com.sun.sgs.impl.service.nodemap.affinity.AffinityGroupFinder;
import com.sun.sgs.impl.service.nodemap.affinity.graph.AffinityGraphBuilder;
import com.sun.sgs.impl.service.nodemap.affinity.graph.LabelVertex;
import com.sun.sgs.impl.service.nodemap.affinity.graph.WeightedEdge;
import com.sun.sgs.profile.AccessedObjectsDetail;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import java.util.HashMap;

/**
 * An graph builder class, used for testing, which accepts a
 * graph as input and implements {@link AffinityGraphBuilder}.
 */
public class AbstractTestGraphBuilder implements AffinityGraphBuilder {
    private final UndirectedSparseGraph<LabelVertex, WeightedEdge> graph;

    private final HashMap<Identity, LabelVertex> identMap =
                new HashMap<Identity, LabelVertex>();

    public AbstractTestGraphBuilder(
                    UndirectedSparseGraph<LabelVertex, WeightedEdge> graph)
    {
        this.graph = graph;
        for (LabelVertex v : graph.getVertices()) {
            identMap.put(v.getIdentity(), v);
        }
    }

    /** {@inheritDoc} */
    public UndirectedSparseGraph<LabelVertex, WeightedEdge> getAffinityGraph() {
        return graph;
    }

    /** {@inheritDoc} */
    public LabelVertex getVertex(Identity id) {
        return identMap.get(id);
    }

    /** {@inheritDoc} */
    public Runnable getPruneTask() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    /** {@inheritDoc} */
    public void updateGraph(Identity owner, AccessedObjectsDetail detail) {
        return;
    }

    /** {@inheritDoc} */
    public void shutdown() {
        // do nothing
    }

    /** {@inheritDoc} */
    public AffinityGroupFinder getAffinityGroupFinder() {
        return null;
    }
}
