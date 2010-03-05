/*
 *
 * Copyright (c) 2007-2010, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in
 *       the documentation and/or other materials provided with the
 *       distribution.
 *     * Neither the name of Sun Microsystems, Inc. nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
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
