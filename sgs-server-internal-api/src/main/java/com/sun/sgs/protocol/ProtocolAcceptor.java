/*
 * This material is distributed under the GNU General Public License
 * Version 2. You may review the terms of this license at
 * http://www.gnu.org/licenses/gpl-2.0.html 
 *
 * Copyright (c) 2007-2010, Oracle and/or its affiliates.
 *
 * All rights reserved.
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
