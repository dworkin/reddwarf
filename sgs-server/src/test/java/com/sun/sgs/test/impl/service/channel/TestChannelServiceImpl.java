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
import com.sun.sgs.app.ResourceUnavailableException;
import com.sun.sgs.app.TransactionNotActiveException;
import com.sun.sgs.auth.Identity;
import com.sun.sgs.impl.io.SocketEndpoint;
import com.sun.sgs.impl.io.TransportType;
import com.sun.sgs.impl.kernel.StandardProperties;
import com.sun.sgs.impl.service.channel.ChannelServiceImpl;
import com.sun.sgs.impl.service.session.ClientSessionWrapper;
import com.sun.sgs.impl.sharedutil.HexDumper;
import com.sun.sgs.impl.sharedutil.MessageBuffer;
import com.sun.sgs.impl.util.AbstractService.Version;
import com.sun.sgs.impl.util.BoundNamesUtil;
import com.sun.sgs.io.Connection;
import com.sun.sgs.io.ConnectionListener;
import com.sun.sgs.io.Connector;
import com.sun.sgs.kernel.TransactionScheduler;
import com.sun.sgs.protocol.simple.SimpleSgsProtocol;
import com.sun.sgs.service.DataService;
import com.sun.sgs.test.util.SgsTestNode;
import com.sun.sgs.test.util.TestAbstractKernelRunnable;
import com.sun.sgs.tools.test.FilteredNameRunner;
import com.sun.sgs.tools.test.IntegrationTest;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.math.BigInteger;
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

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;


import static com.sun.sgs.test.util.UtilProperties.createProperties;

@RunWith(FilteredNameRunner.class)
public class TestChannelServiceImpl extends Assert {
    
    private static final String APP_NAME = "TestChannelServiceImpl";
    
    private static final int WAIT_TIME = 2000;
    
    private static final String LOGIN_FAILED_MESSAGE = "login failed";

    private static Object disconnectedCallbackLock = new Object();

    private static final List<String> sevenDwarfs =
	Arrays.asList(new String[] {
			  "bashful", "doc", "dopey", "grumpy",
			  "happy", "sleepy", "sneezy"});

    /** The Channel service properties. */
    private static final Properties serviceProps =
	createProperties(StandardProperties.APP_NAME, APP_NAME);
    
    /** The node that creates the servers. */
    private SgsTestNode serverNode;

    /** Any additional nodes, keyed by node hostname (for tests
     * needing more than one node). */
    private Map<String,SgsTestNode> additionalNodes;

    /** Version information from ChannelServiceImpl class. */
    private final String VERSION_KEY;
    private final int MAJOR_VERSION;
    private final int MINOR_VERSION;

    /** The transaction scheduler. */
    private TransactionScheduler txnScheduler;

    /** The owner for tasks I initiate. */
    private Identity taskOwner;

    /** The shared data service. */
    private DataService dataService;

    /** The channel service on the server node. */
    private ChannelManager channelService;

    /** The listen port for the client session service. */
    private int port;

    /** If {@code true}, shuts off some printing during performance tests. */
    private boolean isPerformanceTest = false;
    
    /** A list of users for test purposes. */
    private List<String> someUsers =
	Arrays.asList(new String[] { "moe", "larry", "curly" });

    private static Field getField(Class cl, String name) throws Exception {
	Field field = cl.getDeclaredField(name);
	field.setAccessible(true);
	return field;
    }
    
    /** Constructs a test instance. */
    public TestChannelServiceImpl() throws Exception  {
	Class cl = ChannelServiceImpl.class;
	VERSION_KEY = (String) getField(cl, "VERSION_KEY").get(null);
	MAJOR_VERSION = getField(cl, "MAJOR_VERSION").getInt(null);
	MINOR_VERSION = getField(cl, "MINOR_VERSION").getInt(null);
    }

    /** Creates and configures the channel service. */
    @Before
    public void setUp() throws Exception {
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

        txnScheduler = 
            serverNode.getSystemRegistry().
            getComponent(TransactionScheduler.class);
        taskOwner = serverNode.getProxy().getCurrentOwner();

        dataService = serverNode.getDataService();
	channelService = serverNode.getChannelService();
    }

    /** Cleans up the transaction. */
    @After
    public void tearDown() throws Exception {
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
    private void addNodes(String... hosts) throws Exception {
        // Create the other nodes
	if (additionalNodes == null) {
	    additionalNodes = new HashMap<String, SgsTestNode>();
	}

        for (String host : hosts) {
	    Properties props = SgsTestNode.getDefaultProperties(
	        APP_NAME, serverNode, DummyAppListener.class);
	    props.put("com.sun.sgs.impl.service.watchdog.client.host", host);
            SgsTestNode node = 
                    new SgsTestNode(serverNode, DummyAppListener.class, props);
	    additionalNodes.put(host, node);
        }
    }

    // -- Test constructor -- 

    @Test
    public void testConstructorNullProperties() throws Exception {
	try {
	    new ChannelServiceImpl(null, serverNode.getSystemRegistry(),
				   serverNode.getProxy());
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
    }

    @Test
    public void testConstructorNullComponentRegistry() throws Exception {
	try {
	    new ChannelServiceImpl(serviceProps, null, serverNode.getProxy());
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
    }

    @Test
    public void testConstructorNullTransactionProxy() throws Exception {
	try {
	    new ChannelServiceImpl(serviceProps, serverNode.getSystemRegistry(),
				   null);
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
    }

    @Test
    public void testConstructorNoAppName() throws Exception {
	try {
	    new ChannelServiceImpl(
		new Properties(), serverNode.getSystemRegistry(),
		serverNode.getProxy());
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
    }

    @Test
    public void testConstructedVersion() throws Exception {
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
		public void run() {
		    Version version = (Version)
			dataService.getServiceBinding(VERSION_KEY);
		    if (version.getMajorVersion() != MAJOR_VERSION ||
			version.getMinorVersion() != MINOR_VERSION)
		    {
			fail("Expected service version (major=" +
			     MAJOR_VERSION + ", minor=" + MINOR_VERSION +
			     "), got:" + version);
		    }
		}}, taskOwner);
    }

    @Test
    public void testConstructorWithCurrentVersion() throws Exception {
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
		public void run() {
		    Version version = new Version(MAJOR_VERSION, MINOR_VERSION);
		    dataService.setServiceBinding(VERSION_KEY, version);
		}}, taskOwner);

	ChannelServiceImpl newChannelService = null;
	try {
	    newChannelService =
		new ChannelServiceImpl(serviceProps,
				       serverNode.getSystemRegistry(),
				       serverNode.getProxy());
	} finally {
	    if (newChannelService != null) {
		newChannelService.shutdown();
	    }
	}
    }

    @Test
    public void testConstructorWithMajorVersionMismatch() throws Exception {
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
		public void run() {
		    Version version =
			new Version(MAJOR_VERSION + 1, MINOR_VERSION);
		    dataService.setServiceBinding(VERSION_KEY, version);
		}}, taskOwner);

	try {
	    new ChannelServiceImpl(serviceProps, serverNode.getSystemRegistry(),
				   serverNode.getProxy());
	    fail("Expected IllegalStateException");
	} catch (IllegalStateException e) {
	    System.err.println(e);
	}
    }

    @Test
    public void testConstructorWithMinorVersionMismatch() throws Exception {
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
		public void run() {
		    Version version =
			new Version(MAJOR_VERSION, MINOR_VERSION + 1);
		    dataService.setServiceBinding(VERSION_KEY, version);
		}}, taskOwner);

	try {
	    new ChannelServiceImpl(serviceProps, serverNode.getSystemRegistry(),
				   serverNode.getProxy());
	    fail("Expected IllegalStateException");
	} catch (IllegalStateException e) {
	    System.err.println(e);
	}
    }

    // -- Test createChannel --

    @Test
    public void testCreateChannelNullName() throws Exception {
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
	    public void run() {
		try {
		    channelService.createChannel(
			null, new DummyChannelListener(), Delivery.RELIABLE);
		    fail("Expected NullPointerException");
		}  catch (NullPointerException e) {
		    System.err.println(e);
		}
	    }}, taskOwner);
    }

    @Test
    public void testCreateChannelNullListener() throws Exception {
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
	    public void run() {
		try {
		    channelService.createChannel(
			"foo", null, Delivery.RELIABLE);
		    System.err.println("null listener allowed");
		}  catch (NullPointerException e) {
		    fail("Got NullPointerException");
		}
	    }}, taskOwner);
    }

    @Test
    public void testCreateChannelNonSerializableListener() throws Exception {
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
	    public void run() {
		try {
		    channelService.createChannel(
			"foo", new NonSerializableChannelListener(),
			Delivery.RELIABLE);
		    fail("Expected IllegalArgumentException");
		}  catch (IllegalArgumentException e) {
		    System.err.println(e);
		}
	    }}, taskOwner);
    }

    @Test
    public void testCreateChannelNoTxn() throws Exception { 
	try {
	    channelService.createChannel("x", null, Delivery.RELIABLE);
	    fail("Expected TransactionNotActiveException");
	} catch (TransactionNotActiveException e) {
	    System.err.println(e);
	}
    }

    @Test
    public void testChannelToStringNoTxn() throws Exception {
	final List<Channel> channel = new ArrayList<Channel>();
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
	    public void run() {
		channel.add(
		    channelService.createChannel(
			"test", new DummyChannelListener(), Delivery.RELIABLE));
		System.err.println(channel.get(0).toString());
	    }}, taskOwner);
	System.err.println(channel.get(0).toString());
    }
    
    // -- Test Channel serialization --

    @Test
    public void testChannelWriteReadObject() throws Exception {
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
	    public void run() throws Exception {
		Channel savedChannel =
		    channelService.createChannel("x", null, Delivery.RELIABLE);
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
    
    // -- Test Channel.getName --

    @Test
    public void testChannelGetNameNoTxn() throws Exception {
	Channel channel = createChannel();
	try {
	    channel.getName();
	    fail("Expected TransactionNotActiveException");
	} catch (TransactionNotActiveException e) {
	    System.err.println(e);
	}
    }

    @Test
    public void testChannelGetNameMismatchedTxn() throws Exception {
	final Channel channel = createChannel();
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
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

    @Test
    public void testChannelGetName() throws Exception {
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
	    public void run() {
		String name = "foo";
		Channel channel = channelService.createChannel(
		    name, null, Delivery.RELIABLE);
		if (!name.equals(channel.getName())) {
		    fail("Expected: " + name + ", got: " + channel.getName());
		}
	    }
	}, taskOwner);
    }

    @Test
    public void testChannelGetNameClosedChannel() throws Exception {
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
	    public void run() {
		String name = "foo";
		Channel channel = channelService.createChannel(
		    name, null, Delivery.RELIABLE);
		dataService.removeObject(channel);
		try {
		    channel.getName();
		    fail("Expected IllegalStateException");
		} catch (IllegalStateException e) {
		    System.err.println(e);
		}
	    }
	}, taskOwner);
    }

    // -- Test Channel.getDelivery --

    @Test
    public void testChannelGetDeliveryNoTxn() throws Exception {
	Channel channel = createChannel();
	try {
	    channel.getDelivery();
	    fail("Expected TransactionNotActiveException");
	} catch (TransactionNotActiveException e) {
	    System.err.println(e);
	}
    }

    @Test
    public void testChannelGetDeliveryMismatchedTxn() throws Exception {
	// TBD: should the implementation work this way?
	final Channel channel = createChannel();
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
	    public void run() {
		try {
		    channel.getDelivery();
		    fail("Expected TransactionNotActiveException");
		} catch (TransactionNotActiveException e) {
		    System.err.println(e);
		}
	    }
	}, taskOwner);
    }

    @Test
    public void testChannelGetDelivery() throws Exception {
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
	    public void run() {
		for (Delivery delivery : Delivery.values()) {
		    Channel channel = channelService.createChannel(
			delivery.toString(), null, delivery);
		    if (!delivery.equals(channel.getDelivery())) {
			fail("Expected: " + delivery + ", got: " +
			     channel.getDelivery());
		    }
		}
		System.err.println("Delivery requirements are equal");
	    }
	}, taskOwner);
    }

    @Test
    public void testChannelGetDeliveryClosedChannel() throws Exception {
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
	    public void run() {
		for (Delivery delivery : Delivery.values()) {
		    Channel channel = channelService.createChannel(
			delivery.toString(), null, delivery);
		    dataService.removeObject(channel);
		    try {
			channel.getDelivery();
			fail("Expected IllegalStateException");
		    } catch (IllegalStateException e) {
			System.err.println(e);
		    }
		}
		System.err.println("Got delivery requirement on close channel");
	    }
	}, taskOwner);
    }

    // -- Test Channel.hasSessions --

    @Test
    public void testChannelHasSessionsNoTxn() throws Exception {
	Channel channel = createChannel();
	try {
	    channel.hasSessions();
	    fail("Expected TransactionNotActiveException");
	} catch (TransactionNotActiveException e) {
	    System.err.println(e);
	}
    }

    @Test
    public void testChannelHasSessionsMismatchedTxn() throws Exception {
	final Channel channel = createChannel();
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
	    public void run() {
		try {
		    channel.hasSessions();
		    fail("Expected TransactionNotActiveException");
		} catch (TransactionNotActiveException e) {
		    System.err.println(e);
		}
	    }
	}, taskOwner);
    }

    @Test
    public void testChannelHasSessionsNoSessionsJoined() throws Exception {
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
	    public void run() {
		String name = "foo";
		Channel channel = channelService.createChannel(
		    name, null, Delivery.RELIABLE);
		if (channel.hasSessions()) {
		    fail("Expected no sessions joined");
		}
		System.err.println("no sessions joined");
	    }
	}, taskOwner);
    }

    @Test
    public void testChannelHasSessionsWithSessionsJoined() throws Exception {
	final String channelName = "foo";
	createChannel(channelName);
	ClientGroup group = new ClientGroup(someUsers);
	try {
	    joinUsers("foo", someUsers);
	    txnScheduler.runTask(new TestAbstractKernelRunnable() {
		public void run() {
		    Channel channel = channelService.getChannel(channelName);
		    if (! channel.hasSessions()) {
			fail("Expected sessions joined");
		    }
		}
		}, taskOwner);
	} finally {
	    group.disconnect(false);
	}
    }

    @Test
    public void testChannelHasSessionsClosedChannel() throws Exception {
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
	    public void run() {
		String name = "foo";
		Channel channel = channelService.createChannel(
		    name, null, Delivery.RELIABLE);
		dataService.removeObject(channel);
		try {
		    channel.hasSessions();
		    fail("Expected IllegalStateException");
		} catch (IllegalStateException e) {
		    System.err.println(e);
		}
	    }
	}, taskOwner);
    }

    // -- Test Channel.getSessions --

    @Test
    public void testChannelGetSessionsNoTxn() throws Exception {
	Channel channel = createChannel();
	try {
	    channel.getSessions();
	    fail("Expected TransactionNotActiveException");
	} catch (TransactionNotActiveException e) {
	    System.err.println(e);
	}
    }

    @Test
    public void testChannelGetSessionsMismatchedTxn() throws Exception {
	final Channel channel = createChannel();
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
	    public void run() {
		try {
		    channel.getSessions();
		    fail("Expected TransactionNotActiveException");
		} catch (TransactionNotActiveException e) {
		    System.err.println(e);
		}
	    }
	}, taskOwner);
    }

    @Test
    public void testChannelGetSessionsNoSessionsJoined() throws Exception {
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
	    public void run() {
		String name = "foo";
		Channel channel = channelService.createChannel(
		    name, null, Delivery.RELIABLE);
		if (channel.getSessions().hasNext()) {
		    fail("Expected no sessions joined");
		}
		System.err.println("no sessions joined");
	    }
	}, taskOwner);
    }

    @Test
    public void testChannelGetSessionsWithSessionsJoined() throws Exception {
	final String channelName = "foo";
	createChannel(channelName);
	ClientGroup group = new ClientGroup(someUsers);
	try {
	    joinUsers("foo", someUsers);
	    txnScheduler.runTask(new TestAbstractKernelRunnable() {
		public void run() {
		    Channel channel = channelService.getChannel(channelName);
		    Set<String> users = new HashSet<String>(someUsers);
		    Iterator<ClientSession> iter = channel.getSessions();
		    while (iter.hasNext()) {
			ClientSession session = iter.next();
			if (!(session instanceof ClientSessionWrapper)) {
			    fail("session not ClientSessionWrapper instance: " +
				 session);
			}
			String name = session.getName();
			if (! users.contains(name)) {
			    fail("unexpected channel member: " + name);
			} else {
			    System.err.println("getSessions includes: " + name);
			    users.remove(name);
			}
		    }
		    if (! users.isEmpty()) {
			fail("Expected getSessions to include: " + users);
		    }
		}}, taskOwner);
	} finally {
	    group.disconnect(false);
	}
    }

    @Test
    public void testChannelGetSessionsMultipleNodes() throws Exception {
	addNodes("a", "b");
	testChannelGetSessionsWithSessionsJoined();
    }

    @Test
    public void testChannelGetSessionsClosedChannel() throws Exception {
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
	    public void run() {
		String name = "foo";
		Channel channel = channelService.createChannel(
		    name, null, Delivery.RELIABLE);
		dataService.removeObject(channel);
		try {
		    channel.getSessions();
		    fail("Expected IllegalStateException");
		} catch (IllegalStateException e) {
		    System.err.println(e);
		}
	    }
	}, taskOwner);
    }

    // -- Test Channel.join --

    @Test
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

    @Test
    public void testChannelJoinClosedChannel() throws Exception {
	final DummyClient client = newClient();
	try {
	    txnScheduler.runTask(new TestAbstractKernelRunnable() {
		public void run() throws Exception {
		    Channel channel =
			channelService.createChannel(
			    "x", null, Delivery.RELIABLE);
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

    @Test
    public void testChannelJoinNullClientSession() throws Exception {
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
	    public void run() {
		Channel channel =
		    channelService.createChannel("x", null, Delivery.RELIABLE);
		try {
		    channel.join((ClientSession) null);
		    fail("Expected NullPointerException");
		} catch (NullPointerException e) {
		    System.err.println(e);
		}
	    }
	}, taskOwner);
    }

    @Test
    @IntegrationTest
    public void testChannelJoin() throws Exception {
	String channelName = "joinTest";
	ClientGroup group = new ClientGroup(someUsers);
	Thread.sleep(1000);
	printServiceBindings("before channel create");
	int count = getObjectCount();
	createChannel(channelName);
	
	try {
	    joinUsers(channelName, someUsers);
	    checkUsersJoined(channelName, someUsers);
	    printServiceBindings("before close");
	    closeChannel(channelName);
	    Thread.sleep(1000);
	    printServiceBindings("after close");
	    assertEquals(count, getObjectCount());
	} finally {
	    group.disconnect(false);
	}
    }

    // -- Test Channel.leave --

    @Test
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

    @Test
    public void testChannelLeaveMismatchedTxn() throws Exception {
	// TBD: should the implementation work this way?
	final String channelName = "test";
	final Channel channel = createChannel(channelName);
	final DummyClient client = newClient();
	try {
	    txnScheduler.runTask(new TestAbstractKernelRunnable() {
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

    @Test
    public void testChannelLeaveClosedChannel() throws Exception {
	final String channelName = "leaveClosedChannelTest";
	final String user = "daffy";
	final List<String> users = Arrays.asList(new String[] { user });
	createChannel(channelName);
	ClientGroup group = new ClientGroup(users);

	try {
	    txnScheduler.runTask(new TestAbstractKernelRunnable() {
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

    @Test
    public void testChannelLeaveNullClientSession() throws Exception {
	
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
	    public void run() {
		Channel channel =
		    channelService.createChannel("x", null, Delivery.RELIABLE);
		try {
		    channel.leave((ClientSession) null);
		    fail("Expected NullPointerException");
		} catch (NullPointerException e) {
		    System.err.println(e);
		}
	    }
	}, taskOwner);
    }

    @Test
    @IntegrationTest
    public void testChannelLeaveSessionNotJoined() throws Exception {
	final String channelName = "leaveTest";
	createChannel(channelName);
	ClientGroup group = new ClientGroup(someUsers);
	
	try {
	    txnScheduler.runTask(new TestAbstractKernelRunnable() {
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
	    
	    txnScheduler.runTask(new TestAbstractKernelRunnable() {
		public void run() {
		    Channel channel = getChannel(channelName);
	
		    ClientSession moe =
			(ClientSession) dataService.getBinding("moe");

		    ClientSession larry =
			(ClientSession) dataService.getBinding("larry");
		    
		    Set<ClientSession> sessions = getSessions(channel);
		    System.err.println(
			"sessions set (should only have moe): " + sessions);
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

    @Test
    @IntegrationTest
    public void testChannelLeave() throws Exception {
	final String channelName = "leaveTest";
	createChannel(channelName);
	ClientGroup group = new ClientGroup(someUsers);
	
	try {
	    Thread.sleep(1000);
	    int count = getObjectCount();
	    joinUsers(channelName, someUsers);
	    checkUsersJoined(channelName, someUsers);

	    for (final String user : someUsers) {
		
		txnScheduler.runTask(new TestAbstractKernelRunnable() {
		    public void run() {
			Channel channel = getChannel(channelName);
			ClientSession session = getSession(user);
			channel.leave(session);
		    }}, taskOwner);

		Thread.sleep(100);
		
		txnScheduler.runTask(new TestAbstractKernelRunnable() {
		    public void run() {
			Channel channel = getChannel(channelName);
			ClientSession session = getSession(user);
			if (getSessions(channel).contains(session)) {
			    fail("Failed to remove session: " + session);
			}}}, taskOwner);
	    }
	    
	    Thread.sleep(1000);
	    assertEquals(count, getObjectCount());
	    
	    txnScheduler.runTask(new TestAbstractKernelRunnable() {
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

    // -- Test Channel.leaveAll --

    @Test
    public void testChannelLeaveAllNoTxn() throws Exception {
	Channel channel = createChannel();
	try {
	    channel.leaveAll();
	    fail("Expected TransactionNotActiveException");
	} catch (TransactionNotActiveException e) {
	    System.err.println(e);
	}
    }

    @Test
    public void testChannelLeaveAllClosedChannel() throws Exception {
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
	    public void run() {
		Channel channel =
		    channelService.createChannel("x", null, Delivery.RELIABLE);
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

    @Test
    public void testChannelLeaveAllNoSessionsJoined() throws Exception {
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
	    public void run() {
		Channel channel =
		    channelService.createChannel("x", null, Delivery.RELIABLE);
		channel.leaveAll();
		System.err.println(
		    "leaveAll succeeded with no sessions joined");
	    }
	}, taskOwner);
    }

    @Test
    public void testChannelLeaveAll() throws Exception {
	final String channelName = "leaveAllTest";
	createChannel(channelName);
	ClientGroup group = new ClientGroup(someUsers);
	
	try {
	    joinUsers(channelName, someUsers);
	    checkUsersJoined(channelName, someUsers);

	    txnScheduler.runTask(new TestAbstractKernelRunnable() {
		public void run() {
		    Channel channel = getChannel(channelName);
		    channel.leaveAll();
		}
	    }, taskOwner);
	    
	    Thread.sleep(100);
	    
	    txnScheduler.runTask(new TestAbstractKernelRunnable() {
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

    // -- Test Channel.send --

    private static byte[] testMessage = new byte[] {'x'};

    @Test
    public void testChannelSendAllNoTxn() throws Exception {
	Channel channel = createChannel();
	try {
	    channel.send(null, ByteBuffer.wrap(testMessage));
	    fail("Expected TransactionNotActiveException");
	} catch (TransactionNotActiveException e) {
	    System.err.println(e);
	}
    }

    @Test
    public void testChannelSendAllClosedChannel() throws Exception {
	final String channelName = "test";
	createChannel(channelName);
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
	    public void run() {
		Channel channel = getChannel(channelName);
		dataService.removeObject(channel);
		try {
		    channel.send(null, ByteBuffer.wrap(testMessage));
		    fail("Expected IllegalStateException");
		} catch (IllegalStateException e) {
		    System.err.println(e);
		}
	    }
	}, taskOwner);
    }

    @Test
    public void testChannelSendNullMessage() throws Exception {
	final String channelName = "test";
	createChannel(channelName);
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
	    public void run() {
		Channel channel = getChannel(channelName);
		try {
		    channel.send(null, null);
		    fail("Expected NullPointerException");
		} catch (NullPointerException e) {
		    System.err.println(e);
		}
	    }
	}, taskOwner);
    }

    @Test
    public void testChannelSend() throws Exception {
	
	String channelName = "test";
	createChannel(channelName);
	ClientGroup group = new ClientGroup(sevenDwarfs);
	try {
	    joinUsers(channelName, sevenDwarfs);
	    sendMessagesToChannel(channelName, group, 3);
	} finally {
	    group.disconnect(false);
	}
    }

    @Test
    public void testChannelSendMultipleNodes() throws Exception {
	addNodes("one", "two", "three");
	testChannelSend();
    }

    @Test
    @IntegrationTest
    public void testChannelSendToNewMembersAfterAllNodesFail() throws Exception {
	addNodes("one", "two", "three");
	String channelName = "test";
	createChannel(channelName);
	Thread.sleep(1000);
	int count = getObjectCount();
	printServiceBindings("after channel create");
	List<String> users =  sevenDwarfs;
	ClientGroup group1 = new ClientGroup(users);
	joinUsers(channelName, users);
	sendMessagesToChannel(channelName, group1, 3);
	printServiceBindings("after users joined");
	System.err.println("simulate watchdog server crash...");
	tearDown(false);
	setUp(false);
        Thread.sleep(1000);
	addNodes("ay", "bee", "sea");
	Thread.sleep(1000);
	int afterCount = getObjectCount();
	for (int i = 0; i < 2; i++) {
	    // Make sure that previous sessions were cleaned up.
	    if (count == afterCount) {
		break;
	    } else {
		Thread.sleep(1000);
		afterCount = getObjectCount();
	    }
	    System.err.println("retry: count: " + count +
			       ", afterCount: " + afterCount);
	}
	printServiceBindings("after recovery");
	users =  someUsers;
	ClientGroup group2 = new ClientGroup(users);
	try {
	    joinUsers(channelName, users);
	    sendMessagesToChannel(channelName, group2, 2);
	    group1.checkMembership(channelName, false);
	    assertEquals(count, afterCount);
	} finally {
	    group2.disconnect(false);
	}
    }

    @Test
    @IntegrationTest
    public void testChannelSendToExistingMembersAfterNodeFailure()
	throws Exception
    {
	String coordinatorHost = "coordinatorNode";
	String otherHost = "otherNode";
	addNodes(coordinatorHost, otherHost);
	
	// create channels on specific node which will be the coordinator node
	String[] channelNames = new String[] {"channel1", "channel2"};
	for (String channelName : channelNames) {
	    createChannel(channelName, null, coordinatorHost);
	}
	
	ClientGroup group = new ClientGroup(sevenDwarfs);
	try {
	    for (String channelName : channelNames) {
		joinUsers(channelName, sevenDwarfs);
		sendMessagesToChannel(channelName, group, 2);
	    }
	    printServiceBindings("after users joined");
	    // nuke non-coordinator node
	    System.err.println("shutting down node: " + otherHost);
	    int otherHostPort = additionalNodes.get(otherHost).getAppPort();
	    shutdownNode(otherHost);
            Thread.sleep(1000);
	    // remove disconnected sessions from client group
	    System.err.println("remove disconnected sessions");
	    ClientGroup disconnectedSessionsGroup =
		group.removeSessionsFromGroup(otherHostPort);
	    // send messages to sessions that are left
	    System.err.println("send messages to remaining members");
	    for (String channelName : channelNames) {
		sendMessagesToChannel(channelName, group, 2);
	    }
	    if (!disconnectedSessionsGroup.isDisconnectedGroup()) {
		fail("expected disconnected client(s)");
	    }

	    for (String channelName : channelNames) {
		disconnectedSessionsGroup.checkMembership(channelName, false);
	    }
	    
	} finally {
	    printServiceBindings("before group disconnect");
	    group.disconnect(false);
	}
    }

    @Test
    @IntegrationTest
    public void testChannelSendToExistingMembersAfterCoordinatorFailure()
	throws Exception
    {
	String coordinatorHost = "coordinator";
	addNodes(coordinatorHost, "otherNode");
	
	// create channels on specific node which will be the coordinator node
	String[] channelNames = new String[] {"channel1", "channel2"};
	for (String channelName : channelNames) {
	    createChannel(channelName, null, coordinatorHost);
	}

	ClientGroup group = new ClientGroup(sevenDwarfs);
	try {
	    for (String channelName : channelNames) {
		joinUsers(channelName, sevenDwarfs);
		sendMessagesToChannel(channelName, group, 2);
	    }
	    printServiceBindings("after users joined");
	    // nuke coordinator node
	    System.err.println("shutting down node: " + coordinatorHost);
	    int coordinatorHostPort =
		additionalNodes.get(coordinatorHost).getAppPort();
	    shutdownNode(coordinatorHost);
            Thread.sleep(1000);
	    // remove disconnected sessions from client group
	    System.err.println("remove disconnected sessions");
	    ClientGroup disconnectedSessionsGroup =
		group.removeSessionsFromGroup(coordinatorHostPort);
	    // send messages to sessions that are left
	    System.err.println("send messages to remaining members");
	    for (String channelName : channelNames) {
		sendMessagesToChannel(channelName, group, 2);
	    }
	    if (!disconnectedSessionsGroup.isDisconnectedGroup()) {
		fail("expected disconnected client(s)");
	    }

	    for (String channelName : channelNames) {
		disconnectedSessionsGroup.checkMembership(channelName, false);
	    }
	    
	} finally {
	    printServiceBindings("before group disconnect");
	    group.disconnect(false);
	}
    }

    // -- Test client send to channel (with and without ChannelListener) --

    @Test
    @IntegrationTest
    public void testNonMemberClientSendToChannelWithNoListener ()
	throws Exception
    {
	String channelName = "foo";
	createChannel(channelName);
	ClientGroup group = new ClientGroup(someUsers);
	DummyClient nonMember = newClient();
	try {
	    joinUsers(channelName, someUsers);
	    DummyClient moe = group.getClient("moe");
	    moe.waitForJoin(channelName);
	    BigInteger channelId = moe.channelNameToId.get(channelName);
	    nonMember.sendChannelMessage(channelId, 0);
	    Thread.sleep(2000);
	    for (DummyClient client : group.getClients()) {
		if (client.nextChannelMessage() != null) {
		    fail(client.name + " received message!");
		}
	    }
	} finally {
	    group.disconnect(false);
	    nonMember.disconnect();
	}
    }

    @Test
    @IntegrationTest
    public void testNonMemberClientSendToChannelWithForwardingListener ()
	throws Exception
    {
	String channelName = "foo";
	createChannel(channelName, new DummyChannelListener(channelName, true));
	ClientGroup group = new ClientGroup(someUsers);
	DummyClient nonMember = newClient();
	try {
	    joinUsers(channelName, someUsers);
	    DummyClient moe = group.getClient("moe");
	    moe.waitForJoin(channelName);
	    BigInteger channelId = moe.channelNameToId.get(channelName);
	    nonMember.sendChannelMessage(channelId, 0);
	    Thread.sleep(2000);
	    for (DummyClient client : group.getClients()) {
		if (client.nextChannelMessage() != null) {
		    fail(client.name + " received message!");
		}
	    }
	} finally {
	    group.disconnect(false);
	    nonMember.disconnect();
	}
    }

    @Test
    @IntegrationTest
    public void testClientSendToChannelWithNoListener() throws Exception {
	String channelName = "foo";
	createChannel(channelName);
	ClientGroup group = new ClientGroup(someUsers);
	try {
	    joinUsers(channelName, someUsers);
	    DummyClient moe = group.getClient("moe");
	    moe.waitForJoin(channelName);
	    moe.sendChannelMessage(channelName, 0);
	    Thread.sleep(2000);
	    boolean fail = false;
	    for (DummyClient client : group.getClients()) {
		if (client.nextChannelMessage() == null) {
		    System.err.println(client.name + " did not receive message!");
		    fail = true;
		}
	    }
	    if (fail) {
		fail("test failed; one or more clients did not get message");
	    }
	} finally {
	    group.disconnect(false);
	}
    }

    @Test
    @IntegrationTest
    public void testClientSendToChannelWithForwardingListener()
	throws Exception
    {
	String channelName = "foo";
	createChannel(channelName, new DummyChannelListener(channelName, true));
	ClientGroup group = new ClientGroup(someUsers);
	try {
	    joinUsers(channelName, someUsers);
	    DummyClient moe = group.getClient("moe");
	    moe.waitForJoin(channelName);
	    moe.sendChannelMessage(channelName, 0);
	    Thread.sleep(2000);
	    boolean fail = false;
	    for (DummyClient client : group.getClients()) {
		if (client.nextChannelMessage() == null) {
		    System.err.println(client.name + " did not receive message!");
		    fail = true;
		}
	    }
	    if (fail) {
		fail("test failed; one or more clients did not get message");
	    }
	} finally {
	    group.disconnect(false);
	}
    }

    @Test
    @IntegrationTest
    public void testClientSendToChannelWithRejectingListener()
	throws Exception
    {
	String channelName = "foo";
	createChannel(channelName, new DummyChannelListener(channelName, false));
	ClientGroup group = new ClientGroup(someUsers);
	try {
	    joinUsers(channelName, someUsers);
	    DummyClient moe = group.getClient("moe");
	    moe.waitForJoin(channelName);
	    moe.sendChannelMessage(channelName, 0);
	    Thread.sleep(2000);
	    boolean fail = false;
	    for (DummyClient client : group.getClients()) {
		if (client.nextChannelMessage() != null) {
		    System.err.println(client.name + " received message!");
		    fail = true;
		}
	    }
	    if (fail) {
		fail("test failed; one or more clients received message");
	    }
	} finally {
	    group.disconnect(false);
	}
    }

    @Test
    @IntegrationTest
    public void testClientSendToChannelWithFilteringListener()
	throws Exception
    {
	String channelName = "foo";
	createChannel(channelName, new FilteringChannelListener(channelName));
	ClientGroup group = new ClientGroup(someUsers);
	try {
	    joinUsers(channelName, someUsers);
	    DummyClient moe = group.getClient("moe");
	    moe.waitForJoin(channelName);
	    int numMessages = 10;
	    for (int i = 0; i < numMessages; i++) {
		moe.sendChannelMessage(channelName, i);
	    }
	    Thread.sleep(2000);
	    boolean fail = false;
	    for (int i = 0; i < numMessages / 2; i++) {
		for (DummyClient client : group.getClients()) {
		    MessageInfo info = client.nextChannelMessage();
		    if (info == null) {
			System.err.println(
			    client.name +
			    " should have received message: " + i * 2);
			fail = true;
		    } else {
			System.err.println(
			   client.name + " received message: " + info.seq);
			if (info.seq % 2 != 0) {
			    System.err.println("odd numbered message received!");
			    fail = true;
			}
		    }
		}
	    }
	    if (fail) {
		fail("test failed; see output");
	    }
	} finally {
	    group.disconnect(false);
	}
    }

    @Test
    public void testClientSendToChannelValidatingWrappedClientSession()
	throws Exception
    {
	final String channelName = "foo";
	final String user = "dummy";
	final String listenerName = "ValidatingChannelListener";
	DummyClient client =
	    (new DummyClient()).connect(port).login(user, "password");

	// Create a channel with a ValidatingChannelListener and join the
	// client to the channel.
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
	    public void run() {
		ChannelListener listener =
		    new ValidatingChannelListener();
		dataService.setBinding(listenerName, listener);
		ClientSession session =
		    (ClientSession) dataService.getBinding(user);
		Channel channel =
		    channelService.createChannel(
			channelName, listener, Delivery.RELIABLE);
		channel.join(session);
	    }
	}, taskOwner);

	// Wait for the client to join, and then send a channel message.
	client.waitForJoin(channelName);
	client.sendChannelMessage(channelName, 0);

	// Validate that the session passed to the handleChannelMessage
	// method was a wrapped ClientSession.
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
	    public void run() {
		ValidatingChannelListener listener = (ValidatingChannelListener)
		    dataService.getBinding(listenerName);
		ClientSession session =
		    (ClientSession) dataService.getBinding(user);
		listener.validateSession(session);
		System.err.println("sessions are equal");
	    }
	}, taskOwner);
    }

    @Test
    @IntegrationTest
    public void testJoinLeavePerformance() throws Exception {
	final String channelName = "perf";
	createChannel(channelName);
	String user = "dummy";
	DummyClient client =
	    (new DummyClient()).connect(port).login(user, "password");

	final String sessionKey = user;
	isPerformanceTest = true;
	int numIterations = 100;
	long startTime = System.currentTimeMillis();
	for (int i = 0; i < numIterations; i++) {
	    txnScheduler.runTask(new TestAbstractKernelRunnable() {
		public void run() {
		    Channel channel = channelService.getChannel(channelName);
		    DataManager dataManager = AppContext.getDataManager();
		    ClientSession session = (ClientSession)
			dataManager.getBinding(sessionKey);
		    channel.join(session);
		    channel.leave(session);
		}}, taskOwner);
	}
	long endTime = System.currentTimeMillis();
	System.err.println("join/leave, iterations: " + numIterations +
			   ", elapsed time: " + (endTime - startTime) +
			   " ms.");
    }

    // -- Test Channel.close --

    @Test
    public void testChannelCloseNoTxn() throws Exception {
	Channel channel = createChannel();
	try {
	    dataService.removeObject(channel);
	    fail("Expected TransactionNotActiveException");
	} catch (TransactionNotActiveException e) {
	    System.err.println(e);
	}
    }

    @Test
    @IntegrationTest
    public void testChannelClose() throws Exception {
	final String channelName = "closeTest";
	createChannel(channelName);
	printServiceBindings("after channel create");
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
	    public void run() {
		Channel channel = getChannel(channelName);
		dataService.removeObject(channel);
	    }
	}, taskOwner);
	Thread.sleep(100);
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
	    public void run() {
		Channel channel = getChannel(channelName);
		if (getChannel(channelName) != null) {
		    fail("obtained closed channel");
		}
	    }
	}, taskOwner);
	printServiceBindings("after channel close");
    }

    @Test
    @IntegrationTest
    public void testSessionRemovedFromChannelOnLogout() throws Exception {
	String channelName = "test";
	createChannel(channelName);
	int count = getObjectCount();
	ClientGroup group = new ClientGroup(someUsers);

	try {
	    joinUsers(channelName, someUsers);
	    Thread.sleep(500);
	    group.checkMembership(channelName, true);
	    group.disconnect(true);
	    Thread.sleep(WAIT_TIME); // this is necessary, and unfortunate...
	    group.checkMembership(channelName, false);
	    assertEquals(count, getObjectCount());
	    
	} catch (RuntimeException e) {
	    System.err.println("unexpected failure");
	    e.printStackTrace();
	    printServiceBindings("after exception");
	    fail("unexpected failure: " + e);
	} finally {
	    group.disconnect(false);
	}
    }

    @Test
    @IntegrationTest
    public void testSessionsRemovedOnRecovery() throws Exception {
	String channelName = "test";
	createChannel(channelName);
	int count = getObjectCount();
	ClientGroup group = new ClientGroup(someUsers);
	
	try {
	    joinUsers(channelName, someUsers);
	    Thread.sleep(500);
	    group.checkMembership(channelName, true);
	    printServiceBindings("after users joined");

	    // simulate crash
	    System.err.println("simulate watchdog server crash...");
	    tearDown(false);
	    setUp(false);

	    Thread.sleep(WAIT_TIME); // await recovery actions
	    group.checkMembership(channelName, false);
	    assertEquals(count, getObjectCount());
	    printServiceBindings("after recovery");

	} catch (RuntimeException e) {
	    System.err.println("unexpected failure");
	    e.printStackTrace();
	    fail("unexpected failure: " + e);
	} finally {
	    printServiceBindings("before group disconnect");
	    group.disconnect(false);
	}
	
    }

    // -- END TEST CASES --

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

	// Removes the client sessions on the given host from this group
	// and returns a ClientGroup with the removed sessions.
	ClientGroup removeSessionsFromGroup(int port) {
	    Iterator<String> iter = clients.keySet().iterator();
	    Map<String, DummyClient> removedClients =
		new HashMap<String, DummyClient>();
	    while (iter.hasNext()) {
		String user = iter.next();
		DummyClient client = clients.get(user);
		System.err.println("user: " + user +
				   ", redirectHost: " + client.redirectHost +
				   ", redirectPort: " + client.redirectPort);
                // Note that the redirectPort can sometimes be zero,
                // as it won't be assigned if the initial login request
                // was successful.
		if ((client.redirectPort != 0 && port == client.redirectPort) ||
		    (client.redirectPort == 0 && port == client.initialPort))
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

    private DummyClient newClient() {
	return (new DummyClient()).connect(port).login("dummy", "password");	
    }

    private ClientSession getClientSession(String name) {
	try {
	    return (ClientSession) dataService.getBinding(name);
	} catch (ObjectNotFoundException e) {
	    return null;
	}
    }

    private void printServiceBindings(final String message) throws Exception {
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
    private Channel createChannel() throws Exception {
	return createChannel("test");
    }

    private Channel createChannel(String name) throws Exception {
	return createChannel(name,  null, null);
    }

    private Channel createChannel(String name, ChannelListener listener)
	throws Exception
    {
	return createChannel(name, listener, null);

    }
    
    private Channel createChannel(
	String name, ChannelListener listener, String host) throws Exception
    {
	CreateChannelTask createChannelTask =
	    new CreateChannelTask(name, listener, host);
	runTransactionalTask(createChannelTask, host);
	return createChannelTask.getChannel();
    }

    // Runs the given transactional task using the task scheduler on the
    // specified host.
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

    private ClientSession getSession(String name) {
	try {
	    return (ClientSession) dataService.getBinding(name);
	} catch (ObjectNotFoundException e) {
	    return null;
	}
    }

    // FIXME: use the ChannelManager instead...
    private Channel getChannel(String name) {
	try {
	    return (Channel) dataService.getBinding(name);
	} catch (ObjectNotFoundException e) {
	    return null;
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
    
    private void joinUsers(
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
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
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

    private void printIt(String line) {
	if (! isPerformanceTest) {
	    System.err.println(line);
	}
    }
    
    // Shuts down the node with the specified host.
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

    private static class NonSerializableChannelListener
	implements ChannelListener
    {
	NonSerializableChannelListener() {}
	
	public void receivedMessage(
	    Channel channel, ClientSession session, ByteBuffer message)
	{
	}
    }

    private static class DummyChannelListener
	implements ChannelListener, Serializable
    {
	private final static long serialVersionUID = 1L;

	private final String name;
	private final boolean allowMessages;
	
	DummyChannelListener() {
	    this(null, true);
	}

	DummyChannelListener(String name, boolean allowMessages) {
	    this.name = name;
	    this.allowMessages = allowMessages;
	}
	
	public void receivedMessage(
	    Channel channel, ClientSession session, ByteBuffer message)
	{
	    if (name != null) {
		assertEquals(channel,
			     AppContext.getChannelManager().getChannel(name));
	    }
	    if (allowMessages) {
		channel.send(session, message);
	    }
	}
    }
    
    private static class FilteringChannelListener
	implements ChannelListener, Serializable
    {
	private final static long serialVersionUID = 1L;

	private final String name;
	
	FilteringChannelListener(String name) {
	    this.name = name;
	}
	
	public void receivedMessage(
	    Channel channel, ClientSession session, ByteBuffer message)
	{
	    if (name != null) {
		assertEquals(channel,
			     AppContext.getChannelManager().getChannel(name));
	    }

	    if (message.getInt() % 2 == 0) {
		message.flip();
		channel.send(session, message);
	    }
	}
    }

    private static class ValidatingChannelListener
	implements ChannelListener, Serializable, ManagedObject
    {
	private final static long serialVersionUID = 1L;

	private ManagedReference<ClientSession> sessionRef = null;
	
	ValidatingChannelListener() {
	}

	public void receivedMessage(
	    Channel channel, ClientSession session, ByteBuffer message)
	{
	    System.err.println(
		"ValidatingChannelListener.receivedMessage: session = " +
		session);
	    DataManager dm = AppContext.getDataManager();
	    dm.markForUpdate(this);
	    sessionRef = dm.createReference(session);
	}

	public void validateSession(ClientSession session) {
	    if (this.sessionRef == null) {
		throw new ResourceUnavailableException("sessionRef is null");
	    } else {
		System.err.println(
		    "ValidatingChannelListener.validateSession: session = " +
		    session);
		ClientSession thisSession = sessionRef.get();
		if (! (thisSession instanceof ClientSessionWrapper)) {
		    fail("unwrapped session: " + thisSession);
		} else if (! thisSession.equals(session)) {
		    fail("sessions not equal: thisSession: " +
			 thisSession + ", session: " + session);
		}
	    }
	}
    }
    
    // Dummy client code for testing purposes.
    private class DummyClient {

	String name;
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
	private Map<BigInteger, String> channelIdToName =
	    new HashMap<BigInteger, String>();
	private Map<String, BigInteger> channelNameToId =
	    new HashMap<String, BigInteger>();
	private String reason;
	private int initialPort;
	private String redirectHost;
        private int redirectPort;
        private byte[] reconnectKey;
	private final List<MessageInfo> channelMessages =
	    new ArrayList<MessageInfo>();
	
	DummyClient() {
	}

	boolean isConnected() {
	    synchronized (lock) {
		return connected;
	    }
	}

	DummyClient connect(int port) {
	    this.initialPort = port;
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
                        // cache a local copy of redirect port, in case it's ever
                        // cleared by disconnect
                        int port = redirectPort;
                        disconnect();
                        connect(port);
                        return login(user, pass);
		    } else {
			throw new RuntimeException(LOGIN_FAILED_MESSAGE);
		    }
		} catch (InterruptedException e) {
		    throw new RuntimeException(
			"DummyClient.login[" + name + "] timed out", e);
		}
	    }
	}

	ClientSession getSession() throws Exception {
	    GetSessionTask task = new GetSessionTask(name);
	    txnScheduler.runTask(task, taskOwner);
	    return task.getSession();
	}

	// Sends a SESSION_MESSAGE.
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

	// Throws a {@code RuntimeException} if this session is not
	// logged in.
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
	
	void logout() {
            synchronized (lock) {
                if (connected == false) {
                    return;
                }
                logoutAck = false;
                awaitGraceful = true;
            }
            MessageBuffer buf = new MessageBuffer(1);
            buf.putByte(SimpleSgsProtocol.LOGOUT_REQUEST);
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
                } finally {
                    if (! logoutAck)
                        disconnect();
                }
            }
	}

	private class Listener implements ConnectionListener {

	    List<byte[]> messageList = new ArrayList<byte[]>();
	    
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
                    reconnectKey = buf.getBytes(buf.limit() - buf.position());
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
                    redirectPort = buf.getInt();
		    synchronized (lock) {
			loginAck = true;
			loginRedirect = true;
			System.err.println("login redirected: " + name +
					   ", host:" + redirectHost +
                                           ", port:" + redirectPort);
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

		case SimpleSgsProtocol.CHANNEL_JOIN: {
		    String channelName = buf.getString();
		    BigInteger channelId = new BigInteger(1,
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
		    BigInteger channelId = new BigInteger(1,
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
		    BigInteger channelId = new BigInteger(1,
			buf.getBytes(buf.getShort()));
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
		    System.err.println(	
			"[" + name + "] " +
			"processAppProtocolMessage: unknown op code: " +
			opcode);
		    break;
		}
	    }

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

    private int getObjectCount() throws Exception {
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
    private int getChannelServiceBindingCount() throws Exception {
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
    
    private void closeChannel(final String name) throws Exception {

	txnScheduler.runTask(new TestAbstractKernelRunnable() {
	    public void run() {
		Channel channel = channelService.getChannel(name);
		dataService.removeObject(channel);
	    }}, taskOwner);
    }
}
