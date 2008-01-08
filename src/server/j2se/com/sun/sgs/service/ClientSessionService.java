/*
 * Copyright 2008 Sun Microsystems, Inc.
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

/**
 * The client session service manages client sessions.
 */
public interface ClientSessionService extends Service {

    /**
     * Registers the specified protocol message listener for the
     * specified service ID.
     *
     * <p>When a client session receives a protocol message with the
     * specified service ID, the specified listener's {@link
     * ProtocolMessageListener#receivedMessage receivedMessage} method is
     * invoked with the {@link SgsClientSession client session} and
     * the complete protocol message.
     *
     * <p>The reserved service IDs are 0-127.  The current ones in use are:
     * <ul>
     * <li> <code>0x01</code>, application service
     * <li> <code>0x02</code>, channel service
     * </ul>
     *
     * @param serviceId a service ID
     * @param listener a protocol message listener
     */
    void registerProtocolMessageListener(
	byte serviceId, ProtocolMessageListener listener);

    /**
     * Returns the client session corresponding to the specified
     * session ID, or <code>null</code> if there is no existing client
     * session for the specified ID.
     *
     * @param sessionId a session ID
     * @return a client session, or <code>null</code>
     */
    SgsClientSession getClientSession(byte[] sessionId);
}
