/*
 * Copyright 2007 Sun Microsystems, Inc.  All rights reserved.
 */

package com.sun.sgs.impl.service.nodemap;

import com.sun.sgs.auth.Identity;
import com.sun.sgs.service.NodeMappingService;
import java.io.IOException;
import java.rmi.Remote;

/**
 * Defines the service interface for the global portion of the
 * {@link NodeMappingService}
 */
interface NodeMappingServer extends Remote {

    /**
     * Assigns the identity to a node and indicates that the service class
     * believes this identity is active.
     * If the id has already been assigned to a node, simply
     * return that assignment.
     *
     * @param service the class of the calling service
     * @param id the identity to assign
     *    <b> should this be a set? </b>
     *
     * @throws IllegalStateException if no live nodes exist
     * @throws	IOException if a communication problem occurs while
     *          invoking this method
     */
    void assignNode(Class service, Identity id) 
        throws IOException;
    
    /**
     * The identity's reference count has gone to zero, so it can be
     * removed. 
     *
     * @param id the identity which can be removed from the map
     * @throws	IOException if a communication problem occurs while
     *          invoking this method 
     */
    void canRemove(Identity id) throws IOException;
    
    
    /**
     * Register a {@link NotifyClient} object to be called when changes
     * occur on a particular node.  Only one listener object can be 
     * registered per {@code nodeId}, and registering a listener a 
     * {@code nodeId} will clear any previously registered listener for
     * that {@code nodeId}.
     *
     * @param client the callback client
     * @param nodeId the node which {@code client} is interested in changes to
     * @throws	IOException if a communication problem occurs while
     *          invoking this method 
     */
    void registerNodeListener(NotifyClient client, long nodeId) 
        throws IOException;
    
    /**
     * Unregister the {@link NotifyClient} object for a node.
     *
     * @param nodeId the node
     * @throws	IOException if a communication problem occurs while
     *          invoking this method 
     */
    void unregisterNodeListener(long nodeId) throws IOException;
    
    /**
     * Check the validity of the data store for a particular identity.
     * Used for testing.
     *
     * @param identity the identity
     * @return {@code true} if all is well, {@code false} if there is a problem
     *
     * @throws Exception if any error occurs
     */
    boolean assertValid(Identity identity) throws Exception;
}
