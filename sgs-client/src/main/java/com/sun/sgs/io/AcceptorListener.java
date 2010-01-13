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

package com.sun.sgs.io;

/**
 * Receives asynchronous notification of events from an associated
 * {@link Acceptor}.  The {@link #newConnection newConnection} method
 * is invoked when a connection has been accepted to obtain an appropriate
 * {@link ConnectionListener} from this  listener.  When the
 * {@code Acceptor} is shut down, the listener is notified by
 * invoking its {@code disconnected()} method.
 */
public interface AcceptorListener {

    /**
     * Returns an appropriate {@link ConnectionListener} for a newly-accepted
     * connection.  The new {@link Connection} is  passed to the
     * {@link ConnectionListener#connected connected} method of the
     * returned {@code ConnectionListener} once it is fully established.
     *
     * @return a {@code ConnectionListener} to receive events for the
     *          newly-accepted {@code Connection}
     */
    ConnectionListener newConnection();

    /**
     * Notifies this listener that its associated {@link Acceptor}
     * has shut down.
     */
    void disconnected();

}
