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

package com.sun.sgs.test.impl.service.data.store.net;

import com.sun.sgs.app.TransactionAbortedException;
import com.sun.sgs.app.TransactionNotActiveException;
import com.sun.sgs.app.TransactionTimeoutException;
import com.sun.sgs.impl.service.data.store.DataStoreImpl;
import com.sun.sgs.impl.service.data.store.net.DataStoreServerImpl;
import com.sun.sgs.test.impl.service.data.store.BasicDataStoreTestEnv;
import static com.sun.sgs.test.util.UtilDataStoreDb.getLockTimeoutPropertyName;
import static com.sun.sgs.test.util.UtilProperties.createProperties;
import com.sun.sgs.tools.test.FilteredJUnit3TestRunner;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Random;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import org.junit.runner.RunWith;

/**
 * Performs specific tests for the DataStoreServerImpl class that can't easily
 * be performed from via the DataStore interface from the client.
 */
@RunWith(FilteredJUnit3TestRunner.class)
public class TestDataStoreServerImpl extends TestCase {

    /** If this property is set, then only run the single named test method. */
    private static final String testMethod = System.getProperty("test.method");

    /**
     * Specify the test suite to include all tests, or just a single method if
     * specified.
     */
    public static TestSuite suite() {
	if (testMethod == null) {
	    return new TestSuite(TestDataStoreServerImpl.class);
	}
	TestSuite suite = new TestSuite();
	suite.addTest(new TestDataStoreServerImpl(testMethod));
	return suite;
    }

    /** The basic test environment. */
    private static final BasicDataStoreTestEnv env =
	new BasicDataStoreTestEnv(System.getProperties());

    /** The name of the DataStoreImpl class. */
    private static final String DataStoreImplClassName =
	DataStoreImpl.class.getName();

    /** The name of the DataStoreServerImpl package. */
    private static final String DataStoreNetPackage =
	"com.sun.sgs.impl.service.data.store.net";

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
	    DataStoreNetPackage + ".server.port", "0");
	props.setProperty(getLockTimeoutPropertyName(props), "100");
 	server = getDataStoreServer();
	tid = server.createTransaction(1000);
	oid = server.createObject(tid);
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
	return new DataStoreServerImpl(
	    props, env.systemRegistry, env.txnProxy);
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
	props.setProperty("com.sun.sgs.txn.timeout", "2");
	props.setProperty(DataStoreNetPackage + ".server.reap.delay", "2");
	server = getDataStoreServer();
	List<TestReaperConcurrencyThread> threads =
	    new ArrayList<TestReaperConcurrencyThread>();
	for (int i = 0; i < 5; i++) {
	    threads.add(new TestReaperConcurrencyThread(server, i));
	}
	Thread.sleep(5000);
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
		long tid = server.createTransaction(2);
		int succeeds = 0;
		int aborted = 0;
		int notActive = 0;
		while (!getDone()) {
		    long wait = 1 + random.nextInt(3);
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
			    server.prepareAndCommit(tid);
			}
			succeeds++;
		    } catch (TransactionAbortedException e) {
			abort = true;
			aborted++;
		    } catch (TransactionNotActiveException e) {
			abort = true;
			notActive++;
		    }
		    tid = server.createTransaction(2);
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

    /**
     * Test specifying negative transaction IDs to all server methods with
     * transaction ID parameters.
     */
    public void testNegativeTxnIds() {
	new AssertThrowsIllegalArgumentException() { void run() {
	    server.createObject(Long.MIN_VALUE); } };
	new AssertThrowsIllegalArgumentException() { void run() {
	    server.markForUpdate(-1, oid); } };
	new AssertThrowsIllegalArgumentException() { void run() {
	    server.getObject(-2, oid, true); } };
	new AssertThrowsIllegalArgumentException() { void run() {
	    server.setObject(-3, oid, new byte[0]); } };
	new AssertThrowsIllegalArgumentException() { void run() {
	    server.setObjects(
		-4, new long[] { oid }, new byte[][] { new byte[0] }); } };
	new AssertThrowsIllegalArgumentException() { void run() {
	    server.removeObject(-5, oid); } };
	new AssertThrowsIllegalArgumentException() { void run() {
	    server.getBinding(-6, "foo"); } };
	new AssertThrowsIllegalArgumentException() { void run() {
	    server.setBinding(-7, "foo", oid); } };
	new AssertThrowsIllegalArgumentException() { void run() {
	    server.removeBinding(-8, "foo"); } };
	new AssertThrowsIllegalArgumentException() { void run() {
	    server.nextBoundName(-9, "foo"); } };
	new AssertThrowsIllegalArgumentException() { void run() {
	    server.getClassId(-10, new byte[0]); } };
	new AssertThrowsIllegalArgumentException() {
	    void run() throws Exception {
		server.getClassInfo(-11, 3); } };
	new AssertThrowsIllegalArgumentException() { void run() {
	    server.nextObjectId(-12, 4); } };
	new AssertThrowsIllegalArgumentException() { void run() {
	    server.prepare(-13); } };
	new AssertThrowsIllegalArgumentException() { void run() {
	    server.commit(-14); } };
	new AssertThrowsIllegalArgumentException() { void run() {
	    server.prepareAndCommit(-15); } };
	new AssertThrowsIllegalArgumentException() { void run() {
	    server.abort(-16); } };
     }

    /** Run the action and check that it throws IllegalArgumentException. */
    private abstract static class AssertThrowsIllegalArgumentException {
	abstract void run() throws Exception;
	AssertThrowsIllegalArgumentException() {
	    try {
		run();
		fail("Expected IllegalArgumentException");
	    } catch (IllegalArgumentException e) {
		System.err.println(e);
	    } catch (Exception e) {
		fail("Expected IllegalArgumentException: " + e);
	    }
	}
    }

    /* -- Other tests -- */

    /**
     * Test that the maximum transaction timeout overrides the standard
     * timeout.
     */
    public void testGetObjectMaxTxnTimeout() throws Exception {
	server.shutdown();
	props.setProperty(DataStoreNetPackage + ".max.txn.timeout", "50");
	server = getDataStoreServer();
	tid = server.createTransaction(2000);
	oid = server.createObject(tid);
	Thread.sleep(1000);
	try {
	    server.getObject(tid, oid, false);
	    fail("Expected TransactionTimeoutException");
	} catch (TransactionTimeoutException e) {
	    tid = -1;
	    System.err.println(e);
	} catch (TransactionNotActiveException e) {
	    tid = -1;
	    System.err.println(e);
	}
    }

    /**
     * Test that the standard transaction timeout gets applied by the
     * reaper.
     */
    public void testGetObjectTimeoutReap() throws Exception {
	server.prepareAndCommit(tid);
	server.shutdown();
	props.setProperty(DataStoreNetPackage + ".server.reap.delay", "50");
	server = getDataStoreServer();
	tid = server.createTransaction(100);
	server.setBinding(tid, "dummy", oid);
	Thread.sleep(200);
	try {
	    server.getBinding(tid, "dummy");
	    fail("Expected TransactionTimeoutException");
	} catch (TransactionNotActiveException e) {
	    System.err.println(e);
	}
	try {
	    server.abort(tid);
	    fail("Expected TransactionNotActiveException");
	} catch (TransactionNotActiveException e) {
	    System.err.println(e);
	    tid = -1;
	}
    }

    /**
     * Test that the standard transaction timeout gets applied, without the
     * reaper kicking in.
     */
    public void testGetObjectTimeoutNoReap() throws Exception {
	server.prepareAndCommit(tid);
	server.shutdown();
	props.setProperty(DataStoreNetPackage + ".server.reap.delay", "10000");
	server = getDataStoreServer();
	tid = server.createTransaction(100);
	server.setBinding(tid, "dummy", oid);
	Thread.sleep(200);
	try {
	    server.getBinding(tid, "dummy");
	    fail("Expected TransactionTimeoutException");
	} catch (TransactionTimeoutException e) {
	    System.err.println(e);
	}
    }

    /** Test illegal argument for bad transaction timeout. */
    public void testCreateTransactionBadTimeout() {
	try {
	    server.createTransaction(-3);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
	try {
	    server.createTransaction(0);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
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

    /** Use this thread to control a call to shutdown that may block. */
    protected class ShutdownAction extends Thread {
	private boolean done;
	private Throwable exception;

	/** Creates an instance of this class and starts the thread. */
	protected ShutdownAction() {
	    start();
	}

	/** Performs the shutdown and collects the results. */
	public void run() {
	    try {
		shutdown();
	    } catch (Throwable t) {
		exception = t;
	    }
	    synchronized (this) {
		done = true;
		notifyAll();
	    }
	}

	protected void shutdown() {
	    server.shutdown();
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
		return true;
	    } else if (exception instanceof Exception) {
		throw (Exception) exception;
	    } else {
		throw (Error) exception;
	    }
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
	tid = server.createTransaction(1000);
	/* Get write lock in txn 2 */
	final long tid2 = server.createTransaction(1000);
	server.setObject(tid2, oid, new byte[0]);
	/* Block getting read lock in txn 1 */
	final Semaphore flag = new Semaphore(0);
	final AtomicBoolean aborted = new AtomicBoolean(false);
	Thread thread = new Thread() {
	    public void run() {
		try {
		    flag.release();
		    server.getObject(tid, oid, false);
		} catch (TransactionAbortedException e) {
		    System.err.println(e);
		    aborted.set(true);
		} catch (Exception e) {
		    fail("Unexpected exception: " + e);
		} finally {
		    flag.release();
		}
	    }
	};
	thread.start();
	/* Wait for thread to start */
	assertTrue("Blocking thread did not start",
		   flag.tryAcquire(100, TimeUnit.MILLISECONDS));
	/* Wait for the operation to block */
	Thread.sleep(100);
	/* Concurrent access */
	try {
	    action.run();
	    fail("Expected IllegalStateException");
	} catch (IllegalStateException e) {
	    System.err.println(e);
	} finally {
	    /* Clean up */
	    server.abort(tid2);
	    assertTrue("Blocking thread didn't complete",
		       flag.tryAcquire(100, TimeUnit.MILLISECONDS));
	    if (aborted.get()) {
		tid = -1;
	    }
	}
    }
}
