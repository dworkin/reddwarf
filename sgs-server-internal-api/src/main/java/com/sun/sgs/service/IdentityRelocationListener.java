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

import com.sun.sgs.auth.Identity;

/**
 * A service can register an {@code IdentityRelocationListener} to be notified
 * when an identity is about to be relocated from the local node if it needs
 * to perform any work in advance of the identity relocation.
 *
 * @see 
 * NodeMappingService#addIdentityRelocationListener(IdentityRelocationListener)
 */
public interface IdentityRelocationListener {

    /**
     * Notifies this listener that the specified {@code id} has been
     * selected for relocation to {@code newNodeId} and that this listener
     * needs to prepare for that move. This method is invoked outside of 
     * a transaction.
     * <p>
     * When the listener has completed preparations for the identity relocation,
     * the {@link SimpleCompletionHandler#completed completed} method of the
     * specified {@code handler} must be invoked.  After all listeners have
     * noted they are ready for the move, the identity will be relocated and
     * the new node will be returned from
     * {@link NodeMappingService#getNode(Identity)} calls.
     * <p>
     * The implementation of this method should be idempotent
     * because it may be invoked multiple times.  If it is invoked multiple
     * times, the {@link SimpleCompletionHandler#completed completed} method
     * must be called for each {@code handler} provided.
     * 
     * @param id        the identity which has been selected for relocation
     * @param newNodeId the identity of the node that {@code id} will move to
     * @param handler   a handler to notify when relocation preparations are
     *                  complete
     */
    void prepareToRelocate(Identity id, long newNodeId, 
                           SimpleCompletionHandler handler);
}
