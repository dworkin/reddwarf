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
import com.sun.sgs.impl.service.data.store.cache.Request.RequestHandler;
import com.sun.sgs.impl.service.data.store.cache.RequestQueueListener;
import com.sun.sgs.impl.service.data.store.cache.RequestQueueListener.
    ServerDispatcher;
import com.sun.sgs.impl.service.data.store.cache.RequestQueueServer;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import org.junit.Assert;

class BasicRequestQueueTest extends Assert {

    /** Slop time when waiting. */
    static final long EXTRA_WAIT = 200;

    /** Empty properties. */
    static final Properties emptyProperties = new Properties();

    /** A no-op runnable. */
    static final Runnable noopRunnable =
	new Runnable() {
	    public void run() { }
	};

    /** A request queue listener that gets servers from a map. */
    static class SimpleServerDispatcher implements ServerDispatcher {
	private final Map<Long, RequestQueueServer>
	    servers = new HashMap<Long, RequestQueueServer>();
	public synchronized RequestQueueServer getServer(long nodeId) {
	    RequestQueueServer server = servers.get(nodeId);
	    if (server != null) {
		return server;
	    } else {
		throw new IllegalArgumentException(
		    "Server not found: " + nodeId);
	    }
	}
	synchronized void setServer(long nodeId, RequestQueueServer server) {
	    servers.put(nodeId, server);
	}
    }

    /** A request handler that fails. */
    static class DummyRequestHandler implements RequestHandler<Request> {
	public Request readRequest(DataInput in) throws IOException {
	    throw new IOException();
	}
	public void performRequest(Request request) {
	    throw new UnsupportedOperationException();
	}
    }

    /** A request that does nothing. */
    static class DummyRequest implements Request {
	private static int next = 0;
	private final int n;
	DummyRequest() {
	    synchronized (DummyRequest.class) {
		n = next++;
	    }
	}
	public void writeRequest(DataOutput out) { }
	public void completed(Throwable exception) { }
	public String toString() {
	    return "DummyRequest[n:" + n + "]";
	}
    }

    /**
     * A runnable that keeps track of whether its run method has been called.
     */
    static class NoteRun implements Runnable {
	private boolean run;

	public synchronized void run() {
	    run = true;
	    notifyAll();
	}

	/**
	 * Check that the run method is called no sooner than minTimeout
	 * milliseconds and no more than EXTRA_WAIT milliseconds after that.
	 */
	synchronized void checkRun(long minTimeout)
	    throws InterruptedException
	{
	    long start = System.currentTimeMillis();
	    long wait = minTimeout + EXTRA_WAIT;
	    while (!run) {
		wait(wait);
		if (System.currentTimeMillis() > start + wait) {
		    fail("Failed to call run in " + wait + " ms");
		}
	    }
	    long time = System.currentTimeMillis() - start;
	    assertTrue(run);
	    assertTrue("Called run earlier than " + minTimeout + " ms: " +
		       time + " ms",
		       time >= minTimeout); 
	}

	/** Check that the run method has not been called. */
	synchronized void checkNotRun() {
	    assertFalse("Failure handler should not be run", run);
	}
    }

    /**
     * A thread subclass that runs an operation repeatedly, checking if it has
     * been asked to shutdown, and exiting if an exception is thrown, keeping
     * track of the exception.
     */
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
		    if (runOnce()) {
			break;
		    }
		}
	    } catch (InterruptedException e) {
	    } catch (Throwable t) {
		exception = t;
	    }
	}

	/**
	 * The operation to run repeatedly, throwing an exception if the
	 * operation failed and returning {@code true} if the thread should
	 * exit successfully.
	 */
	abstract boolean runOnce() throws Exception;

	/**
	 * Shuts down this thread, throwing the failure exception thrown by
	 * runOnce, if any.
	 */
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

    /** Close a socket, ignoring I/O exceptions and null sockets. */
    static void forceClose(Socket socket) {
	if (socket != null) {
	    try {
		socket.close();
	    } catch (IOException e) {
	    }
	}
    }

    /** Close a server socket, ignoring I/O exceptions and null sockets. */
    static void forceClose(ServerSocket serverSocket) {
	if (serverSocket != null) {
	    try {
		serverSocket.close();
	    } catch (IOException e) {
	    }
	}
    }
}
