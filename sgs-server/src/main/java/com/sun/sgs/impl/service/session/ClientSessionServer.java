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
 */

package com.sun.sgs.impl.service.session;

import com.sun.sgs.app.Delivery;
import java.io.IOException;
import java.rmi.Remote;

/**
 * A remote interface for communicating between peer client session
 * service implementations.
 */
public interface ClientSessionServer extends Remote {

    /**
     * Notifies this server that it should service the event queue of
     * the client session with the specified {@code sessionId}.
     *
     * @param	sessionId a session ID
     * @throws	IOException if a communication problem occurs while
     * 		invoking this method
     */
    void serviceEventQueue(byte[] sessionId) throws IOException;

    /**
     * Forwards the specified {@code message} to be sent to the session
     * with the specified {@code sessionId} according to the delivery
     * guarantee with the corresponding {@code deliveryOrdinal} (the ordinal
     * representing the {@link Delivery} enum).
     *
     * @param	sessionId a session ID
     * @param	message a message
     * @param	deliveryOrdinal a delivery guarantee (the
     *		ordinal representing the {@link Delivery} enum)
     * @throws	IOException if a communication problem occurs while
     * 		invoking this method
     */
    void send(byte[] sessionId, byte[] message, byte deliveryOrdinal)
	throws IOException;
}
