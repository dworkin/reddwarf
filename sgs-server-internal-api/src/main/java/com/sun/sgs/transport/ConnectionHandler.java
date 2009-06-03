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

package com.sun.sgs.transport;

import com.sun.sgs.nio.channels.AsynchronousByteChannel;

/**
 * Interface implemented by objects implementing a connection handler. A
 * connection handler is passed to {@link Transport#accept Transport.accept}.
 * When a new connection is received by the transport,
 * {@link #newConnection newConnection} is invoked with the new I/O channel
 * for that connection.
 */
public interface ConnectionHandler {
    
    /**
     * Notify the handler that a new connection has been initiated. If an
     * exception is thrown the connection will be refused. The implementation
     * of this method should return in a timely manner, starting a separate
     * thread if necessary to perform any IO on the {@code channel}.
     * 
     * @param channel on which the new connection can communicate.
     * @throws Exception if the handler rejects the connection.
     */
    void newConnection(AsynchronousByteChannel channel) throws Exception;
    
    /**
     * Notify the handler that the transport encountered an unrecoverable
     * error and has shutdown.
     */
    void shutdown();
}
