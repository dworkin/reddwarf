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

import com.sun.sgs.app.ClientSession;
import com.sun.sgs.app.Delivery;
import com.sun.sgs.auth.Identity;

/**
 * A representation of a {@link ClientSession} used to send protocol
 * messages to a session's client.
 */
public interface SgsClientSession extends ClientSession {
    /**
     * Returns the {@link Identity} used to authenticate this session, or
     * {@code null} if the session is not authenticated.
     *
     * @return the {@code Identity} used to authenticate this session, or
     *         {@code null} if the session is not authenticated
     */
    Identity getIdentity();

    /**
     * Sends (with the specified delivery guarantee) the specified
     * protocol message to this session's client.  This method is not
     * transactional, and therefore this message send cannot be
     * aborted.
     *
     * @param message a complete protocol message
     * @param delivery a delivery requirement
     */
    void sendProtocolMessage(byte[] message, Delivery delivery);

    /**
     * Sends (with the specified delivery guarantee) the specified
     * protocol message to this session's client when the current
     * transaction commits.
     *
     * @param message a complete protocol message
     * @param delivery a delivery requirement
     */
    void sendProtocolMessageOnCommit(byte[] message, Delivery delivery);
}
