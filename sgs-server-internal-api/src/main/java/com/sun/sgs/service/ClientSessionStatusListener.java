/*
 * This material is distributed under the GNU General Public License
 * Version 2. You may review the terms of this license at
 * http://www.gnu.org/licenses/gpl-2.0.html 
 *
 * Copyright (c) 2007-2010, Oracle and/or its affiliates.
 *
 * All rights reserved.
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
