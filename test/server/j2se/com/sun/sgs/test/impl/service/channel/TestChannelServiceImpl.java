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
import com.sun.sgs.auth.IdentityCoordinator;
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
import com.sun.sgs.impl.sharedutil.CompactId;
import com.sun.sgs.impl.sharedutil.HexDumper;
import com.sun.sgs.impl.sharedutil.MessageBuffer;
import com.sun.sgs.impl.util.AbstractKernelRunnable;
import com.sun.sgs.impl.util.BoundNamesUtil;
import com.sun.sgs.io.Connection;
import com.sun.sgs.io.ConnectionListener;
import com.sun.sgs.io.Connector;
import com.sun.sgs.kernel.TaskOwner;
import com.sun.sgs.kernel.TaskScheduler;
import com.sun.sgs.protocol.simple.SimpleSgsProtocol;
import com.sun.sgs.service.ClientSessionService;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
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
    
    private static final String CHANNEL_NAME = "three stooges";
    
    private static final int WAIT_TIME = 2000;
    
    private static final String LOGIN_FAILED_MESSAGE = "login failed";

    private static final Set<CompactId> ALL_MEMBERS = new HashSet<CompactId>();
    
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
    private TaskOwner taskOwner;

    /** The shared data service. */
    private DataServiceImpl dataService;

    /** The channel service on the server node. */
    private ChannelServiceImpl channelService;

    /** The client session service on the server node. */
    private ClientSessionServiceImpl sessionService;

    /** True if test passes. */
    private boolean passed;

    /** The test clients, keyed by user name. */
    private static Map<String, DummyClient> dummyClients;

    /** The listen port for the client session service. */
    private int port;

    /** The node ID for the local node. */
    private long serverNodeId;

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
		new DummyTransactionProxy());
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
    }

    /* -- Test createChannel -- */

    public void testCreateChannelNullName() throws Exception {
	taskScheduler.runTransactionalTask(new AbstractKernelRunnable() {
	    public void run() {
		try {
		    channelService.createChannel(
			null, new DummyChannelListener(), Delivery.RELIABLE);
		    fail("Expected NullPointerException");
		} catch (NullPointerException e) {
		    System.err.println(e);
		}
	    }
	}, taskOwner);
    }

    public void testCreateChannelNullListener() throws Exception {
	taskScheduler.runTransactionalTask(new AbstractKernelRunnable() {
	    public void run() {
		try {
		    channelService.createChannel(
			"foo", null, Delivery.RELIABLE);
		    System.err.println("channel created");
		} catch (NullPointerException e) {
		    fail("Got NullPointerException");
		}
	    }
	}, taskOwner);
    }

    public void testCreateChannelNonSerializableListener() throws Exception {
	taskScheduler.runTransactionalTask(new AbstractKernelRunnable() {
	    public void run() {
		try {
		    channelService.createChannel(
			"foo", new NonSerializableChannelListener(),
			Delivery.RELIABLE);
		    fail("Expected IllegalArgumentException");
		} catch (IllegalArgumentException e) {
		    System.err.println(e);
		}
	    }
	}, taskOwner);
    }

    public void testCreateChannelNoTxn() throws Exception { 
	try {
	    channelService.createChannel(
		"noTxn", new DummyChannelListener(), Delivery.RELIABLE);
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
	    channelService.getChannel("foo");
	    fail("Expected NameNotBoundException");
	} catch (NameNotBoundException e) {
	    System.err.println(e);
	}
    }
    */

    public void testCreateChannelExistentChannel() throws Exception {
	final String channelName = "exist"; 
	createChannel(channelName);
	taskScheduler.runTransactionalTask(new AbstractKernelRunnable() {
	    public void run() {
		try {
		    channelService.createChannel(
			channelName, new DummyChannelListener(), Delivery.RELIABLE);
		    fail("Expected NameExistsException");
		} catch (NameExistsException e) {
		    System.err.println(e);
		}
	    }
	}, taskOwner);
    }

    /* -- Test getChannel -- */

    public void testGetChannelNullName() throws Exception {
	taskScheduler.runTransactionalTask(new AbstractKernelRunnable() {
	    public void run() {
		try {
		    channelService.getChannel(null);
		    fail("Expected NullPointerException");
		} catch (NullPointerException e) {
		    System.err.println(e);
		}
	    }
	}, taskOwner);
    }

    public void testGetChannelNonExistentName() throws Exception {
	taskScheduler.runTransactionalTask(new AbstractKernelRunnable() {
	    public void run() {
		try {
		    channelService.getChannel("non-existent");
		    fail("Expected NameNotBoundException");
		} catch (NameNotBoundException e) {
		    System.err.println(e);
		}
	    }
	}, taskOwner);
    }

    public void testGetChannelNoTxn() throws Exception {
	try {
	    channelService.getChannel("noTxn");
	    fail("Expected TransactionNotActiveException");
	} catch (TransactionNotActiveException e) {
	    System.err.println(e);
	}
    }

    public void testCreateAndGetChannelSameTxn() throws Exception {
	taskScheduler.runTransactionalTask(new AbstractKernelRunnable() {
	    public void run() {
		String channelName = "foo";
		Channel channel1 =
		    channelService.createChannel(
			channelName, new DummyChannelListener(),
			Delivery.RELIABLE);
		Channel channel2 =
		    channelService.getChannel(channelName);
		if (channel1 != channel2) {
		    fail("channels are not equal");
		}
		System.err.println("Channels are equal");
	    }
	}, taskOwner);
    }
	
    public void testCreateAndGetChannelDifferentTxn() throws Exception {
	final String channelName = "testy";
	final Channel channel1 = createChannel(channelName);
	
	taskScheduler.runTransactionalTask(new AbstractKernelRunnable() {
	    public void run() {
		Channel channel2 = channelService.getChannel(channelName);
		if (channel1 == channel2) {
		    fail("channels are equal");
		}
		System.err.println("Channels are not equal");
	    }
	}, taskOwner);
    }

    /* -- Test Channel serialization -- */

    public void testChannelWriteReadObject() throws Exception {
	taskScheduler.runTransactionalTask(new AbstractKernelRunnable() {
	    public void run() throws Exception {
		Channel savedChannel =
		    channelService.createChannel(
			"readWriteTest", new DummyChannelListener(),
			Delivery.RELIABLE);
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

    /* -- Test Channel.getName -- */

    public void testChannelGetNameNoTxn() throws Exception {
	Channel channel = createChannel();
	try {
	    channel.getName();
	    fail("Expected TransactionNotActiveException");
	} catch (TransactionNotActiveException e) {
	    System.err.println(e);
	}
    }

    public void testChannelGetNameMismatchedTxn() throws Exception {
	final Channel channel = createChannel();
	taskScheduler.runTransactionalTask(new AbstractKernelRunnable() {
	    public void run() {
		try {
		    channel.getName();
		    fail("Expected TransactionNotActiveException");
		} catch (TransactionNotActiveException e) {
		    System.err.println(e);
		}
	    }
	}, taskOwner);
    }
    
    public void testChannelGetName() throws Exception {
	final String channelName = "name";
	createChannel(channelName);
	taskScheduler.runTransactionalTask(new AbstractKernelRunnable() {
	    public void run() {
		Channel channel = channelService.getChannel(channelName);
		String nameAfterCreation = channel.getName();
		if (! channelName.equals(nameAfterCreation)) {
		    fail("Expected: " + channelName + ", got: " +
			 nameAfterCreation);
		}
		System.err.println("Channel names are equal");
	    }
	}, taskOwner);
    }

    public void testChannelGetNameClosedChannel() throws Exception {
	final String channelName = "name";
	createChannel(channelName);
	taskScheduler.runTransactionalTask(new AbstractKernelRunnable() {
	    public void run() {
		Channel channel = channelService.getChannel(channelName);
		channel.close();
		String nameAfterCreation = channel.getName();
		if (!channelName.equals(nameAfterCreation)) {
		    fail("Expected: " + channelName + ", got: " +
			 nameAfterCreation);
		}
		System.err.println("Got channel name on closed channel");
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
		    Channel channel = channelService.createChannel(
			delivery.toString(), new DummyChannelListener(),
			delivery);
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
		    Channel channel = channelService.createChannel(
			delivery.toString(), new DummyChannelListener(),
			delivery);
		    channel.close();
		    if (!delivery.equals(channel.getDeliveryRequirement())) {
			fail("Expected: " + delivery + ", got: " +
			     channel.getDeliveryRequirement());
		    }
		}
		System.err.println("Got delivery requirement on close channel");
	    }
	}, taskOwner);
    }

    /* -- Test Channel.join -- */

    public void testChannelJoinNoTxn() throws Exception {
	Channel channel = createChannel();
	try {
	    channel.join(new DummyClientSession("dummy"), null);
	    fail("Expected TransactionNotActiveException");
	} catch (TransactionNotActiveException e) {
	    System.err.println(e);
	}
    }

    public void testChannelJoinClosedChannel() throws Exception {
	taskScheduler.runTransactionalTask(new AbstractKernelRunnable() {
	    public void run() {
		Channel channel =
		    channelService.createChannel(
			"test", new DummyChannelListener(), Delivery.RELIABLE);
		channel.close();
		try {
		    channel.join(new DummyClientSession("dummy"), null);
		    fail("Expected IllegalStateException");
		} catch (IllegalStateException e) {
		    System.err.println(e);
		}
	    }
	}, taskOwner);
    }

    public void testChannelJoinNullClientSession() throws Exception {
	taskScheduler.runTransactionalTask(new AbstractKernelRunnable() {
	    public void run() {
		Channel channel =
		    channelService.createChannel(
			"test", new DummyChannelListener(), Delivery.RELIABLE);
		try {
		    channel.join(null, new DummyChannelListener());
		    fail("Expected NullPointerException");
		} catch (NullPointerException e) {
		    System.err.println(e);
		}
	    }
	}, taskOwner);
    }

    public void testChannelJoinNonSerializableListener() throws Exception {
	taskScheduler.runTransactionalTask(new AbstractKernelRunnable() {
	    public void run() {
		Channel channel =
		    channelService.createChannel(
			"test", new DummyChannelListener(), Delivery.RELIABLE);
		try {
		    channel.join(new DummyClientSession("dummy"),
				 new NonSerializableChannelListener());
		    fail("Expected IllegalArgumentException");
		} catch (IllegalArgumentException e) {
		    System.err.println(e);
		}
	    }
	}, taskOwner);
    }

    public void testChannelJoin() throws Exception {
	String channelName = "joinTest";
	List<String> users =
	    Arrays.asList(new String[] { "foo", "bar", "baz" });
	createChannel(channelName);
	ClientGroup group = new ClientGroup(users);
	
	try {
	    joinUsers(channelName, users);
	    checkUsersJoined(channelName, users);

	} finally {
	    group.disconnect(false);
	}
    }

    public void testChannelJoinWithListenerReferringToChannel()
	throws Exception
    {
	final String channelName = "joinTest";
	final List<String> users =
	    Arrays.asList(new String[] { "foo", "bar", "baz" });
	createChannel(channelName);
	ClientGroup group = new ClientGroup(users);
	
	try {
	    taskScheduler.runTransactionalTask(new AbstractKernelRunnable() {
		public void run() {
		    Channel channel = channelService.getChannel(channelName);
		    for (String user : users) {
			ClientSession session =
			    dataService.getBinding(user, ClientSession.class);
			channel.join(session, new DummyChannelListener(channel));
		    }
		}
	    }, taskOwner);

	    checkUsersJoined(channelName, users);
		    
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
		Channel channel = channelService.getChannel(channelName);
		for (String user : users) {
		    ClientSession session =
			dataService.getBinding(user, ClientSession.class);
		    channel.join(session, null);
		}
	    }
	}, taskOwner);
    }
    
    private void checkUsersJoined(
	final String channelName, final List<String> users)
	throws Exception
    {
	taskScheduler.runTransactionalTask(new AbstractKernelRunnable() {
	    public void run() {
		Channel channel = channelService.getChannel(channelName);
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
	try {
	    channel.leave(new DummyClientSession("dummy"));
	    fail("Expected TransactionNotActiveException");
	} catch (TransactionNotActiveException e) {
	    System.err.println(e);
	}
    }

    public void testChannelLeaveMismatchedTxn() throws Exception {
	final String channelName = "test";
	final Channel channel = createChannel(channelName);
	taskScheduler.runTransactionalTask(new AbstractKernelRunnable() {
	    public void run() {
		try {
		    channel.leave(new DummyClientSession("dummy"));
		    fail("Expected TransactionNotActiveException");
		} catch (TransactionNotActiveException e) {
		    System.err.println(e);
		}
	    }
	}, taskOwner);
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
		    Channel channel = channelService.getChannel(channelName);
		    ClientSession session =
			dataService.getBinding(user, ClientSession.class);
		    channel.join(session, null);
		    channel.close();
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
		    channelService.createChannel(
			"test", new DummyChannelListener(), Delivery.RELIABLE);
		try {
		    channel.leave(null);
		    fail("Expected NullPointerException");
		} catch (NullPointerException e) {
		    System.err.println(e);
		}
	    }
	}, taskOwner);
    }

    public void testChannelLeaveSessionNotJoined() throws Exception {
	final String channelName = "leaveTest";
	List<String> users =
	    Arrays.asList(new String[] { "foo", "bar", "baz" });
	createChannel(channelName);
	ClientGroup group = new ClientGroup(users);
	
	try {
	    taskScheduler.runTransactionalTask(new AbstractKernelRunnable() {
		public void run() {
		    Channel channel = channelService.getChannel(channelName);
	
		    ClientSession foo =
			dataService.getBinding("foo", ClientSession.class);
		    channel.join(foo, new DummyChannelListener(channel));

		    try {
			ClientSession bar =
			    dataService.getBinding("bar", ClientSession.class);
			channel.leave(bar);
			System.err.println("leave of non-member session returned");
			
		    } catch (Exception e) {
			System.err.println(e);
			fail("test failed with exception: " + e);
		    }
		    
		    Set<ClientSession> sessions = getSessions(channel);
		    if (sessions.size() != 1) {
			fail("Expected 1 session, got " +
			     sessions.size());
		    }

		    if (! sessions.contains(foo)) {
			fail("Expected session: " + foo);
		    }
		    channel.close();
		}
 	    }, taskOwner);
	    
	} finally {
	    group.disconnect(false);
	}
    }
    
    public void testChannelLeave() throws Exception {
	final String channelName = "leaveTest";
	List<String> users =
	    Arrays.asList(new String[] { "foo", "bar", "baz" });
	createChannel(channelName);
	ClientGroup group = new ClientGroup(users);
	
	try {
	    joinUsers(channelName, users);
	    checkUsersJoined(channelName, users);

	    taskScheduler.runTransactionalTask(new AbstractKernelRunnable() {
		public void run() {
		    Channel channel = channelService.getChannel(channelName);
		    for (ClientSession session : getSessions(channel)) {
			channel.leave(session);
			if (getSessions(channel).contains(session)) {
			    fail("Failed to remove session: " + session);
			}
		    }

		    int numJoinedSessions = getSessions(channel).size();
		    if (numJoinedSessions != 0) {
			fail("Expected no sessions, got " + numJoinedSessions);
		    }
		    System.err.println("All sessions left");
		    
		    channel.close();
		}
	    }, taskOwner);
			

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
		    channelService.createChannel(
			"test", new DummyChannelListener(), Delivery.RELIABLE);
		channel.close();
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
		    channelService.createChannel(
			"test", new DummyChannelListener(), Delivery.RELIABLE);
		channel.leaveAll();
		System.err.println(
		    "leaveAll succeeded with no sessions joined");
	    }
	}, taskOwner);
    }
    
    public void testChannelLeaveAll() throws Exception {
	final String channelName = "leaveAllTest";
	List<String> users =
	    Arrays.asList(new String[] { "foo", "bar", "baz" });
	createChannel(channelName);
	ClientGroup group = new ClientGroup(users);
	
	try {
	    joinUsers(channelName, users);
	    checkUsersJoined(channelName, users);

	    taskScheduler.runTransactionalTask(new AbstractKernelRunnable() {
		public void run() {
		    Channel channel = channelService.getChannel(channelName);
		    channel.leaveAll();
		    int numJoinedSessions = getSessions(channel).size();
		    if (numJoinedSessions != 0) {
			fail("Expected no sessions, got " + numJoinedSessions);
		    }
		    System.err.println("All sessions left");
		    channel.close();
		}
	    }, taskOwner);
	    
	} finally {
	    group.disconnect(false);
	}
    }

    /* -- Test Channel.hasSessions -- */

    public void testChannelHasSessionsNoTxn() throws Exception {
	Channel channel = createChannel();
	try {
	    channel.hasSessions();
	    fail("Expected TransactionNotActiveException");
	} catch (TransactionNotActiveException e) {
	    System.err.println(e);
	}
    }

    public void testChannelHasSessionsClosedChannel() throws Exception {
	final String channelName = "test";
	createChannel(channelName);
	taskScheduler.runTransactionalTask(new AbstractKernelRunnable() {
	    public void run() {
		Channel channel = channelService.getChannel(channelName);
		channel.close();
		try {
		    channel.hasSessions();
		    fail("Expected IllegalStateException");
		} catch (IllegalStateException e) {
		    System.err.println(e);
		}
	    }
	}, taskOwner);
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

    public void testChannelHasSessionsNoSessionsJoined() throws Exception {
	final String channelName = "test";
	createChannel(channelName);
	taskScheduler.runTransactionalTask(new AbstractKernelRunnable() {
	    public void run() {
		Channel channel = channelService.getChannel(channelName);
		if (channel.hasSessions()) {
		    fail("Expected hasSessions to return false");
		}
		System.err.println("hasSessions returned false");
	    }
	}, taskOwner);
    }
    
    public void testChannelHasSessionsSessionsJoined() throws Exception {
	final String channelName = "leaveTest";
	List<String> users =
	    Arrays.asList(new String[] { "foo", "bar", "baz" });
	createChannel(channelName);
	ClientGroup group = new ClientGroup(users);
	
	try {
	    joinUsers(channelName, users);
	    checkUsersJoined(channelName, users);

	    taskScheduler.runTransactionalTask(new AbstractKernelRunnable() {
		public void run() {
		    Channel channel = channelService.getChannel(channelName);
		    if (!channel.hasSessions()) {
			fail("Expected hasSessions to return true");
		    }
		    System.err.println("hasSessions returned true");
		    channel.close();
		}
	    }, taskOwner);

	} finally {
	    group.disconnect(false);
	}
    }

    /* -- Test Channel.getSessions -- */

    public void testChannelGetSessionsNoTxn() throws Exception {
	Channel channel = createChannel();
	try {
	    getSessions(channel);
	    fail("Expected TransactionNotActiveException");
	} catch (TransactionNotActiveException e) {
	    System.err.println(e);
	}
    }

    public void testChannelGetSessionsClosedChannel() throws Exception {
	final String channelName = "test";
	createChannel(channelName);
	taskScheduler.runTransactionalTask(new AbstractKernelRunnable() {
	    public void run() {
		Channel channel = channelService.getChannel(channelName);
		channel.close();
		try {
		    getSessions(channel);
		    fail("Expected IllegalStateException");
		} catch (IllegalStateException e) {
		    System.err.println(e);
		}
	    }
	}, taskOwner);
    }

    public void testChannelGetSessionsNoSessionsJoined() throws Exception {
	final String channelName = "test";
	createChannel(channelName);
	taskScheduler.runTransactionalTask(new AbstractKernelRunnable() {
	    public void run() {
		Channel channel = channelService.getChannel(channelName);

		if (!getSessions(channel).isEmpty()) {
		    fail("Expected no sessions");
		}
		System.err.println("No sessions joined");
	    }
	}, taskOwner);
    }
    
    public void testChannelGetSessionsSessionsJoined() throws Exception {
	testChannelJoin();
    }

    /* -- Test Channel.send (to all) -- */

    private static byte[] testMessage = new byte[] {'x'};

    public void testChannelSendAllNoTxn() throws Exception {
	Channel channel = createChannel();
	try {
	    channel.send(testMessage);
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
		Channel channel = channelService.getChannel(channelName);
		channel.close();
		try {
		    channel.send(testMessage);
		    fail("Expected IllegalStateException");
		} catch (IllegalStateException e) {
		    System.err.println(e);
		}
	    }
	}, taskOwner);
    }

    public void testChannelSendAll() throws Exception {
	final String channelName = "test";
	createChannel(channelName);
	ClientGroup group = new ClientGroup(sevenDwarfs);
	
	try {
	    boolean failed = false;
	    group.join(channelName);
	    String messageString = "from server";
	    final MessageBuffer buf =
		new MessageBuffer(MessageBuffer.getSize(messageString) + 4);
	    buf.putString(messageString).
		putInt(0);

	    taskScheduler.runTransactionalTask(new AbstractKernelRunnable() {
		public void run() {
		    Channel channel = channelService.getChannel(channelName);
		    channel.send(buf.getBuffer());
		}
	    }, taskOwner);

	    for (DummyClient client : group.getClients()) {
		MessageInfo info = client.nextChannelMessage();
		if (info == null) {
		    failed = true;
		    System.err.println(
 			"member:" + client.name + " did not get message");
		} else {
		    MessageBuffer infoBuf = new MessageBuffer(info.message);
		    String message = infoBuf.getString();
		    if (! message.equals(messageString)) {
			fail("Got message: " + message + ", Expected: " +
			     messageString);
		    }
		    System.err.println(
			client.name + " got channel message: " + message);
		}
	    }

	    if (failed) {
		fail("test failed");
	    }
	    
	} catch (RuntimeException e) {
	    System.err.println("unexpected failure");
	    e.printStackTrace();
	    printServiceBindings();
	    fail("unexpected failure: " + e);
	} finally {
	    group.disconnect(false);
	}
    }

    public void testChannelSendAllMultipleNodes() throws Exception {
	addNodes("one", "two", "three");
	testChannelSendAll();
    }
    
    /* -- Test Channel.send (one recipient) -- */

    public void testChannelSendToOneNoTxn() throws Exception {
	Channel channel = createChannel();
	try {
	    channel.send(new DummyClientSession("dummy"), testMessage);
	    fail("Expected TransactionNotActiveException");
	} catch (TransactionNotActiveException e) {
	    System.err.println(e);
	}
    }

    public void testChannelSendToOneClosedChannel() throws Exception {
	final String channelName = "test";
	createChannel("test");
	taskScheduler.runTransactionalTask(new AbstractKernelRunnable() {
	    public void run() {
		Channel channel = channelService.getChannel(channelName);
		channel.close();
		try {
		    channel.send(new DummyClientSession("dummy"), testMessage);
		    fail("Expected IllegalStateException");
		} catch (IllegalStateException e) {
		    System.err.println(e);
		}
	    }
	}, taskOwner);
    }
    
    /* -- Test Channel.send (multiple recipients) -- */

    public void testChannelSendToMultiplelNoTxn() throws Exception {
	Channel channel = createChannel();
	try {
	    channel.send(new HashSet<ClientSession>(), testMessage);
	    fail("Expected TransactionNotActiveException");
	} catch (TransactionNotActiveException e) {
	    System.err.println(e);
	}
    }

    public void testChannelSendToMultipleClosedChannel() throws Exception {
	final String channelName = "test";
	createChannel(channelName);
	taskScheduler.runTransactionalTask(new AbstractKernelRunnable() {
	    public void run() {
		Channel channel = channelService.getChannel(channelName);
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
	}, taskOwner);
    }


    /* -- Test Channel.close -- */

    public void testChannelCloseNoTxn() throws Exception {
	Channel channel = createChannel();
	try {
	    channel.close();
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
		Channel channel = channelService.getChannel(channelName);
		channel.close();
		try {
		    channelService.getChannel(channelName);
		    fail("Expected NameNotBoundException");
		} catch (NameNotBoundException e) {
		    System.err.println(e);
		}
	    }
	}, taskOwner);
	printServiceBindings();
    }

    public void testChannelCloseTwice() throws Exception {
	final String channelName = "closeTest";
	createChannel(channelName);
	
	taskScheduler.runTransactionalTask(new AbstractKernelRunnable() {
	    public void run() {
		Channel channel = channelService.getChannel(channelName);
		channel.close();
		try {
		    channelService.getChannel(channelName);
		    fail("Expected NameNotBoundException");
		} catch (NameNotBoundException e) {
		    System.err.println(e);
		}
		channel.close();
		System.err.println("Channel closed twice");
	    }
	}, taskOwner);
    }
    
    public void testChannelJoinReceivedByClient() throws Exception {
	String channelName = "test";
	createChannel(channelName);
	ClientGroup group = new ClientGroup("moe", "larry", "curly");

	try {
	    group.join(channelName);
	    group.disconnect(true);
	    
	} catch (RuntimeException e) {
	    System.err.println("unexpected failure");
	    e.printStackTrace();
	    printServiceBindings();
	    fail("unexpected failure: " + e);
	} finally {
	    group.disconnect(false);
	}
    }
    
    public void testChannelLeaveReceivedByClient() throws Exception {
	String channelName = "test";
	createChannel(channelName);
	ClientGroup group = new ClientGroup("moe", "larry", "curly");

	try {
	    group.join(channelName);
	    group.leave(channelName);
	    group.disconnect(true);
	    
	} catch (RuntimeException e) {
	    System.err.println("unexpected failure");
	    e.printStackTrace();
	    printServiceBindings();
	    fail("unexpected failure: " + e);
	} finally {
	    group.disconnect(false);
	}
    }

    public void testSessionRemovedFromChannelOnLogout() throws Exception {
	String channelName = "test";
	createChannel(channelName);
	ClientGroup group = new ClientGroup("moe", "larry", "curly");

	try {
	    group.join(channelName);
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
	ClientGroup group = new ClientGroup("moe", "larry", "curly");
	
	try {
	    group.join(channelName);
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
	ClientGroup group = new ClientGroup("moe", "larry", "curly");
	
	try {
	    group.join(channelName);
	    group.checkMembership(channelName, true);
	    group.checkChannelSets(true);
	    printServiceBindings();

	    // simulate crash
	    System.err.println("simulate watchdog server crash...");
	    tearDown(false);
	    setUp(false);
	    addNodes("recoveryNode");

	    Thread.sleep(WAIT_TIME); // this is necessary, and unfortunate...
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

    public void testChannelSendRequestWithSingleRecipient() throws Exception {
	String channelName = "test";
	createChannel(channelName);
	ClientGroup group = new ClientGroup("moe", "larry", "curly");

	try {
	    group.join(channelName);
	    DummyClient moe = group.getClient("moe");
	    DummyClient larry = group.getClient("larry");
	    DummyClient curly = group.getClient("curly");
	    CompactId channelId = moe.channelNameToId.get(channelName);
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
		System.err.println("receiver got message from " + senderName +
				   " with sequence number " + num);
		if (!senderName.equals("moe")) {
		    fail("got sender name " + senderName + ", expected " +
			 "moe");
		}
		if (num != nextNum) {
		    fail("got number " + num + ", expected " + nextNum);
		}
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
    
    public void testChannelSendRequestWithSingleRecipientMultipleNodes()
	throws Exception
    {
	addNodes("one", "two", "three");
	testChannelSendRequestWithSingleRecipient();
    }
    
    public void testChannelSendRequestToAllChannelMembers() throws Exception {
	String channelName = "test";
	createChannel(channelName);
	ClientGroup group = new ClientGroup("moe", "larry", "curly");

	try {
	    boolean failed = false;
	    group.join(channelName);
	    String senderName = "moe";
	    DummyClient sender = group.getClient(senderName);
	    CompactId channelId = sender.channelNameToId.get(channelName);
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
			    "TEST FAILED: sender[" + senderName +
			    "] recieved message");
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
		fail("test failed: see output");
	    }
	    
	} catch (RuntimeException e) {
	    System.err.println("unexpected failure");
	    e.printStackTrace();
	    fail("unexpected failure: " + e);
	} finally {
	    group.disconnect(false);
	}
    }

    public void testChannelSendRequestToAllChannelMembersMultipleNodes()
	throws Exception
    {
	addNodes("one", "two", "three");
	testChannelSendRequestToAllChannelMembers();
    }

    public void testChannelSendRequestFromNonMemberSession() throws Exception {
	String channelName = "test";
	createChannel(channelName);
	ClientGroup group = new ClientGroup("moe", "larry");
	ClientGroup outcast = new ClientGroup("curly");

	try {
	    boolean failed = false;
	    group.join(channelName);
	    DummyClient moe = group.getClient("moe");
	    CompactId channelId = moe.channelNameToId.get(channelName);
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

    public void testChannelSendRequestFromNonMemberSessionMultipleNodes()
	throws Exception
    {
	addNodes("one", "two", "three");
	testChannelSendRequestFromNonMemberSession();
    }

    private class ClientGroup {

	final List<String> users;
	final Map<String, DummyClient> clients =
	    new HashMap<String, DummyClient>();
	// FIXME: This is a kludge for now
	final long nodeId = serverNodeId;

	ClientGroup(String... users) {
	    this(Arrays.asList(users));
	}
	
	ClientGroup(List<String> users) {
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

	void checkMembership(final String name, final boolean isMember)
	    throws Exception
	{
	    taskScheduler.runTransactionalTask(new AbstractKernelRunnable() {
		public void run() {
		    Channel channel = channelService.getChannel(name);
		    Set<ClientSession> sessions = getSessions(channel);
		    for (DummyClient client : clients.values()) {

			ClientSession session =
			    sessionService.getClientSession(
				client.getSessionId());

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
			String sessionKey =
			    getChannelSetKey(nodeId, client.getSessionId());
			try {
			    dataService.getServiceBinding(
 				sessionKey, ManagedObject.class);
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
	CreateChannelTask createChannelTask =
	    new CreateChannelTask(name, new DummyChannelListener());
	taskScheduler.runTransactionalTask(createChannelTask, taskOwner);
	return createChannelTask.getChannel();
    }

    private class CreateChannelTask extends AbstractKernelRunnable {
	private final String name;
	private final ChannelListener listener;
	private Channel channel;
	
	CreateChannelTask(String name, ChannelListener listener) {
	    this.name = name;
	    this.listener = listener;
	}
	
	public void run() throws Exception {
	    channel =
		channelService.createChannel(name, listener, Delivery.RELIABLE);
	}

	Channel getChannel() {
	    return channel;
	}
    }

    private Set<ClientSession> getSessions(Channel channel) {
	Set<ClientSession> sessions = new HashSet<ClientSession>();
	Iterator<ClientSession> iter = channel.getSessions();
	while (iter.hasNext()) {
	    sessions.add(iter.next());
	}
	return sessions;
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
	private Map<CompactId, String> channelIdToName =
	    new HashMap<CompactId, String>();
	private Map<String, CompactId> channelNameToId =
	    new HashMap<String, CompactId>();
	//private String channelName = null;
	//private CompactId channelId = null;
	private String reason;	
	private String redirectHost;
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
	    redirectHost = null;
	}

	void login(String user, String pass) {
	    synchronized (lock) {
		if (connected == false) {
		    throw new RuntimeException(
			"DummyClient.login[" + name + "] not connected");
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
			return;
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
	    int redirectPort =
		(additionalNodes.get(host)).getAppPort();
	    disconnect();
	    connect(redirectPort);
	    login(user, pass);
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

		byte version = buf.getByte();
		if (version != SimpleSgsProtocol.VERSION) {
		    System.err.println(
			"[" + name + "] bytesReceived: got version: " +
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
			"[" + name + "] bytesReceived: got service id: " +
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

		case SimpleSgsProtocol.SESSION_MESSAGE:
                    buf.getLong(); // FIXME sequence number
		    byte[] message = buf.getBytes(buf.getUnsignedShort());
		    synchronized (lock) {
			messageList.add(message);
			System.err.println("[" + name + "] message received: " + message);
			lock.notifyAll();
		    }
		    break;

		default:
		    System.err.println(	
			"[" + name + "] processAppProtocolMessage: unknown op code: " +
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
			System.err.println(
			    name + ": got join protocol message, channel: " +
			    channelName);
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
			System.err.println(
			    name + ": got leave protocol message, channel: " +
			    channelName);
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

    public static class DummyAppListener implements AppListener, Serializable {

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
	    dataManager.setBinding(session.getName(), (ManagedObject) session);
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
