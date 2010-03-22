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
 * Sun designates this particular file as subject to the "Classpath"
 * exception as provided by Sun in the LICENSE file that accompanied
 * this code.
 *
 * --
 */

package com.sun.sgs.service;

import com.sun.sgs.app.ManagedReference;
import com.sun.sgs.protocol.SessionProtocol;
import java.math.BigInteger;

/**
 * The client session service manages client sessions.
 */
public interface ClientSessionService extends Service {

    /**
     * Adds the specified status listener to be notified when a local
     * session disconnects or is being prepared to relocate.  This method
     * is non-transactional and should be called outside of a transaction.
     *
     * @param   listener a listener to notify when a session disconnects or
     *		is being prepared to relocate
     * @throws	IllegalStateException if this method is invoked from a
     *		transactional context
     */
    void addSessionStatusListener(ClientSessionStatusListener listener);

    /**
     * Returns a protocol for the <i>local</i> client session with the
     * specified {@code sessionRefId} or {@code null} if the specified
     * client session is not connected to the local node.
     *
     * <p> The {@code sessionRefId} is the ID obtained by invoking {@link
     * ManagedReference#getId getId} on a {@link ManagedReference} to the
     * associated {@code ClientSession}.
     *
     * <p>This method is non-transactional.  However, the method may be
     * invoked inside or outside of a transaction.  The {@code
     * SessionProtocol} returned from this method is used to communicate
     * with a client, so it should only be used outside of a transaction.
     *
     * @param	sessionRefId a client session ID, as a {@code BigInteger}
     * @return	a protocol, or {@code null}
     * @throws	IllegalStateException if this method is invoked from a
     *		transactional context
     */
    SessionProtocol getSessionProtocol(BigInteger sessionRefId);

    /**
     * Returns {@code true} if the session with the specified {@code
     * sessionRefId} is known to be relocating to the local node, and
     * returns {@code false} otherwise.
     *
     * @param	sessionRefId a client session ID, as a {@code BigInteger}
     * @return	{@code true} if the session with the specified {@code
     *		sessionRefId} is known to be relocating to the local node,
     *		and returns {@code false} otherwise 
     */
    boolean isRelocatingToLocalNode(BigInteger sessionRefId);
}
