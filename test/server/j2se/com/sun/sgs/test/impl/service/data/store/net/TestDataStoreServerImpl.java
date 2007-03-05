/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.test.impl.service.data.store.net;

import com.sun.sgs.app.TransactionAbortedException;
import com.sun.sgs.app.TransactionNotActiveException;
import com.sun.sgs.impl.service.data.store.DataStoreImpl;
import com.sun.sgs.impl.service.data.store.net.DataStoreServerImpl;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Random;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import junit.framework.TestCase;

/**
 * Performs specific tests for the DataStoreServerImpl class that can't easily
 * be performed from via the DataStore interface from the client.
 */
public class TestDataStoreServerImpl extends TestCase {

    /** The name of the DataStoreImpl class. */
    private static final String DataStoreImplClassName =
	DataStoreImpl.class.getName();

    /** The name of the DataStoreServerImpl class. */
    private static final String DataStoreServerImplClassName =
	DataStoreServerImpl.class.getName();

    /** Directory used for database shared across multiple tests. */
    private static String dbDirectory =
	System.getProperty("java.io.tmpdir") + File.separator +
	"TestDataStoreImpl.db";

    /** Make sure an empty version of the directory exists. */
    static {
	cleanDirectory(dbDirectory);
    }

    /** Set when the test passes. */
    private boolean passed;

    /** A per-test database directory, or null if not created. */
    private String directory;

    /** Properties for creating the DataStore. */
    private  Properties props;

    /** The server. */
    private DataStoreServerImpl server;

    /** The current transaction ID. */
    private long tid;

    /** An object ID. */
    private long oid;

    /** Creates an instance. */
    public TestDataStoreServerImpl(String name) {
	super(name);
    }

    /** Prints the test case. */
    protected void setUp() throws Exception {
	System.err.println("Testcase: " + getName());
	props = createProperties(
	    DataStoreImplClassName + ".directory", dbDirectory,
	    DataStoreServerImplClassName + ".port", "0");
	server = getDataStoreServer();
	tid = server.createTransaction();
	oid = server.allocateObjects(1);
    }

    /** Sets passed if the test passes. */
    protected void runTest() throws Throwable {
	super.runTest();
	passed = true;
    }

    /** Shutdowns the server. */
    protected void tearDown() throws Exception {
	try {
	    if (server != null) {
		if (tid >= 0) {
		    server.abort(tid);
		    tid = -1;
		}
		new ShutdownAction().waitForDone();
	    }
	} catch (RuntimeException e) {
	    if (passed) {
		throw e;
	    } else {
		e.printStackTrace();
	    }
	}
	server = null;
    }

    /**
     * Create a DataStoreClient, set any default properties, and start the
     * server, if needed.
     */
    DataStoreServerImpl getDataStoreServer() throws Exception {
	return new DataStoreServerImpl(props);
    }

    /* -- Tests -- */

    /* -- Test concurrent access -- */

    public void testMarkForUpdateConcurrent() throws Exception {
	testConcurrent(
	    new Runnable() {
		public void run() { server.markForUpdate(tid, oid); }
	    });
    }

    public void testGetObjectConcurrent() throws Exception {
	testConcurrent(
	    new Runnable() {
		public void run() { server.getObject(tid, oid, false); }
	    });
    }

    public void testSetObjectConcurrent() throws Exception {
	testConcurrent(
	    new Runnable() {
		public void run() { server.setObject(tid, oid, new byte[0]); }
	    });
    }

    public void testSetObjectsConcurrent() throws Exception {
	testConcurrent(
	    new Runnable() {
		public void run() {
		    server.setObjects(
			tid, new long[] { oid }, new byte[][] { { 0 } }); }
	    });
    }

    public void testRemoveObjectConcurrent() throws Exception {
	testConcurrent(
	    new Runnable() {
		public void run() { server.removeObject(tid, oid); }
	    });
    }

    public void testGetBindingConcurrent() throws Exception {
	testConcurrent(
	    new Runnable() {
		public void run() { server.getBinding(tid, "dummy"); }
	    });
    }

    public void testSetBindingConcurrent() throws Exception {
	testConcurrent(
	    new Runnable() {
		public void run() { server.setBinding(tid, "dummy", oid); }
	    });
    }

    public void testRemoveBindingConcurrent() throws Exception {
	testConcurrent(
	    new Runnable() {
		public void run() { server.removeBinding(tid, "dummy"); }
	    });
    }

    public void testNextBoundNameConcurrent() throws Exception {
	testConcurrent(
	    new Runnable() {
		public void run() { server.nextBoundName(tid, "dummy"); }
	    });
    }

    /* -- Test current access against the transaction reaper -- */

    public void testReaperConcurrency() throws Exception {
	server.setBinding(tid, "dummy", oid);
	server.prepareAndCommit(tid);
	tid = -1;
	tearDown();
	props.setProperty("com.sun.sgs.txnTimeout", "2");
	props.setProperty(DataStoreServerImplClassName + ".reap.delay", "2");
	server = getDataStoreServer();
	List<TestReaperConcurrencyThread> threads =
	    new ArrayList<TestReaperConcurrencyThread>();
	for (int i = 0; i < 5; i++) {
	    threads.add(new TestReaperConcurrencyThread(server, i));
	}
	Thread.sleep(10000);
	TestReaperConcurrencyThread.setDone();
	for (TestReaperConcurrencyThread thread : threads) {
	    Throwable t = thread.getResult();
	    if (t != null) {
		t.printStackTrace();
		fail("Unexpected exception: " + t);
	    }
	}
    }

    /** Thread for hammering on expired transactions. */
    private static class TestReaperConcurrencyThread extends Thread {
	static final Random random = new Random();
	static boolean done;
	private final DataStoreServerImpl server;
	private final int id;
	private boolean threadDone;
	private Throwable result;
	TestReaperConcurrencyThread(DataStoreServerImpl server, int id) {
	    super("TestReaperConcurrencyThread" + id);
	    this.server = server;
	    this.id = id;
	    start();
	}
	static synchronized void setDone() {
	    done = true;
	}
	static synchronized boolean getDone() {
	    return done;
	}
	synchronized Throwable getResult() {
	    while (!threadDone) {
		try {
		    wait();
		} catch (InterruptedException e) {
		    return e;
		}
	    }
	    return result;
	}
	public void run() {
	    try {
		long tid = server.createTransaction();
		int succeeds = 0;
		int aborted = 0;
		int notActive = 0;
		while (!getDone()) {
		    long wait = 1 + random.nextInt(4);
		    try {
			Thread.sleep(wait);
		    } catch (InterruptedException e) {
			break;
		    }
		    boolean abort = random.nextBoolean();
		    try {
			if (abort) {
			    server.abort(tid);
			} else {
			    server.getBinding(tid, "dummy");
			}
			succeeds++;
		    } catch (TransactionAbortedException e) {
			abort = true;
			aborted++;
		    } catch (TransactionNotActiveException e) {
			abort = true;
			notActive++;
		    }
		    if (abort) {
			tid = server.createTransaction();
		    }
		}
		System.err.println(getName() +
				   ": succeeds:" + succeeds +
				   ", aborted:" + aborted +
				   ", notActive:" + notActive);
	    } catch (Throwable t) {
		result = t;
	    } finally {
		synchronized (this) {
		    threadDone = true;
		    notifyAll();
		}
	    }
	}
    }

    /* -- Other methods and classes -- */

    /** Creates a per-test directory. */
    private String createDirectory() throws IOException {
	File dir = File.createTempFile(getName(), "dbdir");
	if (!dir.delete()) {
	    throw new RuntimeException("Problem deleting file: " + dir);
	}
	if (!dir.mkdir()) {
	    throw new RuntimeException(
		"Failed to create directory: " + dir);
	}
	directory = dir.getPath();
	return directory;
    }

    /** Insures an empty version of the directory exists. */
    private static void cleanDirectory(String directory) {
	File dir = new File(directory);
	if (dir.exists()) {
	    for (File f : dir.listFiles()) {
		if (!f.delete()) {
		    throw new RuntimeException("Failed to delete file: " + f);
		}
	    }
	    if (!dir.delete()) {
		throw new RuntimeException(
		    "Failed to delete directory: " + dir);
	    }
	}
	if (!dir.mkdir()) {
	    throw new RuntimeException(
		"Failed to create directory: " + dir);
	}
	System.err.println("Cleaned directory");
    }

    /** Creates a property list with the specified keys and values. */
    private static Properties createProperties(String... args) {
	Properties props = new Properties();
	if (args.length % 2 != 0) {
	    throw new RuntimeException("Odd number of arguments");
	}
	for (int i = 0; i < args.length; i += 2) {
	    props.setProperty(args[i], args[i + 1]);
	}
	return props;
    }

    /** Use this thread to control a call to shutdown that may block. */
    protected class ShutdownAction extends Thread {
	private boolean done;
	private Throwable exception;
	private boolean result;

	/** Creates an instance of this class and starts the thread. */
	protected ShutdownAction() {
	    start();
	}

	/** Performs the shutdown and collects the results. */
	public void run() {
	    try {
		result = shutdown();
	    } catch (Throwable t) {
		exception = t;
	    }
	    synchronized (this) {
		done = true;
		notifyAll();
	    }
	}

	protected boolean shutdown() {
	    return server.shutdown();
	}

	/** Asserts that the shutdown call is blocked. */
	public synchronized void assertBlocked() throws InterruptedException {
	    Thread.sleep(5);
	    assertEquals("Expected no exception", null, exception);
	    assertFalse("Expected shutdown to be blocked", done);
	}
	
	/** Waits a while for the shutdown call to complete. */
	public synchronized boolean waitForDone() throws Exception {
	    waitForDoneInternal();
	    if (!done) {
		return false;
	    } else if (exception == null) {
		return result;
	    } else if (exception instanceof Exception) {
		throw (Exception) exception;
	    } else {
		throw (Error) exception;
	    }
	}

	/**
	 * Asserts that the shutdown call has completed with the specified
	 * result.
	 */
	public synchronized void assertResult(boolean expectedResult)
	    throws InterruptedException
	{
	    waitForDoneInternal();
	    assertTrue("Expected shutdown to be done", done);
	    assertEquals("Unexpected result", expectedResult, result);
	    assertEquals("Expected no exception", null, exception);
	}

	/** Wait until done, but give up after a while. */
	private synchronized void waitForDoneInternal()
	    throws InterruptedException
	{
	    long wait = 2000;
	    long start = System.currentTimeMillis();
	    while (!done && wait > 0) {
		wait(wait);
		long now = System.currentTimeMillis();
		wait -= (now - start);
		start = now;
	    }
	}
    }

    /**
     * Test that the action throws IllegalStateException if called while
     * another thread is blocked calling on the same transaction.
     */
    private void testConcurrent(final Runnable action) throws Exception {
	/* Create an object */
	server.setObject(tid, oid, new byte[0]);
	server.prepareAndCommit(tid);
	tid = server.createTransaction();
	/* Get write lock in txn 2 */
	final long tid2 = server.createTransaction();
	server.setObject(tid2, oid, new byte[0]);
	/* Block getting read lock in txn 1 */
	final Semaphore flag = new Semaphore(0);
	Thread thread = new Thread() {
	    public void run() {
		try {
		    flag.release();
		    server.getObject(tid, oid, false);
		    flag.release();
		} catch (Exception e) {
		    fail("Unexpected exception: " + e);
		}
	    }
	};
	thread.start();
	/* Wait for thread to block */
	assertTrue(flag.tryAcquire(100, TimeUnit.MILLISECONDS));
	Thread.sleep(10);
	/* Concurrent access */
	try {
	    action.run();
	    fail("Expected IllegalStateException");
	} catch (IllegalStateException e) {
	    System.err.println(e);
	} finally {
	    /* Clean up */
	    server.abort(tid2);
	    assertTrue(flag.tryAcquire(100, TimeUnit.MILLISECONDS));
	}
    }
}
