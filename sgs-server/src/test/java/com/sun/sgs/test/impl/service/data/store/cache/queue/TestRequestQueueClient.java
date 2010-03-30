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
import com.sun.sgs.impl.service.data.store.cache.queue.RequestQueueClient;
import com.sun.sgs.impl.service.data.store.cache.queue.RequestQueueClient.
    BasicSocketFactory;
import com.sun.sgs.impl.service.data.store.cache.queue.RequestQueueClient.
    SocketFactory;
import com.sun.sgs.impl.service.data.store.cache.queue.RequestQueueListener;
import com.sun.sgs.impl.service.data.store.cache.queue.RequestQueueServer;
import com.sun.sgs.tools.test.FilteredNameRunner;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.FilterInputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Collections;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicBoolean;
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

    /** The queue size for use in tests. */
    private static final int QUEUE_SIZE = 100;

    /** The request queue listener server dispatcher. */
    private SimpleServerDispatcher serverDispatcher;

    /** A failure reporter for checking if failure occurred. */
    private NoteFailure failureReporter;

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
	failureReporter = new NoteFailure();
	listener = new RequestQueueListener(
	    new ServerSocket(PORT), serverDispatcher, failureReporter,
	    MAX_RETRY, RETRY_WAIT);
    }

    @After
    public void afterTest() throws Exception {
	if (failureReporter != null) {
	    failureReporter.checkNotCalled();
	}
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
	if (serverDispatcher != null) {
	    serverDispatcher.shutdown();
	}
    }

    /* -- Tests -- */

    /* Test constructor */

    @Test(expected=IllegalArgumentException.class)
    public void testConstructorNegativeNodeId() {
	new RequestQueueClient(-1, socketFactory, failureReporter, MAX_RETRY,
			       RETRY_WAIT, QUEUE_SIZE);
    }

    @Test(expected=NullPointerException.class)
    public void testConstructorNullSocketFactory() {
	new RequestQueueClient(1, null, failureReporter, MAX_RETRY, RETRY_WAIT,
			       QUEUE_SIZE);
    }

    @Test(expected=NullPointerException.class)
    public void testConstructorNullFailureHandler() {
	new RequestQueueClient(1, socketFactory, null, MAX_RETRY, RETRY_WAIT,
			       QUEUE_SIZE);
    }

    @Test(expected=IllegalArgumentException.class)
    public void testConstructorIllegalMaxRetry() {
	new RequestQueueClient(1, socketFactory, failureReporter, 0, RETRY_WAIT,
			       QUEUE_SIZE);
    }

    @Test(expected=IllegalArgumentException.class)
    public void testConstructorIllegalRetryWait() {
	new RequestQueueClient(1, socketFactory, failureReporter, MAX_RETRY, 0,
			       QUEUE_SIZE);
    }

    @Test(expected=IllegalArgumentException.class)
    public void testConstructorQueueSizeTooSmall() {
	new RequestQueueClient(1, socketFactory, failureReporter, MAX_RETRY,
			       RETRY_WAIT, 0);
    }

    @Test(expected=IllegalArgumentException.class)
    public void testConstructorQueueSizeTooBig() {
	new RequestQueueClient(
	    1, socketFactory, failureReporter, MAX_RETRY, RETRY_WAIT,
	    RequestQueueServer.MAX_OUTSTANDING + 1);
    }

    /* Test connection handling */

    @Test
    public void testConnectionServerSocketClosed() throws Exception {
	listener.shutdown();
	NoteFailure failureReporter = new NoteFailure();
	client = new RequestQueueClient(
	    1, socketFactory, failureReporter, MAX_RETRY, RETRY_WAIT,
	    QUEUE_SIZE);
	failureReporter.checkCalled(MAX_RETRY);
    }

    @Test
    public void testConnectionServerUnknown() throws Exception {
	NoteFailure failureReporter = new NoteFailure();
	client = new RequestQueueClient(
	    1, socketFactory, failureReporter, MAX_RETRY, RETRY_WAIT,
	    QUEUE_SIZE);
	failureReporter.checkCalled(MAX_RETRY);
    }

    /* Test addRequest */

    @Test
    public void testAddRequestNullRequest() {
	client = new RequestQueueClient(
	    1, socketFactory, failureReporter, MAX_RETRY, RETRY_WAIT,
	    QUEUE_SIZE);
	try {
	    client.addRequest(null);
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	}
    }

    @Test
    public void testAddRequestShutdown() {
	client = new RequestQueueClient(
	    1, socketFactory, failureReporter, MAX_RETRY, RETRY_WAIT,
	    QUEUE_SIZE);
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
		1, new SimpleRequestHandler()));
	client = new RequestQueueClient(
	    1, socketFactory, failureReporter, MAX_RETRY, RETRY_WAIT,
	    QUEUE_SIZE);
	SimpleRequest request = new SimpleRequest(1);
	client.addRequest(request);
	request.awaitCompleted(extraWait);
    }

    /* Test shutdown */

    @Test
    public void testShutdownNoRequests() {
	serverDispatcher.setServer(
	    1,
	    new RequestQueueServer<SimpleRequest>(
		1, new SimpleRequestHandler()));
	NoteFailure failureReporter = new NoteFailure();
	client = new RequestQueueClient(
	    1, socketFactory, failureReporter, MAX_RETRY, RETRY_WAIT,
	    QUEUE_SIZE);
	client.shutdown();
	client.shutdown();
	failureReporter.checkNotCalled();
    }

    @Test
    public void testShutdownAfterRequest() throws Exception {
	serverDispatcher.setServer(
	    1,
	    new RequestQueueServer<SimpleRequest>(
		1, new SimpleRequestHandler()));
	NoteFailure failureReporter = new NoteFailure();
	client = new RequestQueueClient(
	    1, socketFactory, failureReporter, MAX_RETRY, RETRY_WAIT,
	    QUEUE_SIZE);
	SimpleRequest request = new SimpleRequest(1);
	client.addRequest(request);
	request.awaitCompleted(extraWait);
	client.shutdown();
	client.shutdown();
	failureReporter.checkNotCalled();
    }

    @Test
    public void testShutdownDuringRequests() throws Exception {
	serverDispatcher.setServer(
	    1,
	    new RequestQueueServer<SimpleRequest>(
		1, new SimpleRequestHandler()));
	NoteFailure failureReporter = new NoteFailure();
	client = new RequestQueueClient(
	    1, socketFactory, failureReporter, MAX_RETRY, RETRY_WAIT,
	    QUEUE_SIZE);
	final SimpleRequest firstRequest = new SimpleRequest(1);
	clientThread = new InterruptableThread() {
	    private int next = 1;
	    boolean runOnce() {
		try {
		    client.addRequest(
			(next == 1 ? firstRequest : new SimpleRequest(next)));
		    next++;
		    return false;
		} catch (IllegalStateException e) {
		    return true;
		}
	    }
	};
	clientThread.start();
	firstRequest.awaitCompleted(100000);
	client.shutdown();
	client.shutdown();
	failureReporter.checkNotCalled();
    }

    /* Test sending requests */

    @Test
    public void testSendRequests() throws Exception {
	final int total = Integer.getInteger("test.message.count", 1000);
	final BlockingDeque<SimpleRequest> requests =
	    new LinkedBlockingDeque<SimpleRequest>();
	serverDispatcher.setServer(
	    1,
	    new RequestQueueServer<SimpleRequest>(
		1, new SimpleRequestHandler()));
	client = new RequestQueueClient(
	    1, socketFactory, failureReporter, MAX_RETRY, RETRY_WAIT,
	    QUEUE_SIZE);
	clientThread = new InterruptableThread() {
	    private int count = 0;
	    boolean runOnce() throws Exception {
		SimpleRequest request = new SimpleRequest(++count);
		client.addRequest(request);
		requests.putLast(request);
		return count > total;
	    }
	};
	long start = System.currentTimeMillis();
	clientThread.start();
	InterruptableThread receiveThread = new InterruptableThread() {
	    private int count = 0;
	    boolean runOnce() throws Exception {
		SimpleRequest request = requests.takeFirst();
		request.awaitCompleted(extraWait);
		return ++count > total;
	    }
	};
	receiveThread.start();
	try {
	    receiveThread.join();
	    long elapsed = System.currentTimeMillis() - start;
	    System.err.println(total + " messages in " + elapsed + " ms: " +
			       (((double) elapsed) / total) + " ms/message");
	} finally {
	    receiveThread.shutdown();
	}
    }

    /**
     * Test sending a stream of requests while there are random failures on the
     * client and the server.  Also test having the last 50 requests throw a
     * different exception, and make sure that only the first of those requests
     * gets performed.
     */
    @Test
    public void testSendRequestsWithIOFailures() throws Exception {
	final NoteFailure failureReporter = new NoteFailure();
	final int total =
	    Math.max(100, Integer.getInteger("test.message.count", 1000));
	final long seed =
	    Long.getLong("test.seed", System.currentTimeMillis());
	System.err.println("test.seed=" + seed);
	final BlockingDeque<SimpleRequest> requests =
	    new LinkedBlockingDeque<SimpleRequest>();
	serverDispatcher.setServer(
	    1,
	    new RequestQueueServer<SimpleRequest>(
		1,
		new FailingRequestHandler(new Random(seed), total - 50)));
	client = new RequestQueueClient(
	    1, new FailingSocketFactory(new Random(seed + 1)),
	    failureReporter, MAX_RETRY, RETRY_WAIT, QUEUE_SIZE);
	final AtomicBoolean clientDone = new AtomicBoolean(false);
	clientThread = new InterruptableThread() {
	    private int count = 0;
	    boolean runOnce() throws Exception {
		SimpleRequest request = new SimpleRequest(++count);
		try {
		    client.addRequest(request);
		    System.err.println("Added request " + request);
		} catch (IllegalStateException e) {
		    if (count > total - 50) {
			clientDone.set(true);
			return true;
		    } else {
			throw e;
		    }
		}
		requests.putLast(request);
		return count > total;
	    }
	};
	clientThread.start();
	final Set<SimpleRequest> failingRequests =
	    Collections.synchronizedSet(new HashSet<SimpleRequest>());
	InterruptableThread receiveThread = new InterruptableThread() {
	    private int count = 0;
	    private String lastMessage;
	    boolean runOnce() throws Exception {
		SimpleRequest request;
		try {
		    request = requests.takeFirst();
		} catch (InterruptedException e) {
		    if (clientDone.get()) {
			return true;
		    } else {
			throw e;
		    }
		}
		++count;
		if (count < total - 50) {
		    request.awaitCompleted(5000);
		    assertEquals(null, failureReporter.getException());
		} else {
		    failingRequests.add(request);
		}
		return count > total;
	    }
	};
	receiveThread.start();
	try {
	    receiveThread.join();
	} finally {
	    receiveThread.shutdown();
	}
	Thread.sleep(1000);
	for (SimpleRequest request : failingRequests) {
	    assertFalse("Completed", request.getCompleted());
	}
	Throwable exception = failureReporter.getException();
	assertFalse("Null exception", exception == null);
	assertEquals(RuntimeException.class, exception.getClass());
    }

    @Test
    public void testSendRequestNoResponse() throws Exception {
	serverDispatcher.setServer(
	    1,
	    new RequestQueueServer<SimpleRequest>(
		1,
		new SimpleRequestHandler() {
		    public void performRequest(SimpleRequest request)
			throws Exception
		    {
			long stop = System.currentTimeMillis() + 4 * MAX_RETRY;
			while (true) {
			    long sleep = stop - System.currentTimeMillis();
			    if (sleep <= 0) {
				break;
			    }
			    try {
				Thread.sleep(sleep);
			    } catch (InterruptedException e) {
			    }
			}
		    }
		}));
	NoteFailure failureReporter = new NoteFailure();
	client = new RequestQueueClient(
	    1, socketFactory, failureReporter, MAX_RETRY, RETRY_WAIT,
	    QUEUE_SIZE);
	client.addRequest(new SimpleRequest(1));
	failureReporter.checkCalled(2 * MAX_RETRY);
    }

    @Test
    public void testSendRequestThrowsException() throws Exception {
	serverDispatcher.setServer(
	    1,
	    new RequestQueueServer<SimpleRequest>(
		1,
		new SimpleRequestHandler() {
		    public void performRequest(SimpleRequest request)
			throws Exception
		    {
			throw new Exception("Test");
		    }
		}));
	NoteFailure failureReporter = new NoteFailure();
	client = new RequestQueueClient(
	    1, socketFactory, failureReporter, MAX_RETRY, RETRY_WAIT,
	    QUEUE_SIZE);
	client.addRequest(new SimpleRequest(1));
	failureReporter.checkCalled(0);
	Throwable exception = failureReporter.getException();
	System.err.println("Exception: " + String.valueOf(exception));
	assertTrue("Expected Exception with message 'Test'",
		   exception instanceof Exception &&
		   "Test".equals(exception.getMessage()));
    }

    @Test
    public void testSendRequestThrowsAssertionError() throws Exception {
	serverDispatcher.setServer(
	    1,
	    new RequestQueueServer<SimpleRequest>(
		1,
		new SimpleRequestHandler() {
		    public void performRequest(SimpleRequest request)
			throws Exception
		    {
			throw new AssertionError("Test");
		    }
		}));
	NoteFailure failureReporter = new NoteFailure();
	client = new RequestQueueClient(
	    1, socketFactory, failureReporter, MAX_RETRY, RETRY_WAIT,
	    QUEUE_SIZE);
	client.addRequest(new SimpleRequest(1));
	failureReporter.checkCalled(0);
	Throwable exception = failureReporter.getException();
	System.err.println("Exception: " + String.valueOf(exception));
	assertTrue(
	    "Expected RuntimeException with non-null message: " + exception,
	    exception instanceof RuntimeException &&
	    exception.getMessage() != null);
    }

    /* -- Other classes and methods -- */

    /** A request handler for {@link SimpleRequest}. */
    private static class SimpleRequestHandler
	implements RequestHandler<SimpleRequest>
    {
	SimpleRequestHandler() {
	    SimpleRequest.resetLastPerformed();
	}
	public SimpleRequest readRequest(DataInput in) throws IOException {
	    return new SimpleRequest(in);
	}
	public void performRequest(SimpleRequest request) throws Exception {
	    request.perform();
	}
    }

    /** Numbered requests that checks that they are performed in order. */
    private static class SimpleRequest implements Request {

	/** The last request performed or {@code 0}. */
	private static int lastPerformed = 0;

	/** The number of this request. */
	private final int n;

	/** Whether this request has been completed. */
	private boolean completed;

	/**
	 * Creates an instance of this class.
	 *
	 * @param	n the number of this request
	 */
	SimpleRequest(int n) {
	    this.n = n;
	}

	/**
	 * Creates an instance of this class.
	 *
	 * @param	in the data input stream for reading the number of this
	 *		request
	 * @throws	IOException if an I/O failure occurs
	 */

	SimpleRequest(DataInput in) throws IOException {
	    n = in.readInt();
	}

	/** Resets the number of the last request performed. */
	static synchronized void resetLastPerformed() {
	    lastPerformed = 0;
	}

	/** Performs this request. */
	void perform() {
	    synchronized (SimpleRequest.class) {
		if (n != lastPerformed + 1) {
		    throw new RuntimeException(
			"Performing request " + n + ", but expected " +
			(lastPerformed + 1));
		}
		lastPerformed = n;
	    }
	}

	/** {@inheritDoc} */
	public void writeRequest(DataOutput out) throws IOException {
	    out.writeInt(n);
	}

	/** {@inheritDoc} */
	public synchronized void completed() {
	    completed = true;
	    notifyAll();
	}

	/**
	 * Waits for the request to be completed.
	 *
	 * @param	timeout the number of milliseconds to wait
	 * @throws	InterruptedException if the current thread is
	 *		interrupted
	 */
	synchronized void awaitCompleted(long timeout)
	    throws InterruptedException
	{
	    long start = System.currentTimeMillis();
	    long stop = start + timeout;
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
	    System.err.println("SimpleRequest.awaitCompleted actual wait: " +
			       (System.currentTimeMillis() - start));
	}

	/** Returns whether the request was completed. */
	synchronized boolean getCompleted() {
	    return completed;
	}

	@Override
	public String toString() {
	    return "SimpleRequest[n:" + n + "]";
	}
    }

    /**
     * Defines a request handler which gets I/O failures randomly in the second
     * half of each group of 20 requests read, and which fails when performing
     * requests starting with the specified request number.
     */
    private static class FailingRequestHandler extends SimpleRequestHandler {
	private final Random random;
	private final int requestFails;
	private int readCount;
	private int performCount;
	FailingRequestHandler(Random random, int requestFails) {
	    this.random = random;
	    this.requestFails = requestFails;
	}
	public SimpleRequest readRequest(DataInput in) throws IOException {
	    if ((readCount++ % 20) >= 10 && random.nextInt(10) == 0) {
		throw new IOException("Random server request I/O failure");
	    }
	    return new SimpleRequest(in);
	}
	public void performRequest(SimpleRequest request) throws Exception {
	    if (++performCount >= requestFails) {
		throw new RuntimeException("Request fails: " + performCount);
	    } else {
		super.performRequest(request);
	    }
	}
    }

    /**
     * Defines a socket factory that generates sockets whose input stream fails
     * randomly in the first half of each 1000 bytes of input and whose output
     * stream fails randomly in the second half of each 1000 bytes of output.
     */
    private static class FailingSocketFactory implements SocketFactory {
	private final Random random;
	FailingSocketFactory(Random random) {
	    this.random = random;
	}
	public Socket createSocket() throws IOException {
	    return new RandomFailingSocket();
	}
	private class RandomFailingSocket extends Socket {
	    RandomFailingSocket() throws IOException {
		super("localhost", PORT);
	    }
	    public InputStream getInputStream() throws IOException {
		return new WrappedInputStream(super.getInputStream());
	    }
	    public OutputStream getOutputStream() throws IOException {
		return new WrappedOutputStream(super.getOutputStream());
	    }
	}
	private class WrappedInputStream extends FilterInputStream {
	    private int byteCount;
	    WrappedInputStream(InputStream in) {
		super(in);
	    }
	    private int maybeFail(int n) throws IOException {
		int numBytes = Math.max(1, n);
		byteCount += numBytes;
		if ((byteCount % 1000) < 500 && random.nextInt(100) == 0) {
		    throw new IOException("Random client receive I/O failure");
		}
		return n;
	    }
	    public int read() throws IOException {
		return maybeFail(super.read());
	    }
	    public int read(byte b[]) throws IOException {
		return maybeFail(super.read(b));
	    }
	    public int read(byte b[], int off, int len) throws IOException {
		return maybeFail(super.read(b, off, len));
	    }
	}
	private class WrappedOutputStream extends FilterOutputStream {
	    private int byteCount;
	    private void maybeFail(int n) throws IOException {
		int numBytes = Math.max(1, n);
		byteCount += numBytes;
		if ((byteCount % 1000) > 500 && random.nextInt(100) == 0) {
		    throw new IOException("Random client send I/O failure");
		}
	    }
	    WrappedOutputStream(OutputStream out) {
		super(out);
	    }
	    public void write(int b) throws IOException {
		maybeFail(1);
		super.write(b);
	    }
	    public void write(byte b[]) throws IOException {
		maybeFail(b.length);
		super.write(b);
	    }
	    public void write(byte b[], int off, int len) throws IOException {
		maybeFail(len);
		super.write(b, off, len);
	    }
	}
    }
}
