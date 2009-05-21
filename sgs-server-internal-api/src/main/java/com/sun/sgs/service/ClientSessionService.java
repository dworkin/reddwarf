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

import com.sun.sgs.app.ManagedReference;
import com.sun.sgs.protocol.SessionProtocol;
import java.math.BigInteger;

/**
 * The client session service manages client sessions.
 */
public interface ClientSessionService extends Service {

    /**
     * Registers the specified disconnect listener with this service.
     * This method is non-transactional and should be called outside
     * of a transaction.
     *
     * @param   listener a listener to notify when a session disconnects
     * @throws	IllegalStateException if this method is invoked from a
     *		transactional context
     */
    void registerSessionDisconnectListener(
        ClientSessionDisconnectListener listener);

    /**
     * Returns a protocol for the <i>local</i> client session with the
     * specified {@code sessionRefId} or {@code null} if the specified
     * client session is not connected to the local node.
     *
     * <p> The {@code sessionRefId} is the ID obtained by invoking {@link
     * ManagedReference#getId getId} on a {@link ManagedReference} to the
     * associated {@code ClientSession}.
     *
     * <p>This method is non-transactional and should be called
     * outside of a transaction.
     *
     * @param	sessionRefId a client session ID, as a {@code BigInteger}
     * @return	a protocol, or {@code null}
     * @throws	IllegalStateException if this method is invoked from a
     *		transactional context
     */
    SessionProtocol getSessionProtocol(BigInteger sessionRefId);
}
