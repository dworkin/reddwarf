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
 *
 * Sun designates this particular file as subject to the "Classpath"
 * exception as provided by Sun in the LICENSE file that accompanied
 * this code.
 */

package com.sun.sgs.service;

import java.math.BigInteger;

import com.sun.sgs.app.ClientSession;

/**
 * A listener that services may register with the {@link ClientSessionService}
 * to receive notification of local client session status changes such as
 * disconnection or relocation.
 * 
 * @see ClientSessionService#addSessionStatusListener
 */
public interface ClientSessionStatusListener {

    /**
     * Notifies this listener that the session with the given
     * {@code sessionRefId} has disconnected.
     * 
     * @param sessionRefId the client session ID for the disconnected client
     */
    void disconnected(BigInteger sessionRefId);

    /**
     * Notifies this listener that the session with the given
     * {@code sessionRefId} is relocating to the node with the
     * specified {@code nodeId}.  This notification gives the
     * service associated with this listener an opportunity to
     * prepare for the relocation.  The associated service should
     * invoke the {@link SimpleCompletionHandler@completed
     * completed} method of the specified {@code handler} when it
     * has completed preparing for the relocation.
     * 
     * @param sessionRefId the client session ID
     * @param newNode the ID of the new node
     * @param handler a handler to notify when preparation is complete
     */
    void prepareToRelocate(BigInteger sessionRefId, long newNode,
			   SimpleCompletionHandler handler);
}
