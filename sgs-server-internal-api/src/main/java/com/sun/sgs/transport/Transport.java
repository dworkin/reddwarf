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

package com.sun.sgs.transport;

import com.sun.sgs.app.Delivery;
import java.io.IOException;

/**
 * I/O transport. A transport object handles incoming connection requests for
 * a specific transport type. A {@code Transport} must have a public
 * constructor that takes the following argument:
 *
 * <ul>
 * <li>{@link java.util.Properties}</li>
 * </ul>
 */
public interface Transport {
    
    /**
     * Returns the descriptor for this transport. Multiple calls to this
     * method may return the same object.
     * 
     * @return the descriptor for this transport
     */
    TransportDescriptor getDescriptor();
    
    /**
     * Returns the delivery guarantee for the transport.
     * @return the delivery guarantee for the transport
     */
    Delivery getDelivery();
    
    /**
     * Start accepting connections. The transport will invoke the specified
     * {@code handler}'s {@link ConnectionHandler#newConnection newConnection}
     * method when a connection is received. Once {@code accept} has
     * been called, subsequent invocations will throw an
     * {@code IllegalStateException}. If
     * {@link #shutdown} has been called this method will throw an
     * {@code IllegalStateException}.
     * 
     * @param handler the connection handler
     * 
     * @throws IllegalStateException if the transport has been shutdown or
     *          {@code accept} has been called.
     * @throws IOException if an I/O error occurs
     */
    void accept(ConnectionHandler handler) throws IOException;
    
    /**
     * Shutdown the transport. The actions of this method are implementation
     * dependent, but typically involve closing open network connections,
     * releasing system resources, etc.. All shutdown activity is
     * synchronous with this call. Once this method is called, subsequent
     * calls to {@code shutdown} will have no affect.
     */
    void shutdown();
}
