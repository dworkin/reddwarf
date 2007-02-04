package com.sun.sgs.test.impl.service.channel;

import com.sun.sgs.app.Channel;
import com.sun.sgs.app.ChannelListener;
import com.sun.sgs.app.ChannelManager;
import com.sun.sgs.app.ClientSession;
import com.sun.sgs.app.DataManager;
import com.sun.sgs.app.Delivery;
import com.sun.sgs.app.NameExistsException;
import com.sun.sgs.app.NameNotBoundException;
import com.sun.sgs.app.TaskManager;
import com.sun.sgs.app.TransactionNotActiveException;
import com.sun.sgs.impl.kernel.DummyAbstractKernelAppContext;
import com.sun.sgs.impl.kernel.MinimalTestKernel;
import com.sun.sgs.impl.kernel.StandardProperties;
import com.sun.sgs.impl.service.channel.ChannelServiceImpl;
import com.sun.sgs.impl.service.data.DataServiceImpl;
import com.sun.sgs.impl.service.data.store.DataStoreImpl;
import com.sun.sgs.impl.service.task.TaskServiceImpl;
import com.sun.sgs.kernel.ComponentRegistry;
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
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import junit.framework.TestCase;

public class TestChannelServiceImpl extends TestCase {
    
    /** The name of the DataServiceImpl class. */
    private static final String DataStoreImplClassName =
	DataStoreImpl.class.getName();

    /** The name of the DataServiceImpl class. */
    private static final String DataServiceImplClassName =
	DataServiceImpl.class.getName();

    /** Directory used for database shared across multiple tests. */
    private static String dbDirectory =
	System.getProperty("java.io.tmpdir") + File.separator +
	"TestChannelServiceImpl.db";

    /** Properties for the channel service. */
    private static Properties serviceProps = createProperties(
	StandardProperties.APP_NAME, "TestChannelServiceImpl");

    /** Properties for creating the shared database. */
    private static Properties dbProps = createProperties(
	DataStoreImplClassName + ".directory",
	dbDirectory,
	StandardProperties.APP_NAME, "TestChannelServiceImpl",
	DataServiceImplClassName + ".debugCheckInterval", "1");
    
    /**
     * Delete the database directory at the start of the test run, but not for
     * each test.
     */
    static {
	System.err.println("Deleting database directory");
	deleteDirectory(dbDirectory);
    }

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
    private DummySessionService sessionService;
    private TaskServiceImpl taskService;
    private DummyTaskScheduler taskScheduler;
    
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
	appContext = MinimalTestKernel.createContext();
	systemRegistry = MinimalTestKernel.getSystemRegistry(appContext);
	serviceRegistry = MinimalTestKernel.getServiceRegistry(appContext);
	    
	// create services
	dataService = createDataService(systemRegistry);
	taskService = new TaskServiceImpl(new Properties(), systemRegistry);
	sessionService = new DummySessionService();
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
	if (txn != null) {
	    try {
		txn.abort();
	    } catch (IllegalStateException e) {
	    }
	    txn = null;
	}
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
	    channelService.createChannel(
		"foo", new DummyChannelListener(), Delivery.RELIABLE);
	    fail("Expected TransactionNotActiveException");
	} catch (TransactionNotActiveException e) {
	    System.err.println(e);
	}
    }

    public void testCreateChannelAndAbort() {
	channelService.createChannel(
	    "foo", new DummyChannelListener(), Delivery.RELIABLE);
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
	channelService.createChannel(
	    "exist", new DummyChannelListener(), Delivery.RELIABLE);
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
	Channel channel1 = channelService.createChannel(
	    "foo", new DummyChannelListener(), Delivery.RELIABLE);
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
	Channel channel1 = channelService.createChannel(
	     "testy", new DummyChannelListener(), Delivery.RELIABLE);
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
	Channel savedChannel = channelService.getChannel("testy");
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
	Channel channel = channelService.getChannel("testy");
	txn.commit();
	try {
	    channel.getName();
	    fail("Expected TransactionNotActiveException");
	} catch (TransactionNotActiveException e) {
	    System.err.println(e);
	}
    }

    public void testChannelGetNameMismatchedTxn() throws Exception {
	Channel channel = channelService.getChannel("testy");
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
	Channel channel = channelService.createChannel(
	    name, new DummyChannelListener(), Delivery.RELIABLE);
	if (!name.equals(channel.getName())) {
	    fail("Expected: " + name + ", got: " + channel.getName());
	}
	System.err.println("Channel names are equal");
    }

    public void testChannelGetNameClosedChannel() {
	String name = "foo";
	Channel channel = channelService.createChannel(
	    name, new DummyChannelListener(), Delivery.RELIABLE);
	channel.close();
	if (!name.equals(channel.getName())) {
	    fail("Expected: " + name + ", got: " + channel.getName());
	}
	System.err.println("Got channel name on closed channel");
    }

    /* -- Test Channel.getDeliveryRequirement -- */

    public void testChannelGetDeliveryNoTxn() throws Exception {
	Channel channel = channelService.getChannel("testy");
	txn.commit();
	try {
	    channel.getDeliveryRequirement();
	    fail("Expected TransactionNotActiveException");
	} catch (TransactionNotActiveException e) {
	    System.err.println(e);
	}
    }

    public void testChannelGetDeliveryMismatchedTxn() throws Exception {
	Channel channel = channelService.getChannel("testy");
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
	Channel channel = channelService.getChannel("testy");
	txn.commit();
	try {
	    channel.join(new DummyClientSession("dummy"), null);
	    fail("Expected TransactionNotActiveException");
	} catch (TransactionNotActiveException e) {
	    System.err.println(e);
	}
    }

    public void testChannelJoinClosedChannel() {
	Channel channel = channelService.getChannel("testy");
	channel.close();
	try {
	    channel.join(new DummyClientSession("dummy"), null);
	    fail("Expected IllegalStateException");
	} catch (IllegalStateException e) {
	    System.err.println(e);
	}
    }

    public void testChannelJoinNullClientSession() {
	Channel channel = channelService.getChannel("testy");
	try {
	    channel.join(null, new DummyChannelListener());
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
    }

    public void testChannelJoinNonSerializableListener() {
	Channel channel = channelService.getChannel("testy");
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
	Channel channel = channelService.createChannel(
		channelName, new DummyChannelListener(), Delivery.RELIABLE);
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
	Channel channel = channelService.createChannel(
		channelName, new DummyChannelListener(), Delivery.RELIABLE);
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
	Channel channel = channelService.getChannel("testy");
	txn.commit();
	try {
	    channel.leave(new DummyClientSession("dummy"));
	    fail("Expected TransactionNotActiveException");
	} catch (TransactionNotActiveException e) {
	    System.err.println(e);
	}
    }

    public void testChannelLeaveClosedChannel() {
	Channel channel = channelService.getChannel("testy");
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
	Channel channel = channelService.getChannel("testy");
	try {
	    channel.leave(null);
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
    }

    public void testChannelLeaveSessionNotJoined() {
	Channel channel = channelService.getChannel("testy");
	channel.leave(new DummyClientSession("dummy"));
	System.err.println("Leave of non-joined session successful");
    }
    
    public void testChannelLeave() throws Exception {
	String channelName = "leaveTest";
	Channel channel = channelService.createChannel(
		channelName, new DummyChannelListener(), Delivery.RELIABLE);
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
	Channel channel = channelService.getChannel("testy");
	txn.commit();
	try {
	    channel.leaveAll();
	    fail("Expected TransactionNotActiveException");
	} catch (TransactionNotActiveException e) {
	    System.err.println(e);
	}
    }

    public void testChannelLeaveAllClosedChannel() {
	Channel channel = channelService.getChannel("testy");
	channel.close();
	try {
	    channel.leaveAll();
	    fail("Expected IllegalStateException");
	} catch (IllegalStateException e) {
	    System.err.println(e);
	}
    }

    public void testChannelLeaveAllNoSessionsJoined() {
	Channel channel = channelService.getChannel("testy");
	channel.leaveAll();
	System.err.println("LeaveAll with no sessions joined is successful");
    }
    
    public void testChannelLeaveAll() throws Exception {
	String channelName = "leaveAllTest";
	Channel channel = channelService.createChannel(
		channelName, new DummyChannelListener(), Delivery.RELIABLE);
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
	Channel channel = channelService.getChannel("testy");
	txn.commit();
	try {
	    channel.hasSessions();
	    fail("Expected TransactionNotActiveException");
	} catch (TransactionNotActiveException e) {
	    System.err.println(e);
	}
    }

    public void testChannelHasSessionsClosedChannel() {
	Channel channel = channelService.getChannel("testy");
	channel.close();
	try {
	    channel.hasSessions();
	    fail("Expected IllegalStateException");
	} catch (IllegalStateException e) {
	    System.err.println(e);
	}
    }

    public void testChannelHasSessionsNoSessionsJoined() {
	Channel channel = channelService.getChannel("testy");
	if (channel.hasSessions()) {
	    fail("Expected hasSessions to return false");
	}
	System.err.println("hasSessions returned false");
    }
    
    public void testChannelHasSessionsSessionsJoined() {
	Channel channel = channelService.getChannel("testy");
	channel.join(new DummyClientSession("dummy"), null);
	if (!channel.hasSessions()) {
	    fail("Expected hasSessions to return true");
	}
	System.err.println("hasSessions returned true");
    }

    /* -- Test Channel.getSessions -- */

    public void testChannelGetSessionsNoTxn() throws Exception {
	Channel channel = channelService.getChannel("testy");
	txn.commit();
	try {
	    channel.getSessions();
	    fail("Expected TransactionNotActiveException");
	} catch (TransactionNotActiveException e) {
	    System.err.println(e);
	}
    }

    public void testChannelGetSessionsClosedChannel() {
	Channel channel = channelService.getChannel("testy");
	channel.close();
	try {
	    channel.getSessions();
	    fail("Expected IllegalStateException");
	} catch (IllegalStateException e) {
	    System.err.println(e);
	}
    }

    public void testChannelGetSessionsNoSessionsJoined() {
	Channel channel = channelService.getChannel("testy");
	if (!channel.getSessions().isEmpty()) {
	    fail("Expected no sessions");
	}
	System.err.println("No sessions joined");
    }
    
    public void testChannelGetSessionsSessionsJoined() {
	Channel channel = channelService.getChannel("testy");
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
	Channel channel = channelService.getChannel("testy");
	txn.commit();
	try {
	    channel.send(testMessage);
	    fail("Expected TransactionNotActiveException");
	} catch (TransactionNotActiveException e) {
	    System.err.println(e);
	}
    }

    public void testChannelSendAllClosedChannel() {
	Channel channel = channelService.getChannel("testy");
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
	Channel channel = channelService.getChannel("testy");
	txn.commit();
	try {
	    channel.send(new DummyClientSession("dummy"), testMessage);
	    fail("Expected TransactionNotActiveException");
	} catch (TransactionNotActiveException e) {
	    System.err.println(e);
	}
    }

    public void testChannelSendToOneClosedChannel() {
	Channel channel = channelService.getChannel("testy");
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
	Channel channel = channelService.getChannel("testy");
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
	Channel channel = channelService.getChannel("testy");
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
	Channel channel = channelService.getChannel("testy");
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
	Channel channel = channelService.createChannel(
	    name, new DummyChannelListener(), Delivery.RELIABLE);
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
	Channel channel = channelService.createChannel(
	    name, new DummyChannelListener(), Delivery.RELIABLE);
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
    
    /* -- other methods -- */

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
    {
	File dir = new File(dbDirectory);
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
	extends NonSerializableChannelListener
	implements Serializable
    {
	private final static long serialVersionUID = 1L;

	private final Channel channel;

	DummyChannelListener() {
	    this(null);
	}

	DummyChannelListener(Channel channel) {
	    this.channel = channel;
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
	public byte[] getSessionId() {
	    return id;
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

    private static class DummySessionService implements ClientSessionService {


	private final Map<Byte, ProtocolMessageListener> serviceListeners =
	    new HashMap<Byte, ProtocolMessageListener>();

	/** A map of current sessions, from session ID to ClientSessionImpl. */
	private final Map<byte[], SgsClientSession> sessions =
	    new HashMap<byte[], SgsClientSession>();

        /** {@inheritDoc} */
	public String getName() {
	    return toString();
	}
	
        /** {@inheritDoc} */
	public void configure(ComponentRegistry registry, TransactionProxy proxy) {
	}
	
        /** {@inheritDoc} */
	public void registerProtocolMessageListener(
	    byte serviceId, ProtocolMessageListener listener)
	{
	    serviceListeners.put(serviceId, listener);
	}

        /** {@inheritDoc} */
	public SgsClientSession getClientSession(byte[] sessionId) {
	    return sessions.get(sessionId);
	}

        /** {@inheritDoc} */
	public boolean shutdown() {
	    return false;
	}
    }
}
