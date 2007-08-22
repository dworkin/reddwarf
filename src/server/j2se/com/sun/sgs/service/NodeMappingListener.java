/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.service;

import com.sun.sgs.auth.Identity;

/**
 * A listener that can be registered with the {@link NodeMappingService}
 * to be notified of identities being added to or removed from the local node.
 * <p>
 * Invocations of the {@code NodeMappingListener} methods are made outside
 * of a transaction, but modifications to the map will have been performed
 * inside a transaction.
 * 
 * 
 * @see NodeMappingService#addNodeMappingListener(NodeMappingListener)
 */
public interface NodeMappingListener {
    /**
     * Notifies this listener that an identity has been added to this node.
     *
     * @param id the added identity
     * @param oldNode the last node the identity was assigned to, or 
     *                {@code null} if this is the identity's first assignment
     */
    void mappingAdded(Identity id, Node oldNode);
    
    /**
     * Notifies this listener that an identity has been removed from this node.
     *
     * @param id the removed identity
     * @param newNode  the new node assignment for the identity, or {@code null} 
     *                 if the identity has been removed from the system
     */
    void mappingRemoved(Identity id, Node newNode);
}