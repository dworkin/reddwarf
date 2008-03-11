 /*
 * Copyright 2007-2008 Sun Microsystems, Inc.
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
import com.sun.sgs.app.ChannelManager;
import com.sun.sgs.app.ClientSession;
import com.sun.sgs.app.ClientSessionListener;
import com.sun.sgs.app.DataManager;
import com.sun.sgs.app.Delivery;
import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.ManagedReference;
import com.sun.sgs.app.NameNotBoundException;
import com.sun.sgs.app.ObjectNotFoundException;
import com.sun.sgs.app.TransactionNotActiveException;
import com.sun.sgs.auth.Identity;
import com.sun.sgs.auth.IdentityCredentials;
import com.sun.sgs.auth.IdentityCoordinator;
import com.sun.sgs.impl.auth.NamePasswordCredentials;
import com.sun.sgs.impl.io.SocketEndpoint;
import com.sun.sgs.impl.io.TransportType;
import com.sun.sgs.impl.kernel.StandardProperties;
import com.sun.sgs.impl.service.channel.ChannelServiceImpl;
import com.sun.sgs.impl.service.channel.ChannelUtil;
import com.sun.sgs.impl.sharedutil.CompactId;
import com.sun.sgs.impl.sharedutil.HexDumper;
import com.sun.sgs.impl.sharedutil.MessageBuffer;
import com.sun.sgs.impl.util.AbstractKernelRunnable;
import com.sun.sgs.impl.util.BoundNamesUtil;
import com.sun.sgs.io.Connection;
import com.sun.sgs.io.ConnectionListener;
import com.sun.sgs.io.Connector;
import com.sun.sgs.kernel.TaskScheduler;
import com.sun.sgs.protocol.simple.SimpleSgsProtocol;
import com.sun.sgs.service.ClientSessionService;
import com.sun.sgs.service.DataService;
import com.sun.sgs.test.util.DummyComponentRegistry;
import com.sun.sgs.test.util.DummyTransactionProxy;
import com.sun.sgs.test.util.SgsTestNode;
import static com.sun.sgs.test.util.UtilProperties.createProperties;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
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
import java.util.concurrent.atomic.AtomicLong;
import junit.framework.TestCase;

public class TestChannelServiceImpl extends TestCase {
    
    private static final String APP_NAME = "TestChannelServiceImpl";
    
    private static final int WAIT_TIME = 3000;
    
    private static final String LOGIN_FAILED_MESSAGE = "login failed";

    private static Object disconnectedCallbackLock = new Object();

    private static final List<String> sevenDwarfs =
	Arrays.asList(new String[] {
			  "bashful", "doc", "dopey", "grumpy",
			  "happy", "sleepy", "sneezy"});

    /** The node that creates the servers. */
    private SgsTestNode serverNode;

    /** Any additional nodes, keyed by node hostname (for tests
     * needing more than one node). */
    private Map<String,SgsTestNode> additionalNodes;

    /** The task scheduler. */
    private TaskScheduler taskScheduler;

    /** The owner for tasks I initiate. */
    private Identity taskOwner;

    /** The shared data service. */
    private DataService dataService;

    /** The channel service on the server node. */
    private ChannelManager channelService;

    /** The client session service on the server node. */
    private ClientSessionService sessionService;

    /** True if test passes. */
    private boolean passed;

    /** The test clients, keyed by user name. */
    private static Map<String, DummyClient> dummyClients;

    /** The listen port for the client session service. */
    private int port;

    /** The node ID for the local node. */
    private long serverNodeId;

    /** A list of users for test purposes. */
    private List<String> someUsers =
	Arrays.asList(new String[] { "moe", "larry", "curly" });
    
    /** Constructs a test instance. */
    public TestChannelServiceImpl(String name) {
	super(name);
    }

    /** Creates and configures the channel service. */
    protected void setUp() throws Exception {
	passed = false;
        dummyClients = new HashMap<String, DummyClient>();
	System.err.println("Testcase: " + getName());
        setUp(true);
    }

    protected void setUp(boolean clean) throws Exception {
        Properties props = 
            SgsTestNode.getDefaultProperties(APP_NAME, null, 
                                             DummyAppListener.class);
        props.setProperty(StandardProperties.AUTHENTICATORS, 
                      "com.sun.sgs.test.util.SimpleTestIdentityAuthenticator");
	serverNode = 
                new SgsTestNode(APP_NAME, DummyAppListener.class, props, clean);
	port = serverNode.getAppPort();

        taskScheduler = 
            serverNode.getSystemRegistry().getComponent(TaskScheduler.class);
        taskOwner = serverNode.getProxy().getCurrentOwner();

        dataService = serverNode.getDataService();
	sessionService = serverNode.getClientSessionService();
	channelService = serverNode.getChannelService();
	
	serverNodeId = serverNode.getWatchdogService().getLocalNodeId();
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
	// This sleep cuts down on the exceptions output due to shutdwon.
	Thread.sleep(750);
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
    private void addNodes(String... hosts) throws Exception {
        // Create the other nodes
        additionalNodes = new HashMap<String, SgsTestNode>();

        for (String host : hosts) {
	    Properties props = SgsTestNode.getDefaultProperties(
	        APP_NAME, serverNode, DummyAppListener.class);
	    props.put("com.sun.sgs.impl.service.watchdog.client.host", host);
            SgsTestNode node = 
                    new SgsTestNode(serverNode, DummyAppListener.class, props);
	    additionalNodes.put(host, node);
        }
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
	    Properties props =
		createProperties("com.sun.sgs.app.name", APP_NAME);
	    new ChannelServiceImpl(props, null,
				   new DummyTransactionProxy());
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
    }

    public void testConstructorNullTransactionProxy() throws Exception {
	try {
	    Properties props =
		createProperties("com.sun.sgs.app.name", APP_NAME);
	    new ChannelServiceImpl(props, new DummyComponentRegistry(),
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
		serverNode.getProxy());
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
    }

    /* -- Test createChannel -- */

    public void testCreateChannelNoTxn() throws Exception { 
	try {
	    channelService.createChannel(Delivery.RELIABLE);
	    fail("Expected TransactionNotActiveException");
	} catch (TransactionNotActiveException e) {
	    System.err.println(e);
	}
    }

    /* TBD: how is this test implemented?
    public void testCreateChannelAndAbort() {
	createChannel("foo");
	txn.abort(null);
	createTransaction();
	try {
	    getChannel("foo");
	    fail("Expected NameNotBoundException");
	} catch (NameNotBoundException e) {
	    System.err.println(e);
	}
    }
    */

    /* -- Test Channel serialization -- */

    public void testChannelWriteReadObject() throws Exception {
	taskScheduler.runTransactionalTask(new AbstractKernelRunnable() {
	    public void run() throws Exception {
		Channel savedChannel =
		    channelService.createChannel(Delivery.RELIABLE);
		ByteArrayOutputStream bout = new ByteArrayOutputStream();
		ObjectOutputStream out = new ObjectOutputStream(bout);
		out.writeObject(savedChannel);
		out.flush();
		out.close();
		
		ByteArrayInputStream bin =
		    new ByteArrayInputStream(bout.toByteArray());
		ObjectInputStream in = new ObjectInputStream(bin);
		Channel channel = (Channel) in.readObject();

		if (!savedChannel.equals(channel)) {
		    fail("Expected channel: " + savedChannel +
			 ", got " + channel);
		}
		System.err.println("Channel {write,read}Object successful");
	    }
	}, taskOwner);
    }
    
    /* -- Test Channel.getDeliveryRequirement -- */

    public void testChannelGetDeliveryNoTxn() throws Exception {
	Channel channel = createChannel();
	try {
	    channel.getDeliveryRequirement();
	    fail("Expected TransactionNotActiveException");
	} catch (TransactionNotActiveException e) {
	    System.err.println(e);
	}
    }

    public void testChannelGetDeliveryMismatchedTxn() throws Exception {
	// TBD: should the implementation work this way?
	final Channel channel = createChannel();
	taskScheduler.runTransactionalTask(new AbstractKernelRunnable() {
	    public void run() {
		try {
		    channel.getDeliveryRequirement();
		    fail("Expected TransactionNotActiveException");
		} catch (TransactionNotActiveException e) {
		    System.err.println(e);
		}
	    }
	}, taskOwner);
    }

    public void testChannelGetDelivery() throws Exception {
	taskScheduler.runTransactionalTask(new AbstractKernelRunnable() {
	    public void run() {
		for (Delivery delivery : Delivery.values()) {
		    Channel channel = channelService.createChannel(delivery);
		    if (!delivery.equals(channel.getDeliveryRequirement())) {
			fail("Expected: " + delivery + ", got: " +
			     channel.getDeliveryRequirement());
		    }
		}
		System.err.println("Delivery requirements are equal");
	    }
	}, taskOwner);
    }

    public void testChannelGetDeliveryClosedChannel() throws Exception {
	taskScheduler.runTransactionalTask(new AbstractKernelRunnable() {
	    public void run() {
		for (Delivery delivery : Delivery.values()) {
		    Channel channel = channelService.createChannel(delivery);
		    dataService.removeObject(channel);
		    try {
			channel.getDeliveryRequirement();
			fail("Expected IllegalStateException");
		    } catch (IllegalStateException e) {
			System.err.println(e);
		    }
		}
		System.err.println("Got delivery requirement on close channel");
	    }
	}, taskOwner);
    }

    /* -- Test Channel.join -- */

    private DummyClient newClient() {
	return (new DummyClient()).connect(port).login("dummy", "password");	
    }

    public void testChannelJoinNoTxn() throws Exception {
	Channel channel = createChannel();
	DummyClient client = newClient();
	try {
	    channel.join(client.getSession());
	    fail("Expected TransactionNotActiveException");
	} catch (TransactionNotActiveException e) {
	    System.err.println(e);
	} finally {
	    if (client != null) {
		client.disconnect();
	    }
	}
    }

    public void testChannelJoinClosedChannel() throws Exception {
	final DummyClient client = newClient();
	try {
	    taskScheduler.runTransactionalTask(new AbstractKernelRunnable() {
		public void run() throws Exception {
		    Channel channel =
			channelService.createChannel(Delivery.RELIABLE);
		    dataService.removeObject(channel);
		    try {
			channel.join(client.getSession());
			fail("Expected IllegalStateException");
		    } catch (IllegalStateException e) {
			System.err.println(e);
		    }
		}
		}, taskOwner);
	    
	} finally {
	    if (client != null) {
		client.disconnect();
	    }
	}
    }

    public void testChannelJoinNullClientSession() throws Exception {
	taskScheduler.runTransactionalTask(new AbstractKernelRunnable() {
	    public void run() {
		Channel channel =
		    channelService.createChannel(Delivery.RELIABLE);
		try {
		    channel.join((ClientSession) null);
		    fail("Expected NullPointerException");
		} catch (NullPointerException e) {
		    System.err.println(e);
		}
	    }
	}, taskOwner);
    }

    public void testChannelJoin() throws Exception {
	String channelName = "joinTest";
	createChannel(channelName);
	ClientGroup group = new ClientGroup(someUsers);
	
	try {
	    joinUsers(channelName, someUsers);
	    checkUsersJoined(channelName, someUsers);

	} finally {
	    group.disconnect(false);
	}
    }

    private void joinUsers(
	final String channelName, final List<String> users)
	throws Exception
    {
	taskScheduler.runTransactionalTask(new AbstractKernelRunnable() {
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

    private void checkUsersJoined(
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
	taskScheduler.runTransactionalTask(new AbstractKernelRunnable() {
	    public void run() {
		Channel channel = getChannel(channelName);
		Set<ClientSession> sessions = getSessions(channel);
		if (sessions.size() != users.size()) {
		    fail("Expected " + users.size() + " sessions, got " +
			 sessions.size());
		}
		for (ClientSession session : sessions) {
		    if (!users.contains(session.getName())) {
			fail("Expected session: " + session);
		    }
		}
		System.err.println("All sessions joined");
	    }
	}, taskOwner);
    }

    /* -- Test Channel.leave -- */

    public void testChannelLeaveNoTxn() throws Exception {
	Channel channel = createChannel();
	DummyClient client = newClient();
	try {
	    channel.leave(client.getSession());
	    fail("Expected TransactionNotActiveException");
	} catch (TransactionNotActiveException e) {
	    System.err.println(e);
	} finally {
	    if (client != null) {
		client.disconnect();
	    }
	}
    }

    public void testChannelLeaveMismatchedTxn() throws Exception {
	// TBD: should the implementation work this way?
	final String channelName = "test";
	final Channel channel = createChannel(channelName);
	final DummyClient client = newClient();
	try {
	    taskScheduler.runTransactionalTask(new AbstractKernelRunnable() {
		public void run() throws Exception {
		    try {
			channel.leave(client.getSession());
			fail("Expected TransactionNotActiveException");
		    } catch (TransactionNotActiveException e) {
			System.err.println(e);
		    }
		}
		}, taskOwner);
	} finally {
	    if (client != null) {
		client.disconnect();
	    }
	}
    }

    public void testChannelLeaveClosedChannel() throws Exception {
	final String channelName = "leaveClosedChannelTest";
	final String user = "daffy";
	final List<String> users = Arrays.asList(new String[] { user });
	createChannel(channelName);
	ClientGroup group = new ClientGroup(users);

	try {
	    taskScheduler.runTransactionalTask(new AbstractKernelRunnable() {
		public void run() {
		    Channel channel = getChannel(channelName);
		    ClientSession session =
			(ClientSession) dataService.getBinding(user);
		    channel.join(session);
		    dataService.removeObject(channel);
		    try {
			channel.leave(session);
			fail("Expected IllegalStateException");
		    } catch (IllegalStateException e) {
			System.err.println(e);
		    }
		}
	    }, taskOwner);
	
	} finally {
	    group.disconnect(false);
	}
    }

    public void testChannelLeaveNullClientSession() throws Exception {
	
	taskScheduler.runTransactionalTask(new AbstractKernelRunnable() {
	    public void run() {
		Channel channel =
		    channelService.createChannel(Delivery.RELIABLE);
		try {
		    channel.leave((ClientSession) null);
		    fail("Expected NullPointerException");
		} catch (NullPointerException e) {
		    System.err.println(e);
		}
	    }
	}, taskOwner);
    }

    public void testChannelLeaveSessionNotJoined() throws Exception {
	final String channelName = "leaveTest";
	createChannel(channelName);
	ClientGroup group = new ClientGroup(someUsers);
	
	try {
	    taskScheduler.runTransactionalTask(new AbstractKernelRunnable() {
		public void run() {
		    Channel channel = getChannel(channelName);
	
		    ClientSession moe =
			(ClientSession) dataService.getBinding("moe");
		    channel.join(moe);

		    try {
			ClientSession larry =
			    (ClientSession) dataService.getBinding("larry");
			channel.leave(larry);
			System.err.println("leave of non-member session returned");
			
		    } catch (Exception e) {
			System.err.println(e);
			fail("test failed with exception: " + e);
		    }
		    
		}
 	    }, taskOwner);

	    Thread.sleep(100);
	    
	    taskScheduler.runTransactionalTask(new AbstractKernelRunnable() {
		public void run() {
		    Channel channel = getChannel(channelName);
	
		    ClientSession moe =
			(ClientSession) dataService.getBinding("moe");

		    ClientSession larry =
			(ClientSession) dataService.getBinding("larry");
		    
		    Set<ClientSession> sessions = getSessions(channel);
		    if (sessions.size() != 1) {
			fail("Expected 1 session, got " +
			     sessions.size());
		    }

		    if (! sessions.contains(moe)) {
			fail("Expected session: " + moe);
		    }
		    dataService.removeObject(channel);
		}
 	    }, taskOwner);
	    
	} finally {
	    group.disconnect(false);
	}
    }
    
    public void testChannelLeave() throws Exception {
	final String channelName = "leaveTest";
	createChannel(channelName);
	ClientGroup group = new ClientGroup(someUsers);
	
	try {
	    joinUsers(channelName, someUsers);
	    checkUsersJoined(channelName, someUsers);

	    for (final String user : someUsers) {
		
		taskScheduler.runTransactionalTask(new AbstractKernelRunnable() {
		    public void run() {
			Channel channel = getChannel(channelName);
			ClientSession session = getSession(user);
			channel.leave(session);
		    }}, taskOwner);

		Thread.sleep(100);
		
		taskScheduler.runTransactionalTask(new AbstractKernelRunnable() {
		    public void run() {
			Channel channel = getChannel(channelName);
			ClientSession session = getSession(user);
			if (getSessions(channel).contains(session)) {
			    fail("Failed to remove session: " + session);
			}}}, taskOwner);
	    }
	    
	    taskScheduler.runTransactionalTask(new AbstractKernelRunnable() {
		public void run() {
		    Channel channel = getChannel(channelName);

		    int numJoinedSessions = getSessions(channel).size();
		    if (numJoinedSessions != 0) {
			fail("Expected no sessions, got " + numJoinedSessions);
		    }
		    System.err.println("All sessions left");
		    
		    dataService.removeObject(channel);
		}}, taskOwner);

	} finally {
	    group.disconnect(false);
	}

    }

    /* -- Test Channel.leaveAll -- */

    public void testChannelLeaveAllNoTxn() throws Exception {
	Channel channel = createChannel();
	try {
	    channel.leaveAll();
	    fail("Expected TransactionNotActiveException");
	} catch (TransactionNotActiveException e) {
	    System.err.println(e);
	}
    }

    public void testChannelLeaveAllClosedChannel() throws Exception {
	taskScheduler.runTransactionalTask(new AbstractKernelRunnable() {
	    public void run() {
		Channel channel =
		    channelService.createChannel(Delivery.RELIABLE);
		dataService.removeObject(channel);
		try {
		    channel.leaveAll();
		    fail("Expected IllegalStateException");
		} catch (IllegalStateException e) {
		    System.err.println(e);
		}
	    }
	}, taskOwner);
    }

    public void testChannelLeaveAllNoSessionsJoined() throws Exception {
	taskScheduler.runTransactionalTask(new AbstractKernelRunnable() {
	    public void run() {
		Channel channel =
		    channelService.createChannel(Delivery.RELIABLE);
		channel.leaveAll();
		System.err.println(
		    "leaveAll succeeded with no sessions joined");
	    }
	}, taskOwner);
    }
    
    public void testChannelLeaveAll() throws Exception {
	final String channelName = "leaveAllTest";
	createChannel(channelName);
	ClientGroup group = new ClientGroup(someUsers);
	
	try {
	    joinUsers(channelName, someUsers);
	    checkUsersJoined(channelName, someUsers);

	    taskScheduler.runTransactionalTask(new AbstractKernelRunnable() {
		public void run() {
		    Channel channel = getChannel(channelName);
		    channel.leaveAll();
		}
	    }, taskOwner);
	    
	    Thread.sleep(100);
	    
	    taskScheduler.runTransactionalTask(new AbstractKernelRunnable() {
		public void run() {
		    Channel channel = getChannel(channelName);
		    int numJoinedSessions = getSessions(channel).size();
		    if (numJoinedSessions != 0) {
			fail("Expected no sessions, got " + numJoinedSessions);
		    }
		    System.err.println("All sessions left");
		    dataService.removeObject(channel);
		}
	    }, taskOwner);
	} finally {
	    group.disconnect(false);
	}
    }

    private void printServiceBindings() throws Exception {
	taskScheduler.runTransactionalTask(new AbstractKernelRunnable() {
	    public void run() {
		System.err.println("Service bindings----------");
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

    /* -- Test Channel.send -- */

    private static byte[] testMessage = new byte[] {'x'};

    public void testChannelSendAllNoTxn() throws Exception {
	Channel channel = createChannel();
	try {
	    channel.send(ByteBuffer.wrap(testMessage));
	    fail("Expected TransactionNotActiveException");
	} catch (TransactionNotActiveException e) {
	    System.err.println(e);
	}
    }

    public void testChannelSendAllClosedChannel() throws Exception {
	final String channelName = "test";
	createChannel(channelName);
	taskScheduler.runTransactionalTask(new AbstractKernelRunnable() {
	    public void run() {
		Channel channel = getChannel(channelName);
		dataService.removeObject(channel);
		try {
		    channel.send(ByteBuffer.wrap(testMessage));
		    fail("Expected IllegalStateException");
		} catch (IllegalStateException e) {
		    System.err.println(e);
		}
	    }
	}, taskOwner);
    }
    
    public void testChannelSend() throws Exception {
	
	String channelName = "test";
	createChannel(channelName);
	ClientGroup group = new ClientGroup(sevenDwarfs);
	try {
	    joinUsers(channelName, sevenDwarfs);
	    sendMessagesToChannel(channelName, group, 5);
	} finally {
	    group.disconnect(false);
	}
    }

    public void testChannelSendMultipleNodes() throws Exception {
	addNodes("one", "two", "three");
	testChannelSend();
    }

    public void testChannelSendToNewMembersAfterAllNodesFail() throws Exception {
	testChannelSendMultipleNodes();
	printServiceBindings();
	System.err.println("simulate watchdog server crash...");
	tearDown(false);
	setUp(false);
	//	Thread.sleep(WAIT_TIME);
	printServiceBindings();
	addNodes("ay", "bee", "sea");
	ClientGroup group = new ClientGroup(sevenDwarfs);
	try {
	    joinUsers("test", sevenDwarfs);
	    sendMessagesToChannel("test", group, 3);
	} finally {
	    group.disconnect(false);
	}
    }

    public void testChannelSendToExistingMembersAfterCoordinatorFailure()
	throws Exception
    {
	String channelName = "talk";
	addNodes("a", "b");
	// create channel on specific node which will be the coordinator node
	createChannel(channelName, "a");
	ClientGroup group = new ClientGroup(sevenDwarfs);
	try {
	    joinUsers(channelName, sevenDwarfs);
	    sendMessagesToChannel(channelName, group, 5);
	    printServiceBindings();
	    // nuke coordinator node
	    System.err.println("shutting down node 'a'");
	    shutdownNode("a");
	    // remove disconnected sessions from client group
	    System.err.println("remove disconnected sessions");
	    ClientGroup disconnectedSessionsGroup =
		group.removeSessionsFromGroup("a");
	    // send messages to sessions that are left
	    System.err.println("send messages to remaining members");
	    sendMessagesToChannel(channelName, group, 2);
	    if (!disconnectedSessionsGroup.isDisconnectedGroup()) {
		fail("expected disconnected client(s)");
	    }
		
	    disconnectedSessionsGroup.checkMembership(channelName, false);
	    disconnectedSessionsGroup.checkChannelSets(false);
	    
	} finally {
	    printServiceBindings();
	    group.disconnect(false);
	}
    }

    /**
     * Shuts down the node with the specified host.
     */
    private void shutdownNode(String host) throws Exception {
	additionalNodes.get(host).shutdown(false);
	additionalNodes.remove(host);
    }
    
    private void sendMessagesToChannel(
	final String channelName, ClientGroup group, int numMessages)
	throws Exception
    {
	try {
	    boolean failed = false;
	    String messageString = "message";

	    for (int i = 0; i < numMessages; i++) {
		final MessageBuffer buf =
		    new MessageBuffer(MessageBuffer.getSize(messageString) +
				      MessageBuffer.getSize(channelName) + 4);
		buf.putString(messageString).
		    putString(channelName).
		    putInt(i);

		System.err.println("Sending message: " +
				   HexDumper.format(buf.getBuffer()));

		taskScheduler.runTransactionalTask(
		    new AbstractKernelRunnable() {
			public void run() {
			    Channel channel = getChannel(channelName);
			    channel.send(ByteBuffer.wrap(buf.getBuffer()));
			}
		    }, taskOwner);
	    }

	    Thread.sleep(5000);
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
	    printServiceBindings();
	    fail("unexpected failure: " + e);
	}
    }

    /* -- Test Channel.close -- */

    public void testChannelCloseNoTxn() throws Exception {
	Channel channel = createChannel();
	try {
	    dataService.removeObject(channel);
	    fail("Expected TransactionNotActiveException");
	} catch (TransactionNotActiveException e) {
	    System.err.println(e);
	}
    }
    
    public void testChannelClose() throws Exception {
	final String channelName = "closeTest";
	createChannel(channelName);
	printServiceBindings();
	taskScheduler.runTransactionalTask(new AbstractKernelRunnable() {
	    public void run() {
		Channel channel = getChannel(channelName);
		dataService.removeObject(channel);
	    }
	}, taskOwner);
	Thread.sleep(100);
	taskScheduler.runTransactionalTask(new AbstractKernelRunnable() {
	    public void run() {
		Channel channel = getChannel(channelName);
		if (getChannel(channelName) != null) {
		    fail("obtained closed channel");
		}
	    }
	}, taskOwner);
	printServiceBindings();
    }

    public void testSessionRemovedFromChannelOnLogout() throws Exception {
	String channelName = "test";
	createChannel(channelName);
	ClientGroup group = new ClientGroup(someUsers);

	try {
	    joinUsers(channelName, someUsers);
	    Thread.sleep(100);
	    group.checkMembership(channelName, true);
	    group.disconnect(true);
	    Thread.sleep(WAIT_TIME); // this is necessary, and unfortunate...
	    group.checkMembership(channelName, false);
	    
	} catch (RuntimeException e) {
	    System.err.println("unexpected failure");
	    e.printStackTrace();
	    printServiceBindings();
	    fail("unexpected failure: " + e);
	} finally {
	    group.disconnect(false);
	}
    }

    public void testChannelSetsRemovedOnLogout() throws Exception {
	String channelName = "test";
	createChannel(channelName);
	ClientGroup group = new ClientGroup(someUsers);
	
	try {
	    joinUsers(channelName, someUsers);
	    Thread.sleep(100);
	    group.checkMembership(channelName, true);
	    group.checkChannelSets(true);
	    printServiceBindings();
	    group.disconnect(true);
	    Thread.sleep(WAIT_TIME); // this is necessary, and unfortunate...
	    group.checkMembership(channelName, false);
	    group.checkChannelSets(false);
	    
	} catch (Exception e) {
	    System.err.println("unexpected failure");
	    e.printStackTrace();
	    fail("unexpected failure: " + e);
	} finally {
	    printServiceBindings();
	    group.disconnect(false);
	}
    }

    public void testSessionsAndChannelSetsRemovedOnRecovery() throws Exception {
	String channelName = "test";
	createChannel(channelName);
	ClientGroup group = new ClientGroup(someUsers);
	
	try {
	    joinUsers(channelName, someUsers);
	    Thread.sleep(100);
	    group.checkMembership(channelName, true);
	    group.checkChannelSets(true);
	    printServiceBindings();

	    // simulate crash
	    System.err.println("simulate watchdog server crash...");
	    tearDown(false);
	    setUp(false);

	    Thread.sleep(WAIT_TIME); // await recovery actions
	    group.checkMembership(channelName, false);
	    group.checkChannelSets(false);
	    printServiceBindings();

	} catch (RuntimeException e) {
	    System.err.println("unexpected failure");
	    e.printStackTrace();
	    fail("unexpected failure: " + e);
	} finally {
	    printServiceBindings();
	    group.disconnect(false);
	}
	
    }

    private class ClientGroup {

	Map<String, DummyClient> clients;

	ClientGroup(String... users) {
	    this(Arrays.asList(users));
	}
	
	ClientGroup(List<String> users) {
	    clients = new HashMap<String, DummyClient>();
	    for (String user : users) {
		DummyClient client = new DummyClient();
		clients.put(user, client);
		client.connect(port);
		client.login(user, "password");
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

	/**
	 * Removes the client sessions on the given host from  this group
	 * and returns a ClientGroup with the removed sessions.
	 */
	ClientGroup removeSessionsFromGroup(String host) {
	    Iterator<String> iter = clients.keySet().iterator();
	    Map<String, DummyClient> removedClients =
		new HashMap<String, DummyClient>();
	    while (iter.hasNext()) {
		String user = iter.next();
		DummyClient client = clients.get(user);
		System.err.println("user: " + user +
				   ", redirectHost: " + client.redirectHost);
		if (host.equals(client.redirectHost)) {
		    iter.remove();
		    removedClients.put(user, client);
		}
	    }
	    return new ClientGroup(removedClients);
	}

	boolean isDisconnectedGroup() {
	    for (DummyClient client : clients.values()) {
		if (client.isConnected()) {
		    return false;
		}
	    }
	    return true;
	}

	void checkMembership(final String name, final boolean isMember)
	    throws Exception
	{
	    taskScheduler.runTransactionalTask(new AbstractKernelRunnable() {
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

	void checkChannelSets(final boolean exists) throws Exception {
	    taskScheduler.runTransactionalTask(new AbstractKernelRunnable() {
		public void run() {
		    for (DummyClient client : clients.values()) {
			long nodeId = client.getNodeId();
			String sessionKey =
			    getChannelSetKey(nodeId, client.getSessionId());
			try {
			    dataService.getServiceBinding(sessionKey);
			    if (!exists) {
				fail("checkChannelSets: set exists: " +
				     client.name);
			    }
			} catch (NameNotBoundException e) {
			    if (exists) {
				fail("checkChannelSets no channel set: " +
				     client.name);
			    }
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

    /* -- other methods -- */

    private ClientSession getClientSession(String name) {
	try {
	    return (ClientSession) dataService.getBinding(name);
	} catch (ObjectNotFoundException e) {
	    return null;
	}
    }

    private static final String CHANNEL_SET_PREFIX =
	"com.sun.sgs.impl.service.channel.set.";
    
    private static String getChannelSetKey(long nodeId, byte[] sessionId) {
	return CHANNEL_SET_PREFIX + nodeId + "." + HexDumper.toHexString(sessionId);
    }

    /**
     * Returns a newly created channel
     */
    private Channel createChannel() throws Exception {
	return createChannel("test");
    }

    private Channel createChannel(String name) throws Exception {
	return createChannel(name,  null);
    }

    private Channel createChannel(String name, String host) throws Exception {
	CreateChannelTask createChannelTask =
	    new CreateChannelTask(name, host);
	runTransactionalTask(createChannelTask, host);
	return createChannelTask.getChannel();
    }

    /**
     * Runs the given transactional task using the task scheduler on the
     * specified host.
     */
    private void runTransactionalTask(AbstractKernelRunnable task, String host)
	throws Exception
    {
	SgsTestNode node =
	    host == null ? serverNode : additionalNodes.get(host);
	if (node == null) {
	    throw new NullPointerException("no node for host: " + host);
	}
	TaskScheduler nodeTaskScheduler =
	    node.getSystemRegistry().getComponent(TaskScheduler.class);
	Identity nodeTaskOwner =
	    node.getProxy().getCurrentOwner();
	nodeTaskScheduler.runTransactionalTask(task, nodeTaskOwner);
    }

    private static class CreateChannelTask extends AbstractKernelRunnable {
	private final String name;
	private final String host;
	private Channel channel;
	
	CreateChannelTask(String name, String host) {
	    this.name = name;
	    this.host = host;
	}
	
	public void run() throws Exception {
	    channel = AppContext.getChannelManager().createChannel(Delivery.RELIABLE);
	    AppContext.getDataManager().setBinding(name, channel);
	}

	Channel getChannel() {
	    return channel;
	}
    }

    private ClientSession getSession(String name) {
	try {
	    return (ClientSession) dataService.getBinding(name);
	} catch (ObjectNotFoundException e) {
	    return null;
	}
    }

    private Channel getChannel(String name) {
	try {
	    return (Channel) dataService.getBinding(name);
	} catch (ObjectNotFoundException e) {
	    return null;
	}
    }

    private Set<ClientSession> getSessions(Channel channel) {
	Set<ClientSession> sessions = new HashSet<ClientSession>();
	Iterator<ClientSession> iter = ChannelUtil.getSessions(channel);
	while (iter.hasNext()) {
	    sessions.add(iter.next());
	}
	return sessions;
    }
    
    /* -- other classes -- */

    /**
     * Dummy identity coordinator for testing purposes.
     */
    private static class DummyIdentityCoordinator implements IdentityCoordinator {
	public Identity authenticateIdentity(IdentityCredentials credentials) {
	    return new DummyIdentity(credentials);
	}
    }
    
    /**
     * Identity returned by the DummyIdentityCoordinator.
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
    private class DummyClient {

	String name;
	CompactId sessionId;
	private Connector<SocketAddress> connector;
	private ConnectionListener listener;
	private Connection connection;
	private boolean connected = false;
	private final Object lock = new Object();
	private boolean loginAck = false;
	private boolean loginSuccess = false;
	private boolean loginRedirect = false;
	private boolean logoutAck = false;
	private boolean joinAck = false;
	private boolean leaveAck = false;
        private boolean awaitGraceful = false;
	private Set<String> channelNames = new HashSet<String>();
	//private String channelName = null;
	//private CompactId channelId = null;
	private String reason;	
	private String redirectHost;
	private final List<MessageInfo> channelMessages =
	    new ArrayList<MessageInfo>();
	private final AtomicLong sequenceNumber = new AtomicLong(0);
	private long nodeId = serverNode.getWatchdogService().getLocalNodeId();

	
	DummyClient() {
	}

	byte[] getSessionId() {
	    return sessionId.getId();
	}

	boolean isConnected() {
	    synchronized (lock) {
		return connected;
	    }
	}

	long getNodeId() {
	    return nodeId;
	}

	DummyClient connect(int port) {
	    if (connected) {
		throw new RuntimeException("DummyClient.connect: already connected");
	    }
	    System.err.println("DummyClient.connect[port=" + port + "]");
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
		System.err.println("DummyClient.connect[" + name + "] throws: " + e);
		e.printStackTrace();
		throw new RuntimeException(
		    "DummyClient.connect[" + name + "]  failed", e);
	    }
	    synchronized (lock) {
		try {
		    if (connected == false) {
			lock.wait(WAIT_TIME * 2);
		    }
		    if (connected != true) {
			throw new RuntimeException(
 			    "DummyClient.connect[" + name + "] timed out");
		    }
		} catch (InterruptedException e) {
		    throw new RuntimeException(
			"DummyClient.connect[" + name + "] timed out", e);
		}
	    }
	    return this;
	}

	void disconnect() {
	    System.err.println("DummyClient.disconnect[" + name + "]");

            synchronized (lock) {
		if (! connected) {
		    return;
		}
                try {
                    connection.close();
		    lock.wait(WAIT_TIME);
                } catch (Exception e) {
                    System.err.println(
                        "DummyClient.disconnect exception:" + e);
                    lock.notifyAll();
		    return;
                } finally {
		    if (connected) {
			reset();
		    }
		}
            }
	}

	void reset() {
	    assert Thread.holdsLock(lock);
	    connected = false;
	    connection = null;
	    loginAck = false;
	    loginSuccess = false;
	    loginRedirect = false;
	    //redirectHost = null;
	}

	DummyClient login(String user, String pass) {
	    synchronized (lock) {
		if (connected == false) {
		    throw new RuntimeException(
			"DummyClient.login[" + name + "] not connected");
		}
	    }
	    this.name = user;

	    MessageBuffer buf =
		new MessageBuffer(2 + MessageBuffer.getSize(user) +
				  MessageBuffer.getSize(pass));
	    buf.putByte(SimpleSgsProtocol.LOGIN_REQUEST).
                putByte(SimpleSgsProtocol.VERSION).
		putString(user).
		putString(pass);
	    loginAck = false;
	    try {
		connection.sendBytes(buf.getBuffer());
	    } catch (IOException e) {
		throw new RuntimeException(e);
	    }
	    String host = null;
	    synchronized (lock) {
		try {
		    if (loginAck == false) {
			lock.wait(WAIT_TIME * 2);
		    }
		    if (loginAck != true) {
			throw new RuntimeException(
			    "DummyClient.login[" + name + "] timed out");
		    }
		    if (loginSuccess) {
			return this;
		    } else if (loginRedirect) {
			host = redirectHost;
		    } else {
			throw new RuntimeException(LOGIN_FAILED_MESSAGE);
		    }
		} catch (InterruptedException e) {
		    throw new RuntimeException(
			"DummyClient.login[" + name + "] timed out", e);
		}
	    }

	    // handle redirected login
	    SgsTestNode node = additionalNodes.get(host);
	    int redirectPort = node.getAppPort();
	    nodeId = node.getWatchdogService().getLocalNodeId();
	    disconnect();
	    connect(redirectPort);
	    return login(user, pass);
	}

	ClientSession getSession() throws Exception {
	    GetSessionTask task = new GetSessionTask(name);
	    taskScheduler.runTransactionalTask(task, taskOwner);
	    return task.getSession();
	}

	/**
	 * Sends a SESSION_MESSAGE.
	 */
	void sendMessage(byte[] message) {
	    checkLoggedIn();

	    MessageBuffer buf =
		new MessageBuffer(1 + message.length);
	    buf.putByte(SimpleSgsProtocol.SESSION_MESSAGE).
		putBytes(message);
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

		    if (! channelNames.contains(channelToJoin)) {
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

		    if (channelNames.contains(channelToLeave)) {
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
                MessageBuffer buf = new MessageBuffer(1);
                buf.putByte(SimpleSgsProtocol.LOGOUT_REQUEST);
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
                                "DummyClient.disconnect[" + name + "] timed out");
                        }
                    } catch (InterruptedException e) {
                        throw new RuntimeException(
                            "DummyClient.disconnect[" + name + "] timed out", e);
                    }
                }
            }
	    disconnect();
	}

	private class Listener implements ConnectionListener {

	    List<byte[]> messageList = new ArrayList<byte[]>();
	    
            /** {@inheritDoc} */
	    public void bytesReceived(Connection conn, byte[] buffer) {
		if (connection != conn) {
		    System.err.println(
			"[" + name + "] bytesReceived: " +
			"wrong handle, got:" +
			conn + ", expected:" + connection);
		    return;
		}

		MessageBuffer buf = new MessageBuffer(buffer);

		processAppProtocolMessage(buf);
	    }

	    private void processAppProtocolMessage(MessageBuffer buf) {

		byte opcode = buf.getByte();

		switch (opcode) {

		case SimpleSgsProtocol.LOGIN_SUCCESS:
		    sessionId = CompactId.getCompactId(buf);
		    synchronized (lock) {
			loginAck = true;
			loginSuccess = true;
			System.err.println("[" + name + "] login succeeded");
			lock.notifyAll();
		    }
		    break;
		    
		case SimpleSgsProtocol.LOGIN_FAILURE:
		    reason = buf.getString();
		    synchronized (lock) {
			loginAck = true;
			loginSuccess = false;
			System.err.println("[" + name + "] login failed: " +
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

		case SimpleSgsProtocol.LOGIN_REDIRECT:
		    redirectHost = buf.getString();
		    synchronized (lock) {
			loginAck = true;
			loginRedirect = true;
			System.err.println("login redirected: " + name +
					   ", host:" + redirectHost);
			lock.notifyAll();
		    } break;

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

		default:
		    System.err.println(	
			"[" + name + "] processAppProtocolMessage: unknown op code: " +
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
		    reset();
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
	final String channelName;
	final int seq;

	MessageInfo(String channelName, int seq) {
	    this.channelName = channelName;
	    this.seq = seq;
	}
    }

    public static class DummyAppListener implements AppListener, Serializable {

	private final static long serialVersionUID = 1L;

        /** {@inheritDoc} */
	public ClientSessionListener loggedIn(ClientSession session) {

	    DummyClientSessionListener listener =
		new DummyClientSessionListener(session);
	    DataManager dataManager = AppContext.getDataManager();
	    dataManager.setBinding(session.getName(), session);
	    System.err.println("DummyAppListener.loggedIn: session:" + session);
	    return listener;
	}

        /** {@inheritDoc} */
	public void initialize(Properties props) {
	}
    }

    private static class DummyClientSessionListener
	implements ClientSessionListener, Serializable, ManagedObject
    {
	private final static long serialVersionUID = 1L;
	private final String name;
	boolean receivedDisconnectedCallback = false;
	boolean wasGracefulDisconnect = false;
	
	private final ManagedReference<ClientSession> sessionRef;
	
	DummyClientSessionListener(ClientSession session) {
	    DataManager dataManager = AppContext.getDataManager();
	    this.sessionRef = dataManager.createReference(session);
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
	    } else if (action.equals("message")) {
		String channelName = buf.getString();
		System.err.println("DummyClientSessionListener: send request, " +
				   "channel name: " + channelName +
				   ", user: " + name);
		Channel channel =
		    (Channel) dataManager.getBinding(channelName);
		channel.send(message.asReadOnlyBuffer());
	    }
	}
    }

    private class GetSessionTask extends AbstractKernelRunnable {

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
}
