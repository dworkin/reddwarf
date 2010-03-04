/*
 * This material is distributed under the GNU General Public License
 * Version 2. You may review the terms of this license at
 * http://www.gnu.org/licenses/gpl-2.0.html 
 *
 * Copyright (c) 2007-2010, Oracle and/or its affiliates.
 *
 * All rights reserved.
 */

package com.sun.sgs.impl.service.data.store.net;

import java.io.IOException;
import java.net.Socket;

/**
 * The client side of an experimental network protocol, not currently used, for
 * implementing DataStoreServer using sockets instead of RMI.
 */
/*
 * XXX: Limit connections and/or close unused connections?
 * XXX: Close and not return sockets on IOException?
 */
class DataStoreClientRemote extends DataStoreProtocolClient {

    /** The server host name. */
    private final String host;

    /** The server network port. */
    private final int port;

    /** Creates an instance for the specified host and port */
    DataStoreClientRemote(String host, int port) {
	this.host = host;
	this.port = port;
    }

    /**
     * {@inheritDoc} <p>
     *
     * This implementation creates a socket.
     */
    @Override
    DataStoreProtocol createHandler() throws IOException {
	Socket socket = new Socket(host, port);
	setSocketOptions(socket);
	return new DataStoreProtocol(
	    socket.getInputStream(), socket.getOutputStream());
    }

    /** Sets TcpNoDelay and KeepAlive options, if possible. */
    private void setSocketOptions(Socket socket) {
	try {
	    socket.setTcpNoDelay(true);
	} catch (Exception e) {
	}
	try {
	    socket.setKeepAlive(true);
	} catch (Exception e) {
	}
    }
}
