/*
 * Copyright 2007 Sun Microsystems, Inc.  All rights reserved.
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
    @Override
    public String toString() {
        return "IdentityMO[id : " + id + ", nodeId : " + nodeId + "]";
    }
    
    /** {@inheritDoc} */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else if (obj.getClass() == this.getClass()) {
            IdentityMO other = (IdentityMO) obj;
            return id.equals(other.id) && nodeId == other.nodeId;
        }
        return false;
    }
    
    /** {@inheritDoc} */
    @Override
    public int hashCode() {
        // Recipe from Effective Java
        if (hashCode == 0) {
            int result = 17;
            result = 37*result + id.hashCode();
            result = 37*result + (int) (nodeId ^ (nodeId >>>32));
            hashCode = result;
        }
        return hashCode;
    }
}
