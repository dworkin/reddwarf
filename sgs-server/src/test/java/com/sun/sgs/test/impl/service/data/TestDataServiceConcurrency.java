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

package com.sun.sgs.test.impl.service.data;

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.TransactionAbortedException;
import com.sun.sgs.auth.Identity;
import com.sun.sgs.impl.sharedutil.LoggerWrapper;
import com.sun.sgs.kernel.TransactionScheduler;
import com.sun.sgs.service.DataService;
import com.sun.sgs.test.util.DummyManagedObject;
import com.sun.sgs.test.util.SgsTestNode;
import com.sun.sgs.test.util.TestAbstractKernelRunnable;
import com.sun.sgs.tools.test.FilteredNameRunner;
import java.util.Properties;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Test concurrent operation of the data service. */
@SuppressWarnings("hiding")
@RunWith(FilteredNameRunner.class)
public class TestDataServiceConcurrency extends Assert {

    /** Logger for this test. */
    private static final LoggerWrapper logger =
	new LoggerWrapper(
	    Logger.getLogger(TestDataServiceConcurrency.class.getName()));

    /** The name of the DataStoreImpl class. */
    private static final String DataStoreImplClass =
	"com.sun.sgs.impl.service.data.store.DataStoreImpl";

    /**
     * The types of operations to perform.  Each value includes the operations
     * for the earlier values.
     */
    static enum WhichOperations {
	/** Get bindings and objects. */
	GET(1),
	/** Set bindings to existing objects. */
	SET(2),
	/** Modify existing objects. */
	MODIFY(3),
	/** Create new objects. */
	CREATE(4);
	final int value;
	WhichOperations(int value) {
	    this.value = value;
	}
    }

    /** Which operations to perform. */
    protected WhichOperations whichOperations =
	WhichOperations.valueOf(
	    System.getProperty("test.which.operations", "MODIFY"));

    /** The number of operations to perform. */
    protected int operations = Integer.getInteger("test.operations", 10000);

    /** The maximum number of objects to allocate per thread. */
    protected int objects = Integer.getInteger("test.objects", 1000);

    /**
     * The number of objects to allocate as a buffer between objects allocated
     * by different threads.
     */
    protected int objectsBuffer = 500;

    /** The initial number of concurrent threads. */
    protected int threads = Integer.getInteger("test.threads", 2);

    /** The maximum number of concurrent threads. */
    protected int maxThreads = Integer.getInteger("test.max.threads", threads);

    /** The number of times to repeat each timing. */
    protected int repeat = Integer.getInteger("test.repeat", 1);

    /**
     * The exception thrown by one of the threads, or null if none of the
     * threads have failed.
     */
    private Throwable failure;

    /** The total number of aborts seen by the various threads. */
    private int aborts;

    /** The total number of commits seen by the various threads. */
    private int commits;

    /** The number of threads that are done. */
    private int done;

    /** The server node. */
    private SgsTestNode serverNode = null;

    /** The data service. */
    private DataService service;

    /** The transaction scheduler. */
    private TransactionScheduler txnScheduler;

    /** The owner of the tests. */
    private Identity taskOwner;

    /** Creates the test. */
    public TestDataServiceConcurrency() { }

    /** Prints the test parameters and sets up the server. */
    @Before
    public void setUp() throws Exception {
	System.err.println(
	    "Parameters:" +
	    "\n  test.which.operations=" + whichOperations +
	    "\n  test.operations=" + operations +
	    "\n  test.objects=" + objects +
	    "\n  test.threads=" + threads +
	    (maxThreads != threads ?
	     "\n  test.max.threads=" + maxThreads : "") +
	    (repeat != 1 ? "\n  test.repeat=" + repeat : ""));

        Properties props = getNodeProps();
        serverNode = new SgsTestNode("TestDataServiceConcurrency", null, props);
	txnScheduler = serverNode.getSystemRegistry().
            getComponent(TransactionScheduler.class);
	service = serverNode.getDataService();
	taskOwner = serverNode.getProxy().getCurrentOwner();
    }

    /** Shuts down the server. */
    @After
    public void tearDown() throws Exception {
	if (serverNode != null) {
	    try {
		shutdown();
	    } catch (RuntimeException e) {
		e.printStackTrace();
		throw e;
	    }
	}
    }

    /** Shuts down the service. */
    protected void shutdown() throws Exception {
	serverNode.shutdown(true);
    }

    /* -- Tests -- */

    @Test
    public void testConcurrency() throws Throwable {
        final int perThread = objects + objectsBuffer;
        /* Create objects */
	for (int t = 0; t < maxThreads; t++) {
            final AtomicInteger i = new AtomicInteger(0);
	    final int start = t * perThread;
            while (i.get() < perThread) {
                txnScheduler.runTask(new TestAbstractKernelRunnable() {
                        public void run() throws Exception {
                            int ival = i.get();
                            while (ival < perThread) {
				service.setBinding(getObjectName(start + ival),
                                                   new ModifiableObject());
                                ival = i.incrementAndGet();
				if (ival > 0 && ival % 100 == 0) {
				    return;
				}
                            }
                        }}, taskOwner);
            }
        }
	/* Warm up */
	if (repeat != 1) {
	    runOperations(1);
	}
	/* Test */
	for (int i = threads; i <= maxThreads; i++) {
	    for (int r = 0; r < repeat; r++) {
		runOperations(i);
	    }
	}
    }

    /* -- Other methods and classes -- */

    /** A utility to get the properties for the node. */
    protected Properties getNodeProps() throws Exception {
	Properties props =
	    SgsTestNode.getDefaultProperties("TestDataServiceConcurrency",
					     null, null);
	props.setProperty("com.sun.sgs.impl.kernel.profile.level", "max");
	props.setProperty("com.sun.sgs.impl.kernel.profile.listeners",
			  "com.sun.sgs.impl.profile.listener." +
			  "OperationLoggingProfileOpListener");
	props.setProperty("com.sun.sgs.txn.timeout", "10000");
	props.setProperty("com.sun.sgs.impl.service.data.DataServiceImpl." +
	                  "data.store.class",
	                  "com.sun.sgs.impl.service.data.store.DataStoreImpl");
	return props;
    }

    /** Perform operations in the specified number of threads. */
    private void runOperations(int threads) throws Throwable {
	aborts = 0;
	commits = 0;
	done = 0;
	long start = System.currentTimeMillis();
	for (int i = 0; i < threads; i++) {
	    new OperationThread(i, service);
	}
	while (true) {
	    synchronized (this) {
		if (failure != null || done >= threads) {
		    break;
		}
		try {
		    wait();
		} catch (InterruptedException e) {
		    failure = e;
		    break;
		}
	    }
	}
	long stop = System.currentTimeMillis();
	if (failure != null) {
	    throw failure;
	}
	long ms = stop - start;
	double s = (stop - start) / 1000.0d;
	System.err.println(
	    "Threads: " + threads + ", " +
	    "time: " + ms + " ms, " +
	    "aborts: " + aborts + ", " +
	    "commits: " + commits + ", " +
	    "ops/sec: " + Math.round((threads * operations) / s));
    }

    /**
     * Notes that a thread has completed successfully, and records the number
     * of aborts and commits that occurred in the thread.
     */
    synchronized void threadDone(int aborts, int commits) {
	done++;
	this.aborts += aborts;
	this.commits += commits;
	notifyAll();
    }

    /** Notes that a thread has failed with the specified exception. */
    synchronized void threadFailed(Throwable failure) {
	this.failure = failure;
	notifyAll();
    }

    /** Performs random operations in a separate thread. */
    class OperationThread extends Thread {
	private final DataService service;
	private final int id;
	private final Random random = new Random();
	private int aborts;
	private int commits;

	OperationThread(int id, DataService service) {
	    super("OperationThread" + id);
	    this.service = service;
	    this.id = id;
	    start();
	}

        public void run() {
            final AtomicInteger i = new AtomicInteger(0);
            try {
                while (i.get() < operations) {
                    txnScheduler.runTask(new TestAbstractKernelRunnable() {
                            public void run() throws Exception {
                                while (i.get() < operations) {
                                    if (i.get() % 1000 == 0 &&
                                        logger.isLoggable(Level.FINE)) {
                                        logger.log(Level.FINE, "Operation {0}",
                                                   i.get());
                                    }
                                    try {
					op(i.get());
                                        i.getAndIncrement();
					if (random.nextInt(10) == 0) {
					    commits++;
					    return;
					}
                                    } catch (TransactionAbortedException e) {
                                        if (logger.isLoggable(Level.FINE)) {
                                            logger.log(Level.FINE, "{0}: {1}",
                                                       this, e);
                                        }
                                        aborts++;
                                        return;
                                    }
                                }
                            }}, taskOwner);
                }
                threadDone(aborts, commits);
            } catch (Throwable t) {
                threadFailed(t);
            }
        }

	private void op(int i) throws Exception {
	    int start = id * (objects + objectsBuffer);
	    String name = getObjectName(start + random.nextInt(objects));
	    switch (random.nextInt(whichOperations.value)) {
	    case 0:
		/* Get binding */
		service.getBinding(name);
		break;
	    case 1:
		/* Set bindings */
		ModifiableObject obj =
		    (ModifiableObject) service.getBinding(name);
		String name2 = getObjectName(start + random.nextInt(objects));
		ModifiableObject obj2 =
		    (ModifiableObject) service.getBinding(name2);
		service.setBinding(name, obj2);
		service.setBinding(name2, obj);
		break;
	    case 2:
		/* Modify object */
		((ModifiableObject)
		 service.getBinding(name)).incrementNumber();
		break;
	    case 3:
		/* Create object */
		service.setBinding(name, new ModifiableObject());
		break;
	    default:
		throw new AssertionError();
	    }
	}
    }

    /** Returns the binding name to use for the i'th object. */
    private static String getObjectName(int i) {
	return String.format("obj-%08d", i);
    }

    /** A managed object with a modify method. */
    static class ModifiableObject extends DummyManagedObject {
	private static final long serialVersionUID = 1;
	private int number = 0;
	public ModifiableObject() { }
	public int getNumber() {
	    return number;
	}
	public void incrementNumber() {
	    AppContext.getDataManager().markForUpdate(this);
	    number++;
	}
    }
}
