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

import edu.uci.ics.jung.graph.UndirectedSparseMultigraph;
import edu.uci.ics.jung.graph.util.Pair;
import java.util.HashMap;
import java.util.Set;

/**
 * A version of undirected sparse multigraph which has a copy
 * constructor.
 *
 * @param <V>  the vertex type
 * @param <E>  the edge type
 */
public class CopyableGraph<V, E> extends UndirectedSparseMultigraph<V, E> {

    /** Serialization version. */
    private static final long serialVersionUID = 1L;

    /**
     * Creates an empty copyable graph.
     */
    public CopyableGraph() {
        super();
    }

    /**
     * Creates a copy of {@code other}.
     * @param other the graph to copy
     */
    public CopyableGraph(CopyableGraph<V, E> other) {
        super();
        synchronized (other) {
            vertices = new HashMap<V, Set<E>>(other.vertices);
            edges = new HashMap<E, Pair<V>>(other.edges);
        }
    }
}
