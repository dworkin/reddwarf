/*
 * Copyright 2007 Sun Microsystems, Inc.
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

package com.sun.sgs.test.impl.service.channel;

import com.sun.sgs.service.WatchdogService;
import com.sun.sgs.impl.service.watchdog.WatchdogServiceImpl;
import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.AppListener;
import com.sun.sgs.app.Channel;
import com.sun.sgs.app.ChannelListener;
import com.sun.sgs.app.ChannelManager;
import com.sun.sgs.app.ClientSession;
import com.sun.sgs.app.ClientSessionId;
import com.sun.sgs.app.ClientSessionListener;
import com.sun.sgs.app.DataManager;
import com.sun.sgs.app.Delivery;
import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.ManagedReference;
import com.sun.sgs.app.NameExistsException;
import com.sun.sgs.app.NameNotBoundException;
import com.sun.sgs.app.TaskManager;
import com.sun.sgs.app.TransactionNotActiveException;
import com.sun.sgs.auth.Identity;
import com.sun.sgs.auth.IdentityCredentials;
import com.sun.sgs.auth.IdentityManager;
import com.sun.sgs.impl.auth.NamePasswordCredentials;
import com.sun.sgs.impl.io.SocketEndpoint;
import com.sun.sgs.impl.io.TransportType;
import com.sun.sgs.impl.kernel.DummyAbstractKernelAppContext;
import com.sun.sgs.impl.kernel.MinimalTestKernel;
import com.sun.sgs.impl.kernel.StandardProperties;
import com.sun.sgs.impl.service.channel.ChannelServiceImpl;
import com.sun.sgs.impl.service.data.DataServiceImpl;
import com.sun.sgs.impl.service.data.store.DataStoreImpl;
import com.sun.sgs.impl.service.nodemap.NodeMappingServiceImpl;
import com.sun.sgs.impl.service.session.ClientSessionServiceImpl;
import com.sun.sgs.impl.service.task.TaskServiceImpl;
import com.sun.sgs.impl.sharedutil.CompactId;
import com.sun.sgs.impl.sharedutil.HexDumper;
import com.sun.sgs.impl.sharedutil.MessageBuffer;
import com.sun.sgs.io.Connection;
import com.sun.sgs.io.ConnectionListener;
import com.sun.sgs.io.Connector;
import com.sun.sgs.protocol.simple.SimpleSgsProtocol;
import com.sun.sgs.service.ClientSessionService;
import com.sun.sgs.service.DataService;
import com.sun.sgs.service.NodeMappingService;
import com.sun.sgs.service.TaskService;
import com.sun.sgs.test.util.DummyComponentRegistry;
import com.sun.sgs.test.util.DummyTransaction;
import com.sun.sgs.test.util.DummyTransactionProxy;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import junit.framework.TestCase;

public class TestChannelServiceImpl extends TestCase {
    
    /** The name of the DataServiceImpl class. */
    private static final String DataStoreImplClassName =
	DataStoreImpl.class.getName();

    /** Directory used for database shared across multiple tests. */
    private static final String DB_DIRECTORY =
        System.getProperty("java.io.tmpdir") + File.separator +
	"TestChannelServiceImpl.db";

    /** Properties for the channel service and client session service. */
    private static Properties serviceProps = createProperties(
	    "com.sun.sgs.app.name", "TestChannelServiceImpl",
	    "com.sun.sgs.app.port", "0",
            "com.sun.sgs.impl.service.data.store.DataStoreImpl.directory",
	    	DB_DIRECTORY,
            "com.sun.sgs.impl.service.watchdog.server.start", "true",
	    "com.sun.sgs.impl.service.watchdog.server.port", "0",
	    "com.sun.sgs.impl.service.watchdog.renew.interval", "1000",
	    "com.sun.sgs.impl.service.nodemap.server.start", "true",
	    "com.sun.sgs.impl.service.nodemap.server.port", "0"
	    );

    private static final String CHANNEL_NAME = "three stooges";
    
    private static final int WAIT_TIME = 2000;
    
    private static final String LOGIN_FAILED_MESSAGE = "login failed";

    private static final Set<CompactId> ALL_MEMBERS = new HashSet<CompactId>();
    
    private static Object disconnectedCallbackLock = new Object();

    /** Kernel/transaction-related test components. */
    private static DummyTransactionProxy txnProxy =
	MinimalTestKernel.getTransactionProxy();
    private DummyAbstractKernelAppContext appContext;
    private DummyComponentRegistry systemRegistry;
    private DummyComponentRegistry serviceRegistry;
    private DummyTransaction txn;

    /** Services. */
    private DataServiceImpl dataService;
    private WatchdogServiceImpl watchdogService;
    private NodeMappingServiceImpl nodeMappingService;
    private TaskServiceImpl taskService;
    private ChannelServiceImpl channelService;
    private ClientSessionServiceImpl sessionService;
    private DummyIdentityManager identityManager;
    
    /** The listen port for the client session service. */
    private int port;

    /** True if test passes. */
    private boolean passed;

    /** Constructs a test instance. */
    public TestChannelServiceImpl(String name) {
	super(name);
    }

    /** Creates and configures the channel service. */
    protected void setUp() throws Exception {
	passed = false;
	System.err.println("Testcase: " + getName());
        setUp(true);
    }

    protected void setUp(boolean clean) throws Exception {
        if (clean) {
            deleteDirectory(DB_DIRECTORY);
        }

	MinimalTestKernel.useMasterScheduler(serviceProps);
        appContext = MinimalTestKernel.createContext();
	systemRegistry = MinimalTestKernel.getSystemRegistry(appContext);
	serviceRegistry = MinimalTestKernel.getServiceRegistry(appContext);

	// create data service
	dataService = createDataService(systemRegistry);
        txnProxy.setComponent(DataService.class, dataService);
        txnProxy.setComponent(DataServiceImpl.class, dataService);
        serviceRegistry.setComponent(DataManager.class, dataService);
        serviceRegistry.setComponent(DataService.class, dataService);
        serviceRegistry.setComponent(DataServiceImpl.class, dataService);

	// create watchdog service
	watchdogService =
	    new WatchdogServiceImpl(serviceProps, systemRegistry, txnProxy);
	txnProxy.setComponent(WatchdogService.class, watchdogService);
	txnProxy.setComponent(WatchdogServiceImpl.class, watchdogService);
	serviceRegistry.setComponent(WatchdogService.class, watchdogService);
	serviceRegistry.setComponent(
	    WatchdogServiceImpl.class, watchdogService);

	// create node mapping service
        nodeMappingService = new NodeMappingServiceImpl(
	    serviceProps, systemRegistry, txnProxy);
        txnProxy.setComponent(NodeMappingService.class, nodeMappingService);
        txnProxy.setComponent(
	    NodeMappingServiceImpl.class, nodeMappingService);
        serviceRegistry.setComponent(
	    NodeMappingService.class, nodeMappingService);
        serviceRegistry.setComponent(
	    NodeMappingServiceImpl.class, nodeMappingService);
	
	// create task service
	taskService = new TaskServiceImpl(
	    new Properties(), systemRegistry, txnProxy);
        txnProxy.setComponent(TaskService.class, taskService);
        txnProxy.setComponent(TaskServiceImpl.class, taskService);
        serviceRegistry.setComponent(TaskManager.class, taskService);
        serviceRegistry.setComponent(TaskService.class, taskService);
        serviceRegistry.setComponent(TaskServiceImpl.class, taskService);

	// create identity manager
	identityManager = new DummyIdentityManager();
	systemRegistry.setComponent(IdentityManager.class, identityManager);

	// create client session service
	sessionService = new ClientSessionServiceImpl(
	    serviceProps, systemRegistry, txnProxy);
	serviceRegistry.setComponent(
	    ClientSessionService.class, sessionService);
	txnProxy.setComponent(
	    ClientSessionService.class, sessionService);
	port = sessionService.getListenPort();
	
	// create channel service
	channelService = new ChannelServiceImpl(
	    serviceProps, systemRegistry, txnProxy);
	txnProxy.setComponent(ChannelServiceImpl.class, channelService);
	serviceRegistry.setComponent(ChannelManager.class, channelService);
	serviceRegistry.setComponent(ChannelServiceImpl.class, channelService);
	
	// services ready
	dataService.ready();
	watchdogService.ready();
	nodeMappingService.ready();
	taskService.ready();
	sessionService.ready();
	channelService.ready();

	createTransaction(1000);
    }

    /** Sets passed if the test passes. */
    protected void runTest() throws Throwable {
	super.runTest();
        Thread.sleep(100);
	passed = true;
    }
    
    /** Cleans up the transaction. */
    protected void tearDown() throws Exception {
        tearDown(true);
    }

    protected void tearDown(boolean clean) throws Exception {
        if (txn != null) {
            try {
                txn.abort(null);
            } catch (RuntimeException e) {
                if ((! clean) || passed) {
                    // ignore
                } else {
                    e.printStackTrace();
                }
            } finally {
                txn = null;
            }
        }
        if (channelService != null) {
            channelService.shutdown();
            channelService = null;
        }
        if (sessionService != null) {
            sessionService.shutdown();
            sessionService = null;
        }
        if (taskService != null) {
            taskService.shutdown();
            taskService = null;
        }
	if (nodeMappingService != null) {
	    nodeMappingService.shutdown();
	    nodeMappingService = null;
	}
	if (watchdogService != null) {
	    watchdogService.shutdown();
	    watchdogService = null;
	}
        if (dataService != null) {
            dataService.shutdown();
            dataService = null;
        }
        if (clean) {
            deleteDirectory(DB_DIRECTORY);
        }
        MinimalTestKernel.destroyContext(appContext);
    }

    /* -- Test constructor -- */

    public void testConstructorNullProperties() throws Exception {
	try {
	    new ChannelServiceImpl(null, new DummyComponentRegistry(),
				   new DummyTransactionProxy());
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
    }

    public void testConstructorNullComponentRegistry() throws Exception {
	try {
	    new ChannelServiceImpl(serviceProps, null,
				   new DummyTransactionProxy());
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
    }

    public void testConstructorNullTransactionProxy() throws Exception {
	try {
	    new ChannelServiceImpl(serviceProps, new DummyComponentRegistry(),
				   null);
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
    }

    public void testConstructorNoAppName() throws Exception {
	try {
	    new ChannelServiceImpl(
		new Properties(), new DummyComponentRegistry(),
		new DummyTransactionProxy());
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
    }

    /* -- Test createChannel -- */

    public void testCreateChannelNullName() {
	try {
	    channelService.createChannel(
		null, new DummyChannelListener(), Delivery.RELIABLE);
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
    }

    public void testCreateChannelNullListener() {
	try {
	    channelService.createChannel(
		"foo", null, Delivery.RELIABLE);
	    System.err.println("channel created");
	} catch (NullPointerException e) {
	    fail("Got NullPointerException");
	}
    }

    public void testCreateChannelNonSerializableListener() {
	try {
	    channelService.createChannel(
		"foo", new NonSerializableChannelListener(), Delivery.RELIABLE);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
    }

    public void testCreateChannelNoTxn() throws Exception { 
	commitTransaction();
	try {
	    createChannel();
	    fail("Expected TransactionNotActiveException");
	} catch (TransactionNotActiveException e) {
	    System.err.println(e);
	}
    }

    public void testCreateChannelAndAbort() {
	createChannel("foo");
	txn.abort(null);
	createTransaction();
	try {
	    channelService.getChannel("foo");
	    fail("Expected NameNotBoundException");
	} catch (NameNotBoundException e) {
	    System.err.println(e);
	}
    }

    public void testCreateChannelExistentChannel() {
	createChannel("exist");
	try {
	    channelService.createChannel(
		"exist", new DummyChannelListener(), Delivery.RELIABLE);
	    fail("Expected NameExistsException");
	} catch (NameExistsException e) {
	    System.err.println(e);
	}
    }

    /* -- Test getChannel -- */

    public void testGetChannelNullName() {
	try {
	    channelService.getChannel(null);
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
    }

    public void testGetChannelNonExistentName() {
	try {
	    channelService.getChannel("qwerty");
	    fail("Expected NameNotBoundException");
	} catch (NameNotBoundException e) {
	    System.err.println(e);
	}
    }

    public void testGetChannelNoTxn() throws Exception {
	commitTransaction();
	try {
	    channelService.getChannel("foo");
	    fail("Expected TransactionNotActiveException");
	} catch (TransactionNotActiveException e) {
	    System.err.println(e);
	}
    }

    public void testCreateAndGetChannelSameTxn() {
	Channel channel1 = createChannel("foo");
	try {
	    Channel channel2 = channelService.getChannel("foo");
	    if (channel1 != channel2) {
		fail("channels are not equal");
	    }
	    System.err.println("Channels are equal");
	} catch (RuntimeException e) {
	    System.err.println(e);
	    throw e;
	}
    }
	
    public void testCreateAndGetChannelDifferentTxn() throws Exception {
	Channel channel1 = createChannel("testy");
	commitTransaction();
	createTransaction();
	Channel channel2 = channelService.getChannel("testy");
	if (channel1 == channel2) {
	    fail("channels are equal");
	}
	System.err.println("Channels are not equal");
    }

    /* -- Test Channel serialization -- */

    public void testChannelWriteReadObject() throws Exception {
	Channel savedChannel = createChannel();
	ByteArrayOutputStream bout = new ByteArrayOutputStream();
	ObjectOutputStream out = new ObjectOutputStream(bout);
	out.writeObject(savedChannel);
	out.flush();
	out.close();

	ByteArrayInputStream bin = new ByteArrayInputStream(bout.toByteArray());
	ObjectInputStream in = new ObjectInputStream(bin);
	Channel channel = (Channel) in.readObject();

	if (!savedChannel.equals(channel)) {
	    fail("Expected channel: " + savedChannel + ", got " + channel);
	}
	System.err.println("Channel writeObject/readObject successful");
    }

    /* -- Test Channel.getName -- */

    public void testChannelGetNameNoTxn() throws Exception {
	Channel channel = createChannel();
	commitTransaction();
	try {
	    channel.getName();
	    fail("Expected TransactionNotActiveException");
	} catch (TransactionNotActiveException e) {
	    System.err.println(e);
	}
    }

    public void testChannelGetNameMismatchedTxn() throws Exception {
	Channel channel = createChannel();
	commitTransaction();
	createTransaction();
	try {
	    channel.getName();
	    fail("Expected TransactionNotActiveException");
	} catch (TransactionNotActiveException e) {
	    System.err.println(e);
	}
    }
    
    public void testChannelGetName() {
	String name = "name";
	Channel channel = createChannel(name);
	if (!name.equals(channel.getName())) {
	    fail("Expected: " + name + ", got: " + channel.getName());
	}
	System.err.println("Channel names are equal");
    }

    public void testChannelGetNameClosedChannel() {
	String name = "foo";
	Channel channel = createChannel(name);
	channel.close();
	if (!name.equals(channel.getName())) {
	    fail("Expected: " + name + ", got: " + channel.getName());
	}
	System.err.println("Got channel name on closed channel");
    }

    /* -- Test Channel.getDeliveryRequirement -- */

    public void testChannelGetDeliveryNoTxn() throws Exception {
	Channel channel = createChannel();
	commitTransaction();
	try {
	    channel.getDeliveryRequirement();
	    fail("Expected TransactionNotActiveException");
	} catch (TransactionNotActiveException e) {
	    System.err.println(e);
	}
    }

    public void testChannelGetDeliveryMismatchedTxn() throws Exception {
	Channel channel = createChannel();
	commitTransaction();
	createTransaction();
	try {
	    channel.getDeliveryRequirement();
	    fail("Expected TransactionNotActiveException");
	} catch (TransactionNotActiveException e) {
	    System.err.println(e);
	}
    }

    public void testChannelGetDelivery() {
	for (Delivery delivery : Delivery.values()) {
	    Channel channel = channelService.createChannel(
		delivery.toString(), new DummyChannelListener(), delivery);
	    if (!delivery.equals(channel.getDeliveryRequirement())) {
		fail("Expected: " + delivery + ", got: " +
		     channel.getDeliveryRequirement());
	    }
	}
	System.err.println("Delivery requirements are equal");
    }

    public void testChannelGetDeliveryClosedChannel() {
	for (Delivery delivery : Delivery.values()) {
	    Channel channel = channelService.createChannel(
		delivery.toString(), new DummyChannelListener(), delivery);
	    channel.close();
	    if (!delivery.equals(channel.getDeliveryRequirement())) {
		fail("Expected: " + delivery + ", got: " +
		     channel.getDeliveryRequirement());
	    }
	}
	System.err.println("Got delivery requirement on close channel");
    }

    /* -- Test Channel.join -- */

    public void testChannelJoinNoTxn() throws Exception {
	Channel channel = createChannel();
	commitTransaction();
	try {
	    channel.join(new DummyClientSession("dummy"), null);
	    fail("Expected TransactionNotActiveException");
	} catch (TransactionNotActiveException e) {
	    System.err.println(e);
	}
    }

    public void testChannelJoinClosedChannel() {
	Channel channel = createChannel();
	channel.close();
	try {
	    channel.join(new DummyClientSession("dummy"), null);
	    fail("Expected IllegalStateException");
	} catch (IllegalStateException e) {
	    System.err.println(e);
	}
    }

    public void testChannelJoinNullClientSession() {
	Channel channel = createChannel();
	try {
	    channel.join(null, new DummyChannelListener());
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
    }

    public void testChannelJoinNonSerializableListener() {
	Channel channel = createChannel();
	try {
	    channel.join(new DummyClientSession("dummy"),
			 new NonSerializableChannelListener());
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
    }

    public void testChannelJoin() throws Exception {
	String channelName = "joinTest";
	Channel channel = createChannel(channelName);
	String[] names = new String[] { "a", "b", "c" };
	Set<ClientSession> savedSessions = new HashSet<ClientSession>();

	for (String name : names) {
	    ClientSession session = new DummyClientSession(name);
	    savedSessions.add(session);
	    channel.join(session, new DummyChannelListener(channel));
	}
	commitTransaction();
	createTransaction();
	try {
	    channel = channelService.getChannel(channelName);
	    Set<ClientSession> sessions = channel.getSessions();
	    if (sessions.size() != names.length) {
		fail("Expected " + names.length + " sessions, got " +
		     sessions.size());
	    }
	    
	    for (ClientSession session : savedSessions) {
		if (!sessions.contains(session)) {
		    fail("Expected session: " + session);
		}
	    }

	    System.err.println("All sessions joined");

	} finally {
	    channel.close();
	    commitTransaction();
	}
    }

    public void testChannelJoinWithListenerReferringToChannel() throws Exception {
	String channelName = "joinWithListenerReferringToChannelTest";
	Channel channel = createChannel(channelName);
	String[] names = new String[] { "foo", "bar", "baz" };
	Set<ClientSession> savedSessions = new HashSet<ClientSession>();

	for (String name : names) {
	    ClientSession session = new DummyClientSession(name);
	    savedSessions.add(session);
	    channel.join(session, new DummyChannelListener());
	}
	commitTransaction();
	createTransaction();
	try {
	    channel = channelService.getChannel(channelName);
	    Set<ClientSession> sessions = channel.getSessions();
	    if (sessions.size() != names.length) {
		fail("Expected " + names.length + " sessions, got " +
		     sessions.size());
	    }
	    
	    for (ClientSession session : savedSessions) {
		if (!sessions.contains(session)) {
		    fail("Expected session: " + session);
		}
	    }

	    System.err.println("All sessions joined");

	} finally {
	    channel.close();
	    commitTransaction();
	}
    }

    /* -- Test Channel.leave -- */

    public void testChannelLeaveNoTxn() throws Exception {
	Channel channel = createChannel();
	commitTransaction();
	try {
	    channel.leave(new DummyClientSession("dummy"));
	    fail("Expected TransactionNotActiveException");
	} catch (TransactionNotActiveException e) {
	    System.err.println(e);
	}
    }

    public void testChannelLeaveClosedChannel() {
	Channel channel = createChannel();
	ClientSession session = new DummyClientSession("dummy");
	channel.join(session, null);
	channel.close();
	try {
	    channel.leave(session);
	    fail("Expected IllegalStateException");
	} catch (IllegalStateException e) {
	    System.err.println(e);
	}
    }

    public void testChannelLeaveNullClientSession() {
	Channel channel =  createChannel();
	try {
	    channel.leave(null);
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
    }

    public void testChannelLeaveSessionNotJoined() {
	Channel channel = createChannel();
	channel.leave(new DummyClientSession("dummy"));
	System.err.println("Leave of non-joined session successful");
    }
    
    public void testChannelLeave() throws Exception {
	String channelName = "leaveTest";
	Channel channel = createChannel(channelName);
	String[] names = new String[] { "foo", "bar", "baz" };
	Set<ClientSession> savedSessions = new HashSet<ClientSession>();

	for (String name : names) {
	    ClientSession session = new DummyClientSession(name);
	    savedSessions.add(session);
	    channel.join(session, new DummyChannelListener());
	}
	commitTransaction();
	createTransaction();
	try {
	    channel = channelService.getChannel(channelName);
	    Set<ClientSession> sessions = channel.getSessions();
	    if (sessions.size() != names.length) {
		fail("Expected " + names.length + " sessions, got " +
		     sessions.size());
	    }
	    
	    for (ClientSession session : savedSessions) {
		if (!sessions.contains(session)) {
		    fail("Expected session: " + session);
		}
		channel.leave(session);
		if (channel.getSessions().contains(session)) {
		    fail("Failed to remove session: " + session);
		}
	    }

	    if (channel.getSessions().size() != 0) {
		fail("Expected no sessions, got " + channel.getSessions().size());
	    }

	    System.err.println("All sessions left");

	} finally {
	    channel.close();
	    commitTransaction();
	}
    }

    /* -- Test Channel.leaveAll -- */

    public void testChannelLeaveAllNoTxn() throws Exception {
	Channel channel = createChannel();
	commitTransaction();
	try {
	    channel.leaveAll();
	    fail("Expected TransactionNotActiveException");
	} catch (TransactionNotActiveException e) {
	    System.err.println(e);
	}
    }

    public void testChannelLeaveAllClosedChannel() {
	Channel channel = createChannel();
	channel.close();
	try {
	    channel.leaveAll();
	    fail("Expected IllegalStateException");
	} catch (IllegalStateException e) {
	    System.err.println(e);
	}
    }

    public void testChannelLeaveAllNoSessionsJoined() {
	Channel channel = createChannel();
	channel.leaveAll();
	System.err.println("LeaveAll with no sessions joined is successful");
    }
    
    public void testChannelLeaveAll() throws Exception {
	String channelName = "leaveAllTest";
	Channel channel = createChannel(channelName);
	String[] names = new String[] { "foo", "bar", "baz" };
	Set<ClientSession> savedSessions = new HashSet<ClientSession>();

	for (String name : names) {
	    ClientSession session = new DummyClientSession(name);
	    savedSessions.add(session);
	    channel.join(session, new DummyChannelListener());
	}
	commitTransaction();
	createTransaction();
	try {
	    channel = channelService.getChannel(channelName);
	    Set<ClientSession> sessions = channel.getSessions();
	    if (sessions.size() != names.length) {
		fail("Expected " + names.length + " sessions, got " +
		     sessions.size());
	    }
	    
	    for (ClientSession session : savedSessions) {
		if (!sessions.contains(session)) {
		    fail("Expected session: " + session);
		}
	    }

	    channel.leaveAll();

	    if (channel.getSessions().size() != 0) {
		fail("Expected no sessions, got " + channel.getSessions().size());
	    }

	    System.err.println("All sessions left");

	} finally {
	    channel.close();
	    commitTransaction();
	}
    }

    /* -- Test Channel.hasSessions -- */

    public void testChannelHasSessionsNoTxn() throws Exception {
	Channel channel = createChannel();
	commitTransaction();
	try {
	    channel.hasSessions();
	    fail("Expected TransactionNotActiveException");
	} catch (TransactionNotActiveException e) {
	    System.err.println(e);
	}
    }

    public void testChannelHasSessionsClosedChannel() {
	Channel channel = createChannel();
	channel.close();
	try {
	    channel.hasSessions();
	    fail("Expected IllegalStateException");
	} catch (IllegalStateException e) {
	    System.err.println(e);
	}
    }

    public void testChannelHasSessionsNoSessionsJoined() {
	Channel channel = createChannel();
	if (channel.hasSessions()) {
	    fail("Expected hasSessions to return false");
	}
	System.err.println("hasSessions returned false");
    }
    
    public void testChannelHasSessionsSessionsJoined() {
	Channel channel = createChannel();
	channel.join(new DummyClientSession("dummy"), null);
	if (!channel.hasSessions()) {
	    fail("Expected hasSessions to return true");
	}
	System.err.println("hasSessions returned true");
    }

    /* -- Test Channel.getSessions -- */

    public void testChannelGetSessionsNoTxn() throws Exception {
	Channel channel = createChannel();
	commitTransaction();
	try {
	    channel.getSessions();
	    fail("Expected TransactionNotActiveException");
	} catch (TransactionNotActiveException e) {
	    System.err.println(e);
	}
    }

    public void testChannelGetSessionsClosedChannel() {
	Channel channel = createChannel();
	channel.close();
	try {
	    channel.getSessions();
	    fail("Expected IllegalStateException");
	} catch (IllegalStateException e) {
	    System.err.println(e);
	}
    }

    public void testChannelGetSessionsNoSessionsJoined() {
	Channel channel = createChannel();
	if (!channel.getSessions().isEmpty()) {
	    fail("Expected no sessions");
	}
	System.err.println("No sessions joined");
    }
    
    public void testChannelGetSessionsSessionsJoined() {
	Channel channel = createChannel();
	ClientSession savedSession = new DummyClientSession("getSessionTest");
	channel.join(savedSession,  null);
	Set<ClientSession> sessions = channel.getSessions();
	if (sessions.isEmpty()) {
	    fail("Expected non-empty collection");
	}
	if (sessions.size() != 1) {
	    fail("Expected 1 session, got " + sessions.size());
	}
	if (!sessions.contains(savedSession)) {
	    fail("Sessions does not contain session: " + savedSession);
	}
	System.err.println("getSessions returned the correct session");
    }

    /* -- Test Channel.send (to all) -- */

    private static byte[] testMessage = new byte[] {'x'};

    public void testChannelSendAllNoTxn() throws Exception {
	Channel channel = createChannel();
	commitTransaction();
	try {
	    channel.send(testMessage);
	    fail("Expected TransactionNotActiveException");
	} catch (TransactionNotActiveException e) {
	    System.err.println(e);
	}
    }

    public void testChannelSendAllClosedChannel() {
	Channel channel = createChannel();
	channel.close();
	try {
	    channel.send(testMessage);
	    fail("Expected IllegalStateException");
	} catch (IllegalStateException e) {
	    System.err.println(e);
	}
    }
    
    
    /* -- Test Channel.send (one recipient) -- */

    public void testChannelSendToOneNoTxn() throws Exception {
	Channel channel = createChannel();
	commitTransaction();
	try {
	    channel.send(new DummyClientSession("dummy"), testMessage);
	    fail("Expected TransactionNotActiveException");
	} catch (TransactionNotActiveException e) {
	    System.err.println(e);
	}
    }

    public void testChannelSendToOneClosedChannel() {
	Channel channel = createChannel();
	channel.close();
	try {
	    channel.send(new DummyClientSession("dummy"), testMessage);
	    fail("Expected IllegalStateException");
	} catch (IllegalStateException e) {
	    System.err.println(e);
	}
    }
    
    /* -- Test Channel.send (multiple recipients) -- */

    public void testChannelSendToMultiplelNoTxn() throws Exception {
	Channel channel = createChannel();
	commitTransaction();
	Set<ClientSession> sessions = new HashSet<ClientSession>();
	sessions.add(new DummyClientSession("dummy"));
	try {
	    channel.send(sessions, testMessage);
	    fail("Expected TransactionNotActiveException");
	} catch (TransactionNotActiveException e) {
	    System.err.println(e);
	}
    }

    public void testChannelSendToMultipleClosedChannel() {
	Channel channel = createChannel();
	channel.close();
	Set<ClientSession> sessions = new HashSet<ClientSession>();
	sessions.add(new DummyClientSession("dummy"));
	try {
	    channel.send(sessions, testMessage);
	    fail("Expected IllegalStateException");
	} catch (IllegalStateException e) {
	    System.err.println(e);
	}
    }

    /* -- Test Channel.close -- */

    public void testChannelCloseNoTxn() throws Exception {
	Channel channel = createChannel();
	commitTransaction();
	try {
	    channel.close();
	    fail("Expected TransactionNotActiveException");
	} catch (TransactionNotActiveException e) {
	    System.err.println(e);
	}
    }

    public void testChannelClose() throws Exception {
	String name = "closeTest";
	Channel channel = createChannel(name);
	commitTransaction();
	createTransaction();
	channel = channelService.getChannel(name);
	channel.close();
	try {
	    channelService.getChannel(name);
	    fail("Expected NameNotBoundException");
	} catch (NameNotBoundException e) {
	    System.err.println(e);
	}
	commitTransaction();
    }

    public void testChannelCloseTwice() throws Exception {
	String name = "closeTest";
	Channel channel = createChannel(name);
	commitTransaction();
	createTransaction();
	channel = channelService.getChannel(name);
	channel.close();
	try {
	    channelService.getChannel(name);
	    fail("Expected NameNotBoundException");
	} catch (NameNotBoundException e) {
	    System.err.println(e);
	}
	channel.close();
	System.err.println("Channel closed twice");
	commitTransaction();
    }

    public void testChannelJoinReceivedByClient() throws Exception {
	commitTransaction();
	String name = CHANNEL_NAME;
	ClientGroup group =
	    createChannelAndClientGroup(name,
					"moe", "larry", "curly");
	try {
	    group.join(name);
	    group.disconnect(true);
	    
	} catch (RuntimeException e) {
	    System.err.println("unexpected failure");
	    e.printStackTrace();
	    fail("unexpected failure: " + e);
	} finally {
	    group.disconnect(false);
	}
    }

    private ClientGroup createChannelAndClientGroup(
	String name, String... users)
	throws Exception
    {
	createTransaction();
	createChannel(name);
	commitTransaction();
	registerAppListener();
	return new ClientGroup(users);
    }

    public void testChannelLeaveReceivedByClient() throws Exception {
	commitTransaction();
	String name = CHANNEL_NAME;
	ClientGroup group =
	    createChannelAndClientGroup(name,
					"moe", "larry", "curly");
	try {
	    group.join(name);
	    group.leave(name);
	    group.disconnect(true);
	    
	} catch (RuntimeException e) {
	    System.err.println("unexpected failure");
	    e.printStackTrace();
	    fail("unexpected failure: " + e);
	} finally {
	    group.disconnect(false);
	}
    }

    public void testSessionRemovedFromChannelOnLogout() throws Exception {
	commitTransaction();
	String name = CHANNEL_NAME;
	ClientGroup group =
	    createChannelAndClientGroup(name,
					"moe", "larry", "curly");
	try {
	    group.join(name);
	    group.checkMembership(name, true);
	    group.disconnect(true);
	    Thread.sleep(WAIT_TIME); // this is necessary, and unfortunate...
	    group.checkMembership(name, false);
	    
	} catch (RuntimeException e) {
	    System.err.println("unexpected failure");
	    e.printStackTrace();
	    fail("unexpected failure: " + e);
	} finally {
	    group.disconnect(false);
	}
    }
    
    public void testChannelSetsRemovedOnLogout() throws Exception {
	commitTransaction();
	String name = CHANNEL_NAME;
	ClientGroup group =
	    createChannelAndClientGroup(name,
					"moe", "larry", "curly");
	try {
	    group.join(name);
	    group.checkMembership(name, true);
	    group.checkChannelSets(true);
	    group.disconnect(true);
	    Thread.sleep(WAIT_TIME); // this is necessary, and unfortunate...
	    group.checkMembership(name, false);
	    group.checkChannelSets(false);
	    
	} catch (RuntimeException e) {
	    System.err.println("unexpected failure");
	    e.printStackTrace();
	    fail("unexpected failure: " + e);
	} finally {
	    group.disconnect(false);
	}
    }

    public void testSessionsRemovedOnCrash() throws Exception {
	commitTransaction();
	String name = CHANNEL_NAME;
	ClientGroup group =
	    createChannelAndClientGroup(name,
					"moe", "larry", "curly");
	try {
	    group.join(name);
	    group.checkMembership(name, true);
	    group.checkChannelSets(true);

            // Simulate "crash"
            tearDown(false);
            System.err.println("simulated crash");
            setUp(false);

	    Thread.sleep(WAIT_TIME); // this is necessary, and unfortunate...
	    group.checkMembership(name, false);
	    group.checkChannelSets(false);

	} catch (RuntimeException e) {
	    System.err.println("unexpected failure");
	    e.printStackTrace();
	    fail("unexpected failure: " + e);
	} finally {
	    group.disconnect(false);
	}
	
    }

    public void testSendFromSessionToSession() throws Exception {
	commitTransaction();
	String name = CHANNEL_NAME;
	ClientGroup group =
	    createChannelAndClientGroup(name, "moe", "larry", "curly");
	try {
	    group.join(name);
	    DummyClient moe = group.getClient("moe");
	    DummyClient larry = group.getClient("larry");
	    DummyClient curly = group.getClient("curly");
	    CompactId channelId = moe.channelNameToId.get(CHANNEL_NAME);
	    int numMessages = 3;
	    Set<CompactId> recipientIds = new HashSet<CompactId>();
	    recipientIds.add(larry.sessionId);
	    for (int i = 0; i < numMessages; i++) {
		MessageBuffer buf =
		    new MessageBuffer(MessageBuffer.getSize("moe") + 4);
		buf.putString("moe").
		    putInt(i);
		moe.sendChannelMessage(channelId, recipientIds, buf.getBuffer());
	    }

	    for (int nextNum = 0; nextNum < numMessages; nextNum++) {
		MessageInfo info = larry.nextChannelMessage();
		if (info == null) {
		    fail("got "  + nextNum +
			 "channel messages, expected " + numMessages);
		}
		if (! info.channelId.equals(channelId)) {
		    fail("got channelId: " + info.channelId + ", expected " +
			 channelId);
		}
		MessageBuffer buf = new MessageBuffer(info.message);
		String senderName = buf.getString();
		int num = buf.getInt();
		if (!senderName.equals("moe")) {
		    fail("got sender name " + senderName + ", expected " +
			 "moe");
		}
		if (num != nextNum) {
		    fail("got number " + num + ", expected " + nextNum);
		}
		System.err.println("receiver got message from " + senderName +
				   " with sequence number " + num);
	    }

	    if (moe.nextChannelMessage() != null) {
		fail("sender received channel message");
	    }

	    if (curly.nextChannelMessage() != null) {
		fail("non-recipient received channel message");
	    }

	    
	} catch (RuntimeException e) {
	    System.err.println("unexpected failure");
	    e.printStackTrace();
	    fail("unexpected failure: " + e);
	} finally {
	    group.disconnect(false);
	}
    }
    
    public void testSendtoAllChannelMembers() throws Exception {
	commitTransaction();
	ClientGroup group =
	    createChannelAndClientGroup(CHANNEL_NAME, "moe", "larry", "curly");

	try {
	    boolean failed = false;
	    group.join(CHANNEL_NAME);
	    String senderName = "moe";
	    DummyClient sender = group.getClient(senderName);
	    CompactId channelId = sender.channelNameToId.get(CHANNEL_NAME);
	    String messageString = "from moe";
	    MessageBuffer buf =
		new MessageBuffer(MessageBuffer.getSize(messageString) + 4);
	    buf.putString(messageString).
		putInt(0);
	    
	    sender.sendChannelMessage(channelId, ALL_MEMBERS, buf.getBuffer());

	    for (DummyClient client : group.getClients()) {
		MessageInfo info = client.nextChannelMessage();
		if (senderName.equals(client.name)) {
		    if (info != null) {
			failed = true;
			System.err.println(
			    "sender:" + senderName + " recieved message");
		    }
		} else {
		    if (info != null) {
			buf = new MessageBuffer(info.message);
			String message = buf.getString();
			if (!message.equals(messageString)) {
			    fail("Got message: " + message + ", Expected: " +
				 messageString);
			}
			System.err.println(
			    client.name + " got channel message: " + message);
		    } else {
			failed = true;
			System.err.println(
 			    "member:" + client.name + " did not get message");
		    }
		}
	    }

	    if (failed) {
		fail("test failed");
	    }
	    
	} catch (RuntimeException e) {
	    System.err.println("unexpected failure");
	    e.printStackTrace();
	    fail("unexpected failure: " + e);
	} finally {
	    group.disconnect(false);
	}
    }
    
    public void testSendFromNonMemberSession() throws Exception {
	commitTransaction();
	ClientGroup group =
	    createChannelAndClientGroup(CHANNEL_NAME, "moe", "larry");
	ClientGroup outcast = new ClientGroup("curly");

	try {
	    boolean failed = false;
	    group.join(CHANNEL_NAME);
	    DummyClient moe = group.getClient("moe");
	    CompactId channelId = moe.channelNameToId.get(CHANNEL_NAME);
	    DummyClient curly = outcast.getClient("curly");
	    String messageString = "from curly";
	    MessageBuffer buf =
		new MessageBuffer(MessageBuffer.getSize(messageString) + 4);
	    buf.putString(messageString).
		putInt(0);
	    
	    curly.sendChannelMessage(channelId, ALL_MEMBERS, buf.getBuffer());

	    for (DummyClient client : group.getClients()) {
		MessageInfo info = client.nextChannelMessage();
		if (info != null) {
		    buf = new MessageBuffer(info.message);
		    String message = buf.getString();
		    System.err.println(
			client.name + " got channel message: " + message);
		    failed = true;
		}
	    }

	    if (failed) {
		fail("one or more clients got channel message");
	    }
	    
	} catch (RuntimeException e) {
	    System.err.println("unexpected failure");
	    e.printStackTrace();
	    fail("unexpected failure: " + e);
	} finally {
	    try {
		group.disconnect(false);
	    } catch (Exception e) {
	    }
	    try {
		outcast.disconnect(false);
	    } catch (Exception e) {
	    }
	}
    }
    
    public void testChannelSetsRemovedOnCrash() throws Exception {
		/*
	fail("this test needs to be implemented");
		*/
    }

    private class ClientGroup {

	final String[] users;
	final Map<String, DummyClient> clients =
	    new HashMap<String, DummyClient>();

	ClientGroup(String... users) {
	    this.users = users;
	    for (String user : users) {
		DummyClient client = new DummyClient();
		clients.put(user, client);
		client.connect(port);
		client.login(user, "password");
	    }
	}

	void join(String channelName) {
	    for (DummyClient client : clients.values()) {
		client.join(channelName);
	    }
	}

	void leave(String channelName) {
	    for (DummyClient client : clients.values()) {
		client.leave(channelName);
	    }
	}

	void checkMembership(String name, boolean isMember) throws Exception {
	    createTransaction();
	    Channel channel = channelService.getChannel(name);
	    Set<ClientSession> sessions = channel.getSessions();
	    for (DummyClient client : clients.values()) {

		ClientSession session =
		    sessionService.getClientSession(client.getSessionId());

		if (session != null && sessions.contains(session)) {
		    if (!isMember) {
			fail("ClientGroup.checkMembership session: " +
			     session.getName() + " is a member of " + name);
		    }
		} else if (isMember) {
                    String sessionName =
                        (session == null) ? "null" : session.getName();
                    fail("ClientGroup.checkMembership session: " +
                        sessionName + " is not a member of " + name);
		}
	    }

	    commitTransaction();
	}

	void checkChannelSets(boolean exists) throws Exception {
	    createTransaction();
	    for (DummyClient client : clients.values()) {
		String sessionKey = getSessionKey(client.getSessionId());
		try {
		    dataService.getServiceBinding(
			sessionKey, ManagedObject.class);
		    if (!exists) {
			fail("ClientGroup.checkChannelSets set exists: " +
			     client.name);
		    }
		} catch (NameNotBoundException e) {
		    if (exists) {
			fail("ClientGroup.checkChannelSets no channel set: " +
			     client.name);
		    }
		}
	    }
	    commitTransaction();
	}

	DummyClient getClient(String name) {
	    return clients.get(name);
	}

	Collection<DummyClient> getClients() {
	    return clients.values();
	}
	
	void disconnect(boolean graceful) {
	    for (DummyClient client : clients.values()) {
		client.disconnect(graceful);
	    }
	}
    }

    /* -- other methods -- */

    private static final String SESSION_PREFIX =
	ChannelServiceImpl.class.getName() + ".session.";
    
    private static String getSessionKey(byte[] sessionId) {
	return SESSION_PREFIX + HexDumper.toHexString(sessionId);
    }

    /** Deletes the specified directory, if it exists. */
    static void deleteDirectory(String directory) {
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
    }

    /**
     * Returns a newly created channel
     */
    private Channel createChannel() {
	return createChannel("test");
    }

    private Channel createChannel(String name) {
	return channelService.createChannel(
	    name,
	    new DummyChannelListener(),
	    Delivery.RELIABLE);
    }

    /**
     * Creates a new transaction, and sets transaction proxy's
     * current transaction.
     */
    private DummyTransaction createTransaction() {
	txn = new DummyTransaction();
	txnProxy.setCurrentTransaction(txn);
	return txn;
    }
    
    /**
     * Creates a new transaction with the specified timeout, and sets
     * transaction proxy's current transaction.
     */
    private DummyTransaction createTransaction(long timeout) {
	txn = new DummyTransaction(timeout);
	txnProxy.setCurrentTransaction(txn);
	return txn;
    }

    private void commitTransaction() throws Exception {
	txn.commit();
	txnProxy.setCurrentTransaction(null);
	txn = null;
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
 
    /**
     * Creates a new data service.  If the database directory does
     * not exist, one is created.
     */
    private DataServiceImpl createDataService(
	DummyComponentRegistry registry)
	throws Exception
    {
	File dir = new File(DB_DIRECTORY);
	if (!dir.exists()) {
	    if (!dir.mkdir()) {
		throw new RuntimeException(
		    "Problem creating directory: " + dir);
	    }
	}
	return new DataServiceImpl(serviceProps, registry, txnProxy);
    }
    
    /* -- other classes -- */

    private static class NonSerializableChannelListener
	implements ChannelListener
    {
	NonSerializableChannelListener() {}
	
        /** {@inheritDoc} */
	public void receivedMessage(
	    Channel channel, ClientSession session, byte[] message)
	{
	}
    }

    private static class DummyChannelListener
	implements ChannelListener, Serializable
    {
	private final static long serialVersionUID = 1L;

	private final Channel expectedChannel;

	DummyChannelListener() {
	    this(null);
	}

	DummyChannelListener(Channel channel) {
	    expectedChannel = channel;
	}
	
        /** {@inheritDoc} */
	public void receivedMessage(
	    Channel channel, ClientSession session, byte[] message)
	{
            if (expectedChannel != null) {
                assertEquals(expectedChannel, channel);
            }
	}
    }

    private static class DummyClientSession
	implements ClientSession, Serializable
    {
	private final static long serialVersionUID = 1L;
	private static byte nextByte = 0x00;

	private final String name;
	private transient byte[] id = new byte[1];
	
	DummyClientSession(String name) {
	    this.name = name;
	    this.id[0] = nextByte;
	    nextByte += 0x01;
	}

	/* -- Implement ClientSession -- */
	
        /** {@inheritDoc} */
	public String getName() {
	    return name;
	}

        /** {@inheritDoc} */
	public ClientSessionId getSessionId() {
	    return new ClientSessionId(id);
	}

        /** {@inheritDoc} */
	public void send(byte[] message) {
	}

        /** {@inheritDoc} */
	public void disconnect() {
	}

        /** {@inheritDoc} */
	public boolean isConnected() {
	    return true;
	}

	/* -- Implement ClientSession -- */

        /** {@inheritDoc} */
        public Identity getIdentity() {
            return new DummyIdentity(name);
        }

        /** {@inheritDoc} */
	public void sendProtocolMessage(byte[] message, Delivery delivery) {
	}

        /** {@inheritDoc} */
	public void sendProtocolMessageOnCommit(
		byte[] message, Delivery delivery) {
	}
	
	/* -- Implement Object -- */
	
        /** {@inheritDoc} */
	public int hashCode() {
	    return id[0];
	}

        /** {@inheritDoc} */
	public boolean equals(Object obj) {
	    if (this == obj) {
		return true;
	    } else if (obj instanceof DummyClientSession) {
		DummyClientSession session = (DummyClientSession) obj;
		return
		    name.equals(session.name) && Arrays.equals(id, session.id);
	    }
	    return false;
	}

        /** {@inheritDoc} */
	public String toString() {
	    return getClass().getName() + "[" + name + "]";
	}

	/* -- Serialization -- */

	private void writeObject(ObjectOutputStream out) throws IOException {
	    out.defaultWriteObject();
	    out.writeInt(id.length);
	    for (byte b : id) {
		out.writeByte(b);
	    }
	}

	private void readObject(ObjectInputStream in)
	    throws IOException, ClassNotFoundException
	{
	    in.defaultReadObject();
	    int size = in.readInt();
	    this.id = new byte[size];
	    for (int i = 0; i < size; i++) {
		id[i] = in.readByte();
	    }
	}
    }

    private void registerAppListener() throws Exception {
	createTransaction();
	DummyAppListener appListener = new DummyAppListener();
	dataService.setServiceBinding(
	    StandardProperties.APP_LISTENER, appListener);
	commitTransaction();
    }
    
    private DummyAppListener getAppListener() {
	return (DummyAppListener) dataService.getServiceBinding(
	    "com.sun.sgs.app.AppListener", AppListener.class);
    }

    /**
     * Dummy identity manager for testing purposes.
     */
    private static class DummyIdentityManager implements IdentityManager {
	public Identity authenticateIdentity(IdentityCredentials credentials) {
	    return new DummyIdentity(credentials);
	}
    }
    
    /**
     * Identity returned by the DummyIdentityManager.
     */
    private static class DummyIdentity implements Identity, Serializable {

        private static final long serialVersionUID = 1L;
        private final String name;

        DummyIdentity(String name) {
            this.name = name;
        }

	DummyIdentity(IdentityCredentials credentials) {
	    this.name = ((NamePasswordCredentials) credentials).getName();
	}
	
	public String getName() {
	    return name;
	}

	public void notifyLoggedIn() {}

	public void notifyLoggedOut() {}
        
        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (! (o instanceof DummyIdentity))
                return false;
            return ((DummyIdentity)o).name.equals(name);
        }
        
        @Override
        public int hashCode() {
            return name.hashCode();
        }
    }

    /**
     * Dummy client code for testing purposes.
     */
    private static class DummyClient {

	String name;
	CompactId sessionId;
	private Connector<SocketAddress> connector;
	private ConnectionListener listener;
	private Connection connection;
	private boolean connected = false;
	private final Object lock = new Object();
	private boolean loginAck = false;
	private boolean loginSuccess = false;
	private boolean logoutAck = false;
	private boolean joinAck = false;
	private boolean leaveAck = false;
        private boolean awaitGraceful = false;
	private Map<CompactId, String> channelIdToName =
	    new HashMap<CompactId, String>();
	private Map<String, CompactId> channelNameToId =
	    new HashMap<String, CompactId>();
	//private String channelName = null;
	//private CompactId channelId = null;
	private String reason;
	private CompactId reconnectionKey;
	private final List<MessageInfo> channelMessages =
	    new ArrayList<MessageInfo>();
	private final AtomicLong sequenceNumber = new AtomicLong(0);

	
	DummyClient() {
	}

	byte[] getSessionId() {
	    return sessionId.getId();
	}

	void connect(int port) {
	    connected = false;
	    listener = new Listener();
	    try {
		SocketEndpoint endpoint =
		    new SocketEndpoint(
		        new InetSocketAddress(InetAddress.getLocalHost(), port),
			TransportType.RELIABLE);
		connector = endpoint.createConnector();
		connector.connect(listener);
	    } catch (Exception e) {
		System.err.println("DummyClient.connect throws: " + e);
		e.printStackTrace();
		throw new RuntimeException("DummyClient.connect failed", e);
	    }
	    synchronized (lock) {
		try {
		    if (connected == false) {
			lock.wait(WAIT_TIME);
		    }
		    if (connected != true) {
			throw new RuntimeException(
 			    "DummyClient.connect timed out");
		    }
		} catch (InterruptedException e) {
		    throw new RuntimeException(
			"DummyClient.connect timed out", e);
		}
	    }
	    
	}

	void disconnect(boolean graceful) {
	    System.err.println("DummyClient.disconnect: " + graceful);

            if (graceful) {
	        logout();
                return;
            }

            synchronized (lock) {
                if (connected == false) {
                    return;
                }
                try {
                    connection.close();
                } catch (IOException e) {
                    System.err.println(
                        "DummyClient.disconnect exception:" + e);
                    connected = false;
                    lock.notifyAll();
                }
            }
	}

	void login(String user, String pass) {
	    synchronized (lock) {
		if (connected == false) {
		    throw new RuntimeException(
			"DummyClient.login not connected");
		}
	    }
	    this.name = user;

	    MessageBuffer buf =
		new MessageBuffer(3 + MessageBuffer.getSize(user) +
				  MessageBuffer.getSize(pass));
	    buf.putByte(SimpleSgsProtocol.VERSION).
		putByte(SimpleSgsProtocol.APPLICATION_SERVICE).
		putByte(SimpleSgsProtocol.LOGIN_REQUEST).
		putString(user).
		putString(pass);
	    loginAck = false;
	    try {
		connection.sendBytes(buf.getBuffer());
	    } catch (IOException e) {
		throw new RuntimeException(e);
	    }
	    synchronized (lock) {
		try {
		    if (loginAck == false) {
			lock.wait(WAIT_TIME);
		    }
		    if (loginAck != true) {
			throw new RuntimeException(
			    "DummyClient.login timed out");
		    }
		    if (!loginSuccess) {
			throw new RuntimeException(LOGIN_FAILED_MESSAGE);
		    }
		} catch (InterruptedException e) {
		    throw new RuntimeException(
			"DummyClient.login timed out", e);
		}
	    }
	}

	/**
	 * Returns the next sequence number for this session.
	 */
	private long nextSequenceNumber() {
	    return sequenceNumber.getAndIncrement();
	}

	/**
	 * Sends a SESSION_MESSAGE.
	 */
	void sendMessage(byte[] message) {
	    checkLoggedIn();

	    MessageBuffer buf =
		new MessageBuffer(13 + message.length);
	    buf.putByte(SimpleSgsProtocol.VERSION).
		putByte(SimpleSgsProtocol.APPLICATION_SERVICE).
		putByte(SimpleSgsProtocol.SESSION_MESSAGE).
		putLong(nextSequenceNumber()).
		putByteArray(message);
	    try {
		connection.sendBytes(buf.getBuffer());
	    } catch (IOException e) {
		throw new RuntimeException(e);
	    }
	}

	/**
	 * Sends a CHANNEL_SEND_REQUEST.
	 */
	void sendChannelMessage(CompactId channelToSend,
				Set<CompactId> recipientIds,
				byte[] message) {
	    checkLoggedIn();

	    
	    MessageBuffer buf =
		new MessageBuffer(3 + channelToSend.getExternalFormByteCount() +
				  8 + 2 + getSize(recipientIds) +
				  2 + message.length);
	    buf.putByte(SimpleSgsProtocol.VERSION).
		putByte(SimpleSgsProtocol.CHANNEL_SERVICE).
		putByte(SimpleSgsProtocol.CHANNEL_SEND_REQUEST).
		putBytes(channelToSend.getExternalForm()).
		putLong(nextSequenceNumber()).
		putShort(recipientIds.size());
	    for (CompactId recipientId : recipientIds) {
		recipientId.putCompactId(buf);
	    }
	    buf.putByteArray(message);
	    try {
		connection.sendBytes(buf.getBuffer());
	    } catch (IOException e) {
		throw new RuntimeException(e);
	    }
	}

	MessageInfo nextChannelMessage() {
	    synchronized (lock) {
		if (channelMessages.isEmpty()) {
		    try {
			lock.wait(WAIT_TIME);
		    } catch (InterruptedException e) {
		    }
		}
		return
		    channelMessages.isEmpty() ?
		    null :
		    channelMessages.remove(0);
	    }
	}

	private int getSize(Set<CompactId> ids) {
	    int size = 0;
	    for (CompactId id : ids) {
		size += id.getExternalFormByteCount();
	    }
	    return size;
	}

	/**
	 * Throws a {@code RuntimeException} if this session is not
	 * logged in.
	 */
	private void checkLoggedIn() {
	    synchronized (lock) {
		if (!connected || !loginSuccess) {
		    throw new RuntimeException(
			"DummyClient.login not connected or loggedIn");
		}
	    }
	}

	void join(String channelToJoin) {
	    String action = "join";
	    MessageBuffer buf =
		new MessageBuffer(MessageBuffer.getSize(action) +
				  MessageBuffer.getSize(channelToJoin));
	    buf.putString(action).putString(channelToJoin);
	    sendMessage(buf.getBuffer());
	    joinAck = false;
	    synchronized (lock) {
		try {
		    if (joinAck == false) {
			lock.wait(WAIT_TIME);
		    }
		    if (joinAck != true) {
			throw new RuntimeException(
			    "DummyClient.join timed out: " + channelToJoin);
		    }

		    if (! channelNameToId.containsKey(channelToJoin)) {
			fail("DummyClient.join not joined: " +
			     channelToJoin);
		    }
		    
		} catch (InterruptedException e) {
		    throw new RuntimeException(
			    "DummyClient.join timed out: " + channelToJoin, e);
		}
	    }
	}
	    
	void leave(String channelToLeave) {
	    String action = "leave";
	    MessageBuffer buf =
		new MessageBuffer(MessageBuffer.getSize(action) +
				  MessageBuffer.getSize(channelToLeave));
	    buf.putString(action).putString(channelToLeave);
	    sendMessage(buf.getBuffer());
	    leaveAck = false;
	    synchronized (lock) {
		try {
		    if (leaveAck == false) {
			lock.wait(WAIT_TIME);
		    }
		    if (leaveAck != true) {
			throw new RuntimeException(
			    "DummyClient.leave timed out: " + channelToLeave);
		    }

		    if (channelNameToId.containsKey(channelToLeave)) {
			fail("DummyClient.leave still joined: " +
			     channelToLeave);
		    }
		    
		} catch (InterruptedException e) {
		    throw new RuntimeException(
			    "DummyClient.leave timed out: " + channelToLeave, e);
		}
	    }
	}
	
	void logout() {
            synchronized (lock) {
                if (connected == false) {
                    return;
                }
                MessageBuffer buf = new MessageBuffer(3);
                buf.putByte(SimpleSgsProtocol.VERSION).
                putByte(SimpleSgsProtocol.APPLICATION_SERVICE).
                putByte(SimpleSgsProtocol.LOGOUT_REQUEST);
                logoutAck = false;
                awaitGraceful = true;
                try {
                    connection.sendBytes(buf.getBuffer());
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                synchronized (lock) {
                    try {
                        if (logoutAck == false) {
                            lock.wait(WAIT_TIME);
                        }
                        if (logoutAck != true) {
                            throw new RuntimeException(
                                "DummyClient.disconnect timed out");
                        }
                    } catch (InterruptedException e) {
                        throw new RuntimeException(
                            "DummyClient.disconnect timed out", e);
                    }
                }
            }
	}

	private class Listener implements ConnectionListener {

	    List<byte[]> messageList = new ArrayList<byte[]>();
	    
            /** {@inheritDoc} */
	    public void bytesReceived(Connection conn, byte[] buffer) {
		if (connection != conn) {
		    System.err.println(
			"DummyClient.Listener connected wrong handle, got:" +
			conn + ", expected:" + connection);
		    return;
		}

		MessageBuffer buf = new MessageBuffer(buffer);

		byte version = buf.getByte();
		if (version != SimpleSgsProtocol.VERSION) {
		    System.err.println(
			"bytesReceived: got version: " +
			version + ", expected: " + SimpleSgsProtocol.VERSION);
		    return;
		}

		byte serviceId = buf.getByte();
		switch (serviceId) {
		    
		case SimpleSgsProtocol.APPLICATION_SERVICE:
		    processAppProtocolMessage(buf);
		    break;

		case SimpleSgsProtocol.CHANNEL_SERVICE:
		    processChannelProtocolMessage(buf);
		    break;

		default:
		    System.err.println(
			"bytesReceived: got service id: " +
                        serviceId + ", expected: " +
                        SimpleSgsProtocol.APPLICATION_SERVICE);
		    return;
		}
	    }

	    private void processAppProtocolMessage(MessageBuffer buf) {

		byte opcode = buf.getByte();

		switch (opcode) {

		case SimpleSgsProtocol.LOGIN_SUCCESS:
		    sessionId = CompactId.getCompactId(buf);
		    reconnectionKey = CompactId.getCompactId(buf);
		    synchronized (lock) {
			loginAck = true;
			loginSuccess = true;
			System.err.println("login succeeded: " + name);
			lock.notifyAll();
		    }
		    break;
		    
		case SimpleSgsProtocol.LOGIN_FAILURE:
		    reason = buf.getString();
		    synchronized (lock) {
			loginAck = true;
			loginSuccess = false;
			System.err.println("login failed: " + name +
					   ", reason:" + reason);
			lock.notifyAll();
		    }
		    break;

		case SimpleSgsProtocol.LOGOUT_SUCCESS:
		    synchronized (lock) {
			logoutAck = true;
			System.err.println("logout succeeded: " + name);
                        // let disconnect do the lock notification
		    }
		    break;

		case SimpleSgsProtocol.SESSION_MESSAGE:
                    buf.getLong(); // FIXME sequence number
		    byte[] message = buf.getBytes(buf.getUnsignedShort());
		    synchronized (lock) {
			messageList.add(message);
			System.err.println("message received: " + message);
			lock.notifyAll();
		    }
		    break;

		default:
		    System.err.println(	
			"processAppProtocolMessage: unknown op code: " +
			opcode);
		    break;
		}
	    }

	    private void processChannelProtocolMessage(MessageBuffer buf) {

		byte opcode = buf.getByte();

		switch (opcode) {

		case SimpleSgsProtocol.CHANNEL_JOIN: {
		    String channelName = buf.getString();
		    CompactId channelId = CompactId.getCompactId(buf);
		    synchronized (lock) {
			joinAck = true;
			channelIdToName.put(channelId, channelName);
			channelNameToId.put(channelName, channelId);
			System.err.println("join succeeded: " + channelName);
			lock.notifyAll();
		    }
		    break;
		}
		    
		case SimpleSgsProtocol.CHANNEL_LEAVE: {
		    CompactId channelId = CompactId.getCompactId(buf);
		    synchronized (lock) {
			leaveAck = true;
			String channelName = channelIdToName.remove(channelId);
			channelNameToId.remove(channelName);
			System.err.println("leave succeeded: " + channelName);
			lock.notifyAll();
		    }
		    break;
		}

		case SimpleSgsProtocol.CHANNEL_MESSAGE: {
		    CompactId channelId = CompactId.getCompactId(buf);
		    long seq = buf.getLong();
		    CompactId senderId = CompactId.getCompactId(buf);
		    byte[] message = buf.getByteArray();
		    synchronized (lock) {
			channelMessages.add(
			    new MessageInfo(channelId, senderId, message, seq));
			lock.notifyAll();
		    }
		    break;
		}

		default:
		    System.err.println(	
			"processChannelProtocolMessage: unknown op code: " +
			opcode);
		    break;
		}
	    }

            /** {@inheritDoc} */
	    public void connected(Connection conn) {
		System.err.println("DummyClient.Listener.connected");
		if (connection != null) {
		    System.err.println(
			"DummyClient.Listener.already connected handle: " +
			connection);
		    return;
		}
		connection = conn;
		synchronized (lock) {
		    connected = true;
		    lock.notifyAll();
		}
	    }

            /** {@inheritDoc} */
	    public void disconnected(Connection conn) {
                synchronized (lock) {
                    if (awaitGraceful) {
                        // Hack since client might not get last msg
                        logoutAck = true;
                    }
                    connected = false;
                    lock.notifyAll();
                }
	    }
	    
            /** {@inheritDoc} */
	    public void exceptionThrown(Connection conn, Throwable exception) {
		System.err.println("DummyClient.Listener.exceptionThrown " +
				   "exception:" + exception);
		exception.printStackTrace();
	    }
	}
    }

    private static class MessageInfo {
	final CompactId channelId;
	final CompactId senderId;
	final byte[] message;
	final long seq;

	MessageInfo(CompactId channelId, CompactId senderId,
		    byte[] message, long seq) {
	    this.channelId = channelId;
	    this.senderId = senderId;
	    this.message = message;
	    this.seq = seq;
	}
    }

    private static class DummyAppListener implements AppListener, Serializable {

	private final static long serialVersionUID = 1L;

	private final Map<ClientSession, ManagedReference> sessions =
	    Collections.synchronizedMap(
		new HashMap<ClientSession, ManagedReference>());

        /** {@inheritDoc} */
	public ClientSessionListener loggedIn(ClientSession session) {
	    
	    DummyClientSessionListener listener =
		new DummyClientSessionListener(session);
	    DataManager dataManager = AppContext.getDataManager();
	    dataManager.markForUpdate(this);
	    ManagedReference listenerRef =
		dataManager.createReference(listener);
	    sessions.put(session, listenerRef);
	    System.err.println("DummyAppListener.loggedIn: session:" + session);
	    return listener;
	}

        /** {@inheritDoc} */
	public void initialize(Properties props) {
	}

	private Set<ClientSession> getSessions() {
	    return sessions.keySet();
	}

	DummyClientSessionListener getClientSessionListener(String name) {

	    for (Map.Entry<ClientSession,ManagedReference> entry :
		     sessions.entrySet()) {

		ClientSession session = entry.getKey();
		ManagedReference listenerRef = entry.getValue();
		if (session.getName().equals(name)) {
		    return listenerRef.get(DummyClientSessionListener.class);
		}
	    }
	    return null;
	}
    }

    private static class DummyClientSessionListener
	implements ClientSessionListener, Serializable, ManagedObject
    {
	private final static long serialVersionUID = 1L;
	private final String name;
	boolean receivedDisconnectedCallback = false;
	boolean wasGracefulDisconnect = false;
	
	private final ClientSession session;
	
	DummyClientSessionListener(ClientSession session) {
	    this.session = session;
	    this.name = session.getName();
	}

        /** {@inheritDoc} */
	public void disconnected(boolean graceful) {
	    System.err.println("DummyClientSessionListener[" + name +
			       "] disconnected invoked with " + graceful);
	    AppContext.getDataManager().markForUpdate(this);
	    synchronized (disconnectedCallbackLock) {
		receivedDisconnectedCallback = true;
		this.wasGracefulDisconnect = graceful;
		disconnectedCallbackLock.notifyAll();
	    }
	}

        /** {@inheritDoc} */
	public void receivedMessage(byte[] message) {
	    MessageBuffer buf = new MessageBuffer(message);
	    String action = buf.getString();
	    if (action.equals("join")) {
		String channelName = buf.getString();
		System.err.println("DummyClientSessionListener: join request, " +
				   "channel name: " + channelName +
				   ", user: " + name);
		Channel channel =
		    AppContext.getChannelManager().getChannel(channelName);
		channel.join(session, null);
	    } else if (action.equals("leave")) {
		String channelName = buf.getString();
		System.err.println("DummyClientSessionListener: leave request, " +
				   "channel name: " + channelName +
				   ", user: " + name);
		Channel channel =
		    AppContext.getChannelManager().getChannel(channelName);
		channel.leave(session);
	    }
	}
    }
}
