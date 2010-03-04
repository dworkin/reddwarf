/*
 * This material is distributed under the GNU General Public License
 * Version 2. You may review the terms of this license at
 * http://www.gnu.org/licenses/gpl-2.0.html 
 *
 * Copyright (c) 2007-2010, Oracle and/or its affiliates.
 *
 * All rights reserved.
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
