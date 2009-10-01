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
import com.sun.sgs.service.DataService;
import com.sun.sgs.service.TransactionProxy;
import com.sun.sgs.test.impl.service.data.BasicDataServiceMultiTest;
import com.sun.sgs.test.util.DummyManagedObject;
import com.sun.sgs.test.util.SgsTestNode;
import com.sun.sgs.test.util.TestAbstractKernelRunnable;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import static java.util.concurrent.TimeUnit.SECONDS;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
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
