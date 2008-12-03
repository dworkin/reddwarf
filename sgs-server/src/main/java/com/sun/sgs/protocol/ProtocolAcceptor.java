/*
 * Copyright 2007-2008 Sun Microsystems, Inc.
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

package com.sun.sgs.protocol;

import com.sun.sgs.service.Service;

/**
 * A service for accepting incoming connections for a given protocol. A
 * {@code ProtocolAcceptor} must have a constructor that takes the following
 * arguments:
 *
 * <ul>
 * <li>{@link java.util.Properties}</li>
 * <li>{@link com.sun.sgs.kernel.ComponentRegistry}</li>
 * <li>{@link com.sun.sgs.service.TransactionProxy}</li>
 * <li>{@link com.sun.sgs.protocol.ProtocolListener}</li>
 * </ul>
 *
 * When an incoming connection with a given identity is established with
 * this protocol acceptor, the protocol acceptor should invoke the provided
 * listener's {@link ProtocolListener#newConnection
 * ProtocolListener.newConnection} method passing the protocol connection
 * and associated identity.
 */
public interface ProtocolAcceptor extends Service {

    /**
     * {@inheritDoc}
     *
     * This method begins accepting connections.
     */
    void ready() throws Exception;

    /**
     * {@inheritDoc}
     *
     * This method shuts down any pending accept operation as well as the
     * acceptor itself.
     */
    boolean shutdown();
}
