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
 * --
 */

package com.sun.sgs.test.impl.service.data.store.cache.queue;

import com.sun.sgs.impl.service.data.store.cache.queue.Request;
import com.sun.sgs.impl.service.data.store.cache.queue.Request.RequestHandler;
import com.sun.sgs.impl.service.data.store.cache.queue.RequestQueueServer;
import static com.sun.sgs.impl.util.DataStreamUtil.readString;
import com.sun.sgs.tools.test.FilteredNameRunner;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.CountDownLatch;
import static java.util.concurrent.TimeUnit.SECONDS;
import java.util.concurrent.atomic.AtomicInteger;
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

    /** The server socket or {@code null}. */
    ServerSocket serverSocket;

    /** The accepted socket or {@code null}. */
    Socket socket;

    /** The client socket or {@code null}. */
    volatile Socket clientSocket;

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

    @Test(expected=IllegalArgumentException.class)
    public void testConstructorNegativeNodeId() {
	new RequestQueueServer<Request>(-1, dummyRequestHandler);
    }

    @Test(expected=NullPointerException.class)
    public void testConstructorNullRequestHandler() {
	new RequestQueueServer<Request>(1, null);
    }

    /* Test handleConnection */

    @Test
    public void testHandleConnectionNullSocket() throws Exception {
	server = new RequestQueueServer<Request>(1, dummyRequestHandler);
	try {
	    server.handleConnection(null);
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	}
    }

    @Test
    public void testHandleConnectionUnboundSocket() throws Exception {
	server = new RequestQueueServer<Request>(1, dummyRequestHandler);
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
	server = new RequestQueueServer<Request>(1, dummyRequestHandler);
	serverSocket = new ServerSocket(PORT);
	for (int i = 0; i < 2; i++) {
	    final CountDownLatch ready = new CountDownLatch(1);
	    connect = new InterruptableThread() {
		boolean runOnce() throws IOException {
		    clientSocket = new Socket("localhost", PORT);
		    ready.countDown();
		    return true;
		}
	    };
	    connect.start();
	    socket = serverSocket.accept();
	    ready.await(10, SECONDS);
	    server.handleConnection(socket);
	    connect.shutdown();
	}
    }

    /* Test disconnect */

    @Test
    public void testDisconnectNotConnected() throws Exception {
	server = new RequestQueueServer<Request>(1, dummyRequestHandler);
	server.disconnect();
	server.disconnect();
    }

    @Test
    public void testDisconnectConnected() throws Exception {
	server = new RequestQueueServer<Request>(1, dummyRequestHandler);
	serverSocket = new ServerSocket(PORT);
	final CountDownLatch ready = new CountDownLatch(1);
	connect = new InterruptableThread() {
	    boolean runOnce() throws IOException {
		clientSocket = new Socket("localhost", PORT);
		ready.countDown();
		return true;
	    }
	};
	connect.start();
	socket = serverSocket.accept();
	ready.await(10, SECONDS);
	server.handleConnection(socket);
	connect.shutdown();
	server.disconnect();
	assertTrue(socket.isClosed());
    }

    /* Test earlierRequest */

    @Test
    public void testEarlierRequestNegativeRequests() {
	server = new RequestQueueServer<Request>(1, dummyRequestHandler);
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
	server = new RequestQueueServer<Request>(1, dummyRequestHandler);
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
	server = new RequestQueueServer<Request>(1, dummyRequestHandler);
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
    public void testRequestReadThrowsIOException() throws Exception {
	server = new RequestQueueServer<Request>(1, dummyRequestHandler);
	serverSocket = new ServerSocket(PORT);
	final CountDownLatch ready = new CountDownLatch(1);
	connect = new InterruptableThread() {
	    boolean runOnce() throws IOException {
		clientSocket = new Socket("localhost", PORT);
		ready.countDown();
		return true;
	    }
	};
	connect.start();
	socket = serverSocket.accept();
	ready.await(10, SECONDS);
	server.handleConnection(socket);
	DataOutputStream out =
	    new DataOutputStream(clientSocket.getOutputStream());
	/* Write request number */
	out.writeShort(0);
	out.flush();
	Thread.sleep(extraWait);
	assertTrue("Socket should be closed", socket.isClosed());
    }

    @Test
    public void testRequestReadThrowsOtherException() throws Exception {
	server = new RequestQueueServer<Request>(
	    1,
	    new DummyRequestHandler() {
		public Request readRequest(DataInput in) throws IOException {
		    throw new RuntimeException("Yow!");
		}
	    });
	serverSocket = new ServerSocket(PORT);
	final CountDownLatch ready = new CountDownLatch(1);
	connect = new InterruptableThread() {
	    boolean runOnce() throws IOException {
		clientSocket = new Socket("localhost", PORT);
		ready.countDown();
		return true;
	    }
	};
	connect.start();
	socket = serverSocket.accept();
	ready.await(10, SECONDS);
	server.handleConnection(socket);
	DataOutputStream out =
	    new DataOutputStream(clientSocket.getOutputStream());
	/* Write request number */
	out.writeShort(0);
	out.flush();
	Thread.sleep(extraWait);
	assertTrue("Socket should be closed", socket.isClosed());
    }

    @Test
    public void testRequestPerformThrowsException() throws Exception {
	final AtomicInteger performed = new AtomicInteger();
	server = new RequestQueueServer<Request>(
	    1,
	    new RequestHandler<Request>() {
		private int i;
		public Request readRequest(DataInput in) throws IOException {
		    return new DummyRequest();
		}
		public void performRequest(Request request) {
		    performed.incrementAndGet();
		    throw new RuntimeException(String.valueOf(++i));
		}
	    });
	serverSocket = new ServerSocket(PORT);
	final CountDownLatch ready = new CountDownLatch(1);
	connect = new InterruptableThread() {
	    boolean runOnce() throws IOException {
		clientSocket = new Socket("localhost", PORT);
		ready.countDown();
		return true;
	    }
	};
	connect.start();
	socket = serverSocket.accept();
	ready.await(10, SECONDS);
	server.handleConnection(socket);
	DataOutputStream out =
	    new DataOutputStream(clientSocket.getOutputStream());
	DataInputStream in =
	    new DataInputStream(clientSocket.getInputStream());
	/* Connection acknowledgement */
	assertTrue(in.readBoolean());
	/* First request */
	out.writeShort(0);
	out.flush();
	assertFalse(in.readBoolean());
	assertEquals("java.lang.RuntimeException", readString(in));
	String exceptionMessage = readString(in);
	/* Second request -- should get same result */
	out.writeShort(1);
	out.flush();
	assertFalse(in.readBoolean());
	assertEquals("java.lang.RuntimeException", readString(in));
	assertEquals(exceptionMessage, readString(in));
	assertEquals(1, performed.get());
    }

    @Test
    public void testRequestPerformSuccess() throws Exception {
	server = new RequestQueueServer<Request>(
	    1,
	    new DummyRequestHandler() {
		public Request readRequest(DataInput in) throws IOException {
		    assertEquals("Hello", in.readUTF());
		    return new DummyRequest();
		}
		public void performRequest(Request request) { }
	    });
	serverSocket = new ServerSocket(PORT);
	final CountDownLatch ready = new CountDownLatch(1);
	connect = new InterruptableThread() {
	    boolean runOnce() throws IOException {
		clientSocket = new Socket("localhost", PORT);
		ready.countDown();
		return true;
	    }
	};
	connect.start();
	socket = serverSocket.accept();
	ready.await(10, SECONDS);
	server.handleConnection(socket);
	DataOutputStream out =
	    new DataOutputStream(clientSocket.getOutputStream());
	/* Write request number and data */
	out.writeShort(0);
	out.writeUTF("Hello");
	out.flush();
	DataInputStream in =
	    new DataInputStream(clientSocket.getInputStream());
	/* Connection acknowledgement */
	assertTrue(in.readBoolean());
	assertTrue(in.readBoolean());
	assertFalse("Socket should not be closed", socket.isClosed());
    }

    @Test
    public void testRequestPerformIgnoreDuplicates() throws Exception {
	final AtomicInteger requests = new AtomicInteger();
	server = new RequestQueueServer<Request>(
	    1,
	    new RequestHandler<Request>() {
		public Request readRequest(DataInput in) throws IOException {
		    return new DummyRequest();
		}
		public void performRequest(Request request) {
		    requests.incrementAndGet();
		}
	    });
	serverSocket = new ServerSocket(PORT);
	final CountDownLatch ready = new CountDownLatch(1);
	connect = new InterruptableThread() {
	    boolean runOnce() throws IOException {
		clientSocket = new Socket("localhost", PORT);
		ready.countDown();
		DataOutputStream out =
		    new DataOutputStream(clientSocket.getOutputStream());
		out.writeShort(0);
		out.writeShort(0);
		out.writeShort(1);
		out.writeShort(1);
		out.writeShort(2);
		out.flush();
		return true;
	    }
	};
	connect.start();
	socket = serverSocket.accept();
	ready.await(10, SECONDS);
	server.handleConnection(socket);
	DataInputStream in =
	    new DataInputStream(clientSocket.getInputStream());
	/* Connection acknowledgement */
	assertTrue(in.readBoolean());
	for (int i = 0; i < 5; i++) {
	    assertTrue("Read response " + i, in.readBoolean());
	}
	assertFalse("Socket should not be closed", socket.isClosed());
	assertEquals(3, requests.get());
    }

    @Test
    public void testRequestPerformIgnoreDuplicatesAcrossConnections()
	throws Exception
    {
	final AtomicInteger requests = new AtomicInteger();
	server = new RequestQueueServer<Request>(
	    1,
	    new RequestHandler<Request>() {
		public Request readRequest(DataInput in) throws IOException {
		    return new DummyRequest();
		}
		public void performRequest(Request request) {
		    requests.incrementAndGet();
		}
	    });
	serverSocket = new ServerSocket(PORT);
	final CountDownLatch ready = new CountDownLatch(1);
	connect = new InterruptableThread() {
	    boolean runOnce() throws IOException {
		clientSocket = new Socket("localhost", PORT);
		ready.countDown();
		DataOutputStream out =
		    new DataOutputStream(clientSocket.getOutputStream());
		out.writeShort(0);
		out.writeShort(1);
		out.writeShort(2);
		out.flush();
		return true;
	    }
	};
	connect.start();
	socket = serverSocket.accept();
	ready.await(10, SECONDS);
	server.handleConnection(socket);
	DataInputStream in =
	    new DataInputStream(clientSocket.getInputStream());
	/* Connection acknowledgement */
	assertTrue(in.readBoolean());
	for (int i = 0; i < 3; i++) {
	    assertTrue("Read response " + i, in.readBoolean());
	}
	assertEquals(3, requests.get());
	/* Close the socket and connect again */
	clientSocket.close();
	Thread.sleep(extraWait);
	assertTrue("Socket should be closed", socket.isClosed());
	final CountDownLatch ready2 = new CountDownLatch(1);
	connect = new InterruptableThread() {
	    boolean runOnce() throws IOException {
		clientSocket = new Socket("localhost", PORT);
		ready2.countDown();
		DataOutputStream out =
		    new DataOutputStream(clientSocket.getOutputStream());
		out.writeShort(1);
		out.writeShort(2);
		out.writeShort(3);
		out.writeShort(4);
		out.flush();
		return true;
	    }
	};
	connect.start();
	socket = serverSocket.accept();
	ready2.await(10, SECONDS);
	server.handleConnection(socket);
	in = new DataInputStream(clientSocket.getInputStream());
	/* Connection acknowledgement */
	assertTrue(in.readBoolean());
	for (int i = 0; i < 4; i++) {
	    assertTrue("Read response " + i, in.readBoolean());
	}
	assertEquals(5, requests.get());
	assertFalse("Socket should not be closed", socket.isClosed());
    }

    @Test
    public void testRequestPerformTooManyOutstanding() throws Exception {
	server = new RequestQueueServer<Request>(
	    1,
	    new RequestHandler<Request>() {
		public Request readRequest(DataInput in) throws IOException {
		    return new DummyRequest();
		}
		public void performRequest(Request request) { }
	    });
	serverSocket = new ServerSocket(PORT);
	final CountDownLatch ready = new CountDownLatch(1);
	connect = new InterruptableThread() {
	    boolean runOnce() throws IOException {
		clientSocket = new Socket("localhost", PORT);
		ready.countDown();
		return true;
	    }
	};
	connect.start();
	socket = serverSocket.accept();
	ready.await(10, SECONDS);
	server.handleConnection(socket);
	DataOutputStream out =
	    new DataOutputStream(clientSocket.getOutputStream());
	DataInputStream in =
	    new DataInputStream(clientSocket.getInputStream());
	/* Connection acknowledgement */
	assertTrue(in.readBoolean());
	/* First request */
	out.writeShort(0);
	out.flush();
	assertTrue(in.readBoolean());
	/* Second request -- should cause connection to be closed */
	out.writeShort(20000);
	out.flush();
	try {
	    in.readBoolean();
	    fail("Expected EOFException");
	} catch (EOFException e) {
	}
    }
}
