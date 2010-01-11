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
import com.sun.sgs.app.Channel;
import com.sun.sgs.app.ChannelListener;
import com.sun.sgs.app.ClientSession;
import com.sun.sgs.app.DataManager;
import com.sun.sgs.app.Delivery;
import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.ManagedReference;
import com.sun.sgs.app.ResourceUnavailableException;
import com.sun.sgs.app.TransactionNotActiveException;
import com.sun.sgs.impl.service.channel.ChannelServiceImpl;
import com.sun.sgs.impl.service.session.ClientSessionWrapper;
import com.sun.sgs.impl.util.AbstractService.Version;
import com.sun.sgs.test.util.ConfigurableNodePolicy;
import com.sun.sgs.test.util.SgsTestNode;
import com.sun.sgs.test.util.TestAbstractKernelRunnable;
import com.sun.sgs.tools.test.FilteredNameRunner;
import com.sun.sgs.tools.test.IntegrationTest;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(FilteredNameRunner.class)
public class TestChannelServiceImpl extends AbstractChannelServiceTest {
    
    /** Constructs a test instance. */
    public TestChannelServiceImpl() throws Exception  {
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
	    checkUsersJoined("foo", someUsers);
	    txnScheduler.runTask(new TestAbstractKernelRunnable() {
		public void run() {
		    Channel channel = channelService.getChannel(channelName);
		    List<String> users =
			new ArrayList<String>(Arrays.asList(someUsers));
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
	addNodes(2);
	ConfigurableNodePolicy.setRoundRobinPolicy();
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
	createChannel(channelName);
	ClientGroup group = new ClientGroup(user);

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
			(ClientSession) dataService.getBinding(MOE);
		    channel.join(moe);

		    try {
			ClientSession larry =
			    (ClientSession) dataService.getBinding(LARRY);
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
			(ClientSession) dataService.getBinding(MOE);

		    ClientSession larry =
			(ClientSession) dataService.getBinding(LARRY);
		    
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
	    leaveAll(channelName);
	    Thread.sleep(100);
	    checkUsersJoined(channelName, noUsers);
	    for (DummyClient client : group.getClients()) {
		client.assertLeftChannel(channelName);
	    }
	} finally {
	    group.disconnect(false);
	}
    }

    @Test
    public void testChannelLeaveAllThenJoin() throws Exception {
	final String channelName = "leaveAllTest";
	createChannel(channelName);
	ClientGroup group = new ClientGroup(someUsers);
	
	try {
	    joinUsers(channelName, someUsers);
	    checkUsersJoined(channelName, someUsers);
	    leaveAll(channelName);
	    Thread.sleep(200);
	    checkUsersJoined(channelName, noUsers);
	    for (DummyClient client : group.getClients()) {
		client.assertLeftChannel(channelName);
	    }
	    joinUsers(channelName, someUsers);
	    checkUsersJoined(channelName, someUsers);
	    sendMessagesToChannel(channelName, 2);
	    checkChannelMessagesReceived(group, channelName, 2);
	    
	} finally {
	    group.disconnect(false);
	}
    }
    
    @Test
    public void testChannelLeaveAllThenSend() throws Exception {
	final String channelName = "leaveAllTest";
	createChannel(channelName);
	ClientGroup group = new ClientGroup(someUsers);
	
	try {
	    joinUsers(channelName, someUsers);
	    checkUsersJoined(channelName, someUsers);
	    leaveAll(channelName);
	    Thread.sleep(200);
	    checkUsersJoined(channelName, noUsers);
	    for (DummyClient client : group.getClients()) {
		client.assertLeftChannel(channelName);
	    }
	    sendMessagesToChannel(channelName, 2);
	    checkChannelMessagesReceived(group, channelName, 0);
	    
	} finally {
	    group.disconnect(false);
	}
    }
    
    @Test
    @IntegrationTest
    public void testChannelLeaveAllWithCoordinatorCrash() throws Exception {
	final String channelName = "leaveAllTest";
	// Create channel on coordinator node
	SgsTestNode coordinatorNode = addNode();
	createChannel(channelName, null, coordinatorNode);
	// Clients will log into server node.
	ClientGroup group = new ClientGroup(serverNode.getAppPort(), someUsers);
	
	try {
	    joinUsers(channelName, someUsers);
	    checkUsersJoined(channelName, someUsers);
	    holdChannelServerMethodToNode(serverNode, "close");
	    leaveAll(channelName);
	    waitForHeldChannelServerMethodToNode(serverNode);
	    coordinatorNode.shutdown(false);
	    Thread.sleep(2000);
	    checkUsersJoined(channelName, noUsers);
	    for (DummyClient client : group.getClients()) {
		client.assertLeftChannel(channelName);
	    }
	    
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
	    sendMessagesToChannel(channelName, 3);
	    checkChannelMessagesReceived(group, channelName, 3);
	} finally {
	    group.disconnect(false);
	}
    }

    @Test
    public void testChannelSendMultipleNodes() throws Exception {
	addNodes(3);
	ConfigurableNodePolicy.setRoundRobinPolicy();
	testChannelSend();
    }

    @Test
    @IntegrationTest
    public void testChannelSendToNewMembersAfterAllNodesFail()
	throws Exception
    {
	addNodes(3);
	ConfigurableNodePolicy.setRoundRobinPolicy();
	String channelName = "test";
	createChannel(channelName);
	Thread.sleep(1000);
	int count = getObjectCount();
	printServiceBindings("after channel create");
	ClientGroup group1 = new ClientGroup(sevenDwarfs);
	joinUsers(channelName, sevenDwarfs);
	sendMessagesToChannel(channelName, 3);
	checkChannelMessagesReceived(group1, channelName, 3);
	printServiceBindings("after users joined");
	System.err.println("simulate watchdog server crash...");
	tearDown(false);
	setUp(false);
        Thread.sleep(1000);
	addNodes(3);
	ConfigurableNodePolicy.setRoundRobinPolicy();
	Thread.sleep(2000);
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
	ClientGroup group2 = new ClientGroup(someUsers);
	try {
	    joinUsers(channelName, someUsers);
	    sendMessagesToChannel(channelName, 2);
	    checkChannelMessagesReceived(group2, channelName, 2);
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
	SgsTestNode coordinatorNode = addNode();
	SgsTestNode otherNode = addNode();
	ConfigurableNodePolicy.setRoundRobinPolicy();
	
	// create channels on specific node which will be the coordinator node
	String[] channelNames = new String[] {"channel1", "channel2"};
	for (String channelName : channelNames) {
	    createChannel(channelName, null, coordinatorNode);
	}
	
	ClientGroup group = new ClientGroup(sevenDwarfs);
	try {
	    for (String channelName : channelNames) {
		joinUsers(channelName, sevenDwarfs);
		sendMessagesToChannel(channelName, 2);
		checkChannelMessagesReceived(group, channelName, 2);
	    }
	    printServiceBindings("after users joined");
	    // nuke non-coordinator node
	    System.err.println("shutting down other node: " + otherNode);
	    int otherNodePort = otherNode.getAppPort();
	    shutdownNode(otherNode);
            Thread.sleep(1000);
	    // remove disconnected sessions from client group
	    System.err.println("remove disconnected sessions");
	    ClientGroup disconnectedSessionsGroup =
		group.removeSessionsFromGroup(otherNodePort);
	    // send messages to sessions that are left
	    System.err.println("send messages to remaining members");
	    for (String channelName : channelNames) {
		sendMessagesToChannel(channelName, 2);
		checkChannelMessagesReceived(group, channelName, 2);
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
	SgsTestNode coordinatorNode = addNode();
	SgsTestNode otherNode = addNode();
	ConfigurableNodePolicy.setRoundRobinPolicy();
	
	// create channels on specific node which will be the coordinator node
	String[] channelNames = new String[] {"channel1", "channel2"};
	for (String channelName : channelNames) {
	    createChannel(channelName, null, coordinatorNode);
	}

	ClientGroup group = new ClientGroup(sevenDwarfs);
	try {
	    for (String channelName : channelNames) {
		joinUsers(channelName, sevenDwarfs);
		sendMessagesToChannel(channelName, 2);
		checkChannelMessagesReceived(group, channelName, 2);
	    }
	    printServiceBindings("after users joined");
	    // nuke coordinator node
	    System.err.println("shutting down coordinator: " + coordinatorNode);
	    int coordinatorNodePort = coordinatorNode.getAppPort();
	    shutdownNode(coordinatorNode);
            Thread.sleep(1000);
	    // remove disconnected sessions from client group
	    System.err.println("remove disconnected sessions");
	    ClientGroup disconnectedSessionsGroup =
		group.removeSessionsFromGroup(coordinatorNodePort);
	    // send messages to sessions that are left
	    System.err.println("send messages to remaining members");
	    for (String channelName : channelNames) {
		sendMessagesToChannel(channelName, 2);
		checkChannelMessagesReceived(group, channelName, 2);
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
	    DummyClient moe = group.getClient(MOE);
	    moe.assertJoinedChannel(channelName);
	    nonMember.sendChannelMessage(channelName, 0);
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
	    DummyClient moe = group.getClient(MOE);
	    moe.assertJoinedChannel(channelName);
	    nonMember.sendChannelMessage(channelName, 0);
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
	    DummyClient moe = group.getClient(MOE);
	    moe.assertJoinedChannel(channelName);
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
	    DummyClient moe = group.getClient(MOE);
	    moe.assertJoinedChannel(channelName);
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
	    DummyClient moe = group.getClient(MOE);
	    moe.assertJoinedChannel(channelName);
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
	    DummyClient moe = group.getClient(MOE);
	    moe.assertJoinedChannel(channelName);
	    int numMessages = 10;
	    for (int i = 0; i < numMessages; i++) {
		moe.sendChannelMessage(channelName, i);
	    }
	    Thread.sleep(4000);
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
	DummyClient client = new DummyClient(user);
	client.connect(port).login();

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
	client.assertJoinedChannel(channelName);
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
	DummyClient client = new DummyClient(user);
	client.connect(port).login();

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
	int count = getObjectCount();
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
        assertEquals(count, getObjectCount());
    }

    @Test
    @IntegrationTest
    public void testChannelCloseWithMembers() throws Exception {
	final String channelName = "closeTest";
	String user = "user";
	DummyClient client = new DummyClient(user);
	client.connect(serverNode.getAppPort()).login();
	try {
	    int count = getObjectCount();
	    createChannel(channelName);
	    printServiceBindings("after channel create");
	    joinUsers(channelName, user);
	    client.assertJoinedChannel(channelName);
	    client.sendChannelMessage(channelName, 0);
	    checkChannelMessagesReceived(client, channelName, 1);
	
	    txnScheduler.runTask(new TestAbstractKernelRunnable() {
		public void run() {
		    Channel channel = getChannel(channelName);
		    dataService.removeObject(channel);
		} }, taskOwner);
	    Thread.sleep(100);
	    txnScheduler.runTask(new TestAbstractKernelRunnable() {
	        public void run() {
		    Channel channel = getChannel(channelName);
		    if (getChannel(channelName) != null) {
			fail("obtained closed channel");
		    }
		} }, taskOwner);
	    printServiceBindings("after channel close");
	    Thread.sleep(6000);
	    printServiceBindings("after sleep");
	    assertEquals(count, getObjectCount());
	} finally {
	    client.disconnect();
	}
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

    @Test
    @IntegrationTest
    public void testSendFromClientSessionWithDelayedJoin() throws Exception {
	String user = "user";
	String channelName = "test";
	// Create channel with coordinator on server node.
	createChannel(channelName);
	SgsTestNode userNode = addNode();
	DummyClient client = new DummyClient(user);
	client.connect(userNode.getAppPort()).login();
	try {
	    holdChannelServerMethodToNode(userNode, "join");
	    joinUsers(channelName, user);
	    waitForHeldChannelServerMethodToNode(userNode);
	    for (int i = 0; i < 3; i++) {
		client.sendChannelMessage(channelName, i);
	    }
	    Thread.sleep(1000);
	    releaseChannelServerMethodHeld(userNode);
	    checkChannelMessagesReceived(client, channelName, 3);
	} finally {
	    client.disconnect();
	}
    }

    @Test
    @IntegrationTest
    public void testSendFromClientSessionWithDelayedLeave() throws Exception {
	String user = "user";
	String channelName = "test";
	// Create channel with coordinator on server node.
	createChannel(channelName);
	SgsTestNode userNode = addNode();
	DummyClient client = new DummyClient(user);
	client.connect(userNode.getAppPort()).login();
	try {
	    joinUsers(channelName, user);
	    client.assertJoinedChannel(channelName);
	    client.sendChannelMessage(channelName, 0);
	    checkChannelMessagesReceived(client, channelName, 1);
	    holdChannelServerMethodToNode(userNode, "leave");
	    leaveUsers(channelName, user);
	    waitForHeldChannelServerMethodToNode(userNode);
	    client.sendChannelMessage(channelName, 0);
	    Thread.sleep(1000);
	    releaseChannelServerMethodHeld(userNode);
	    client.assertLeftChannel(channelName);
	    assertNull(client.nextChannelMessage());
	} finally {
	    client.disconnect();
	}
    }

    @Test
    @IntegrationTest
    public void testSendFromClientSessionWithDelayedJoinLeave()
	throws Exception
    {
	String user = "user";
	String channelName = "test";
	// Create channel with coordinator on server node.
	createChannel(channelName);
	SgsTestNode userNode = addNode();
	DummyClient client = new DummyClient(user);
	client.connect(userNode.getAppPort()).login();
	try {
	    holdChannelServerMethodToNode(userNode, "join");
	    joinUsers(channelName, user);
	    waitForHeldChannelServerMethodToNode(userNode);
	    leaveUsers(channelName, user);
	    client.sendChannelMessage(channelName, 0);
	    releaseChannelServerMethodHeld(userNode);
	    client.assertLeftChannel(channelName);
	    Thread.sleep(3000);
	    assertNull(client.nextChannelMessage());
	} finally {
	    client.disconnect();
	}
    }
    
    @Test
    @IntegrationTest
    public void testSendFromClientSessionWithDelayedJoinLeaveJoin()
	throws Exception
    {
	String user = "user";
	String channelName = "test";
	// Create channel with coordinator on server node.
	createChannel(channelName);
	SgsTestNode userNode = addNode();
	DummyClient client = new DummyClient(user);
	client.connect(userNode.getAppPort()).login();
	try {
	    holdChannelServerMethodToNode(userNode, "join");
	    joinUsers(channelName, user);
	    waitForHeldChannelServerMethodToNode(userNode);
	    leaveUsers(channelName, user);
	    joinUsers(channelName, user);
	    for (int i = 0; i < 2; i++) {
		client.sendChannelMessage(channelName, i);
	    }
	    Thread.sleep(1000);
	    releaseChannelServerMethodHeld(userNode);
	    client.assertJoinedChannel(channelName);
	    checkChannelMessagesReceived(client, channelName, 2);
	    for (int i = 0; i < 2; i++) {
		client.sendChannelMessage(channelName, i);
	    }
	    checkChannelMessagesReceived(client, channelName, 2);
	} finally {
	    client.disconnect();
	}
    }
    @Test
    @IntegrationTest
    public void testSendFromClientSessionWithDelayedJoinAndCoordinatorCrash()
	throws Exception
    {
	String user = "user";
	String channelName = "test";
	SgsTestNode coordinatorNode = addNode();
	// Create channel with coordinator on coordinator node.
	createChannel(channelName, null, coordinatorNode);
	SgsTestNode userNode = addNode();
	DummyClient client = new DummyClient(user);
	client.connect(userNode.getAppPort()).login();
	try {
	    holdChannelServerMethodToNode(userNode, "join");
	    joinUsers(channelName, user);
	    waitForHeldChannelServerMethodToNode(userNode);
	    for (int i = 0; i < 3; i++) {
		client.sendChannelMessage(channelName, i);
	    }
	    Thread.sleep(1000);
	    // Shutdown coordinator node.
	    coordinatorNode.shutdown(false);
	    Thread.sleep(3000);
	    checkChannelMessagesReceived(client, channelName, 3);
	} finally {
	    client.disconnect();
	}
    }

    @Test
    @IntegrationTest
    public void
	testSendFromClientSessionWithDelayedLeaveAndCoordinatorCrash()
	    throws Exception
    {
	String user = "user";
	String channelName = "test";
	SgsTestNode coordinatorNode = addNode();
	// Create channel with coordinator on server node.
	createChannel(channelName, null, coordinatorNode);
	SgsTestNode userNode = addNode();
	DummyClient client = new DummyClient(user);
	client.connect(userNode.getAppPort()).login();
	try {
	    joinUsers(channelName, user);
	    client.assertJoinedChannel(channelName);
	    client.sendChannelMessage(channelName, 0);
	    checkChannelMessagesReceived(client, channelName, 1);
	    holdChannelServerMethodToNode(userNode, "leave");
	    leaveUsers(channelName, user);
	    waitForHeldChannelServerMethodToNode(userNode);
	    client.sendChannelMessage(channelName, 0);
	    Thread.sleep(1000);
	    coordinatorNode.shutdown(false);
	    Thread.sleep(3000);
	    client.assertLeftChannel(channelName);
	    assertNull(client.nextChannelMessage());
	} finally {
	    client.disconnect();
	}
    }

    @Test
    @IntegrationTest
    public void
	testSendFromClientSessionWithDelayedJoinLeaveAndCoordinatorCrash()
	    throws Exception
    {
	String user = "user";
	String channelName = "test";
	SgsTestNode coordinatorNode = addNode();
	// Create channel with coordinator on coordinator node.
	createChannel(channelName, null, coordinatorNode);
	SgsTestNode userNode = addNode();
	DummyClient client = new DummyClient(user);
	client.connect(userNode.getAppPort()).login();
	try {
	    holdChannelServerMethodToNode(userNode, "join");
	    joinUsers(channelName, user);
	    waitForHeldChannelServerMethodToNode(userNode);
	    leaveUsers(channelName, user);
	    client.sendChannelMessage(channelName, 0);
	    Thread.sleep(1000);
	    coordinatorNode.shutdown(false);
	    Thread.sleep(3000);
	    client.assertLeftChannel(channelName);
	    assertNull(client.nextChannelMessage());
	} finally {
	    client.disconnect();
	}
    }
    
    @Test
    @IntegrationTest
    public void
	testSendFromClientSessionWithDelayedJoinLeaveJoinAndCoordinatorCrash()
	    throws Exception
    {
	String user = "user";
	String channelName = "test";
	SgsTestNode coordinatorNode = addNode();
	// Create channel with coordinator on server node.
	createChannel(channelName, null, coordinatorNode);
	SgsTestNode userNode = addNode();
	DummyClient client = new DummyClient(user);
	client.connect(userNode.getAppPort()).login();
	try {
	    holdChannelServerMethodToNode(userNode, "join");
	    joinUsers(channelName, user);
	    waitForHeldChannelServerMethodToNode(userNode);
	    leaveUsers(channelName, user);
	    joinUsers(channelName, user);
	    for (int i = 0; i < 2; i++) {
		client.sendChannelMessage(channelName, i);
	    }
	    Thread.sleep(1000);
	    coordinatorNode.shutdown(false);
	    Thread.sleep(3000);
	    client.assertJoinedChannel(channelName);
	    checkChannelMessagesReceived(client, channelName, 2);
	    for (int i = 0; i < 2; i++) {
		client.sendChannelMessage(channelName, i);
	    }
	    checkChannelMessagesReceived(client, channelName, 2);
	} finally {
	    client.disconnect();
	}
    }

    @Test
    @IntegrationTest
    public void testSendWithCoordinatorCrash() throws Exception {
	String user = "user";
	String channelName = "test";
	SgsTestNode coordinatorNode = addNode();
	// Create channel with coordinator on coordinator node.
	createChannel(channelName, null, coordinatorNode);
	SgsTestNode userNode = addNode();
	DummyClient client = new DummyClient(user);
	client.connect(userNode.getAppPort()).login();
	try {
	    joinUsers(channelName, user);
	    client.assertJoinedChannel(channelName);
	    holdChannelServerMethodToNode(userNode, "send");
	    for (int i = 0; i < 3; i++) {
		client.sendChannelMessage(channelName, i);
	    }
	    waitForHeldChannelServerMethodToNode(userNode);
	    // Shutdown coordinator node.
	    coordinatorNode.shutdown(false);
	    Thread.sleep(5000);
	    checkChannelMessagesReceived(client, channelName, 3);
	} finally {
	    client.disconnect();
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
    
    private DummyClient newClient() {
	DummyClient client = new DummyClient("dummy");
	client.connect(port).login();
	return client;
    }
    
    private void closeChannel(final String name) throws Exception {

	txnScheduler.runTask(new TestAbstractKernelRunnable() {
	    public void run() {
		Channel channel = channelService.getChannel(name);
		dataService.removeObject(channel);
	    }}, taskOwner);
    }
}
