/*
 *
 * Copyright (c) 2007-2010, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in
 *       the documentation and/or other materials provided with the
 *       distribution.
 *     * Neither the name of Sun Microsystems, Inc. nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 */

package com.sun.sgs.service;

import com.sun.sgs.app.TransactionException;
import com.sun.sgs.auth.Identity;
import java.util.Iterator;

/**
 * 
 * The Node Mapping Service has the following responsibilities:
 * <ul>
 * <li> maintain mapping of active identities to nodes, and vice versa
 * <li> decide when the mapping should change (e.g. when a new
 *      client joins the system, a client account is canceled, an AI object
 *      is created, node failure, load balancing, an identity is quiescent)
 * <li> notify interested services of changes in the mapping
 * </ul>
 * 
 * While decisions about changes to the map will be made outside a 
 * transaction, the actual mapping change will occur inside a transaction.
 * Services can assume the mapping has not changed during one of their own
 * transactions.
 * <p>
 * On initial client login, the {@link ClientSessionService} will get a
 * node assignment via {@link #assignNode assignNode}.  If the assigned node
 * is the current node (as determined by the nodeId), the client will proceed 
 * with the login protocol.
 * Otherwise, the client will get a login response indicating a redirection,
 * and the client code should attempt to log in at the redirected node.
 * <p>
 * Mappings will be removed when the system believes an identity has become
 * quiescent, for example when a client logs out and there are no more durable
 * tasks to be run for that client's identity.  In order to determine whether an
 * identity is quiescent, services which manage resources being used on an
 * identity's behalf inform this mapping service when the identity is active
 * or inactive on a node by calling {@link #setStatus setStatus}.   The node 
 * mapping service will use this information for a simple reference counting 
 * garbage collection scheme within the map.   Status is tracked per node, so
 * services should consider only their local node state when calling setStatus.
 *              
 * <p>
 * <b> TODO We might want a transactional method that returns true if
 *     an identity is both assigned to the local node and the local node
 *     is alive, as a convenience to other services. </b>
 * <p>
 * <b> TODO A variation of "assignNode" which adds hints for identities the 
 *     input might want to colocate with will probably be added later. </b>
 */
public interface NodeMappingService extends Service {
        
    /**
     * Assigns the identity to a node and adds the assignment to the map.   
     * If the identity has no node assignment, or the current assignment
     * is not to a live node, a node is selected for it; otherwise, no action 
     * is performed.   
     * <p>
     * Additionally (and atomically), notes that the service considers the 
     * identity to be active, as though 
     * {@link #setStatus setStatus(service, identity, true)} had
     * been called on the assigned node.
     * <p>
     * This method should not be called while in a transaction, as this
     * method call could entail remote communication.
     * <p>
     * The returned node ID might not match the ID of the node returned from an
     * immediate call to {@link #getNode getNode} in a transaction because an
     * identity's node assignment may change at any time.
     *
     * @param service the class of the caller
     * @param identity the identity to assign to a node
     *
     * @return the ID of the node that the identity was assigned to,
     *         or -1 if the assignment failed
     * @throws IllegalStateException if this method is called while in a
     *         transaction
     *
     */
    long assignNode(Class service, Identity identity);
    
    /**
     * Inform the {@code NodeMappingService} that a service instance has 
     * observed a change in status of an identity on this node.  When all
     * services which have previously noted an identity as active set the 
     * status to false, the identity can be removed from the map.  If a node
     * fails or the node mapping service initiates a mapping change (for 
     * load balancing), all status votes for the failing or old node are 
     * implicitly set to inactive.  
     * <p>
     * This method should not be called while in a transaction, as this
     * method call could entail remote communication.
     * <p>
     * @param service the class of the calling service
     * @param identity the identity for which {@code service} has observed a 
     *                 state change
     * @param active {@code true} if the identity is active,
     *               {@code false} if the identity is inactive
     *
     * @throws UnknownIdentityException if the identity is not in the map
     * @throws IllegalStateException if this method is called while in a
     *         transaction
     */
    void setStatus(Class service, Identity identity, 
                   boolean active) throws UnknownIdentityException;
    
    /** 
     * Returns the live node to which the identity is assigned.  
     * <p>
     * This method must be called from within a transaction.
     *
     * @param identity the identity 
     * @return node information for the specified {@code identity}
     *
     * @throws UnknownIdentityException if the identity is not in the map
     * @throws TransactionException if the operation failed because of
     *         a problem with the current transaction
     */
    Node getNode(Identity identity) throws UnknownIdentityException;
    
    /**
     * Returns an {@code Iterator} for the set of identities assigned to a node.
     * The set will be empty if no identities are assigned to the node.
     * <p>
     * The {@code remove} operation of the returned iterator is not supported
     * and will throw {@code UnsupportedOperationException} if invoked.
     * <p>
     * This method should only be called within a transaction, and the 
     * returned iterator should only be used within that transaction.
     * 
     * @param nodeId a node ID
     * @return an iterator for all identities assigned to this node
     *
     * @throws UnknownNodeException if the nodeId is unknown
     * @throws	IllegalArgumentException if the specified {@code nodeId}
     *		is not within the range of valid IDs
     * @throws TransactionException if the operation failed because of
     *         a problem with the current transaction
     */
    Iterator<Identity> getIdentities(long nodeId) throws UnknownNodeException;
     
    /**
     * Adds a {@code listener} to be notified when an identity has been selected
     * to be relocated off the local node.  The listener will be invoked
     * outside of a transaction.
     * <p>
     * If a {@code Service} needs to take actions before an identity is moved,
     * it should register one (or more) {@code listener} objects when 
     * constructed.  The order of callbacks to listener objects is not
     * specified, and the callbacks will occur asynchronously.
     * <p>
     * The identity will be moved, and the mapping modified, when all 
     * {@code listener} objects have completed their work, or after a time 
     * delay in case a {@code listener} does not respond that it is finished.
     * 
     * @param listener a listener to be notified prior to an identity moving
     *                 from the local node
     */
    void addIdentityRelocationListener(IdentityRelocationListener listener);
    
    /** 
     * Adds a {@code listener} to be notified when the identity
     * mapping for this node is modified.   This method is not performed
     * under a transaction; {@code listeners} are held locally on nodes.
     * <p>
     * If a {@code Service} needs to take actions when identities are added to
     * or removed from a local node, it should register one (or more) 
     * {@code listener} objects when it is constructed.  The order of callbacks
     * to listener objects is not specified, and the callbacks will occur
     * asynchronously.
     *
     * @param listener a listener to be notified of local changes to the map
     */
    void addNodeMappingListener(NodeMappingListener listener);
 
}
