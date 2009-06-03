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

import java.io.FilterInputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketAddress;

/**
 * Define a socket which can be asked to fail when creating connections, and
 * when sending and receiving data.
 */
public class FailingSocket extends Socket {

    /**
     * Whether to throw an exception when attempting to connect, or when
     * sending or receiving data.
     */
    private boolean shouldFail = false;

    /** Creates an unconnected socket. */
    public FailingSocket() { }

    /**
     * Creates a socket and connects it to the specified host and port.
     *
     * @param	host the host
     * @param	port the port
     * @throws	IOException if an I/O error occurs when creating the socket
     */
    public FailingSocket(String host, int port) throws IOException {
	super(host, port);
    }

    /**
     * Requests that future attempts to connect, or to send or receive data,
     * should fail.
     */
    public synchronized void shouldFail() {
	shouldFail = true;
    }

    /** Throws an exception if operations should fail. */
    synchronized void maybeFail() throws IOException {
	if (shouldFail) {
	    throw new IOException("Injected I/O failure");
	}
    }

    @Override
    public void connect(SocketAddress endpoint) throws IOException {
	maybeFail();
	super.connect(endpoint);
    }

    @Override
    public void connect(SocketAddress endpoint, int timeout)
	throws IOException
    {
	maybeFail();
	super.connect(endpoint, timeout);
    }
    
    @Override
    public InputStream getInputStream() throws IOException {
	return new WrappedInputStream(super.getInputStream());
    }

    @Override
    public OutputStream getOutputStream() throws IOException {
	return new WrappedOutputStream(super.getOutputStream());
    }
    
    /** A delegating input stream whose write operations fail if requested. */
    private class WrappedInputStream extends FilterInputStream {

	WrappedInputStream(InputStream in) {
	    super(in);
	}

	@Override
	public int read() throws IOException {
	    maybeFail();
	    return super.read();
	}

	@Override
	public int read(byte b[]) throws IOException {
	    maybeFail();
	    return super.read(b);
	}

	@Override
	public int read(byte b[], int off, int len) throws IOException {
	    maybeFail();
	    return super.read(b, off, len);
	}
    }

    /**
     * A delegating output stream whose write operations fail if requested.
     */
    private class WrappedOutputStream extends FilterOutputStream {

	WrappedOutputStream(OutputStream out) {
	    super(out);
	}

	@Override
	public void write(int b) throws IOException {
	    maybeFail();
	    super.write(b);
	}

	@Override
	public void write(byte b[]) throws IOException {
	    maybeFail();
	    super.write(b);
	}
	
	@Override
	public void write(byte b[], int off, int len) throws IOException {
	    maybeFail();
	    super.write(b, off, len);
	}
    }
}
