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
import com.sun.sgs.impl.service.data.store.cache.queue.RequestQueueListener;
import com.sun.sgs.impl.service.data.store.cache.queue.RequestQueueListener.
    ServerDispatcher;
import com.sun.sgs.impl.service.data.store.cache.queue.RequestQueueServer;
import com.sun.sgs.tools.test.FilteredNameRunner;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Tests for {@link RequestQueueListener}. */
@RunWith(FilteredNameRunner.class)
public class TestRequestQueueListener extends BasicRequestQueueTest {

    /** The server socket port. */
    private static final int PORT = 30000;

    /** An unbound server socket. */
    private static final ServerSocket unboundServerSocket;
    static {
	try {
	    unboundServerSocket = new ServerSocket();
	} catch (IOException e) {
	    throw new ExceptionInInitializerError(e);
	}
    }

    /** The shorter maximum retry to use for tests. */
    private static final long MAX_RETRY = 100;

    /** The shorter retry wait to use for tests. */
    private static final long RETRY_WAIT = 10;

    /** The server socket or {@code null}. */
    ServerSocket serverSocket;

    /** The request queue listener server dispatcher. */
    SimpleServerDispatcher serverDispatcher;

    /** A failure reporter for checking if failure occurred. */
    NoteFailure failureReporter;

    /** The request queue listener or {@code null}. */
    RequestQueueListener listener;

    /** The client-side connection thread or {@code null}. */
    InterruptableThread connect;

    /** Create the server dispatcher. */
    @Before
    public void beforeTest() {
	serverDispatcher = new SimpleServerDispatcher();
	failureReporter = new NoteFailure();
    }

    /** Close the server socket and shutdown the connect thread, if present. */
    @After
    public void afterTest() throws Exception {
	forceClose(serverSocket);
	if (listener != null) {
	    listener.shutdown();
	}
	if (connect != null) {
	    connect.shutdown();
	}
	if (failureReporter != null) {
	    failureReporter.checkNotCalled();
	}
	if (serverDispatcher != null) {
	    serverDispatcher.shutdown();
	}
    }

    /* -- Tests -- */

    /* Test constructor */

    @Test(expected=NullPointerException.class)
    public void testConstructorNullSocket() {
	new RequestQueueListener(
	    null, serverDispatcher, failureReporter, MAX_RETRY, RETRY_WAIT);
    }

    @Test(expected=NullPointerException.class)
    public void testConstructorNullServerDispatcher() {
	new RequestQueueListener(
	    unboundServerSocket, null, failureReporter, MAX_RETRY, RETRY_WAIT);
    }

    @Test(expected=NullPointerException.class)
    public void testConstructorNullFailureReporter() {
	new RequestQueueListener(
	    unboundServerSocket, serverDispatcher, null, MAX_RETRY,
	    RETRY_WAIT);
    }

    @Test(expected=IllegalArgumentException.class)
    public void testConstructorIllegalMaxRetry() {
	new RequestQueueListener(
	    unboundServerSocket, serverDispatcher, failureReporter, -1,
	    RETRY_WAIT);
    }

    @Test(expected=IllegalArgumentException.class)
    public void testConstructorIllegalRetryWait() {
	new RequestQueueListener(
	    unboundServerSocket, serverDispatcher, failureReporter, MAX_RETRY,
	    -33);
    }

    /* Test socket accept */

    /**
     * Test that the listener fails after the right delay if the server socket
     * accepts fail because the socket isn't bound.
     */
    @Test
    public void testAcceptFails() throws Exception {
	NoteFailure failureReporter = new NoteFailure();
	listener = new RequestQueueListener(
	    unboundServerSocket, serverDispatcher, failureReporter, MAX_RETRY,
	    RETRY_WAIT);
	failureReporter.checkCalled(MAX_RETRY);
    }

    /**
     * Test that the listener fails after the right delay if connections fail
     * to supply any input before disconnecting.
     */
    @Test
    public void testAcceptInputDisconnected() throws Exception {
	serverSocket = new ServerSocket(PORT);
	NoteFailure failureReporter = new NoteFailure();
	listener = new RequestQueueListener(
	    serverSocket, serverDispatcher, failureReporter, MAX_RETRY,
	    RETRY_WAIT);
	connect = new InterruptableThread() {
	    boolean runOnce() {
		try {
		    new Socket("localhost", PORT).close();
		} catch (IOException e) {
		}
		return false;
	    }
	};
	connect.start();
	failureReporter.checkCalled(MAX_RETRY);
    }

    @Test
    public void testAcceptNoInput() throws Exception {
	serverSocket = new ServerSocket(PORT);
	NoteFailure failureReporter = new NoteFailure();
	listener = new RequestQueueListener(
	    serverSocket, serverDispatcher, failureReporter, MAX_RETRY,
	    RETRY_WAIT);
	connect = new InterruptableThread() {
	    boolean runOnce() {
		Socket socket = null;
		try {
		    socket = new Socket("localhost", PORT);
		} catch (IOException e) {
		    forceClose(socket);
		}
		return false;
	    }
	};
	connect.start();
	failureReporter.checkNotCalled();
    }

    /**
     * Test that the listener fails after the right delay if a connection
     * supplies an unknown node ID.
     */
    @Test
    public void testAcceptUnknownNodeId() throws Exception {
	serverSocket = new ServerSocket(PORT);
	NoteFailure failureReporter = new NoteFailure();
	listener = new RequestQueueListener(
	    serverSocket, serverDispatcher, failureReporter, MAX_RETRY,
	    RETRY_WAIT);
	connect = new InterruptableThread() {
	    boolean runOnce() {
		Socket socket = null;
		try {
		    socket = new Socket("localhost", PORT);
		    DataOutputStream out =
			new DataOutputStream(socket.getOutputStream());
		    out.writeLong(33);
		    Thread.sleep(1);
		} catch (IOException e) {
		} catch (InterruptedException e) {
		} finally {
		    forceClose(socket);
		}
		return false;
	    }
	};
	connect.start();
	failureReporter.checkCalled(MAX_RETRY);
    }

    /**
     * Test that the listener fails after the right delay if a connection
     * supplies an incomplete node ID before disconnecting.
     */
    @Test
    public void testAcceptIncompleteNodeId() throws Exception {
	serverSocket = new ServerSocket(PORT);
	NoteFailure failureReporter = new NoteFailure();
	listener = new RequestQueueListener(
	    serverSocket, serverDispatcher, failureReporter, MAX_RETRY,
	    RETRY_WAIT);
	connect = new InterruptableThread() {
	    boolean runOnce() {
		Socket socket = null;
		try {
		    socket = new Socket("localhost", PORT);
		    OutputStream out = socket.getOutputStream();
		    out.write(new byte[] { 1, 2, 3, 4 });
		    Thread.sleep(1);
		} catch (IOException e) {
		} catch (InterruptedException e) {
		} finally {
		    forceClose(socket);
		}
		return false;
	    }
	};
	connect.start();
	failureReporter.checkCalled(MAX_RETRY);
    }

    /**
     * Test that the listener fails after the right delay if getting the
     * connection's server throws an unexpected exception.
     */
    @Test
    public void testAcceptUnexpectedException() throws Exception {
	serverSocket = new ServerSocket(PORT);
	NoteFailure failureReporter = new NoteFailure();
	listener = new RequestQueueListener(
	    serverSocket,
	    new ServerDispatcher() {
		public RequestQueueServer<? extends Request> getServer(
		    long nodeId)
		{
		    throw new NullPointerException("Whoa!");
		}
	    },
	    failureReporter, MAX_RETRY, RETRY_WAIT);
	connect = new InterruptableThread() {
	    boolean runOnce() {
		Socket socket = null;
		try {
		    socket = new Socket("localhost", PORT);
		    DataOutputStream out =
			new DataOutputStream(socket.getOutputStream());
		    out.writeLong(33);
		    Thread.sleep(1);
		} catch (IOException e) {
		} catch (InterruptedException e) {
		} finally {
		    forceClose(socket);
		}
		return false;
	    }
	};
	connect.start();
	failureReporter.checkCalled(MAX_RETRY);
    }

    /**
     * Test that the listener continues to accept connections if failures
     * alternate with successful connections.
     */
    @Test
    public void testAcceptAlternatingFailures() throws Exception {
	serverSocket = new ServerSocket(PORT);
	NoteFailure failureReporter = new NoteFailure();
	listener = new RequestQueueListener(
	    serverSocket, serverDispatcher, failureReporter, MAX_RETRY,
	    RETRY_WAIT);
	DummyRequestQueueServer server = new DummyRequestQueueServer(1);
	serverDispatcher.setServer(1, server);
	connect = new InterruptableThread() {
	    private long n = 0;
	    private long getNodeId() { return n++ % 2; }
	    boolean runOnce() throws Exception {
		Socket socket = null;
		try {
		    socket = new Socket("localhost", PORT);
		    DataOutputStream out =
			new DataOutputStream(socket.getOutputStream());
		    out.writeLong(getNodeId());
		    Thread.sleep(1);
		} catch (ConnectException e) {
		} catch (InterruptedException e) {
		} finally {
		    forceClose(socket);
		}
		return false;
	    }
	};
	connect.start();
	Thread.sleep(MAX_RETRY + extraWait);
	assertTrue("Expected a non-zero number of connections",
		   server.connectionCount.get() > 0);
	failureReporter.checkNotCalled();
	listener.shutdown();
    }

    /** Test the listener accepting connections successfully. */
    @Test
    public void testAcceptSuccess() throws Exception {
	serverSocket = new ServerSocket(PORT);
	NoteFailure failureReporter = new NoteFailure();
	listener = new RequestQueueListener(
	    serverSocket, serverDispatcher, failureReporter, MAX_RETRY,
	    RETRY_WAIT);
	DummyRequestQueueServer server33 = new DummyRequestQueueServer(33);
	serverDispatcher.setServer(33, server33);
	DummyRequestQueueServer server999 = new DummyRequestQueueServer(999);
	serverDispatcher.setServer(999, server999);
	Socket socket = null;
	try {
	    while (true) {
		try {
		    socket = new Socket("localhost", PORT);
		    break;
		} catch (ConnectException e) {
		}
	    }
	    new DataOutputStream(socket.getOutputStream()).writeLong(33);
	    Thread.sleep(extraWait);
	    assertEquals(1, server33.connectionCount.get());
	    assertEquals(0, server999.connectionCount.get());
	    socket.close();
	    socket = new Socket("localhost", PORT);
	    new DataOutputStream(socket.getOutputStream()).writeLong(999);
	    Thread.sleep(extraWait);
	    assertEquals(1, server33.connectionCount.get());
	    assertEquals(1, server999.connectionCount.get());
	    failureReporter.checkNotCalled();
	    listener.shutdown();
	} finally {
	    forceClose(socket);
	}
    }

    /* Test shutdown */

    /**
     * Test that the listener shuts down without error if shutdown is called
     * when there have been no connections.
     */
    @Test
    public void testShutdownNoConnections() throws Exception {
	serverSocket = new ServerSocket(PORT);
	NoteFailure failureReporter = new NoteFailure();
	listener = new RequestQueueListener(
	    serverSocket, serverDispatcher, failureReporter, MAX_RETRY,
	    RETRY_WAIT);
	Thread.sleep(extraWait);
	listener.shutdown();
	/* Make sure the server socket has been shutdown */
	Socket socket = null;
	try {
	    socket = new Socket("localhost", PORT);
	    fail("Expected ConnectException");
	} catch (ConnectException e) {
	} finally {
	    forceClose(socket);
	}
	failureReporter.checkNotCalled();
    }

    /**
     * Test that the listener shuts down without error if shutdown is called
     * while the listener is in the process of accepting connections.
     */
    @Test
    public void testShutdownWithConnections() throws Exception {
	serverSocket = new ServerSocket(PORT);
	NoteFailure failureReporter = new NoteFailure();
	listener = new RequestQueueListener(
	    serverSocket, serverDispatcher, failureReporter, MAX_RETRY,
	    RETRY_WAIT);
	serverDispatcher.setServer(33, new DummyRequestQueueServer(33));
	connect = new InterruptableThread() {
	    boolean runOnce() {
		Socket socket = null;
		try {
		    socket = new Socket("localhost", PORT);
		    DataOutputStream out =
			new DataOutputStream(socket.getOutputStream());
		    out.writeLong(33);
		    Thread.sleep(1);
		} catch (IOException e) {
		} catch (InterruptedException e) {
		} finally {
		    forceClose(socket);
		}
		return false;
	    }
	};
	connect.start();
	Thread.sleep(extraWait);
	listener.shutdown();
	Thread.sleep(extraWait);
	connect.shutdown();
	/* Make sure the server socket has been shutdown */
	Socket socket = null;
	try {
	    socket = new Socket("localhost", PORT);
	    fail("Expected ConnectException");
	} catch (ConnectException e) {
	} finally {
	    forceClose(socket);
	}
	failureReporter.checkNotCalled();
    }

    /* -- Other classes and methods -- */

    /**
     * A request queue server that counts the number of connections it
     * receives, and closes them immediately.
     */
    static class DummyRequestQueueServer extends RequestQueueServer<Request> {
	final AtomicInteger connectionCount = new AtomicInteger();
	DummyRequestQueueServer(long nodeId) {
	    super(nodeId, new DummyRequestHandler());
	}
	@Override
	public void handleConnection(Socket socket) {
	    connectionCount.incrementAndGet();
	    forceClose(socket);
	}
    }
}
