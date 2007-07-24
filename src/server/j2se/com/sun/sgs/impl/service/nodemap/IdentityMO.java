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

    /**
     * 
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
}
