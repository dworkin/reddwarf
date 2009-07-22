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
import com.sun.sgs.auth.Identity;
import com.sun.sgs.impl.kernel.StandardProperties;
import com.sun.sgs.impl.service.channel.ChannelServiceImpl;
import com.sun.sgs.impl.service.nodemap.DirectiveNodeAssignmentPolicy;
import com.sun.sgs.impl.service.session.ClientSessionWrapper;
import com.sun.sgs.impl.sharedutil.HexDumper;
import com.sun.sgs.impl.sharedutil.MessageBuffer;
import com.sun.sgs.impl.util.BoundNamesUtil;
import com.sun.sgs.kernel.TransactionScheduler;
import com.sun.sgs.protocol.simple.SimpleSgsProtocol;
import com.sun.sgs.service.DataService;
import com.sun.sgs.test.util.AbstractDummyClient;
import com.sun.sgs.test.util.SgsTestNode;
import com.sun.sgs.test.util.TestAbstractKernelRunnable;
import com.sun.sgs.tools.test.FilteredJUnit3TestRunner;
import java.io.Serializable;
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

import junit.framework.TestCase;
import org.junit.runner.RunWith;

import static com.sun.sgs.test.util.UtilProperties.createProperties;

@RunWith(FilteredJUnit3TestRunner.class)
public abstract class AbstractChannelServiceTest extends TestCase {
    
    private static final String APP_NAME = "TestChannelServiceImpl";
    
    protected static final int WAIT_TIME = 2000;
    
    protected static final List<String> sevenDwarfs =
	Arrays.asList(new String[] {
			  "bashful", "doc", "dopey", "grumpy",
			  "happy", "sleepy", "sneezy"});

    /** The Channel service properties. */
    protected static final Properties serviceProps =
	createProperties(StandardProperties.APP_NAME, APP_NAME);
    
    /** The node that creates the servers. */
    protected SgsTestNode serverNode;

    /** Any additional nodes, keyed by node hostname (for tests
     * needing more than one node). */
    protected Map<String,SgsTestNode> additionalNodes;

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

    /** The listen port for the client session service. */
    protected int port;

    /** A list of users for test purposes. */
    protected List<String> someUsers =
	Arrays.asList(new String[] { "moe", "larry", "curly" });

    protected boolean isPerformanceTest = false;

    private static Field getField(Class cl, String name) throws Exception {
	Field field = cl.getDeclaredField(name);
	field.setAccessible(true);
	return field;
    }
    
    /** Constructs a test instance. */
    public AbstractChannelServiceTest(String name) throws Exception  {
	super(name);
	Class cl = ChannelServiceImpl.class;
	VERSION_KEY = (String) getField(cl, "VERSION_KEY").get(null);
	MAJOR_VERSION = getField(cl, "MAJOR_VERSION").getInt(null);
	MINOR_VERSION = getField(cl, "MINOR_VERSION").getInt(null);
    }

    /** Creates and configures the channel service. */
    protected void setUp() throws Exception {
	System.err.println("Testcase: " + getName());
        setUp(true);
    }

    protected void setUp(boolean clean) throws Exception {
        Properties props = 
            SgsTestNode.getDefaultProperties(APP_NAME, null, 
                                             DummyAppListener.class);
        props.setProperty(StandardProperties.AUTHENTICATORS, 
                      "com.sun.sgs.test.util.SimpleTestIdentityAuthenticator");
	props.setProperty("com.sun.sgs.impl.service.nodemap.policy.class",
			  DirectiveNodeAssignmentPolicy.class.getName());
	serverNode = 
                new SgsTestNode(APP_NAME, DummyAppListener.class, props, clean);
	port = serverNode.getAppPort();

        txnScheduler = 
            serverNode.getSystemRegistry().
            getComponent(TransactionScheduler.class);
        taskOwner = serverNode.getProxy().getCurrentOwner();

        dataService = serverNode.getDataService();
	channelService = serverNode.getChannelService();
    }

    /** Sets passed if the test passes. */
    protected void runTest() throws Throwable {
	super.runTest();
        Thread.sleep(100);
    }
    
    /** Cleans up the transaction. */
    protected void tearDown() throws Exception {
        tearDown(true);
    }

    protected void tearDown(boolean clean) throws Exception {
	// This sleep cuts down on the exceptions output due to shutdwon.
	Thread.sleep(500);
	if (additionalNodes != null) {
            for (SgsTestNode node : additionalNodes.values()) {
                node.shutdown(false);
            }
            additionalNodes = null;
        }
        serverNode.shutdown(clean);
        serverNode = null;
    }

    /** 
     * Add additional nodes.  We only do this as required by the tests. 
     *
     * @param hosts contains a host name for each additional node
     */
    protected void addNodes(String... hosts) throws Exception {
        // Create the other nodes
	if (additionalNodes == null) {
	    additionalNodes = new HashMap<String, SgsTestNode>();
	}

        for (String host : hosts) {
	    Properties props = SgsTestNode.getDefaultProperties(
	        APP_NAME, serverNode, DummyAppListener.class);
	    props.setProperty(StandardProperties.AUTHENTICATORS, 
                "com.sun.sgs.test.util.SimpleTestIdentityAuthenticator");
	    props.put("com.sun.sgs.impl.service.watchdog.client.host", host);
            SgsTestNode node = 
                    new SgsTestNode(serverNode, DummyAppListener.class, props);
	    additionalNodes.put(host, node);
        }
    }

    protected class ClientGroup {

	Map<String, DummyClient> clients;

	ClientGroup(String... users) {
	    this(Arrays.asList(users));
	}

	ClientGroup(List<String> users) {
	    this(users, port);
	}
	
	ClientGroup(List<String> users, int connectPort) {
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
	return createChannel(name,  null, null);
    }

    protected Channel createChannel(String name, ChannelListener listener)
	throws Exception
    {
	return createChannel(name, listener, null);

    }
    
    protected Channel createChannel(
	String name, ChannelListener listener, String host) throws Exception
    {
	CreateChannelTask createChannelTask =
	    new CreateChannelTask(name, listener, host);
	runTransactionalTask(createChannelTask, host);
	return createChannelTask.getChannel();
    }

    private void runTransactionalTask(TestAbstractKernelRunnable task, String host)
	throws Exception
    {
	SgsTestNode node =
	    host == null ? serverNode : additionalNodes.get(host);
	if (node == null) {
	    throw new NullPointerException("no node for host: " + host);
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
	private final String host;
	private Channel channel;
	
	CreateChannelTask(String name, ChannelListener listener, String host) {
	    this.name = name;
	    this.listener = listener;
	    this.host = host;
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
	final String channelName, final List<String> users)
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

    protected void checkUsersJoined(
	final String channelName, final List<String> users)
	throws Exception
    {
	for (int i = 0; i < 3; i++) {
	    try {
		checkUsersJoined0(channelName, users);
		return;
	    } catch (junit.framework.AssertionFailedError e) {
	    }
	    Thread.sleep(100);
	}
    }
    
    private void checkUsersJoined0(
	final String channelName, final List<String> users)
	throws Exception
    {
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
	    public void run() {
		Channel channel = getChannel(channelName);
		Set<ClientSession> sessions = getSessions(channel);
		System.err.println("Sessions joined:" + sessions);
		if (sessions.size() != users.size()) {
		    fail("Expected " + users.size() + " sessions, got " +
			 sessions.size());
		}
		for (ClientSession session : sessions) {
		    if (!users.contains(session.getName())) {
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
    protected void shutdownNode(String host) throws Exception {
	additionalNodes.get(host).shutdown(false);
	additionalNodes.remove(host);
    }
    
    protected void sendMessagesToChannel(
	final String channelName, ClientGroup group, int numMessages)
	throws Exception
    {
	try {
	    boolean failed = false;
	    String messageString = "message";

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

	    Thread.sleep(3000);
	    for (DummyClient client : group.getClients()) {
		for (int i = 0; i < numMessages; i++) {
		    MessageInfo info = client.nextChannelMessage();
		    if (info == null) {
			failed = true;
			System.err.println(
			    "FAILURE: " + client.name +
			    " did not get message: " + i);
			continue;
		    } else {
			if (! info.channelName.equals(channelName)) {
			    fail("Got channel name: " + info.channelName +
				 ", Expected: " + channelName);
			}
			System.err.println(
			    client.name + " got channel message: " + info.seq);
			if (info.seq != i) {
			    failed = true;
			    System.err.println(
				"\tFAILURE: expected sequence number: " + i);
			}
		    }
		}
	    }

	    if (failed) {
		fail("test failed: see output");
	    }
	    
	} catch (RuntimeException e) {
	    System.err.println("unexpected failure");
	    e.printStackTrace();
	    printServiceBindings("after exception");
	    fail("unexpected failure: " + e);
	}
    }

    // -- other classes --

    // Dummy client code for testing purposes.
    protected class DummyClient extends AbstractDummyClient {

	private final Object lock = new Object();
	private boolean joinAck = false;
	private boolean leaveAck = false;
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
	    super(name);
	}

	ClientSession getSession() throws Exception {
	    GetSessionTask task = new GetSessionTask(name);
	    txnScheduler.runTask(task, taskOwner);
	    return task.getSession();
	}

	/** {@inheritDoc} */
	@Override
	public void sendMessage(byte[] message) {
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
	    sendRaw(buf.getBuffer());
	}

	// Sends a CHANNEL_MESSAGE.
	void sendChannelMessage(String channelName, int seq) {
	    checkLoggedIn();
	    sendChannelMessage(channelNameToId.get(channelName), seq);
	}

	void sendChannelMessage(BigInteger channelRefId, int seq) {
	    byte[] channelId = channelRefId.toByteArray();
	    MessageBuffer buf =
		new MessageBuffer(3 + channelId.length + 4);
	    buf.putByte(SimpleSgsProtocol.CHANNEL_MESSAGE).
		putShort(channelId.length).
		putBytes(channelId).
		putInt(seq);
	    sendRaw(buf.getBuffer());
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

	void join(String channelToJoin) {
	    String action = "join";
	    MessageBuffer buf =
		new MessageBuffer(MessageBuffer.getSize(action) +
				  MessageBuffer.getSize(channelToJoin));
	    buf.putString(action).putString(channelToJoin);
	    sendMessage(buf.getBuffer());
	    joinAck = false;
	    waitForJoin(channelToJoin);
	}

	void waitForJoin(String channelToJoin) {
	    synchronized (lock) {
		try {
		    if (joinAck == false) {
			lock.wait(WAIT_TIME);
		    }
		    if (joinAck != true) {
			throw new RuntimeException(
			    "DummyClient.join timed out: " + channelToJoin);
		    }

		    if (channelNameToId.get(channelToJoin) == null) {
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

		    if (channelNameToId.get(channelToLeave) != null) {
			fail("DummyClient.leave still joined: " +
			     channelToLeave);
		    }
		    
		} catch (InterruptedException e) {
		    throw new RuntimeException(
			    "DummyClient.leave timed out: " + channelToLeave, e);
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
			joinAck = true;
			channelNames.add(channelName);
			System.err.println(
			    name + ": got join ack, channel: " +
			    channelName);
			lock.notifyAll();
		    }
		} else if (action.equals("leave")) {
		    String channelName = buf.getString();
		    synchronized (lock) {
			leaveAck = true;
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
		    joinAck = true;
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
		    leaveAck = true;
		    String channelName = channelIdToName.remove(channelId);
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
		    System.err.println("[" + name + "] received message: " +
				       seq + ", channel: " + channelName);
		    channelMessages.add(new MessageInfo(channelName, seq));
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

	private final static long serialVersionUID = 1L;

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
	private final static long serialVersionUID = 1L;
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
	private ClientSession session;
	
	GetSessionTask(String name) {
	    this.name = name;
	}

	public void run() {
	    session = (ClientSession) dataService.getBinding(name);
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

    private ClientSession getClientSession(String name) {
	try {
	    return (ClientSession) dataService.getBinding(name);
	} catch (ObjectNotFoundException e) {
	    return null;
	}
    }
}
