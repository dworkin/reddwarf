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

/**
 * Vertices for affinity graphs.  All affinity graph vertices use
 * {@code Identities} as their basis.  Subtypes might add additional
 * information.
 * 
 */
public class AffinityVertex {
    /** The identity this vertex represents. */
    private final Identity id;
    /** The cached hashcode for this object. */
    private volatile int hashCode = 0;

    /**
     * Constructs a new vertex for the affinity graph.
     * @param id the identity this vertex represents
     */
    public AffinityVertex(Identity id) {
        this.id = id;
    }

    /**
     * Returns the identity this vertex represents.
     * @return the identity this vertex represents
     */
    public Identity getIdentity() {
        return id;
    }
    
    /** {@inheritDoc} */
    public boolean equals(Object o) {
        if (o ==  this) {
            return true;
        }
        if (!(o instanceof AffinityVertex)) {
            return false;
        }
        AffinityVertex oVertex = (AffinityVertex) o;
        return id.equals(oVertex.getIdentity());
    }

    /** {@inheritDoc} */
    public int hashCode() {
        if (hashCode == 0) {
            hashCode = id.hashCode();
        }
        return hashCode;
    }

    /** {@inheritDoc} */
    public String toString() {
        return "[" + id.getName() + "]";
    }
}
