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
