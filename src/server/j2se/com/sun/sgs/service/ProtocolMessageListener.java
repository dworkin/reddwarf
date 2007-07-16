/*
 * Copyright 2007 Sun Microsystems, Inc.
 *
 * This file is part of Project Darkstar Server.
 *
 * Project Darkstar Server is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
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

/**
 * Listener for protocol messages and session disconnection events.  A
 * service can register a {@code ProtocolMessageListener} and
 * associated service ID with the {@link ClientSessionService} in order
 * to be notified of protocol messages, received by client sessions,
 * that are destined for that service.  When a session becomes
 * disconnected, all registered {@code ProtocolMessageListener}s are
 * notified that that session is disconnected.
 *
 * @see ClientSessionService#registerProtocolMessageListener
 */
public interface ProtocolMessageListener {

    /**
     * Notifies this listener that the specified protocol
     * message has been received by the specified client session.
     *
     * @param	session a client session
     * @param	message a protocol messge
     */
    void receivedMessage(SgsClientSession session, byte[] message);

    /**
     * Notifies this listener that the specified client session has
     * become disconnected.
     *
     * @param	session a client session
     */
    void disconnected(SgsClientSession session);
}
