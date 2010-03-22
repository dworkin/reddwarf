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

package com.sun.sgs.test.impl.service.channel;

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.AppListener;
import com.sun.sgs.app.Channel;
import com.sun.sgs.app.ChannelListener;
import com.sun.sgs.app.ChannelManager;
import com.sun.sgs.app.ClientSession;
import com.sun.sgs.app.ClientSessionListener;
import com.sun.sgs.app.DataManager;
import com.sun.sgs.app.Delivery;
import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.ManagedReference;
import com.sun.sgs.app.ObjectNotFoundException;
import com.sun.sgs.app.util.ManagedSerializable;
import com.sun.sgs.auth.Identity;
import com.sun.sgs.impl.kernel.StandardProperties;
import com.sun.sgs.impl.service.channel.ChannelServer;
import com.sun.sgs.impl.service.channel.ChannelServiceImpl;
import com.sun.sgs.impl.service.session.ClientSessionWrapper;
import com.sun.sgs.impl.sharedutil.HexDumper;
import com.sun.sgs.impl.sharedutil.MessageBuffer;
import com.sun.sgs.impl.util.BoundNamesUtil;
import com.sun.sgs.impl.util.KernelCallable;
import com.sun.sgs.kernel.TransactionScheduler;
import com.sun.sgs.protocol.simple.SimpleSgsProtocol;
import com.sun.sgs.service.DataService;
import com.sun.sgs.test.util.AbstractDummyClient;
import com.sun.sgs.test.util.ConfigurableNodePolicy;
import com.sun.sgs.test.util.IdentityAssigner;
import com.sun.sgs.test.util.SgsTestNode;
import com.sun.sgs.test.util.TestAbstractKernelRunnable;
import com.sun.sgs.tools.test.FilteredNameRunner;
import java.io.Serializable;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.lang.reflect.Field;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.runner.RunWith;

import static com.sun.sgs.test.util.UtilProperties.createProperties;

@RunWith(FilteredNameRunner.class)
public abstract class AbstractChannelServiceTest extends Assert {
    
    private static final String APP_NAME = "TestChannelServiceImpl";
    
    protected static final byte PROTOCOL_v4 = 0x04;

    protected static final int WAIT_TIME = 2000;
    
    /** A list of users for test purposes. */
    protected static final String MOE = "moe";
    protected static final String LARRY = "larry";
    protected static final String CURLY = "curly";
    protected static final String[] someUsers = new String[] { MOE, LARRY, CURLY };

    /** A longer list of users for test purposes. */
    protected static final String[] sevenDwarfs =
	new String[] {"bashful", "doc", "dopey", "grumpy",
		      "happy", "sleepy", "sneezy"};

    /** No users. */
    protected static final String[] noUsers = new String[0];

    /** The Channel service properties. */
    protected static final Properties serviceProps =
	createProperties(StandardProperties.APP_NAME, APP_NAME);

    private static Map<Long, MethodInfo> holdMethodMap =
	new HashMap<Long, MethodInfo>();
    
    /** The number for creating host names. */
    private int hostNum = 1;
    
    /** The node that creates the servers. */
    protected SgsTestNode serverNode;

    /** Any additional nodes created by a test. */
    private Set<SgsTestNode> additionalNodes = new HashSet<SgsTestNode>();

    /** Version information from ChannelServiceImpl class. */
    protected final String VERSION_KEY;
    protected final int MAJOR_VERSION;
    protected int MINOR_VERSION;

    /** The transaction scheduler. */
    protected TransactionScheduler txnScheduler;

    /** The owner for tasks I initiate. */
    protected Identity taskOwner;

    /** The shared data service. */
    protected DataService dataService;

    /** The channel service on the server node. */
    protected ChannelManager channelService;

    /** The identity assigner, for moving identities. */
    protected IdentityAssigner identityAssigner;

    /** The listen port for the client session service. */
    protected int port;

    /** The SimpleSgsProtocol version for clients. */
    private byte protocolVersion;

    protected boolean isPerformanceTest = false;

    private static Field getField(Class cl, String name) throws Exception {
	Field field = cl.getDeclaredField(name);
	field.setAccessible(true);
	return field;
    }
    
    /** Constructs a test instance. */
    public AbstractChannelServiceTest() throws Exception  {
	Class cl = ChannelServiceImpl.class;
	VERSION_KEY = (String) getField(cl, "VERSION_KEY").get(null);
	MAJOR_VERSION = getField(cl, "MAJOR_VERSION").getInt(null);
	MINOR_VERSION = getField(cl, "MINOR_VERSION").getInt(null);
    }

    /** Creates and configures the channel service. */
    @Before
    public void setUp() throws Exception {
        setUp(true);
	holdMethodMap.clear();
    }

    protected void setUp(boolean clean) throws Exception {
        Properties props = 
            SgsTestNode.getDefaultProperties(APP_NAME, null, 
                                             DummyAppListener.class);
	this.protocolVersion = SimpleSgsProtocol.VERSION;
        props.setProperty(StandardProperties.AUTHENTICATORS, 
                      "com.sun.sgs.test.util.SimpleTestIdentityAuthenticator");
	props.setProperty("com.sun.sgs.impl.service.nodemap.policy.class",
			  ConfigurableNodePolicy.class.getName());
	props.setProperty(
 	   "com.sun.sgs.impl.protocol.simple.protocol.version",
	   Byte.toString(protocolVersion));
	props.setProperty(
	    StandardProperties.SESSION_RELOCATION_TIMEOUT_PROPERTY,
	    "5000");
			  
	serverNode = 
                new SgsTestNode(APP_NAME, DummyAppListener.class, props, clean);
	port = serverNode.getAppPort();

        txnScheduler = 
            serverNode.getSystemRegistry().
            getComponent(TransactionScheduler.class);
        taskOwner = serverNode.getProxy().getCurrentOwner();

	identityAssigner = new IdentityAssigner(serverNode);
        dataService = serverNode.getDataService();
	channelService = serverNode.getChannelService();
	wrapChannelServerProxy(serverNode);
    }

    /** Cleans up the transaction. */
    @After
    public void tearDown() throws Exception {
        tearDown(true);
    }

    protected void tearDown(boolean clean) throws Exception {
	// This sleep cuts down on the exceptions output due to shutdown.
	Thread.sleep(500);
	for (SgsTestNode node : additionalNodes) {
	    node.shutdown(false);
	}
	additionalNodes.clear();
        serverNode.shutdown(clean);
        serverNode = null;
    }
    
    /**
     * Creates a new test node, and returns it.
     */
    protected SgsTestNode addNode() throws Exception {
	String host = "node" + hostNum++;
	Properties props = SgsTestNode.getDefaultProperties(
	    APP_NAME, serverNode, DummyAppListener.class);
	props.setProperty(StandardProperties.AUTHENTICATORS, 
	    "com.sun.sgs.test.util.SimpleTestIdentityAuthenticator");
	props.put("com.sun.sgs.impl.service.watchdog.client.host", host);
	props.setProperty(
 	   "com.sun.sgs.impl.protocol.simple.protocol.version",
	   Byte.toString(protocolVersion));
	props.setProperty(
	    StandardProperties.SESSION_RELOCATION_TIMEOUT_PROPERTY,
	    "5000");
	SgsTestNode node = 
	    new SgsTestNode(serverNode, DummyAppListener.class, props);
	wrapChannelServerProxy(node);
	additionalNodes.add(node);
	return node;
    }

    /**
     * Creates the specified number of test nodes, and returns a set
     * containing the constructed nodes.
     */
    protected Set<SgsTestNode> addNodes(int numNodes) throws Exception {
	if (additionalNodes == null) {
	}

	Set<SgsTestNode> nodes = new HashSet<SgsTestNode>();
        for (int i = 0; i < numNodes; i++) {
	    nodes.add(addNode());
        }
	return nodes;
    }

    protected class ClientGroup {

	Map<String, DummyClient> clients;

	ClientGroup(String... users) {
	    this(port, users);
	}

	ClientGroup(int connectPort, String... users) {
	    clients = new HashMap<String, DummyClient>();
	    for (String user : users) {
		DummyClient client = new DummyClient(user);
		clients.put(user, client);
		client.connect(connectPort);
		client.login();
	    }
	}

	private ClientGroup(Map<String, DummyClient> clients) {
	    this.clients = clients;
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

	// Removes the client sessions on the given host from this group
	// and returns a ClientGroup with the removed sessions.
	ClientGroup removeSessionsFromGroup(int port) {
	    Iterator<String> iter = clients.keySet().iterator();
	    Map<String, DummyClient> removedClients =
		new HashMap<String, DummyClient>();
	    while (iter.hasNext()) {
		String user = iter.next();
		DummyClient client = clients.get(user);
                // Note that the redirectPort can sometimes be zero,
                // as it won't be assigned if the initial login request
                // was successful.
		int redirectPort = client.getRedirectPort();
		if ((redirectPort != 0 && port == redirectPort) ||
		    (redirectPort == 0 && port == client.getConnectPort()))
		{
		    iter.remove();
		    removedClients.put(user, client);
		    client.disconnect();
		}
	    }
	    return new ClientGroup(removedClients);
	}

	boolean isDisconnectedGroup() {
	    boolean allSessionsDisconnected = true;
	    for (DummyClient client : clients.values()) {
		if (client.isConnected()) {
		    System.err.println(client.name + " is still connected!");
		    allSessionsDisconnected = false;
		}
	    }
	    return allSessionsDisconnected;
	}

	void checkMembership(final String name, final boolean isMember)
	    throws Exception
	{
	    txnScheduler.runTask(new TestAbstractKernelRunnable() {
		public void run() {
		    Channel channel = getChannel(name);
		    Set<ClientSession> sessions = getSessions(channel);
		    for (DummyClient client : clients.values()) {

			ClientSession session = getClientSession(client.name);

			if (session != null && sessions.contains(session)) {
			    if (!isMember) {
				fail("ClientGroup.checkMembership session: " +
				     session.getName() + " is a member of " +
				     name);
			    }
			} else if (isMember) {
			    String sessionName =
				(session == null) ? "null" : session.getName();
			    fail("ClientGroup.checkMembership session: " +
				 sessionName + " is not a member of " + name);
			}
		    }
		}
	    }, taskOwner);
	}

	DummyClient getClient(String name) {
	    return clients.get(name);
	}

	Collection<DummyClient> getClients() {
	    return clients.values();
	}

	void disconnect(boolean graceful) {
	    for (DummyClient client : clients.values()) {
		if (graceful) {
		    client.logout();
		} else {
		    client.disconnect();
		}
	    }
	}
    }

    // -- other methods --

    protected void printServiceBindings(final String message) throws Exception {
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
	    public void run() {
		System.err.println("Service bindings <<" + message +
				   ">>----------");
		Iterator<String> iter =
		    BoundNamesUtil.getServiceBoundNamesIterator(
			dataService, "com.sun.sgs.impl.service.channel.");
		while (iter.hasNext()) {
		    System.err.println(iter.next());
		}
		System.err.println("--------------------------");
	    }
	}, taskOwner);
    }

    // Returns a newly created channel
    protected Channel createChannel() throws Exception {
	return createChannel("test");
    }

    protected Channel createChannel(String name) throws Exception {
	return createChannel(name, null, null);
    }

    protected Channel createChannel(String name, ChannelListener listener)
	throws Exception
    {
	return createChannel(name, listener, null);

    }
    
    protected Channel createChannel(
	String name, ChannelListener listener, SgsTestNode node)
	throws Exception
    {
	CreateChannelTask createChannelTask =
	    new CreateChannelTask(name, listener);
	runTransactionalTask(createChannelTask, node);
	return createChannelTask.getChannel();
    }

    private void runTransactionalTask(
	TestAbstractKernelRunnable task, SgsTestNode node)
	throws Exception
    {
	if (node == null) {
	    node = serverNode;
	}
	TransactionScheduler nodeTxnScheduler =
	    node.getSystemRegistry().getComponent(TransactionScheduler.class);
	Identity nodeTaskOwner =
	    node.getProxy().getCurrentOwner();
	nodeTxnScheduler.runTask(task, nodeTaskOwner);
    }

    private static class CreateChannelTask extends TestAbstractKernelRunnable {
	private final String name;
	private final ChannelListener listener;
	private Channel channel;
	
	CreateChannelTask(String name, ChannelListener listener) {
	    this.name = name;
	    this.listener = listener;
	}
	
	public void run() throws Exception {
	    channel = AppContext.getChannelManager().
		createChannel(name, listener, Delivery.RELIABLE);
	    AppContext.getDataManager().setBinding(name, channel);
	}

	Channel getChannel() {
	    return channel;
	}
    }

    protected ClientSession getSession(String name) {
	try {
	    return (ClientSession) dataService.getBinding(name);
	} catch (ObjectNotFoundException e) {
	    return null;
	}
    }

    // FIXME: use the ChannelManager instead...
    protected Channel getChannel(String name) {
	try {
	    return (Channel) dataService.getBinding(name);
	} catch (ObjectNotFoundException e) {
	    return null;
	}
    }

    protected Set<ClientSession> getSessions(Channel channel) {
	Set<ClientSession> sessions = new HashSet<ClientSession>();
	Iterator<ClientSession> iter = channel.getSessions();
	while (iter.hasNext()) {
	    sessions.add(iter.next());
	}
	return sessions;
    }
    
    protected void joinUsers(
	final String channelName, final String... users)
	throws Exception
    {
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
	    public void run() {
		Channel channel = getChannel(channelName);
		for (String user : users) {
		    ClientSession session =
			(ClientSession) dataService.getBinding(user);
		    channel.join(session);
		}
	    }
	}, taskOwner);
    }

    protected void leaveUsers(
 	final String channelName, final String... users)
	throws Exception
    {
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
	    public void run() {
		Channel channel = getChannel(channelName);
		for (String user : users) {
		    ClientSession session =
			(ClientSession) dataService.getBinding(user);
		    channel.leave(session);
		}
	    }
	}, taskOwner);
    }

    protected void leaveAll(final String channelName) throws Exception {
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
	    public void run() {
		Channel channel = getChannel(channelName);
		channel.leaveAll();
	    }
	}, taskOwner);
    }

    protected void checkUsersJoined(
	final String channelName, final String... users)
	throws Exception
    {
	// Need to wait for possible cache entry to expire so that a true
	// picture of the channel membership is reflected.
	Thread.sleep(1000);
	checkUsersJoined0(channelName, users);
    }
    
    private void checkUsersJoined0(
	final String channelName, final String... users)
	throws Exception
    {
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
	    public void run() {
		Channel channel = getChannel(channelName);
		Set<ClientSession> sessions = getSessions(channel);
		System.err.println("Sessions joined:" + sessions);
		if (sessions.size() != users.length) {
		    fail("Expected " + users.length + " sessions, got " +
			 sessions.size());
		}
		List<String> userList = Arrays.asList(users);
		for (ClientSession session : sessions) {
		    if (!userList.contains(session.getName())) {
			fail("Expected session: " + session);
		    }
		}
	    }
	}, taskOwner);
    }

    private void printIt(String line) {
	if (! isPerformanceTest) {
	    System.err.println(line);
	}
    }
    
    // Shuts down the node with the specified host.
    protected void shutdownNode(SgsTestNode node) throws Exception {
	node.shutdown(false);
	additionalNodes.remove(node);
    }
    
    protected void sendMessagesToChannel(
	final String channelName, int numMessages)
	throws Exception
    {
	for (int i = 0; i < numMessages; i++) {
	    final MessageBuffer buf = (new MessageBuffer(4)).putInt(i);
	    System.err.println("Sending message: " +
			       HexDumper.format(buf.getBuffer()));
	    
	    txnScheduler.runTask(
		new TestAbstractKernelRunnable() {
		    public void run() {
			Channel channel = getChannel(channelName);
			channel.send(null, ByteBuffer.wrap(buf.getBuffer()));
		    }
		}, taskOwner);
	}
    }

    protected void checkChannelMessagesReceived(
	DummyClient client, String channelName, int numMessages)
    {
	for (int i = 0; i < numMessages; i++) {
	    checkNextChannelMessage(client, channelName, i);
	}
    }

    protected void checkChannelMessagesReceived(
	ClientGroup group, String channelName, int numMessages)
	throws InterruptedException
    {
	Thread.sleep(3000);
	for (DummyClient client : group.getClients()) {
	    checkChannelMessagesReceived(client, channelName, numMessages);
	}
    }

    protected void checkNextChannelMessage(
	DummyClient client, String channelName, int value)
    {
	MessageInfo info = client.nextChannelMessage();
	if (info == null) {
	    fail("FAILURE: " + client.name +
		 " did not get any message for channel: " + channelName);
	}
	assertEquals("Mismatched channel names", channelName, info.channelName);
	assertEquals("Unexpected channel message sequence", value, info.seq);
    }
    

    // -- other classes --

    /**
     * Creates a dummy client with the specified {@code name} and logs it
     * into the specified {@code node}.
     */
    protected DummyClient createDummyClient(String name, SgsTestNode node) {
	DummyClient client = new DummyClient(name);
	client.connect(node.getAppPort());
	assertTrue(client.login());
	return client;
    }

    // Dummy client code for testing purposes.
    public class DummyClient extends AbstractDummyClient {

	private final Object lock = new Object();
	private Set<String> channelNames = new HashSet<String>();
	private Map<BigInteger, String> channelIdToName =
	    new HashMap<BigInteger, String>();
	Map<String, BigInteger> channelNameToId =
	    new HashMap<String, BigInteger>();
	private String reason;
	private final List<MessageInfo> channelMessages =
	    new ArrayList<MessageInfo>();

	/** Constructs an instance with the given {@code name}. */
	DummyClient(String name) {
	    super(name, SimpleSgsProtocol.VERSION);
	}
	
	ClientSession getSession() throws Exception {
	    GetSessionTask task = new GetSessionTask(name);
	    txnScheduler.runTask(task, taskOwner);
	    return task.getSession();
	}

	/** {@inheritDoc} */
	@Override
	public void sendMessage(byte[] message, boolean checkSuspend) {
	    checkLoggedIn();

	    // A zero-length message is sent when the superclass processes
	    // a LOGIN_SUCCESS or RELOCATE_SUCCESS message, so eat it here.
	    if (message.length == 0) {
		return;
	    }

	    MessageBuffer buf =
		new MessageBuffer(1 + message.length);
	    buf.putByte(SimpleSgsProtocol.SESSION_MESSAGE).
		putBytes(message);
	    sendRaw(buf.getBuffer(), checkSuspend);
	}

	// Sends a CHANNEL_MESSAGE.
	void sendChannelMessage(String channelName, int seq)
	    throws Exception
	{
	    checkLoggedIn();
	    BigInteger channelRefId = channelNameToId.get(channelName);
	    if (channelRefId == null) {
		channelRefId = getChannelId(channelName);
	    }
	    System.err.println(toString() + " sending message:" + seq +
			       " to channel:" + channelName);
	    byte[] channelId = channelRefId.toByteArray();
	    MessageBuffer buf =
		new MessageBuffer(3 + channelId.length + 4);
	    buf.putByte(SimpleSgsProtocol.CHANNEL_MESSAGE).
		putShort(channelId.length).
		putBytes(channelId).
		putInt(seq);
	    sendRaw(buf.getBuffer(), true);
	}
	
	MessageInfo nextChannelMessage() {
	    int totalWaitTime = WAIT_TIME * 2;
	    int waitInterval = totalWaitTime / 10;
	    synchronized (lock) {
		while (channelMessages.isEmpty()) {
		    try {
			lock.wait(waitInterval);
		    } catch (InterruptedException e) {
		    }
		    totalWaitTime -= waitInterval;
		    if (totalWaitTime <= 0) {
			break;
		    }
		}
		return
		    channelMessages.isEmpty() ?
		    null :
		    channelMessages.remove(0);
	    }
	}

	void join(String channelToJoin) {
	    String action = "join";
	    MessageBuffer buf =
		new MessageBuffer(MessageBuffer.getSize(action) +
				  MessageBuffer.getSize(channelToJoin));
	    buf.putString(action).putString(channelToJoin);
	    sendMessage(buf.getBuffer(), true);
	    assertJoinedChannel(channelToJoin);
	}
	
	void assertJoinedChannel(String channelName) {
	    synchronized (lock) {
		if (!channelNameToId.containsKey(channelName)) {
		    try {
			lock.wait(WAIT_TIME);
		    } catch (InterruptedException e) {
		    }
		    assertTrue(
			toString() + " did not receive CHANNEL_JOIN, " +
			"channel: " + channelName,
			channelNameToId.containsKey(channelName));
		}
	    }
	}
	    
	void leave(String channelToLeave) {
	    String action = "leave";
	    MessageBuffer buf =
		new MessageBuffer(MessageBuffer.getSize(action) +
				  MessageBuffer.getSize(channelToLeave));
	    buf.putString(action).putString(channelToLeave);
	    sendMessage(buf.getBuffer(), true);
	    assertLeftChannel(channelToLeave);
	}

	void assertLeftChannel(String channelName) {
	    synchronized (lock) {
		if (channelNameToId.containsKey(channelName)) {
		    try {
			lock.wait(WAIT_TIME);
		    } catch (InterruptedException e) {
		    }
		    assertFalse(
			toString() + " did not receive CHANNEL_LEAVE, " +
			"channel: " + channelName,
			channelNameToId.containsKey(channelName));
		}
	    }
	}
	
	/**
	 * Handles session and channel messages and channel joins and
	 * leaves, then delegates to the super class to handle those
	 * opcodes it doesn't handle.
	 */
	@Override
	protected void handleOpCode(byte opcode, MessageBuffer buf) {
	    switch (opcode) {
	    case SimpleSgsProtocol.SESSION_MESSAGE: {
		String action = buf.getString();
		if (action.equals("join")) {
		    String channelName = buf.getString();
		    synchronized (lock) {
			channelNames.add(channelName);
			System.err.println(
			    name + ": got join ack, channel: " +
			    channelName);
			lock.notifyAll();
		    }
		} else if (action.equals("leave")) {
		    String channelName = buf.getString();
		    synchronized (lock) {
			channelNames.remove(channelName);
			System.err.println(
			    name + ": got leave ack, channel: " +
			    channelName);
			lock.notifyAll();
		    }
		} else if (action.equals("message")) {
		    String channelName = buf.getString();
		    int seq = buf.getInt();
		    synchronized (lock) {
			channelMessages.add(new MessageInfo(channelName, seq));
			System.err.println(name + ": message received: " + seq);
			lock.notifyAll();
		    }
		} else {
		    System.err.println(
			name + ": received message with unknown action: " +
			action);
		}
		break;
	    }
		
	    case SimpleSgsProtocol.CHANNEL_JOIN: {
		String channelName = buf.getString();
		BigInteger channelId =
		    new BigInteger(1,
				   buf.getBytes(buf.limit() - buf.position()));
		synchronized (lock) {
		    channelIdToName.put(channelId, channelName);
		    channelNameToId.put(channelName, channelId);
		    printIt("[" + name + "] join succeeded: " +
			    channelName);
		    lock.notifyAll();
		}
		break;
	    }
		
	    case SimpleSgsProtocol.CHANNEL_LEAVE: {
		BigInteger channelId =
		    new BigInteger(1,
				   buf.getBytes(buf.limit() - buf.position()));
		synchronized (lock) {
		    String channelName = channelIdToName.remove(channelId);
		    channelNameToId.remove(channelName);
		    printIt("[" + name + "] leave succeeded: " +
			    channelName);
		    lock.notifyAll();
		}
		break;
		
	    }
	    case SimpleSgsProtocol.CHANNEL_MESSAGE: {
		BigInteger channelId =
		    new BigInteger(1, buf.getBytes(buf.getShort()));
		int seq = buf.getInt();
		synchronized (lock) {
		    String channelName = channelIdToName.get(channelId);
		    if (channelName != null) {
			System.err.println(
			    "[" + name + "] received message: " +
			    seq + ", channel: " + channelName);
			channelMessages.add(new MessageInfo(channelName, seq));
		    } else {
			System.err.println(
			    "[" + name + "] received message: " +
			    seq + ", but not joined to channel: " +
			    HexDumper.toHexString(channelId.toByteArray()));
		    }
		    lock.notifyAll();
		}
		break;
	    }
		
	    default:
		super.handleOpCode(opcode, buf);
		break;
	    }
	}
    }

    protected static class MessageInfo {
	final String channelName;
	final int seq;

	MessageInfo(String channelName, int seq) {
	    this.channelName = channelName;
	    this.seq = seq;
	}
    }

    public static class DummyAppListener implements AppListener, Serializable {

	private static final long serialVersionUID = 1L;

	public DummyAppListener() { }

	public ClientSessionListener loggedIn(ClientSession session) {

	    if (!(session instanceof ClientSessionWrapper)) {
		fail("session not ClientSessionWrapper instance: " +
		     session);
	    }
	    DummyClientSessionListener listener =
		new DummyClientSessionListener(session);
	    DataManager dataManager = AppContext.getDataManager();
	    dataManager.setBinding(session.getName(), session);
	    System.err.println("DummyAppListener.loggedIn: session:" + session);
	    return listener;
	}

	public void initialize(Properties props) {
	}
    }

    private static class DummyClientSessionListener
	implements ClientSessionListener, Serializable, ManagedObject
    {
	private static final long serialVersionUID = 1L;
	private final String name;
	
	private final ManagedReference<ClientSession> sessionRef;
	
	DummyClientSessionListener(ClientSession session) {
	    DataManager dataManager = AppContext.getDataManager();
	    this.sessionRef = dataManager.createReference(session);
	    this.name = session.getName();
	}

	public void disconnected(boolean graceful) {
	    AppContext.getDataManager().removeObject(this);
	}

	public void receivedMessage(ByteBuffer message) {
            byte[] bytes = new byte[message.remaining()];
            message.asReadOnlyBuffer().get(bytes);
	    MessageBuffer buf = new MessageBuffer(bytes);
	    String action = buf.getString();
	    DataManager dataManager = AppContext.getDataManager();
	    ClientSession session = sessionRef.get();
	    if (action.equals("join")) {
		String channelName = buf.getString();
		System.err.println("DummyClientSessionListener: join request, " +
				   "channel name: " + channelName +
				   ", user: " + name);
		Channel channel =
		    (Channel) dataManager.getBinding(channelName);
		channel.join(session);
		session.send(message.asReadOnlyBuffer());
	    } else if (action.equals("leave")) {
		String channelName = buf.getString();
		System.err.println("DummyClientSessionListener: leave request, " +
				   "channel name: " + channelName +
				   ", user: " + name);
		Channel channel =
		    (Channel) dataManager.getBinding(channelName);
		channel.leave(session);
		session.send(message.asReadOnlyBuffer());
	    } else {
		System.err.println("DummyClientSessionListener: UNKNOWN request, " +
				   "action: " +  action +
				   ", user: " + name);
	    }
	}
    }

    private class GetSessionTask extends TestAbstractKernelRunnable {

	private final String name;
	private ClientSession session = null;
	
	GetSessionTask(String name) {
	    this.name = name;
	}

	public void run() {
	    try {
		session = (ClientSession) dataService.getBinding(name);
	    } catch (ObjectNotFoundException e) {
		session = null;
	    }
	}

	ClientSession getSession() {
	    return session;
	}
    }

    protected int getObjectCount() throws Exception {
	GetObjectCountTask task = new GetObjectCountTask();
	txnScheduler.runTask(task, taskOwner);
	return task.count;
    }
    
    private class GetObjectCountTask extends TestAbstractKernelRunnable {

	volatile int count = 0;
	
	GetObjectCountTask() {
	}

	public void run() {
	    count = 0;
	    BigInteger last = null;
	    while (true) {
		BigInteger next = dataService.nextObjectId(last);
		if (next == null) {
		    break;
		}
                // NOTE: this count is used at the end of the test to make sure
                // that no objects were leaked in stressing the structure but
                // any given service (e.g., the task service) may accumulate
                // managed objects, so a more general way to exclude these from
                // the count would be nice but for now the specific types that
                // are accumulated get excluded from the count.
		ManagedReference ref =
		    dataService.createReferenceForId(next);
		Object obj = ref.get();
                String name = obj.getClass().getName();
                if (! name.equals("com.sun.sgs.impl.service.task.PendingTask") &&
		    ! name.equals("com.sun.sgs.impl.service.nodemap.IdentityMO"))
		{
		    /*
		    System.err.print(count + "[" + obj.getClass().getName() + "]:");
		    try {
			System.err.println(obj.toString());
		    } catch (ObjectNotFoundException e) {
			System.err.println("<< caught ObjectNotFoundException >>");
		    }
		    */
                    count++;
		}
                last = next;
	    }
	}
    }

    /**
     * Returns the count of channel service bindings, i,e. bindings that
     * have the following prefix:
     *
     * com.sun.sgs.impl.service.channel
     */
    protected int getChannelServiceBindingCount() throws Exception {
	GetChannelServiceBindingCountTask task =
	    new GetChannelServiceBindingCountTask();
	txnScheduler.runTask(task, taskOwner);
	return task.count;
    }
    
    private class GetChannelServiceBindingCountTask
	extends TestAbstractKernelRunnable
    {
	volatile int count = 0;
	
	GetChannelServiceBindingCountTask() {
	}

	public void run() {
	    count = 0;
	    Iterator<String> iter =
		BoundNamesUtil.getServiceBoundNamesIterator(
		    dataService, "com.sun.sgs.impl.service.channel.");
	    while (iter.hasNext()) {
		iter.next();
		count++;
	    }
	}
    }

    /**
     * Returns the client session with the specified {@code name} by
     * looking it up in the data service.
     */
    private ClientSession getClientSession(String name) {
	try {
	    return (ClientSession) dataService.getBinding(name);
	} catch (ObjectNotFoundException e) {
	    return null;
	}
    }

    /**
     * Holds the next invocation of the specified {@code ChannelServer}
     * {@code methodName} from a remote node to the specified {@code node},
     * until the "{@link #releaseChannelServerMethodHeld
     * releaseChannelServerMethodHeld} method is invoke with the specified
     * {@code node}.  Invocations to a {@code ChannelServer} on a local
     * node will not be held because such invocations are made directly on
     * the server implementation and not through a proxy.
     */
    protected void holdChannelServerMethodToNode(
	SgsTestNode node, final String methodName)
	throws Exception
    {
	long nodeId = node.getNodeId();
	MethodInfo info = holdMethodMap.get(nodeId);
	if (info != null) {
	    // just update method info and return.
	    info.name = methodName;
	    info.hold = true;
	    info.isHeld = false;
	} else {
	    // Store method info in map, and wrap channel server to hold methods
	    holdMethodMap.put(nodeId, new MethodInfo(methodName));
	}
    }

    /**
     * Waits for a notification of the method specified by a previous
     * invocation to {@link #holdChannelServerMethodToNode} to the
     * specified node.
     */
    protected void waitForHeldChannelServerMethodToNode(SgsTestNode node) 
	throws Exception
    {
	long nodeId = node.getNodeId();
	MethodInfo info = holdMethodMap.get(nodeId);
	if (info != null) {
	    synchronized (info) {
		if (!info.isHeld) {
		    try {
			info.wait(WAIT_TIME);
		    } catch (InterruptedException e) {
		    }
		}
		if (!info.isHeld) {
		    fail("Timeout waiting for held method: " + info.name +
			 " to node:" + nodeId);
		}
	    }
	    
	} else {
	    fail("No method to node:" + nodeId + " held");
	}
    }

    /**
     * Releases the {@code ChannelServer} method to the specified {@code
     * node} that is currently being held.
     */
    protected void releaseChannelServerMethodHeld(SgsTestNode node) {
	MethodInfo info = holdMethodMap.get(node.getNodeId());
	// Release "join" to oldNode.
	synchronized (info) {
	    info.hold = false;
	    info.notifyAll();
	}
    }

    /**
     * Wraps the {@code ChannelServer} proxy to the specified {@code node}
     * with a proxy that enables tests to hold and release specified method
     * invocations through the proxy.
     */
    private void wrapChannelServerProxy(SgsTestNode node)
	throws Exception
    {
	final long nodeId = node.getNodeId();
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
	    @SuppressWarnings("unchecked")
	    public void run() throws Exception {
		String key =
		    "com.sun.sgs.impl.service.channel.server." + nodeId;
		ManagedSerializable<ChannelServer> managedServer =
		    (ManagedSerializable<ChannelServer>)
		    dataService.getServiceBinding(key);
		Constructor constr =
 		    managedServer.getClass().getDeclaredConstructors()[0];
		constr.setAccessible(true);
		ChannelServer channelServer = managedServer.get();
		ControllableInvocationHandler handler =
		    new ControllableInvocationHandler(channelServer, nodeId);
		ChannelServer newServer = (ChannelServer)
		    Proxy.newProxyInstance(
			ChannelServer.class.getClassLoader(),
			new Class[] { ChannelServer.class },
			handler);
		dataService.setServiceBinding(
 		    key, constr.newInstance(newServer));
		dataService.removeObject(managedServer);
		
	    }}, taskOwner);
    }

    /**
     * Returns the channel ID for the channel with the specified name.
     */
    protected BigInteger getChannelId(final String channelName)
	throws Exception
    {
	return KernelCallable.call(
	    new KernelCallable<BigInteger>("getChannelId") {
		public BigInteger call() throws Exception {
		Channel channel =
		    AppContext.getChannelManager().getChannel(channelName);
		Field field = getField(channel.getClass(), "channelRef");
		ManagedReference channelRef =
		    (ManagedReference) field.get(channel);
		return channelRef.getId();
		}
	    }, txnScheduler, taskOwner);
    }
    
    /**
     * This invocation handler waits to be notified before invoking a
     * method (on the underlying instance) if the method being invoked
     * matches the method information currently on record for the node and
     * the method's "hold" status is true.
     */
    private static class ControllableInvocationHandler
	implements InvocationHandler, Serializable
    {
	private static final long serialVersionUID = 1L;
	private final Object obj;
	private final long nodeId;
	
	ControllableInvocationHandler(Object obj, long nodeId) {
	    this.obj = obj;
	    this.nodeId = nodeId;
	}
	
	public Object invoke(Object proxy, Method method, Object[] args)
	    throws Exception
	{
	    MethodInfo info = holdMethodMap.get(nodeId);
	    if (info != null) {
		synchronized (info) {
		    if (method.getName().equals(info.name)) {
			if (info.hold) {
			    System.err.println(
				">>HOLD ChannelServer method: " + info.name +
				" to node: " + nodeId);
			    info.isHeld = true;
			    info.notifyAll();
			    while (info.hold) {
				try {
				    info.wait();
				} catch (InterruptedException e) {
				}
			    }
			    info.isHeld = false;
			    System.err.println(
				">>RELEASE ChannelServer method: " + info.name +
				" to node: " + nodeId);
			}
		    }
		}
	    }
		    
	    try {
		return method.invoke(obj, args);
	    } catch (InvocationTargetException e) {
		Throwable cause = e.getCause();
		if (cause instanceof Exception) {
		    throw (Exception) cause;
		} else if (cause instanceof Error) {
		    throw (Error) cause;
		} else {
		    throw new RuntimeException(
			"Unexpected exception:" + cause, cause);
		}
	    }
	}
    }

    /**
     * Method information used to hold and release methods invoked on a
     * ChannelServer. If a method is being held, the instance can be used
     * as the lock to wait for notification. The instance is notified when
     * the method is released.
     */
    private static class MethodInfo {
	volatile String name;
	volatile boolean hold = true;
	volatile boolean isHeld = false;

	MethodInfo(String name) {
	    this.name = name;
	}
    }
}
