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

package com.sun.sgs.test.impl.service.data.store.net;

import com.sun.sgs.auth.Identity;
import com.sun.sgs.impl.kernel.LockingAccessCoordinator;
import static com.sun.sgs.impl.kernel.StandardProperties.NODE_TYPE;
import static com.sun.sgs.impl.service.data.
    DataServiceImpl.DATA_STORE_CLASS_PROPERTY;
import static com.sun.sgs.impl.service.data.store.cache.
    CachingDataStore.CALLBACK_PORT_PROPERTY;
import static com.sun.sgs.impl.service.data.store.cache.
    CachingDataStore.CHECK_BINDINGS_PROPERTY;
import static com.sun.sgs.impl.service.data.store.cache.
    CachingDataStore.DEFAULT_CALLBACK_PORT;
import static com.sun.sgs.impl.service.data.store.cache.
    CachingDataStore.DEFAULT_SERVER_PORT;
import static com.sun.sgs.impl.service.data.store.cache.
    CachingDataStore.SERVER_HOST_PROPERTY;
import static com.sun.sgs.impl.service.data.store.cache.
    CachingDataStore.SERVER_PORT_PROPERTY;
import static com.sun.sgs.impl.service.data.store.cache.
    CachingDataStoreServerImpl.DEFAULT_UPDATE_QUEUE_PORT;
import static com.sun.sgs.impl.service.data.store.cache.
    CachingDataStoreServerImpl.UPDATE_QUEUE_PORT_PROPERTY;
import com.sun.sgs.impl.service.data.store.cache.CachingDataStore;
import com.sun.sgs.kernel.ComponentRegistry;
import com.sun.sgs.kernel.NodeType;
import com.sun.sgs.kernel.TransactionScheduler;
import com.sun.sgs.service.DataConflictListener;
import com.sun.sgs.service.DataService;
import com.sun.sgs.service.TransactionProxy;
import com.sun.sgs.test.impl.service.data.BasicDataServiceMultiTest;
import com.sun.sgs.test.util.DummyManagedObject;
import com.sun.sgs.test.util.SgsTestNode;
import com.sun.sgs.test.util.TestAbstractKernelRunnable;
import java.math.BigInteger;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
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
    
    /** The network port for the server's update queue. */
    private static final int updateQueuePort =
	Integer.getInteger("test.update.queue.port",
			   DEFAULT_UPDATE_QUEUE_PORT);

    /** The network port for the node's callback server. */
    private static final int nodeCallbackPort =
	Integer.getInteger("test.callback.port", DEFAULT_CALLBACK_PORT);

    @Override
    protected Properties getServerProperties() throws Exception {
	Properties props = super.getServerProperties();
	String host = serverHost;
	int port = serverPort;
	int queuePort = updateQueuePort;
	int callbackPort = nodeCallbackPort;
        String nodeType = NodeType.appNode.toString();
	if (host == null) {
	    host = "localhost";
	    port = 0;
	    queuePort = 0;
	    callbackPort = 0;
            nodeType = NodeType.coreServerNode.toString();
        }
	if (port == 0) {
	    port = SgsTestNode.getNextUniquePort();
	}
	if (queuePort == 0) {
	    queuePort = SgsTestNode.getNextUniquePort();
	}
	if (callbackPort == 0) {
	    callbackPort = SgsTestNode.getNextUniquePort();
	}
        props.setProperty(NODE_TYPE, nodeType);
	props.setProperty(SERVER_HOST_PROPERTY, host);
	props.setProperty(SERVER_PORT_PROPERTY, String.valueOf(port));
	props.setProperty(UPDATE_QUEUE_PORT_PROPERTY,
			  String.valueOf(queuePort));
	props.setProperty(CALLBACK_PORT_PROPERTY,
			  String.valueOf(callbackPort));
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
	props.setProperty(CALLBACK_PORT_PROPERTY,
			  String.valueOf(SgsTestNode.getNextUniquePort()));
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
	    new CheckingDataConflictListener();
	node0.getDataService().addDataConflictListener(listener0);
	SgsTestNode node1 = appNodes.get(1);
	long nodeId1 = node1.getNodeId();
	CheckingDataConflictListener listener1 =
	    new CheckingDataConflictListener();
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

    /** A data conflict listener that checks its calls. */
    private class CheckingDataConflictListener
	implements DataConflictListener
    {
	private Object expectedAccessId = null;
	private long expectedNodeId;
	private boolean expectedForUpdate;
	private boolean called;
	private Throwable exception;

	@Override
	public synchronized void nodeConflictDetected(
	    Object accessId, long nodeId, boolean forUpdate)
	{
	    try {
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

    /* -- Other classes and methods -- */

    /** Define a convenience class for scheduling tasks. */
    private abstract class RunTask extends TestAbstractKernelRunnable {
	final SgsTestNode node;
	final long nodeId;
	final ComponentRegistry systemRegistry;
	final TransactionProxy txnProxy;
	final TransactionScheduler txnScheduler;
	final DataService dataService;
	final Identity taskOwner;
	RunTask(SgsTestNode node) {
	    this.node = node;
	    nodeId = node.getNodeId();
	    systemRegistry = node.getSystemRegistry();
	    txnProxy = node.getProxy();
	    txnScheduler =
		systemRegistry.getComponent(TransactionScheduler.class);
	    dataService = node.getDataService();
	    taskOwner = txnProxy.getCurrentOwner();
	}
	void runTask() throws Exception {
	    txnScheduler.runTask(this, taskOwner);
	}
    }
}
