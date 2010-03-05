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

import java.math.BigInteger;

/**
 * A listener that services may register with the {@link ClientSessionService}
 * to receive notification of local client session status changes such as
 * disconnection or relocation.
 * 
 * @see ClientSessionService#addSessionStatusListener
 */
public interface ClientSessionStatusListener {

    /**
     * Notifies this listener that the session with the given {@code
     * sessionRefId} has disconnected so that any relevant cached or
     * persistent data associated with the client session can be cleaned
     * up.
     * 
     * @param	sessionRefId the client session ID
     * @param	isRelocating if {@code true}, the disconnection is due to
     *	        the client session relocating to another node
     */
    void disconnected(BigInteger sessionRefId, boolean isRelocating);

    /**
     * Notifies this listener that the session with the given
     * {@code sessionRefId} is relocating to the node with the
     * specified {@code nodeId}.  This notification gives the
     * service associated with this listener an opportunity to
     * prepare for the relocation.  The associated service should
     * invoke the {@link SimpleCompletionHandler#completed
     * completed} method of the specified {@code handler} when it
     * has completed preparing for the relocation.
     * 
     * @param sessionRefId the client session ID
     * @param newNodeId the ID of the new node
     * @param handler a handler to notify when preparation is complete
     */
    void prepareToRelocate(BigInteger sessionRefId, long newNodeId,
			   SimpleCompletionHandler handler);


    /**
     * Notifies this listener that the session with the given
     * {@code sessionRefId} has completed relocation to the local
     * node, so that this listener can communicate with the
     * relocated client session (e.g. to deliver enqueued
     * requests) if needed.
     *
     * @param sessionRefId the client session ID
     */
    void relocated(BigInteger sessionRefId);
}
