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
 */

package com.sun.sgs.test.util;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;


/**
 * Defines a server socket which can be asked to fail when accepting
 * connections, and when sending and receiving data on the last accepted
 * connection.
 */
public class FailingServerSocket extends ServerSocket {

    /**
     * Whether to throw an exception when attempting to accept a connection, or
     * when sending or receiving data on the last accepted connection.
     */
    private boolean shouldFail = false;

    /** The last accepted socket or {@code null}. */
    private FailingSocket socket;

    /**
     * Creates a server socket bound to the specified port.
     *
     * @param	port the port number
     * @throws	IOException if an I/O error occurs while opening the socket
     */
    public FailingServerSocket(int port) throws IOException {
	super(port);
    }

    /**
     * Requests that future attempts to accept connections, or to send or
     * receive data on the last accepted socket, should fail.
     */
    public synchronized void shouldFail() {
	shouldFail = true;
	if (socket != null) {
	    socket.shouldFail();
	}
    }

    @Override
    public synchronized Socket accept() throws IOException {
	if (shouldFail) {
	    throw new IOException("Injected I/O failure");
	}
	socket = new FailingSocket();
	implAccept(socket);
	return socket;
    }
}
