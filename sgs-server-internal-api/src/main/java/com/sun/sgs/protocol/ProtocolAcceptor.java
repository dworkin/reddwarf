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

package com.sun.sgs.protocol;

import java.io.IOException;

/**
 * A service for accepting incoming connections for a given protocol. A
 * {@code ProtocolAcceptor} must have a constructor that takes the following
 * arguments:
 *
 * <ul>
 * <li>{@link java.util.Properties}</li>
 * <li>{@link com.sun.sgs.kernel.ComponentRegistry}</li>
 * <li>{@link com.sun.sgs.service.TransactionProxy}</li>
 * </ul>
 */
public interface ProtocolAcceptor {
    
    /**
     * Returns the descriptor for this protocol. Multiple calls to this
     * method may return the same object.
     * 
     * @return the descriptor for this protocol
     */
    ProtocolDescriptor getDescriptor();
    
    /**
     * Starts accepting connections, and notifies the specified {@code
     * listener} of new connections.
     *
     * <p>When an incoming connection with a given identity is established
     * with this protocol acceptor, the protocol acceptor should invoke the
     * provided listener's {@link ProtocolListener#newLogin newLogin}
     * method with the identity and the {@link SessionProtocol protocol
     * connection}.
     *
     * @param	listener a protocol listener
     * @throws	IOException if an IO problem occurs
     */
    void accept(ProtocolListener listener) throws IOException;

    /**
     * Shuts down any pending accept operation as well as the acceptor
     * itself.
     *
     * @throws	IOException if an IO problem occurs
     */
    void close() throws IOException;
}
