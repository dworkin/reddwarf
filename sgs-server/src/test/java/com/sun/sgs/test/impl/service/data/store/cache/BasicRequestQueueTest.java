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
import java.io.DataInput;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import org.junit.Assert;

class BasicRequestQueueTest extends Assert {

    /** Slop time when waiting. */
    static final long EXTRA_WAIT = 50;

    /** A request handler that fails. */
    static class DummyRequestHandler implements RequestHandler<Request> {
	public Request readRequest(DataInput in) throws IOException {
	    throw new IOException();
	}
	public void performRequest(Request request) {
	    throw new UnsupportedOperationException();
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
	    while (!run) {
		wait(minTimeout + EXTRA_WAIT);
	    }
	    long time = System.currentTimeMillis() - start;
	    assertTrue(run);
	    assertTrue(
		"Expected time to be at least " + minTimeout + ": " + time,
		time >= minTimeout); 
	}

	/** Check that the run method has not been called. */
	synchronized void checkNotRun() {
	    assertFalse(run);
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
