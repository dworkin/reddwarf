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

import com.sun.sgs.impl.service.data.store.cache.RequestQueueListener;
import static com.sun.sgs.impl.service.data.store.cache.RequestQueueListener
    .MAX_RETRY_PROPERTY;
import static com.sun.sgs.impl.service.data.store.cache.RequestQueueListener
    .RETRY_WAIT_PROPERTY;
import com.sun.sgs.impl.service.data.store.cache.RequestQueueServer;
import com.sun.sgs.tools.test.FilteredNameRunner;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.ConnectException;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(FilteredNameRunner.class)
public class TestRequestQueueListener extends Assert {

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

    /** A no-op runnable. */
    private static final Runnable noopRunnable =
	new Runnable() { public void run() { } };

    /** Empty properties. */
    private static final Properties emptyProperties = new Properties();

    /** The shorter maximum retry to use for tests. */
    private static final long MAX_RETRY = 100;

    /** The shorter retry wait to use for tests. */
    private static final long RETRY_WAIT = 10;

    /** Slop time when waiting. */
    private static final long EXTRA_WAIT = 50;

    /** Properties specifying the shorter maximum retry and retry waits. */
    private static final Properties props = new Properties();
    static {
	props.setProperty(MAX_RETRY_PROPERTY, String.valueOf(MAX_RETRY));
	props.setProperty(RETRY_WAIT_PROPERTY, String.valueOf(RETRY_WAIT));
    }

    /* -- Tests -- */

    /* Test constructor */

    @Test(expected=NullPointerException.class)
    public void testConstructorNullSocket() {
	new SimpleRequestQueueListener(null, noopRunnable, emptyProperties);
    }

    @Test(expected=NullPointerException.class)
    public void testConstructorNullFailureHandler() {
	new SimpleRequestQueueListener(
	    unboundServerSocket, null, emptyProperties);
    }

    @Test(expected=NullPointerException.class)
    public void testConstructorNullProperties() {
	new SimpleRequestQueueListener(
	    unboundServerSocket, noopRunnable, null);
    }

    /* Test socket accept */

    /**
     * Test that the listener fails after the right delay if the server socket
     * accepts fail because the socket isn't bound.
     */
    @Test
    public void testAcceptFails() throws Exception {
	NoteRun failureHandler = new NoteRun();
	SimpleRequestQueueListener listener = new SimpleRequestQueueListener(
	    unboundServerSocket, failureHandler, props);
	listener.start();
	long start = System.currentTimeMillis();
	failureHandler.checkRun(MAX_RETRY);
    }

    /**
     * Test that the listener fails after the right delay if connections fail
     * to supply any input before disconnecting.
     */
    @Test
    public void testAcceptServerNoInput() throws Exception {
	ServerSocket serverSocket = new ServerSocket(PORT);
	InterruptableThread connect = null;
	try {
	    NoteRun failureHandler = new NoteRun();
	    SimpleRequestQueueListener listener =
		new SimpleRequestQueueListener(
		    serverSocket, failureHandler, emptyProperties);
	    connect = new InterruptableThread() {
		void runOnce() {
		    try {
			new Socket("localhost", PORT).close();
		    } catch (IOException e) {
		    }
		}
	    };
	    listener.start();
	    connect.start();
	    failureHandler.checkRun(MAX_RETRY);
	} finally {
	    try {
		serverSocket.close();
	    } catch (IOException e) {
	    }
	    if (connect != null) {
		connect.shutdown();
	    }
	}
    }

    /**
     * Test that the listener fails after the right delay if a connection
     * supplies an unknown node ID.
     */
    @Test
    public void testAcceptServerUnknownNodeId() throws Exception {
	ServerSocket serverSocket = new ServerSocket(PORT);
	InterruptableThread connect = null;
	try {
	    NoteRun failureHandler = new NoteRun();
	    SimpleRequestQueueListener listener =
		new SimpleRequestQueueListener(
		    serverSocket, failureHandler, emptyProperties);
	    connect = new InterruptableThread() {
		void runOnce() {
		    try {
			Socket socket = new Socket("localhost", PORT);
			DataOutputStream out = 
			    new DataOutputStream(socket.getOutputStream());
			out.writeLong(0);
			Thread.sleep(1);
			socket.close();
		    } catch (IOException e) {
		    } catch (InterruptedException e) {
		    }
		}
	    };
	    listener.start();
	    connect.start();
	    failureHandler.checkRun(MAX_RETRY);
	} finally {
	    try {
		serverSocket.close();
	    } catch (IOException e) {
	    }
	    if (connect != null) {
		connect.shutdown();
	    }
	}
    }

    /**
     * Test that the listener fails after the right delay if a connection
     * supplies a partial node ID before disconnecting.
     */
    @Test
    public void testAcceptServerPartialNodeId() throws Exception {
	ServerSocket serverSocket = new ServerSocket(PORT);
	InterruptableThread connect = null;
	try {
	    NoteRun failureHandler = new NoteRun();
	    SimpleRequestQueueListener listener =
		new SimpleRequestQueueListener(
		    serverSocket, failureHandler, emptyProperties);
	    connect = new InterruptableThread() {
		void runOnce() {
		    try {
			Socket socket = new Socket("localhost", PORT);
			OutputStream out = socket.getOutputStream();
			out.write(new byte[] { 1, 2, 3 });
			Thread.sleep(1);
			socket.close();
		    } catch (IOException e) {
		    } catch (InterruptedException e) {
		    }
		}
	    };
	    listener.start();
	    connect.start();
	    failureHandler.checkRun(MAX_RETRY);
	} finally {
	    try {
		serverSocket.close();
	    } catch (IOException e) {
	    }
	    if (connect != null) {
		connect.shutdown();
	    }
	}
    }

    /**
     * Test that the listener fails after the right delay if getting the
     * connection's server throws an unexpected exception.
     */
    @Test
    public void testAcceptServerUnexpectedException() throws Exception {
	ServerSocket serverSocket = new ServerSocket(PORT);
	InterruptableThread connect = null;
	try {
	    NoteRun failureHandler = new NoteRun();
	    SimpleRequestQueueListener listener =
		new SimpleRequestQueueListener(
		    serverSocket, failureHandler, emptyProperties)
		{
		    protected RequestQueueServer getServer(long nodeId) {
			throw new NullPointerException("Whoa!");
		    }
		};
	    connect = new InterruptableThread() {
		void runOnce() {
		    try {
			Socket socket = new Socket("localhost", PORT);
			DataOutputStream out = 
			    new DataOutputStream(socket.getOutputStream());
			out.writeLong(0);
			Thread.sleep(1);
			socket.close();
		    } catch (IOException e) {
		    } catch (InterruptedException e) {
		    }
		}
	    };
	    listener.start();
	    connect.start();
	    failureHandler.checkRun(MAX_RETRY);
	} finally {
	    try {
		serverSocket.close();
	    } catch (IOException e) {
	    }
	    if (connect != null) {
		connect.shutdown();
	    }
	}
    }

    /* Test shutdown */

    /**
     * Test that the listener shuts down without error if shutdown is called
     * when there have been no connections.
     */
    @Test
    public void testShutdownNoConnections() throws Exception {
	ServerSocket serverSocket = new ServerSocket(PORT);
	try {
	    NoteRun failureHandler = new NoteRun();
	    SimpleRequestQueueListener listener =
		new SimpleRequestQueueListener(
		    serverSocket, failureHandler, emptyProperties);
	    listener.start();
	    Thread.sleep(EXTRA_WAIT);
	    listener.shutdown();
	    Thread.sleep(EXTRA_WAIT);
	    /* Make sure the server socket has been shutdown */
	    Socket socket = null;
	    try {
		socket = new Socket("localhost", PORT);
		fail("Expected ConnectException");
	    } catch (ConnectException e) {
	    } finally {
		if (socket != null) {
		    try {
			socket.close();
		    } catch (IOException e) {
		    }
		}
	    }
	    failureHandler.checkNotRun();
	} finally {
	    try {
		serverSocket.close();
	    } catch (IOException e) {
	    }
	}
    }

    /**
     * Test that the listener shuts down without error if shutdown is called
     * while the listener is in the process of accepting connections.
     */
    @Test
    public void testShutdownWithConnections() throws Exception {
	ServerSocket serverSocket = new ServerSocket(PORT);
	InterruptableThread connect = null;
	try {
	    NoteRun failureHandler = new NoteRun();
	    SimpleRequestQueueListener listener =
		new SimpleRequestQueueListener(
		    serverSocket, failureHandler, emptyProperties);
	    connect = new InterruptableThread() {
		void runOnce() {
		    try {
			Socket socket = new Socket("localhost", PORT);
			DataOutputStream out = 
			    new DataOutputStream(socket.getOutputStream());
			out.writeLong(0);
			Thread.sleep(1);
			socket.close();
		    } catch (IOException e) {
		    } catch (InterruptedException e) {
		    }
		}
	    };
	    listener.start();
	    connect.start();
	    Thread.sleep(EXTRA_WAIT);
	    listener.shutdown();
	    Thread.sleep(EXTRA_WAIT);
	    /* Make sure the server socket has been shutdown */
	    Socket socket = null;
	    try {
		socket = new Socket("localhost", PORT);
		fail("Expected ConnectException");
	    } catch (ConnectException e) {
	    } finally {
		if (socket != null) {
		    try {
			socket.close();
		    } catch (IOException e) {
		    }
		}
	    }
	    failureHandler.checkNotRun();
	} finally {
	    try {
		serverSocket.close();
	    } catch (IOException e) {
	    }
	}
    }

    /* -- Other classes and methods -- */

    static class SimpleRequestQueueListener extends RequestQueueListener {
	final Map<Long, RequestQueueServer> servers =
	    new HashMap<Long, RequestQueueServer>();
	SimpleRequestQueueListener(ServerSocket serverSocket,
				   Runnable failureHandler,
				   Properties properties)
	{
	    super(serverSocket, failureHandler, properties);
	}
	protected RequestQueueServer getServer(long nodeId) {
	    RequestQueueServer server = servers.get(nodeId);
	    if (server != null) {
		return server;
	    } else {
		throw new IllegalArgumentException(
		    "Server not found: " + nodeId);
	    }
	}
    }

    static class NoteRun implements Runnable {
	private boolean run;
	public synchronized void run() {
	    run = true;
	    notifyAll();
	}
	synchronized void checkRun(long minTimeout)
	    throws InterruptedException
	{
	    long start = System.currentTimeMillis();
	    while (!run) {
		wait(minTimeout + EXTRA_WAIT);
	    }
	    long time = System.currentTimeMillis() - start;
	    assertTrue(run);
	    assertTrue(
		"Expected time to be at least " + minTimeout + ": " + time,
		time >= minTimeout); 
	}
	synchronized void checkNotRun() {
	    assertFalse(run);
	}
    }

    abstract static class InterruptableThread extends Thread {
	private boolean shutdown;
	private Throwable exception;

	public void run() {
	    try {
		while (true) {
		    synchronized (this) {
			if (shutdown) {
			    break;
			}
		    }
		    runOnce();
		}
	    } catch (Throwable t) {
		exception = t;
	    }
	}

	abstract void runOnce() throws Exception;

	public void shutdown() throws Exception {
	    synchronized (this) {
		shutdown = true;
	    }
	    interrupt();
	    join();
	    synchronized (this) {
		if (exception instanceof Exception) {
		    throw (Exception) exception;
		} else if (exception instanceof Error) {
		    throw (Error) exception;
		} else {
		    assertNull(exception);
		}
	    }
	}
    }
}
