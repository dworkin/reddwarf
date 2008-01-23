/*
 * Copyright 2007 Sun Microsystems, Inc.
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
 */

package com.sun.sgs.service;

import com.sun.sgs.app.ClientSession;
import com.sun.sgs.app.Delivery;
import com.sun.sgs.app.ManagedReference;
import java.math.BigInteger;

/**
 * The client session service manages client sessions.
 */
public interface ClientSessionService extends Service {

    /**
     * Registers the specified disconnect listener with this service.
     * This method is non-transactional and
     * should be called outside of a transaction.
     * 
     * TBD: This approach may be replaced with a scheme for registering
     * interest in notification of a ClientSession's managed object removal.
     *
     * @param   listener a listener to notify when a session disconnects
     */
    void registerSessionDisconnectListener(
        ClientSessionDisconnectListener listener);

    /**
     * Sends the specified protocol {@code message} to the specified
     * client {@code session} with the specified {@code delivery}
     * guarantee.  This method must be called within a transaction.
     * The message is delivered when the transaction commits.
     *
     * @param	session	a client session
     * @param	message a complete protocol message
     * @param	delivery a delivery requirement
     *
     * @throws 	TransactionException if there is a problem with the
     *		current transaction
     */
    void sendProtocolMessage(
	ClientSession session, byte[] message, Delivery delivery);

    /**
     * Sends the specified protocol {@code message} to the <i>local</i>
     * client session with the specified {@code sessionRefId}. If the
     * specified client session is not connected to the local node, the
     * message is dropped.  This method is non-transactional, and therefore
     * this message send cannot be aborted.
     *
     * <p> The {@code sessionRefId} is the ID obtained by invoking {@link
     * ManagedReference#getId getId} on a {@link ManagedReference} to the
     * associated {@code ClientSession}.
     *
     * @param	sessionRefId a client session ID, as a {@code BigInteger}
     * @param	message a complete protocol message
     * @param	delivery a delivery requirement
     */
    void sendProtocolMessageNonTransactional(
	BigInteger sessionRefId, byte[] message, Delivery delivery);
}
