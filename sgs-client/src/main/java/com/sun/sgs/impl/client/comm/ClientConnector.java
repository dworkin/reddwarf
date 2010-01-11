/*
 * Copyright (c) 2007-2010, Sun Microsystems, Inc.
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
 * --
 */

package com.sun.sgs.impl.client.comm;

import java.io.IOException;
import java.util.Properties;

/**
 * An abstract mechanism for actively initiating a {@link ClientConnection}.
 */
public abstract class ClientConnector
{
    /** The static singleton factory. */
    private static ClientConnectorFactory theSingletonFactory =
        new com.sun.sgs.impl.client.simple.SimpleConnectorFactory();

    /**
     * Creates a {@code ClientConnector} according to the given
     * {@code properties}.
     *
     * @param properties which affect the implementation of
     *        {@code ClientConnector} returned
     * @return a {@code ClientConnector}
     */
    public static ClientConnector create(Properties properties) {
	return theSingletonFactory.createConnector(properties);
    }

    /**
     * Sets the {@link ClientConnectorFactory} that will be used
     * to create new {@code ClientConnector}s.
     *
     * @param factory the factory to create new {@code ClientConnector}s
     */
    protected static void setConnectorFactory(ClientConnectorFactory factory) {
	theSingletonFactory = factory;
    }

    /**
     * Only allow construction by subclasses.
     */
    protected ClientConnector() {
	// empty
    }

    /**
     * Actively initates a connection to the target remote address.
     * This call is non-blocking. {@link ClientConnectionListener#connected}
     * will be called asynchronously on the {@code listener} upon successful
     * connection, or {@link ClientConnectionListener#disconnected} if it
     * fails.
     *
     * @param listener the listener for all IO events on the
     *        connection, including the result of the connection attempt
     *
     * @throws IOException if an IO error occurs synchronously
     * @throws SecurityException if a security manager has been installed
     *         and it does not permit access to the remote endpoint
     */
    public abstract void connect(ClientConnectionListener listener)
            throws IOException;

    /**
     * Cancels a pending connect operation on this {@code ClientConnecton}.
     *
     * @throws IOException if an IO error occurs synchronously
     */
    public abstract void cancel() throws IOException;

}
