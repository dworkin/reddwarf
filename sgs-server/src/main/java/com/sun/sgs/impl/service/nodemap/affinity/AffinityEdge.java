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

import com.sun.sgs.auth.Identity;

/**
 * Edges in our affinity graph.  Edges are between two vertices, and
 * contain a weight for the number of times both vertices (identities)
 * have accessed the object this edge represents since the edge was
 * first created.
 */
public class AffinityEdge {
    // the object this edge represents
    private final Object objId;
    // the number of times this object has been accessed by the two vertices
    private long weight = 1;
    
    // one of the vertices, used to track which vertex has accessed the object
    private final Identity v1Ident;
    // the number of times v1Ident accessed the object
    private long v1Count;
    // the number of times the other vertex (not v1Ident) accessed the object
    private long v2Count;
    
    /**
     * Create a new edge.
     * 
     * @param id  the object id of the object this edge represents
     * @param vertex one of the vertices, used to track the number of times
     *               each vertex accesses the object
     */
    AffinityEdge(Object id, Identity vertex) {
        super();
        if (id == null) {
            throw new NullPointerException("id must not be null");
        }
        if (vertex == null) {
            throw new NullPointerException("vertex must not be null");
        }
        objId = id;
        v1Ident = vertex;
    }

    /**
     * Returns the object id of the object this edge represents.
     * 
     * @return the object id of the object this edge represents
     */
    public Object getId() {
        return objId;
    }

    /**
     * Returns the weight of this edge, which is the number of times both
     * vertices accessed the object since this edge was created.
     * 
     * @return the weight of this edge
     */
    public long getWeight() {
        return weight;
    }

    /** {@inheritDoc} */
    public String toString() {
        return "E:" + objId + ":" + weight;
    }
    
    /*  Package private methods */
    
    /**
     * Perform bookkeeping for an object access.
     * 
     * We want the edge weight to be updated only when both vertices have
     * accessed the object, so we track their accesses individually.  
     * 
     * @param id  the identity which accessed the object
     */
    synchronized void noteObjectAccess(Identity id) {
        if (id.equals(v1Ident)) {
            v1Count++;
        } else {
            v2Count++;
        }
        if (v1Count > 0 && v2Count > 0) {
            v1Count--;
            v2Count--;
            weight++;
        }
    }
}
