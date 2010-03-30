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

package com.sun.sgs.test.impl.service.data.store.cache;

import com.sun.sgs.app.ExceptionRetryStatus;
import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.NameNotBoundException;
import com.sun.sgs.auth.Identity;
import com.sun.sgs.impl.kernel.LockingAccessCoordinator;
import static com.sun.sgs.impl.kernel.StandardProperties.NODE_TYPE;
import static com.sun.sgs.impl.service.data.
    DataServiceImpl.DATA_STORE_CLASS_PROPERTY;
import com.sun.sgs.impl.service.data.store.cache.CachingDataStore;
import static com.sun.sgs.impl.service.data.store.cache.
    CachingDataStore.CHECK_BINDINGS_PROPERTY;
import static com.sun.sgs.impl.service.data.store.cache.
    CachingDataStore.SERVER_HOST_PROPERTY;
import static com.sun.sgs.impl.service.data.store.cache.server.
    CachingDataStoreServerImpl.DEFAULT_SERVER_PORT;
import static com.sun.sgs.impl.service.data.store.cache.server.
    CachingDataStoreServerImpl.SERVER_PORT_PROPERTY;
import com.sun.sgs.kernel.ComponentRegistry;
import com.sun.sgs.kernel.NodeType;
import com.sun.sgs.kernel.TransactionScheduler;
import com.sun.sgs.service.DataConflictListener;
import com.sun.sgs.service.DataService;
import com.sun.sgs.service.TaskService;
import com.sun.sgs.service.TransactionProxy;
import com.sun.sgs.test.impl.service.data.BasicDataServiceMultiTest;
import com.sun.sgs.test.util.AwaitDone;
import com.sun.sgs.test.util.DummyManagedObject;
import com.sun.sgs.test.util.SgsTestNode;
import com.sun.sgs.test.util.TestAbstractKernelRunnable;
import java.io.Serializable;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static java.util.concurrent.TimeUnit.SECONDS;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;

/**
 * Perform multi-node tests on the {@code DataService} using the caching data
 * store.
 */
public class TestDataServiceCachingMulti extends BasicDataServiceMultiTest {

    /** The configuration property for specifying the access coordinator. */
    private static final String ACCESS_COORDINATOR_PROPERTY =
	"com.sun.sgs.impl.kernel.access.coordinator";

    /**
     * The name of the host running the {@link CachingDataStoreServer}, or
     * {@code null} to create one locally.
     */
    private static final String serverHost =
	System.getProperty("test.server.host");

    /** The network port for the {@link CachingDataStoreServer}. */
    private static final int serverPort =
	Integer.getInteger("test.server.port", DEFAULT_SERVER_PORT);

    @Override
    protected Properties getServerProperties() throws Exception {
	Properties props = super.getServerProperties();
	String host = serverHost;
	int port = serverPort;
	String nodeType = NodeType.appNode.toString();
	if (host == null) {
	    host = "localhost";
	    port = 0;
	    nodeType = NodeType.coreServerNode.toString();
	}
	if (port == 0) {
	    port = SgsTestNode.getNextUniquePort();
	}
	props.setProperty(NODE_TYPE, nodeType);
	props.setProperty(SERVER_HOST_PROPERTY, host);
	props.setProperty(SERVER_PORT_PROPERTY, String.valueOf(port));
	if (props.getProperty(CHECK_BINDINGS_PROPERTY) == null) {
	    props.setProperty(CHECK_BINDINGS_PROPERTY, "TXN");
	}
	props.setProperty(DATA_STORE_CLASS_PROPERTY,
			  CachingDataStore.class.getName());
	props.setProperty(ACCESS_COORDINATOR_PROPERTY,
			  LockingAccessCoordinator.class.getName());
	return props;
    }

    @Override
    protected Properties getAppProperties() throws Exception {
	Properties props = super.getAppProperties();
	props.setProperty(DATA_STORE_CLASS_PROPERTY,
			  CachingDataStore.class.getName());
	props.setProperty(ACCESS_COORDINATOR_PROPERTY,
			  LockingAccessCoordinator.class.getName());
	return props;
    }

    /* -- Tests -- */

    @Test
    public void testCallbackWriteInTurn() throws Exception {
	final AtomicInteger count = new AtomicInteger();
	new RunTask(serverNode) { public void run() {
	    DummyManagedObject dummy = new DummyManagedObject();
	    dummy.setValue(count.getAndIncrement());
	    dataService.setBinding("dummy", dummy);
	    System.err.println(
		"Node " + nodeId + ": set dummy value: " + dummy.value);
	} }.runTask();
	for (SgsTestNode node : appNodes) {
	    new RunTask(node) { public void run() {
		DummyManagedObject dummy =
		    (DummyManagedObject) dataService.getBinding("dummy");
		System.err.println(
		    "Node " + nodeId + ": get dummy value: " + dummy.value);
		dummy.setValue(count.getAndIncrement());
		System.err.println(
		    "Node " + nodeId + ": set dummy value: " + dummy.value);
	    } }.runTask();
	}
    }

    @Test
    public void testCallbackWriteMultipleReaders() throws Exception {
	new RunTask(serverNode) { public void run() {
	    DummyManagedObject dummy = new DummyManagedObject();
	    dummy.setValue("1");
	    dataService.setBinding("dummy", dummy);
	    System.err.println(
		"Node " + nodeId + ": set dummy value: " + dummy.value);
	} }.runTask();
	for (int i = 1; i < appNodes.size(); i++) {
	    SgsTestNode node = appNodes.get(i);
	    new RunTask(node) { public void run() {
		DummyManagedObject dummy =
		    (DummyManagedObject) dataService.getBinding("dummy");
		System.err.println(
		    "Node " + nodeId + ": get dummy value: " + dummy.value);
	    } }.runTask();
	}
	SgsTestNode node = appNodes.get(0);
	    new RunTask(node) { public void run() {
		DummyManagedObject dummy = (DummyManagedObject)
		    dataService.getBindingForUpdate("dummy");
		dummy.setValue("2");
		System.err.println(
		    "Node " + nodeId + ": set dummy value: " + dummy.value);
	    } }.runTask();
    }

    @Test
    public void testCallbackWithContentionOneWriter() throws Exception {
	final AtomicInteger count = new AtomicInteger(1);
	new RunTask(serverNode) { public void run() {
	    DummyManagedObject dummy = new DummyManagedObject();
	    dummy.setValue(count.getAndIncrement());
	    dataService.setBinding("dummy", dummy);
	    System.err.println(
		"Node " + nodeId + ": set dummy value: " + dummy.value);
	} }.runTask();
	final AtomicBoolean stop = new AtomicBoolean();
	for (int i = 0; i < 2; i++) {
	    new RunTask(appNodes.get(0)) { public void run() {
		if (stop.get()) {
		    return;
		}
		DummyManagedObject dummy =
		    (DummyManagedObject) dataService.getBinding("dummy");
		System.err.println(
		    "Node " + nodeId + ": get dummy value: " + dummy.value);
		txnScheduler.scheduleTask(this, taskOwner);
	    } }.runTask();
	}
	for (int i = 0; i < 10; i++) {
	    new RunTask(appNodes.get(1)) { public void run() {
		DummyManagedObject dummy = (DummyManagedObject)
		    dataService.getBindingForUpdate("dummy");
		dummy.setValue(count.getAndIncrement());
		System.err.println(
		    "Node " + nodeId + ": set dummy value: " + dummy.value);
	    } }.runTask();
	}
	stop.set(true);
    }

    @Test
    public void testCallbackWithContentionMultipleWriters() throws Exception {
	new RunTask(serverNode) { public void run() {
	    DummyManagedObject dummy = new DummyManagedObject();
	    dummy.setValue(1);
	    dataService.setBinding("dummy", dummy);
	    System.err.println(
		"Node " + nodeId + ": set dummy value: " + dummy.value);
	} }.runTask();
	final AtomicBoolean stop = new AtomicBoolean();
	final CountDownLatch done = new CountDownLatch(3);
	for (SgsTestNode node : appNodes) {
	    new RunTask(node) { public void run() {
		if (stop.get()) {
		    done.countDown();
		    return;
		}
		DummyManagedObject dummy = (DummyManagedObject)
		    dataService.getBindingForUpdate("dummy");
		int value = (Integer) dummy.value;
		if (value >= 30) {
		    done.countDown();
		    stop.set(true);
		    return;
		}
		dummy.setValue(++value);
		System.err.println(
		    "Node " + nodeId + ": set dummy value: " + dummy.value);
		txnScheduler.scheduleTask(this, taskOwner);
	    } }.runTask();
	}
	done.await(10000, SECONDS);
    }

    /**
     * Test that the data conflict listener is called appropriately for
     * evictions and downgrades of objects and name bindings.  Also make sure
     * that exceptions thrown by listeners are ignored.
     */
    @Test
    public void testDataConflictListeners() throws Exception {
	final AtomicReference<BigInteger> dummyId =
	    new AtomicReference<BigInteger>();
	new RunTask(serverNode) { public void run() {
	    DummyManagedObject dummy = new DummyManagedObject();
	    dataService.setBinding("dummy", dummy);
	    dummyId.set(dataService.getObjectId(dummy));
	    dataService.setBinding("dummy2", new DummyManagedObject());
	} }.runTask();
	SgsTestNode node0 = appNodes.get(0);
	long nodeId0 = node0.getNodeId();
	CheckingDataConflictListener listener0 =
	    new CheckingDataConflictListener("a.dummy", dummyId.get());
	node0.getDataService().addDataConflictListener(listener0);
	SgsTestNode node1 = appNodes.get(1);
	long nodeId1 = node1.getNodeId();
	CheckingDataConflictListener listener1 =
	    new CheckingDataConflictListener("a.dummy", dummyId.get());
	node1.getDataService().addDataConflictListener(listener1);
	/* Evict object: Node 0 writes object, node 1 writes object */
	listener0.setExpected(dummyId.get(), nodeId1, true);
	listener1.setExpected(null, 0, false);
	new RunTask(node0) { public void run() {
	    dataService.getBindingForUpdate("dummy");
	} }.runTask();
	new RunTask(node1) { public void run() {
	    dataService.getBindingForUpdate("dummy");
	} }.runTask();
	listener0.await();
	listener1.awaitNotCalled();
	/* Downgrade object: Node 0 reads object */
	listener0.setExpected(null, 0, false);
	listener1.setExpected(dummyId.get(), nodeId0, false);
	new RunTask(node0) { public void run() {
	    dataService.getBinding("dummy");
	} }.runTask();
	listener1.await();
	listener0.awaitNotCalled();
	/* Evict binding: Node 0 writes binding */
	listener0.setExpected(null, 0, false);
	listener1.setExpected("a.dummy", nodeId0, true);
	new RunTask(node0) { public void run() {
	    dataService.setBinding("dummy", dataService.getBinding("dummy2"));
	} }.runTask();
	listener1.await();
	listener0.awaitNotCalled();
	/* Downgrade binding: Node 1 reads binding */
	listener0.setExpected("a.dummy", nodeId1, false);
	listener1.setExpected(null, 0, false);
	new RunTask(node1) { public void run() {
	    dataService.getBinding("dummy");
	} }.runTask();
	listener0.await();
	listener1.awaitNotCalled();
    }

    /**
     * A data conflict listener that checks its calls, but only paying
     * attention to interesting access IDs.
     */
    private class CheckingDataConflictListener
	implements DataConflictListener
    {
	private final Collection<?> interestingIds;
	private Object expectedAccessId = null;
	private long expectedNodeId;
	private boolean expectedForUpdate;
	private boolean called;
	private Throwable exception;

	CheckingDataConflictListener(Object... interestingIds) {
	    this.interestingIds = Arrays.asList(interestingIds);
	}

	@Override
	public synchronized void nodeConflictDetected(
	    Object accessId, long nodeId, boolean forUpdate)
	{
	    try {
		if (!interestingIds.contains(accessId)) {
		    return;
		}
		if (expectedAccessId == null) {
		    throw new Exception("Unexpected notification");
		} else {
		    assertFalse("Listener should not have been called before",
				called);
		    assertEquals(expectedAccessId, accessId);
		    assertEquals(expectedNodeId, nodeId);
		    assertEquals(expectedForUpdate, forUpdate);
		    called = true;
		    notifyAll();
		}
	    } catch (Throwable t) {
		exception = t;
		notifyAll();
	    }
	    throw new RuntimeException("Listener throws");
	}

	/**
	 * Specify the expected arguments to the next call to
	 * nodeConflictDetected.  If expectedAccessId is null, then no
	 * notification is expected.
	 */
	synchronized void setExpected(Object expectedAccessId,
				      long expectedNodeId,
				      boolean expectedForUpdate)
	{
	    this.expectedAccessId = expectedAccessId;
	    this.expectedNodeId = expectedNodeId;
	    this.expectedForUpdate = expectedForUpdate;
	    called = false;
	}

	/** Wait for nodeConflictDetected to be called. */
	synchronized void await() {
	    long done = System.currentTimeMillis() + 5000;
	    while (true) {
		if (exception != null) {
		    throw new RuntimeException(
			"Unexpected exception: " + exception, exception);
		}
		if (called) {
		    break;
		}
		long wait = done - System.currentTimeMillis();
		if (wait <= 0) {
		    fail("Listener not called");
		}
		try {
		    wait(wait);
		} catch (InterruptedException e) {
		}
	    }
	}

	/** Wait to be sure that nodeConflictDetected is not called. */
	synchronized void awaitNotCalled() {
	    long done = System.currentTimeMillis() + 1000;
	    while (true) {
		if (exception != null) {
		    throw new RuntimeException(
			"Unexpected exception: " + exception, exception);
		}
		if (called) {
		    fail("Listener was called");
		}
		long wait = done - System.currentTimeMillis();
		if (wait <= 0) {
		    break;
		}
		try {
		    wait(wait);
		} catch (InterruptedException e) {
		}
	    }
	}
    }

    /**
     * Test that the cache correctly handles a case where a removed binding is
     * the next entry in the cache but is not known to be removed by the
     * server.
     */
    @Test
    public void testRemoveBindingProblems() throws Exception {
	RunTask init = new RunTask(serverNode) { public void run() {
	    DummyManagedObject dummy = new DummyManagedObject();
	    dataService.setBinding("a", dummy);
	    dataService.setBinding("b", dummy);
	    dataService.setBinding("c", dummy);
	} };
	init.runTask();
	new RunTask(appNodes.get(0)) { public void run() {
	    dataService.getBinding("a");
	    dataService.removeBinding("b");
	    assertEquals("c", dataService.nextBoundName("a"));
	} }.runTask();
	init.runTask();
	new RunTask(appNodes.get(0)) { public void run() {
	    dataService.getBinding("a");
	    dataService.removeBinding("b");
	    dataService.removeBinding("a");
	} }.runTask();
	init.runTask();
	new RunTask(appNodes.get(0)) { public void run() {
	    dataService.getBinding("a");
	    dataService.removeBinding("b");
	    dataService.getBinding("a");
	} }.runTask();
	init.runTask();
	new RunTask(appNodes.get(0)) { public void run() {
	    dataService.getBinding("a");
	    dataService.removeBinding("b");
	    dataService.setBinding("a", new DummyManagedObject());
	} }.runTask();
    }

    /**
     * Test that {@code getBinding} gets the right result when a new, earlier,
     * binding is introduced while waiting for the lock on an existing, later,
     * binding.
     */
    @Test
    public void testConcurrentEarlierBindingGet() throws Exception {
	/* Set up */
	new RunTask(appNodes.get(0)) { public void run() {
	    try {
		dataService.removeBinding("a");
	    } catch (NameNotBoundException e) {
	    }
	    dataService.setBinding("b", new ManagedInteger(33));
	} }.runTask();
	final AwaitDone waitA = new AwaitDone(1);
	final AwaitDone waitB = new AwaitDone(1);
	final AwaitDone done = new AwaitDone(1);
	/* Node 1 */
	new RunTask(appNodes.get(0)) { public void run() {
	    if (done.getDone()) {
		return;
	    }
	    try {
		dataService.setBinding("a", new ManagedInteger(1));
		waitA.taskSucceeded();
		waitB.await(1, SECONDS);
		Thread.sleep(10);
		done.taskSucceeded();
	    } catch (Throwable e) {
		done.taskFailed(e);
	    }
	} }.scheduleTask();
	waitA.await(1, SECONDS);
	/* Node 2 */
	new RunTask(appNodes.get(1)) {
	    private boolean ok;
	    public void run() {
		if (ok) {
		    return;
		}
		waitB.taskSucceeded();
		dataService.getBinding("a");
		ok = true;
	    }
	}.runTask();
	done.await(1, SECONDS);
	/* Clean up */
	new RunTask(appNodes.get(0)) { public void run() {
	    dataService.removeBinding("a");
	} }.runTask();
	new RunTask(appNodes.get(1)) { public void run() {
	    dataService.removeBinding("b");
	} }.runTask();
    }

    /**
     * Test that {@code setBinding} gets the right result when a new, earlier,
     * binding is introduced while waiting for the lock on an existing, later,
     * binding.
     */
    @Test
    public void testConcurrentEarlierBindingSet() throws Exception {
	/* Set up */
	new RunTask(appNodes.get(0)) { public void run() {
	    try {
		dataService.removeBinding("a");
	    } catch (NameNotBoundException e) {
	    }
	    dataService.setBinding("b", new ManagedInteger(33));
	} }.runTask();
	final AwaitDone waitA = new AwaitDone(1);
	final AwaitDone waitB = new AwaitDone(1);
	final AwaitDone done = new AwaitDone(1);
	/* Node 1 */
	new RunTask(appNodes.get(0)) { public void run() {
	    if (done.getDone()) {
		return;
	    }
	    try {
		dataService.setBinding("a", new ManagedInteger(1));
		waitA.taskSucceeded();
		waitB.await(1, SECONDS);
		Thread.sleep(10);
		done.taskSucceeded();
	    } catch (Throwable e) {
		done.taskFailed(e);
	    }
	} }.scheduleTask();
	waitA.await(1, SECONDS);
	/* Node 2 */
	new RunTask(appNodes.get(1)) {
	    private boolean ok;
	    public void run() {
		if (ok) {
		    return;
		}
		waitB.taskSucceeded();
		dataService.setBinding("a", new ManagedInteger(2));
		ok = true;
	    }
	}.runTask();
	done.await(1, SECONDS);
	/* Clean up */
	new RunTask(appNodes.get(0)) { public void run() {
	    dataService.removeBinding("a");
	} }.runTask();
	new RunTask(appNodes.get(1)) { public void run() {
	    dataService.removeBinding("b");
	} }.runTask();
    }

    /**
     * Test that {@code removeBinding} gets the right result when a new,
     * earlier, binding is introduced while waiting for the lock on an
     * existing, later, binding.
     */
    @Test
    public void testConcurrentEarlierBindingRemove() throws Exception {
	/* Set up */
	new RunTask(appNodes.get(0)) { public void run() {
	    try {
		dataService.removeBinding("a");
	    } catch (NameNotBoundException e) {
	    }
	    dataService.setBinding("b", new ManagedInteger(33));
	} }.runTask();
	final AwaitDone waitA = new AwaitDone(1);
	final AwaitDone waitB = new AwaitDone(1);
	final AwaitDone done = new AwaitDone(1);
	/* Node 1 */
	new RunTask(appNodes.get(0)) { public void run() {
	    if (done.getDone()) {
		return;
	    }
	    try {
		dataService.setBinding("a", new ManagedInteger(1));
		waitA.taskSucceeded();
		waitB.await(1, SECONDS);
		Thread.sleep(10);
		done.taskSucceeded();
	    } catch (Throwable e) {
		done.taskFailed(e);
	    }
	} }.scheduleTask();
	waitA.await(1, SECONDS);
	/* Node 2 */
	new RunTask(appNodes.get(1)) {
	    private boolean ok;
	    public void run() {
		if (ok) {
		    return;
		}
		waitB.taskSucceeded();
		dataService.removeBinding("a");
		ok = true;
	    }
	}.runTask();
	done.await(1, SECONDS);
	/* Clean up */
	new RunTask(appNodes.get(0)) { public void run() {
	    try {
		dataService.removeBinding("a");
	    } catch (NameNotBoundException e) {
	    }
	} }.runTask();
	new RunTask(appNodes.get(1)) { public void run() {
	    dataService.removeBinding("b");
	} }.runTask();
    }

    /**
     * Test that {@code nextBoundName} gets the right result when a new,
     * earlier, binding is introduced while waiting for the lock on an
     * existing, later, binding.
     */
    @Test
    public void testConcurrentEarlierBindingNext() throws Exception {
	/* Set up */
	new RunTask(appNodes.get(0)) { public void run() {
	    try {
		dataService.removeBinding("b");
	    } catch (NameNotBoundException e) {
	    }
	    dataService.setBinding("c", new ManagedInteger(33));
	} }.runTask();
	final AwaitDone waitA = new AwaitDone(1);
	final AwaitDone waitB = new AwaitDone(1);
	final AwaitDone done = new AwaitDone(1);
	/* Node 1 */
	new RunTask(appNodes.get(0)) { public void run() {
	    if (done.getDone()) {
		return;
	    }
	    try {
		dataService.setBinding("b", new ManagedInteger(1));
		waitA.taskSucceeded();
		waitB.await(1, SECONDS);
		Thread.sleep(10);
		done.taskSucceeded();
	    } catch (Throwable e) {
		done.taskFailed(e);
	    }
	} }.scheduleTask();
	waitA.await(1, SECONDS);
	/* Node 2 */
	new RunTask(appNodes.get(1)) {
	    private boolean ok;
	    public void run() {
		if (ok) {
		    return;
		}
		waitB.taskSucceeded();
		assertEquals("b", dataService.nextBoundName("a"));
		ok = true;
	    }
	}.runTask();
	done.await(1, SECONDS);
	/* Clean up */
	new RunTask(appNodes.get(0)) { public void run() {
	    dataService.removeBinding("b");
	} }.runTask();
	new RunTask(appNodes.get(1)) { public void run() {
	    dataService.removeBinding("c");
	} }.runTask();
    }

    /**
     * Test that eviction of an in-use name that has been removed only evicts
     * the next name, not the unbound name.
     */
    @Test
    public void testEvictInUseRemoved() throws Exception {
	/* Set up */
	new RunTask(appNodes.get(0)) { public void run() {
	    dataService.setBinding("a", new ManagedInteger(1));
	    dataService.setBinding("b", new ManagedInteger(2));
	} }.runTask();
	final AwaitDone waitA = new AwaitDone(1);
	final AwaitDone waitB = new AwaitDone(1);
	final AwaitDone done = new AwaitDone(1);
	/* Node 1 */
	new RunTask(appNodes.get(0)) { public void run() {
	    if (done.getDone()) {
		return;
	    }
	    try {
		dataService.removeBinding("b");
		waitA.taskSucceeded();
		waitB.await(1, SECONDS);
		Thread.sleep(10);
		done.taskSucceeded();
	    } catch (Throwable e) {
		done.taskFailed(e);
	    }
	} }.scheduleTask();
	waitA.await(1, SECONDS);
	/* Node 2 */
	new RunTask(appNodes.get(1)) {
	    private boolean ok;
	    public void run() {
		if (ok) {
		    return;
		}
		waitB.taskSucceeded();
		dataService.setBinding("b", new ManagedInteger(3));
		ok = true;
	    }
	}.runTask();
	done.await(1, SECONDS);
	/* Clean up */
	new RunTask(appNodes.get(0)) { public void run() {
	    dataService.removeBinding("a");
	} }.runTask();
	new RunTask(appNodes.get(1)) { public void run() {
	    dataService.removeBinding("b");
	} }.runTask();
    }

    /**
     * Test that downgrade of an in-use name that has been removed only
     * downgrades the next name, not the unbound name.
     */
    @Test
    public void testDowngradeInUseRemoved() throws Exception {
	/* Set up */
	new RunTask(appNodes.get(0)) { public void run() {
	    dataService.setBinding("a", new ManagedInteger(1));
	    dataService.setBinding("b", new ManagedInteger(2));
	} }.runTask();
	final AwaitDone waitA = new AwaitDone(1);
	final AwaitDone waitB = new AwaitDone(1);
	final AwaitDone done = new AwaitDone(1);
	/* Node 1 */
	new RunTask(appNodes.get(0)) { public void run() {
	    if (done.getDone()) {
		return;
	    }
	    try {
		dataService.removeBinding("b");
		waitA.taskSucceeded();
		waitB.await(1, SECONDS);
		Thread.sleep(10);
		done.taskSucceeded();
	    } catch (Throwable e) {
		done.taskFailed(e);
	    }
	} }.scheduleTask();
	waitA.await(1, SECONDS);
	/* Node 2 */
	new RunTask(appNodes.get(1)) {
	    private boolean ok;
	    public void run() {
		if (ok) {
		    return;
		}
		waitB.taskSucceeded();
		try {
		    dataService.getBinding("b");
		    fail("Expected NameNotBoundException");
		} catch (NameNotBoundException e) {
		}
		ok = true;
	    }
	}.runTask();
	done.await(1, SECONDS);
	Thread.sleep(1000);
	/* Clean up */
	new RunTask(appNodes.get(0)) { public void run() {
	    dataService.removeBinding("a");
	} }.runTask();
	new RunTask(appNodes.get(1)) { public void run() {
	    try {
		dataService.removeBinding("b");
	    } catch (NameNotBoundException e) {
	    }
	} }.runTask();
    }

    /** Test multi-node upgrade/upgrade deadlock. */
    @Test
    public void testUpgradeUpgradeDeadlock() throws Exception {
	/* Set up */
	new RunTask(appNodes.get(0)) { public void run() {
	    dataService.setBinding("a", new ManagedInteger(1));
	} }.runTask();
	new RunTask(appNodes.get(1)) { public void run() {
	    dataService.getBinding("a");
	} }.runTask();
	final AwaitDone waitA = new AwaitDone(1);
	final AwaitDone waitB = new AwaitDone(1);
	final AwaitDone done1 = new AwaitDone(1);
	final AwaitDone done2 = new AwaitDone(1);
	/* Node 1 */
	new RunTask(appNodes.get(0)) { public void run() {
	    if (done1.getDone() || done2.getDone()) {
		done1.taskSucceeded();
		return;
	    }
	    try {
		dataService.getBinding("a");
		waitA.taskSucceeded();
		waitB.await(1, SECONDS);
		Thread.sleep(10);
		dataService.setBinding("a", new ManagedInteger(2));
		txnScheduler.scheduleTask(this, taskOwner);
	    } catch (RuntimeException e) {
		if (isRetryable(e)) {
		    throw e;
		} else {
		    done1.taskFailed(e);
		}
	    } catch (Throwable e) {
		done1.taskFailed(e);
	    }
	} }.scheduleTask();
	waitA.await(1, SECONDS);
	/* Node 2 */
	new RunTask(appNodes.get(1)) {
	    private boolean ok;
	    public void run() {
		if (ok) {
		    return;
		}
		waitB.taskSucceeded();
		dataService.setBinding("a", new ManagedInteger(3));
		ok = true;
	    }
	}.runTask();
	done2.taskSucceeded();
	done1.await(10, SECONDS);
	/* Clean up */
	new RunTask(appNodes.get(0)) { public void run() {
	    dataService.getBinding("a");
	} }.runTask();
	new RunTask(appNodes.get(1)) { public void run() {
	    dataService.removeBinding("a");
	} }.runTask();
    }

    /** Test random concurrent name binding accesses. */
    @Test
    public void testConcurrentBindings() throws Exception {
	final int bindings = Integer.getInteger("test.bindings", 50);
	int threads = Integer.getInteger("test.threads", 1);
	int nodes = Integer.getInteger("test.nodes", 2);
	int repeat = Integer.getInteger("test.repeat", 10);
	int wait = Integer.getInteger("test.wait", 60);
	System.err.println("Testing with bindings:" + bindings +
			   ", threads:" + threads +
			   ", nodes:" + nodes +
			   ", repeat:" + repeat +
			   ", wait:" + wait);
	if (nodes > appNodes.size()) {
	    setUp(nodes);
	}
	long start = System.currentTimeMillis();
	/* Set half the bindings */
	new RunChunkedTask(serverNode) { boolean runChunk() {
	    ManagedInteger remaining;
	    DummyManagedObject dummy;
	    try {
		remaining =
		    (ManagedInteger) dataService.getBinding("remaining");
		dummy = (DummyManagedObject) dataService.getBinding("dummy");
	    } catch (NameNotBoundException e) {
		remaining = new ManagedInteger(bindings / 2);
		dataService.setBinding("remaining", remaining);
		dummy = new DummyManagedObject();
		dataService.setBinding("dummy", dummy);
	    }
	    if (remaining.value == 0) {
		dataService.removeBinding("remaining");
		return true;
	    } else {
		dataService.markForUpdate(remaining);
		while (remaining.value > 0 && taskService.shouldContinue()) {
		    dataService.setBinding(
			String.valueOf(--remaining.value), dummy);
		}
		return false;
	    } } }.runTask();
	/* Random work */
	AwaitDone done = new AwaitDone(nodes * threads);
	for (int i = 0; i < nodes; i++) {
	    SgsTestNode node = appNodes.get(i);
	    for (int j = 0; j < threads; j++) {
		new RandomWorkTask(
		    node, done, bindings, repeat).scheduleTask();
	    }
	}
	done.await(wait, SECONDS);
	/* Remove all bindings */
	new RunChunkedTask(serverNode) { boolean runChunk() {
	    ManagedInteger remaining;
	    try {
		remaining =
		    (ManagedInteger) dataService.getBinding("remaining");
	    } catch (NameNotBoundException e) {
		remaining = new ManagedInteger(bindings);
		dataService.setBinding("remaining", remaining);
	    }
	    if (remaining.value == 0) {
		dataService.removeBinding("remaining");
		return true;
	    } else {
		dataService.markForUpdate(remaining);
		while (remaining.value > 0 && taskService.shouldContinue()) {
		    try {
			dataService.removeBinding(
			    String.valueOf(--remaining.value));
		    } catch (NameNotBoundException e) {
		    }
		}
		return false;
	    } } }.runTask();
	long time = System.currentTimeMillis() - start;
	System.err.println("Test finished in " + (time / 1000) + " seconds");
    }

    /** Get, set, and remove random bindings */
    private class RandomWorkTask extends RunChunkedTask {
	private final int bindings;
	private final AtomicInteger remaining;
	private final Random rand = new Random();
	RandomWorkTask(SgsTestNode node, AwaitDone done, int bindings,
		       int repeat)
	{
	    super(node, done);
	    this.bindings = bindings;
	    remaining = new AtomicInteger(repeat);
	}
	void scheduleTask() {
	    txnScheduler.scheduleTask(this, taskOwner);
	}
	boolean runChunk() {
	    if (remaining.get() == 0) {
		return true;
	    }
	    List<Character> ops = Arrays.asList('r', 'r', 'w', 'x');
	    Collections.shuffle(ops);
	    for (char op : ops) {
		String name = String.valueOf(rand.nextInt(bindings));
		System.err.println(nodeId + " " + op + " a." + name);
		switch (op) {
		case 'r':
		    try {
			dataService.getBinding(name);
		    } catch (NameNotBoundException e) {
		    }
		    break;
		case 'w':
		    dataService.setBinding(
			name, dataService.getBinding("dummy"));
		    break;
		case 'x':
		    try {
			dataService.removeBinding(name);
		    } catch (NameNotBoundException e) {
		    }
		    break;
		default:
		    throw new AssertionError();
		}
	    }
	    remaining.getAndDecrement();
	    return false;
	}
    }

    /* -- Other classes and methods -- */

    /** Define a convenience class for scheduling tasks. */
    private abstract class RunTask extends TestAbstractKernelRunnable {
	final SgsTestNode node;
	final long nodeId;
	final ComponentRegistry systemRegistry;
	final TransactionProxy txnProxy;
	final TransactionScheduler txnScheduler;
	final DataService dataService;
	final TaskService taskService;
	final Identity taskOwner;
	RunTask(SgsTestNode node) {
	    this.node = node;
	    nodeId = node.getNodeId();
	    systemRegistry = node.getSystemRegistry();
	    txnProxy = node.getProxy();
	    txnScheduler =
		systemRegistry.getComponent(TransactionScheduler.class);
	    dataService = node.getDataService();
	    taskService = node.getTaskService();
	    taskOwner = txnProxy.getCurrentOwner();
	}
	void runTask() throws Exception {
	    txnScheduler.runTask(this, taskOwner);
	}
	void scheduleTask() throws Exception {
	    txnScheduler.scheduleTask(this, taskOwner);
	}
    }

    /**
     * Define a convenience class for scheduling tasks that are broken into
     * separate transactions.
     */
    private abstract class RunChunkedTask extends RunTask {

	/** The object to use to wait for task completion. */
	private final AwaitDone done;

	/** Creates an instance of this class. */
	RunChunkedTask(SgsTestNode node) {
	    this(node, new AwaitDone(1));
	}

	/**
	 * Creates an instance of this class using the specified count down
	 * latch and failure reference.
	 */
	RunChunkedTask(SgsTestNode node, AwaitDone done) {
	    super(node);
	    this.done = done;
	}

	/**
	 * Runs a portion of the task, returning true if the entire task is
	 * done.
	 *
	 * @return	whether the task is done
	 */
	abstract boolean runChunk();

	/** Runs this task and 1 second for it to be done. */
	void runTask() throws InterruptedException {
	    txnScheduler.scheduleTask(this, taskOwner);
	    done.await(1, SECONDS);
	}

	/** Runs the task chunks, rescheduling this task until it is done. */
	public final void run() {
	    if (!done.getDone()) {
		try {
		    if (runChunk()) {
			done.taskSucceeded();
		    } else {
			txnScheduler.scheduleTask(this, taskOwner);
		    }
		} catch (RuntimeException e) {
		    if (isRetryable(e)) {
			throw e;
		    }
		    done.taskFailed(e);
		} catch (Error e) {
		    done.taskFailed(e);
		}
	    }
	}
    }

    /**
     * Returns true if the given {@code Throwable} will be retried
     * @param t the throwable to test
     * @return true if {@code t} will be retried
     */
    private static boolean isRetryable(Throwable t) {
	return
	    t instanceof ExceptionRetryStatus &&
	    ((ExceptionRetryStatus) t).shouldRetry();
    }

    /** A managed object that contains an integer, which may be null. */
    static class ManagedInteger implements ManagedObject, Serializable {
	private static final long serialVersionUID = 1;
	int value;
	ManagedInteger(int value) {
	    this.value = value;
	}
    }
}
