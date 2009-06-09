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
import com.sun.sgs.impl.service.data.store.cache.RequestQueueClient;
import com.sun.sgs.impl.service.data.store.cache.RequestQueueClient.
    BasicSocketFactory;
import static com.sun.sgs.impl.service.data.store.cache.RequestQueueClient.
    MAX_RETRY_PROPERTY;
import static com.sun.sgs.impl.service.data.store.cache.RequestQueueClient.
    REQUEST_QUEUE_SIZE_PROPERTY;
import static com.sun.sgs.impl.service.data.store.cache.RequestQueueClient.
    RETRY_WAIT_PROPERTY;
import static com.sun.sgs.impl.service.data.store.cache.RequestQueueClient.
    SENT_QUEUE_SIZE_PROPERTY;
import com.sun.sgs.impl.service.data.store.cache.RequestQueueClient.
    SocketFactory;
import com.sun.sgs.impl.service.data.store.cache.RequestQueueListener;
import com.sun.sgs.impl.service.data.store.cache.RequestQueueServer;
import com.sun.sgs.tools.test.FilteredNameRunner;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.Properties;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Tests for {@link RequestQueueClient}. */
@RunWith(FilteredNameRunner.class)
public class TestRequestQueueClient extends BasicRequestQueueTest {

    /** The server socket port. */
    private static final int PORT = 30002;

    /** The shorter maximum retry to use for tests. */
    private static final long MAX_RETRY = 100;

    /** The shorter retry wait to use for tests. */
    private static final long RETRY_WAIT = 10;

    /** The shorter queue size for use in tests. */
    private static final int QUEUE_SIZE = 10;

    /**
     * Properties specifying the shorter maximum retry and retry waits, and
     * queue size.
     */
    private static final Properties props = new Properties();
    static {
	props.setProperty(MAX_RETRY_PROPERTY, String.valueOf(MAX_RETRY));
	props.setProperty(RETRY_WAIT_PROPERTY, String.valueOf(RETRY_WAIT));
	props.setProperty(
	    REQUEST_QUEUE_SIZE_PROPERTY, String.valueOf(QUEUE_SIZE));
	props.setProperty(
	    SENT_QUEUE_SIZE_PROPERTY, String.valueOf(QUEUE_SIZE));
    }

    /** The request queue listener server dispatcher. */
    private SimpleServerDispatcher serverDispatcher;

    /** The request queue listener. */
    private RequestQueueListener listener;

    /** A basic socket factory for connecting to the server. */
    private static final SocketFactory socketFactory =
	new BasicSocketFactory("localhost", PORT);

    /** The request queue client or {@code null}. */
    private RequestQueueClient client;

    /** A thread for running client operations or {@code null}. */
    private InterruptableThread clientThread;

    @Before
    public void beforeTest() throws IOException {
	serverDispatcher = new SimpleServerDispatcher();
	listener = new RequestQueueListener(
	    new ServerSocket(PORT), serverDispatcher, noopRunnable,
	    emptyProperties);
    }

    @After
    public void afterTest() throws Exception {
	if (clientThread != null) {
	    clientThread.shutdown();
	    clientThread = null;
	}
	if (client != null) {
	    client.shutdown();
	    client = null;
	}
	if (listener != null) {
	    listener.shutdown();
	}
    }

    /* -- Tests -- */

    /* Test constructor */

    @Test(expected=NullPointerException.class)
    public void testConstructorNullSocketFactory() {
	new RequestQueueClient(1, null, noopRunnable, emptyProperties);
    }

    @Test(expected=NullPointerException.class)
    public void testConstructorNullFailureHandler() {
	new RequestQueueClient(1, socketFactory, null, emptyProperties);
    }

    @Test(expected=NullPointerException.class)
    public void testConstructorNullProperties() {
	new RequestQueueClient(1, socketFactory, noopRunnable, null);
    }

    /* Test connection handling */

    @Test
    public void testConnectionFails() throws Exception {
	listener.shutdown();
	listener = null;
	NoteRun failureHandler = new NoteRun();
	client = new RequestQueueClient(
	    1, socketFactory, failureHandler, props);
	failureHandler.checkRun(MAX_RETRY);	
    }

    @Test
    public void testConnectionServerUnknown() throws Exception {
	NoteRun failureHandler = new NoteRun();
	client = new RequestQueueClient(
	    1, socketFactory, failureHandler, props);
	clientThread = new InterruptableThread() {
	    boolean runOnce() throws Exception {
		try {
		    client.addRequest(new DummyRequest());
		} catch (IllegalStateException e) {
		}
		return true;
	    }
	};
	clientThread.start();
	failureHandler.checkRun(MAX_RETRY);
    }	

    /* Test addRequest */

    @Test
    public void testAddRequestNullRequest() {
	client = new RequestQueueClient(
	    1, socketFactory, noopRunnable, emptyProperties);
	try {
	    client.addRequest(null);
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	}
    }

    @Test
    public void testAddRequestShutdown() {
	client = new RequestQueueClient(
	    1, socketFactory, noopRunnable, emptyProperties);
	client.shutdown();
	try {
	    client.addRequest(new DummyRequest());
	    fail("Expected IllegalStateException");
	} catch (IllegalStateException e) {
	    System.err.println(e);
	}
    }

    @Test
    public void testAddRequestSuccess() throws Exception {
	serverDispatcher.setServer(
	    1,
	    new RequestQueueServer<SimpleRequest>(
		new SimpleRequestHandler(), emptyProperties));
	client = new RequestQueueClient(
	    1, socketFactory, noopRunnable, emptyProperties);
	SimpleRequest request = new SimpleRequest(1);
	client.addRequest(request);
	Throwable exception = request.awaitCompleted(extraWait);
	if (exception == null) {
	    fail("Expected non-null result");
	}
	System.err.println(exception);
    }

    /* -- Other classes and methods -- */

    private static class SimpleRequestHandler
	implements RequestHandler<SimpleRequest>
    {
	public SimpleRequest readRequest(DataInput in) throws IOException {
	    return new SimpleRequest(in);
	}
	public void performRequest(SimpleRequest request) throws Exception {
	    request.perform();
	}
    }

    private static class SimpleRequest implements Request {
	private final int n;
	private boolean completed;
	private Throwable exception;
	SimpleRequest(int n) {
	    this.n = n;
	}
	SimpleRequest(DataInput in) throws IOException {
	    n = in.readInt();
	}
	void perform() {
	    throw new RuntimeException("Exception for request " + n);
	}
	public void writeRequest(DataOutput out) throws IOException {
	    out.writeInt(n);
	}
	public synchronized void completed(Throwable exception) {
	    completed = true;
	    this.exception = exception;
	    notifyAll();
	}
	public synchronized Throwable awaitCompleted(long timeout)
	    throws InterruptedException
	{
	    long stop = System.currentTimeMillis() + timeout;
	    while (!completed) {
		long wait = stop - System.currentTimeMillis();
		if (wait <= 0) {
		    break;
		} else {
		    wait(wait);
		}
	    }
	    if (!completed) {
		fail("Not completed");
	    }
	    return exception;
	}
	public String toString() {
	    return "SimpleRequest[n:" + n + "]";
	}
    }
}
