/*
 * Copyright 2009 Sun Microsystems, Inc.
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

/**
 * Weighted edges in our affinity graph.  Edges are between two vertices, and
 * contain a weight for the number of times both vertices (identities)
 * have accessed the object this edge represents since the edge was
 * first created.
 */
public class AffinityEdge extends WeightedEdge {
    // the object this edge represents
    private final Object objId;
    
    /**
     * Create a new edge with initial weight {@code 1}.
     * 
     * @param id  the object id of the object this edge represents
     */
    AffinityEdge(Object id) {
        this(id, 1);
    }
    
    /**
     * Create a new edge with the given initial weight.
     * 
     * @param id  the object id of the object this edge represents
     * @param value the initial weight value
     */
    AffinityEdge(Object id, long value) {
        super(value);
        if (id == null) {
            throw new NullPointerException("id must not be null");
        }
        objId = id;
    }

    /**
     * Returns the object id of the object this edge represents.
     * 
     * @return the object id of the object this edge represents
     */
    public Object getId() {
        return objId;
    }

    /** {@inheritDoc} */
    public String toString() {
        return "E:" + objId + ":" + getWeight();
    }
}
