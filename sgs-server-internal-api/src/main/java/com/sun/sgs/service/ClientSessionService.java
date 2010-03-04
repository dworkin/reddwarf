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
