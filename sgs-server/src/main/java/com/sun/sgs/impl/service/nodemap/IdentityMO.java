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

package com.sun.sgs.impl.service.nodemap;

import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.auth.Identity;
import java.io.Serializable;

/**
 * The {@link NodeMappingServiceImpl} representation of a 
 * id->node mapping.  This class is immutable.
 *
 */
class IdentityMO implements ManagedObject, Serializable {
    /** Serialization version. */
    private static final long serialVersionUID = 1L;
    
    /** The identity */
    private final Identity id;
    /** The node the identity is mapped to */
    private final long nodeId;
    
    /* The hashcode, lazily calculated (this is an immutable object). */
    private volatile int hashCode = 0;

    /**
     * Returns a new IdentityMO instance.
     *
     * @param id  the identity
     * @param nodeId the node the identity is mapped to
     */
    IdentityMO(Identity id, long nodeId) {
        this.id = id;
        this.nodeId = nodeId;
    }
    
    /**
     * Returns the identity.
     * @return the identity
     */
    Identity getIdentity() {
        return id;
    }
    
    /**
     * Returns the node.
     * @return the node
     */
    long getNodeId() {
        return nodeId;
    }
    
    /** {@inheritDoc} */
    @Override public String toString() {
        return "IdentityMO[id:" + id + ", nodeId:" + nodeId + "]";
    }
    
    /** {@inheritDoc} */
    @Override public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else if (obj == null) {
            return false;
        } else if (obj.getClass() == this.getClass()) {
            IdentityMO other = (IdentityMO) obj;
            return id.equals(other.id) && nodeId == other.nodeId;
        }
        return false;
    }
    
    /** {@inheritDoc} */
    @Override public int hashCode() {
        // Recipe from Effective Java
        if (hashCode == 0) {
            int result = 17;
            result = 37 * result + id.hashCode();
            result = 37 * result + (int) (nodeId ^ (nodeId >>> 32));
            hashCode = result;
        }
        return hashCode;
    }
}
