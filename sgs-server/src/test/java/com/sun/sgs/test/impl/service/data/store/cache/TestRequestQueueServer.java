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

package com.sun.sgs.test.impl.service.data.store.cache;

import com.sun.sgs.impl.service.data.store.cache.Request;
import com.sun.sgs.impl.service.data.store.cache.RequestQueueServer;
import com.sun.sgs.tools.test.FilteredNameRunner;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Properties;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Tests for {@link RequestQueueServer}. */
@RunWith(FilteredNameRunner.class)
public class TestRequestQueueServer extends BasicRequestQueueTest {

    /** The server socket port. */
    private static final int PORT = 30001;

    /** Dummy request handler. */
    private static final DummyRequestHandler dummyRequestHandler =
	new DummyRequestHandler();

    /** Empty properties. */
    private static final Properties emptyProperties = new Properties();

    /** The server socket or {@code null}. */
    ServerSocket serverSocket;

    /** The accepted socket or {@code null}. */
    Socket socket;

    /** The client socket or {@code null}. */
    Socket clientSocket;

    /** The client connection thread or {@code null}. */
    InterruptableThread connect;

    /** The server or {@code null}. */
    RequestQueueServer<Request> server;

    /** Close sockets and shutdown the server, if present. */
    @After
    public void afterTest() {
	forceClose(serverSocket);
	forceClose(socket);
	forceClose(clientSocket);
	if (server != null) {
	    server.disconnect();
	}
    }

    /* -- Tests -- */

    /* Test constructor */

    @Test(expected=NullPointerException.class)
    public void testConstructorNullRequestHandler() {
	new RequestQueueServer<Request>(null, emptyProperties);
    }

    @Test(expected=NullPointerException.class)
    public void testConstructorNullProperties() {
	new RequestQueueServer<Request>(dummyRequestHandler, null);
    }

    /* Test handleConnection */

    @Test
    public void testHandleConnectionNullSocket() throws Exception {
	server = new RequestQueueServer<Request>(
	    dummyRequestHandler, emptyProperties);
	try {
	    server.handleConnection(null);
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	}
    }

    @Test
    public void testHandleConnectionUnboundSocket() throws Exception {
	server = new RequestQueueServer<Request>(
	    dummyRequestHandler, emptyProperties);
	Socket socket = new Socket();
	try {
	    server.handleConnection(socket);
	    fail("Expected IOException");
	} catch (IOException e) {
	} finally {
	    forceClose(socket);
	}
    }
    
    @Test
    public void testHandleConnectionWithExistingConnection() throws Exception {
	server = new RequestQueueServer<Request>(
	    dummyRequestHandler, emptyProperties);
	serverSocket = new ServerSocket(PORT);
	for (int i = 0; i < 2; i++) {
	    connect = new InterruptableThread() {
		boolean runOnce() throws IOException {
		    clientSocket = new Socket("localhost", PORT);
		    return true;
		}
	    };
	    connect.start();
	    socket = serverSocket.accept();
	    server.handleConnection(socket);
	    connect.shutdown();
	}
    }

    /* Test disconnect */

    @Test
    public void testDisconnectNotConnected() throws Exception {
	server = new RequestQueueServer<Request>(
	    dummyRequestHandler, emptyProperties);
	server.disconnect();
	server.disconnect();
    }

    @Test
    public void testDisconnectConnected() throws Exception {
	server = new RequestQueueServer<Request>(
	    dummyRequestHandler, emptyProperties);
	serverSocket = new ServerSocket(PORT);
	connect = new InterruptableThread() {
	    boolean runOnce() throws IOException {
		clientSocket = new Socket("localhost", PORT);
		return true;
	    }
	};
	connect.start();
	socket = serverSocket.accept();
	server.handleConnection(socket);
	connect.shutdown();
	server.disconnect();
	assertTrue(socket.isClosed());
    }

    /* Test earlierRequest */

    @Test
    public void testEarlierRequestNegativeRequests() {
	server = new RequestQueueServer<Request>(
	    dummyRequestHandler, emptyProperties);
	try {
	    server.earlierRequest(-1, 0);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
	try {
	    server.earlierRequest(0, -1);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
    }

    @Test
    public void testEarlierRequestTooLargeRequests() {
	server = new RequestQueueServer<Request>(
	    dummyRequestHandler, emptyProperties);
	try {
	    server.earlierRequest(32768, 0);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
	try {
	    server.earlierRequest(0, 32768);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
    }

    @Test
    public void testEarlierRequestMisc() {
	server = new RequestQueueServer<Request>(
	    dummyRequestHandler, emptyProperties);
	assertFalse(server.earlierRequest(0, 0));
	assertTrue(server.earlierRequest(0, 1));
	assertTrue(server.earlierRequest(0, 9999));
	try {
	    server.earlierRequest(0, 10000);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
	try {
	    server.earlierRequest(0, 22767);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
	assertFalse(server.earlierRequest(0, 22768));
	assertFalse(server.earlierRequest(0, 32767));

	assertFalse(server.earlierRequest(32767, 32767));
	assertTrue(server.earlierRequest(32767, 0));
	assertTrue(server.earlierRequest(32767, 9998));
	try {
	    server.earlierRequest(32767, 9999);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
	try {
	    server.earlierRequest(32767, 22766);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
	assertFalse(server.earlierRequest(32767, 22767));
	assertFalse(server.earlierRequest(32767, 32766));
    }

    /* Test requests */

    @Test
    public void testRequestThrowsIOException() throws Exception {
	server = new RequestQueueServer<Request>(
	    dummyRequestHandler, emptyProperties);
	serverSocket = new ServerSocket(PORT);
	connect = new InterruptableThread() {
	    boolean runOnce() throws IOException {
		clientSocket = new Socket("localhost", PORT);
		return true;
	    }
	};
	connect.start();
	socket = serverSocket.accept();
	server.handleConnection(socket);
	DataOutputStream out =
	    new DataOutputStream(clientSocket.getOutputStream());
	/* Write request number */
	out.writeShort(0);
	out.flush();
	Thread.sleep(EXTRA_WAIT);
	assertTrue("Socket should be closed", socket.isClosed());
    }
}
