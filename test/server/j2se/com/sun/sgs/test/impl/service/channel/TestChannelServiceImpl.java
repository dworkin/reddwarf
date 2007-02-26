/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.test.impl.service.channel;

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
import com.sun.sgs.impl.service.session.ClientSessionServiceImpl;
import com.sun.sgs.impl.service.task.TaskServiceImpl;
import com.sun.sgs.impl.util.HexDumper;
import com.sun.sgs.impl.util.MessageBuffer;
import com.sun.sgs.io.Connection;
import com.sun.sgs.io.ConnectionListener;
import com.sun.sgs.io.Connector;
import com.sun.sgs.kernel.ComponentRegistry;
import com.sun.sgs.protocol.simple.SimpleSgsProtocol;
import com.sun.sgs.service.ClientSessionService;
import com.sun.sgs.service.DataService;
import com.sun.sgs.service.ProtocolMessageListener;
import com.sun.sgs.service.SgsClientSession;
import com.sun.sgs.service.TaskService;
import com.sun.sgs.service.TransactionProxy;
import com.sun.sgs.test.util.DummyComponentRegistry;
import com.sun.sgs.test.util.DummyTaskScheduler;
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

    /** The name of the DataServiceImpl class. */
    private static final String DataServiceImplClassName =
	DataServiceImpl.class.getName();

    /** Directory used for database shared across multiple tests. */
    private static final String DB_DIRECTORY =
        System.getProperty("java.io.tmpdir") + File.separator +
	"TestChannelServiceImpl.db";

    /** The port for the client session service. */
    private static int PORT = 0;

    /** Properties for the channel service and client session service. */
    private static Properties serviceProps = createProperties(
	StandardProperties.APP_NAME, "TestChannelServiceImpl",
	StandardProperties.APP_PORT, Integer.toString(PORT));

    /** Properties for creating the shared database. */
    private static Properties dbProps = createProperties(
	DataStoreImplClassName + ".directory", DB_DIRECTORY,
	StandardProperties.APP_NAME, "TestChannelServiceImpl",
	DataServiceImplClassName + ".debugCheckInterval", "1");
    
    private static final int SESSION_ID_SIZE = 8;

    private static final String CHANNEL_NAME = "three stooges";
    
    private static final int WAIT_TIME = 2000;
    
    private static final String LOGIN_FAILED_MESSAGE = "login failed";
    
    private static Object disconnectedCallbackLock = new Object();

    /** A per-test database directory, or null if not created. */
    private String directory;
    
    private static DummyTransactionProxy txnProxy =
	MinimalTestKernel.getTransactionProxy();

    private DummyAbstractKernelAppContext appContext;
    private DummyComponentRegistry systemRegistry;
    private DummyComponentRegistry serviceRegistry;
    private DummyTransaction txn;

    private DataServiceImpl dataService;
    private ChannelServiceImpl channelService;
    private ClientSessionServiceImpl sessionService;
    private TaskServiceImpl taskService;
    private DummyTaskScheduler taskScheduler;
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

        appContext = MinimalTestKernel.createContext();
	systemRegistry = MinimalTestKernel.getSystemRegistry(appContext);
	serviceRegistry = MinimalTestKernel.getServiceRegistry(appContext);

	// create services
	dataService = createDataService(systemRegistry);
	taskService = new TaskServiceImpl(new Properties(), systemRegistry);
	identityManager = new DummyIdentityManager();
	systemRegistry.setComponent(IdentityManager.class, identityManager);
	sessionService =
	    new ClientSessionServiceImpl(serviceProps, systemRegistry);
	channelService = new ChannelServiceImpl(serviceProps, systemRegistry);

	createTransaction();

	// configure data service
        dataService.configure(serviceRegistry, txnProxy);
        txnProxy.setComponent(DataService.class, dataService);
        txnProxy.setComponent(DataServiceImpl.class, dataService);
        serviceRegistry.setComponent(DataManager.class, dataService);
        serviceRegistry.setComponent(DataService.class, dataService);
        serviceRegistry.setComponent(DataServiceImpl.class, dataService);

	// configure task service
        taskService.configure(serviceRegistry, txnProxy);
        txnProxy.setComponent(TaskService.class, taskService);
        txnProxy.setComponent(TaskServiceImpl.class, taskService);
        serviceRegistry.setComponent(TaskManager.class, taskService);
        serviceRegistry.setComponent(TaskService.class, taskService);
        serviceRegistry.setComponent(TaskServiceImpl.class, taskService);
	//serviceRegistry.registerAppContext();

	// configure client session service
	sessionService.configure(serviceRegistry, txnProxy);
	serviceRegistry.setComponent(
	    ClientSessionService.class, sessionService);
	txnProxy.setComponent(
	    ClientSessionService.class, sessionService);
	port = sessionService.getListenPort();
	
	// configure channel service
	channelService.configure(serviceRegistry, txnProxy);
	serviceRegistry.setComponent(ChannelManager.class, channelService);
	
	txn.commit();
	createTransaction();
    }

    /** Sets passed if the test passes. */
    protected void runTest() throws Throwable {
	super.runTest();
	passed = true;
    }
    
    /** Cleans up the transaction. */
    protected void tearDown() throws Exception {
        tearDown(true);
    }

    protected void tearDown(boolean clean) throws Exception {
        if (txn != null) {
            try {
                txn.abort();
            } catch (IllegalStateException e) {
            }
            txn = null;
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

    public void testConstructorNullProperties() {
	try {
	    new ChannelServiceImpl(null, new DummyComponentRegistry());
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
    }

    public void testConstructorNullComponentRegistry() {
	try {
	    new ChannelServiceImpl(serviceProps, null);
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
    }

    public void testConstructorNoAppName() throws Exception {
	try {
	    new ChannelServiceImpl(new Properties(), new DummyComponentRegistry());
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
    }

    /* -- Test configure -- */

    public void testConfigureNullRegistry() {
	ChannelServiceImpl service =
	    new ChannelServiceImpl(serviceProps, systemRegistry);
	try {
            service.configure(null, new DummyTransactionProxy());
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
    }
    
    public void testConfigureNullTransactionProxy() {
	ChannelServiceImpl service =
	    new ChannelServiceImpl(serviceProps, systemRegistry);
	try {
            service.configure(new DummyComponentRegistry(), null);
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
    }

    public void testConfigureTwice() {
	try {
	    channelService.configure(new DummyComponentRegistry(), txnProxy);
	    fail("Expected IllegalStateException");
	} catch (IllegalStateException e) {
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
	txn.commit();
	try {
	    createChannel();
	    fail("Expected TransactionNotActiveException");
	} catch (TransactionNotActiveException e) {
	    System.err.println(e);
	}
    }

    public void testCreateChannelAndAbort() {
	createChannel("foo");
	txn.abort();
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
	txn.commit();
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
	txn.commit();
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
	txn.commit();
	try {
	    channel.getName();
	    fail("Expected TransactionNotActiveException");
	} catch (TransactionNotActiveException e) {
	    System.err.println(e);
	}
    }

    public void testChannelGetNameMismatchedTxn() throws Exception {
	Channel channel = createChannel();
	txn.commit();
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
	txn.commit();
	try {
	    channel.getDeliveryRequirement();
	    fail("Expected TransactionNotActiveException");
	} catch (TransactionNotActiveException e) {
	    System.err.println(e);
	}
    }

    public void testChannelGetDeliveryMismatchedTxn() throws Exception {
	Channel channel = createChannel();
	txn.commit();
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
	txn.commit();
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
	txn.commit();
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
	    txn.commit();
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
	txn.commit();
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
	    txn.commit();
	}
    }

    /* -- Test Channel.leave -- */

    public void testChannelLeaveNoTxn() throws Exception {
	Channel channel = createChannel();
	txn.commit();
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
	txn.commit();
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
	    txn.commit();
	}
    }

    /* -- Test Channel.leaveAll -- */

    public void testChannelLeaveAllNoTxn() throws Exception {
	Channel channel = createChannel();
	txn.commit();
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
	txn.commit();
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
	    txn.commit();
	}
    }

    /* -- Test Channel.hasSessions -- */

    public void testChannelHasSessionsNoTxn() throws Exception {
	Channel channel = createChannel();
	txn.commit();
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
	txn.commit();
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
	txn.commit();
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
	txn.commit();
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
	txn.commit();
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
	txn.commit();
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
	txn.commit();
	createTransaction();
	channel = channelService.getChannel(name);
	channel.close();
	try {
	    channelService.getChannel(name);
	    fail("Expected NameNotBoundException");
	} catch (NameNotBoundException e) {
	    System.err.println(e);
	}
	txn.commit();
    }

    public void testChannelCloseTwice() throws Exception {
	String name = "closeTest";
	Channel channel = createChannel(name);
	txn.commit();
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
	txn.commit();
    }

    public void testChannelJoinReceivedByClient() throws Exception {
	txn.commit();
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
	txn.commit();
	registerAppListener();
	return new ClientGroup(users);
    }

    public void testChannelLeaveReceivedByClient() throws Exception {
	txn.commit();
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
	txn.commit();
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
	txn.commit();
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
	txn.commit();
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
    
    public void testSendFromSessionToChannel() throws Exception {
	txn.commit();
	String name = CHANNEL_NAME;
	ClientGroup group =
	    createChannelAndClientGroup(name, "moe", "larry");
	try {
	    group.join(name);
	    DummyClient moe = group.getClient("moe");
	    DummyClient larry = group.getClient("larry");
	    int numMessages = 3;
	    Set<byte[]> recipientIds = new HashSet<byte[]>();
	    recipientIds.add(larry.getSessionId());
	    for (int i = 0; i < numMessages; i++) {
		MessageBuffer buf =
		    new MessageBuffer(MessageBuffer.getSize("moe") + 4);
		buf.putString("moe").
		    putInt(i);
		moe.sendChannelMessage(name, recipientIds, buf.getBuffer());
	    }

	    for (int nextNum = 0; nextNum < numMessages; nextNum++) {
		MessageInfo info = larry.nextChannelMessage();
		if (info == null) {
		    fail("got "  + nextNum +
			 "channel messages, expected " + numMessages);
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

	    
	} catch (RuntimeException e) {
	    System.err.println("unexpected failure");
	    e.printStackTrace();
	    fail("unexpected failure: " + e);
	} finally {
	    group.disconnect(false);
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

	ClientGroup(String[] users) {
	    this.users = users;
	    for (String user : users) {
		DummyClient client = new DummyClient();
		clients.put(user, client);
		client.connect(port);
		client.login(user, "password");
	    }
	}

	void join(String name) {
	    for (DummyClient client : clients.values()) {
		client.join(name);
	    }
	}

	void leave(String name) {
	    for (DummyClient client : clients.values()) {
		client.leave(name);
	    }
	}

	void checkMembership(String name, boolean isMember) throws Exception {
	    createTransaction();
	    Channel channel = channelService.getChannel(name);
	    Set<ClientSession> sessions = channel.getSessions();
	    for (DummyClient client : clients.values()) {

		ClientSession session =
		    sessionService.getClientSession(client.sessionId);

		if (sessions.contains(session)) {
		    if (!isMember) {
			fail("ClientGroup.checkMembership session: " +
			     session.getName() + " is a member of " + name);
		    }
		} else if (isMember) {
			fail("ClientGroup.checkMembership session: " +
			     session.getName() + " is not a member of " + name);
		}
	    }

	    txn.commit();
	}

	void checkChannelSets(boolean exists) throws Exception {
	    createTransaction();
	    for (DummyClient client : clients.values()) {
		String sessionKey = getSessionKey(client.sessionId);
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
	    txn.commit();
	}

	DummyClient getClient(String name) {
	    return clients.get(name);
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
	return new DataServiceImpl(dbProps, registry);
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

	private final Channel channel;

	DummyChannelListener() {
	    this(null);
	}

	DummyChannelListener(Channel channel) {
	    this.channel = channel;
	}
	
        /** {@inheritDoc} */
	public void receivedMessage(
	    Channel channel, ClientSession session, byte[] message)
	{
	}
    }

    private static class DummyClientSession
	implements SgsClientSession, Serializable
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

	/* -- Implement SgsClientSession -- */

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
	txn.commit();
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
	byte[] sessionId;
	private String password;
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
	private String channelName = null;
	private String reason;
	private byte[] reconnectionKey;
	private final List<MessageInfo> channelMessages =
	    new ArrayList<MessageInfo>();
	private final AtomicLong sequenceNumber = new AtomicLong(0);

	
	DummyClient() {
	}

	byte[] getSessionId() {
	    return sessionId;
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
	    if (!graceful) {
		synchronized (lock) {
		    if (connected == false) {
			return;
		    }
		    connected = false;
		    try {
			connection.close();
		    } catch (IOException e) {
			System.err.println(
			    "DummyClient.disconnect exception:" + e);
		    }
		    lock.notifyAll();
		}
	    } else {
		synchronized (lock) {
		    if (connected == false) {
			return;
		    }
		    MessageBuffer buf = new MessageBuffer(3);
		    buf.putByte(SimpleSgsProtocol.VERSION).
			putByte(SimpleSgsProtocol.APPLICATION_SERVICE).
			putByte(SimpleSgsProtocol.LOGOUT_REQUEST);
		    logoutAck = false;
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
	}

	void login(String name, String password) {
	    synchronized (lock) {
		if (connected == false) {
		    throw new RuntimeException(
			"DummyClient.login not connected");
		}
	    }
	    this.name = name;
	    this.password = password;

	    MessageBuffer buf =
		new MessageBuffer(3 + MessageBuffer.getSize(name) +
				  MessageBuffer.getSize(password));
	    buf.putByte(SimpleSgsProtocol.VERSION).
		putByte(SimpleSgsProtocol.APPLICATION_SERVICE).
		putByte(SimpleSgsProtocol.LOGIN_REQUEST).
		putString(name).
		putString(password);
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
	 * Returns the size that the set of ids will use when put in a
	 * message buffer.  The returned value accounts for the
	 * two-byte header for the number of ids in the set.
	 */
	private static int getSize(Set<byte[]> ids) {
	    int size = 2;
	    for (byte[] id : ids) {
		size += 2 + id.length;
	    }
	    return size;
	}

	/**
	 * Sends a CHANNEL_SEND_REQUEST.
	 */
	void sendChannelMessage(String name, Set<byte[]> recipientIds,
				byte[] message) {
	    checkLoggedIn();

	    MessageBuffer buf =
		new MessageBuffer(3 + MessageBuffer.getSize(name) + 8 +
				  getSize(recipientIds) +
				  2 + message.length);
	    buf.putByte(SimpleSgsProtocol.VERSION).
		putByte(SimpleSgsProtocol.CHANNEL_SERVICE).
		putByte(SimpleSgsProtocol.CHANNEL_SEND_REQUEST).
		putString(name).
		putLong(nextSequenceNumber()).
		putShort(recipientIds.size());
	    for (byte[] sessionId : recipientIds) {
		buf.putByteArray(sessionId);
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

	void join(String name) {
	    String action = "join";
	    MessageBuffer buf =
		new MessageBuffer(MessageBuffer.getSize(action) +
				  MessageBuffer.getSize(name));
	    buf.putString(action).putString(name);
	    sendMessage(buf.getBuffer());
	    joinAck = false;
	    channelName = null;
	    synchronized (lock) {
		try {
		    if (joinAck == false) {
			lock.wait(WAIT_TIME);
		    }
		    if (joinAck != true) {
			throw new RuntimeException(
			    "DummyClient.join timed out: " + name);
		    }

		    if (channelName == null ||
			!name.equals(channelName)) {
			fail("DummyClient.leave expected channel " + name +
			     ", got " + channelName);
		    }
		    
		} catch (InterruptedException e) {
		    throw new RuntimeException(
			    "DummyClient.join timed out: " + name, e);
		}
	    }
	}
	    
	void leave(String name) {
	    String action = "leave";
	    MessageBuffer buf =
		new MessageBuffer(MessageBuffer.getSize(action) +
				  MessageBuffer.getSize(name));
	    buf.putString(action).putString(name);
	    sendMessage(buf.getBuffer());
	    leaveAck = false;
	    channelName = null;
	    synchronized (lock) {
		try {
		    if (leaveAck == false) {
			lock.wait(WAIT_TIME);
		    }
		    if (leaveAck != true) {
			throw new RuntimeException(
			    "DummyClient.leave timed out: " + name);
		    }

		    if (channelName == null ||
			!name.equals(channelName)) {
			fail("DummyClient.leave expected channel " + name +
			     ", got " + channelName);
		    }
		    
		} catch (InterruptedException e) {
		    throw new RuntimeException(
			    "DummyClient.leave timed out: " + name, e);
		}
	    }
	}
	
	void logout() {
	    synchronized (lock) {
		if (connected == false) {
		    throw new RuntimeException(
			"DummyClient.login not connected");
		}
	    }

	    MessageBuffer buf = new MessageBuffer(3);
	    buf.putByte(SimpleSgsProtocol.VERSION).
		putByte(SimpleSgsProtocol.APPLICATION_SERVICE).
		putByte(SimpleSgsProtocol.LOGOUT_REQUEST);
	    logoutAck = false;

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
			    "DummyClient.logout timed out");
		    }
		} catch (InterruptedException e) {
		    throw new RuntimeException(
			"DummyClient.logout timed out", e);
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
		    sessionId = buf.getBytes(buf.getUnsignedShort());
		    reconnectionKey = buf.getBytes(buf.getUnsignedShort());
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
			lock.notifyAll();
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
		    String name = buf.getString();
		    synchronized (lock) {
			joinAck = true;
			channelName = name;
			System.err.println("join succeeded: " + name);
			lock.notifyAll();
		    }
		    break;
		}
		    
		case SimpleSgsProtocol.CHANNEL_LEAVE: {
		    String name = buf.getString();
		    synchronized (lock) {
			leaveAck = true;
			channelName = name;
			System.err.println("leave succeeded: " + name);
			lock.notifyAll();
		    }
		    break;
		}

		case SimpleSgsProtocol.CHANNEL_MESSAGE: {
		    String name = buf.getString();
		    long seq = buf.getLong();
		    byte[] senderId = buf.getByteArray();
		    byte[] message = buf.getByteArray();
		    synchronized (lock) {
			channelMessages.add(
			    new MessageInfo(name, senderId, message, seq));
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
	final String name;
	final byte[] senderId;
	final byte[] message;
	final long seq;

	MessageInfo(String name, byte[] senderId, byte[] message, long seq) {
	    this.name = name;
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
	    ManagedReference listenerRef =
		txnProxy.getService(DataService.class).
		createReference(listener);
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
	boolean graceful = false;
	
	private final ClientSession session;
	
	DummyClientSessionListener(ClientSession session) {
	    this.session = session;
	    this.name = session.getName();
	}

        /** {@inheritDoc} */
	public void disconnected(boolean graceful) {
	    System.err.println("DummyClientSessionListener[" + name +
			       "] disconnected invoked with " + graceful);
	    synchronized (disconnectedCallbackLock) {
		receivedDisconnectedCallback = true;
		this.graceful = graceful;
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
