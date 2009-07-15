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

import com.sun.sgs.impl.service.nodemap.DirectiveNodeAssignmentPolicy;
import com.sun.sgs.service.ClientSessionStatusListener;
import com.sun.sgs.service.SimpleCompletionHandler;
import com.sun.sgs.test.util.SgsTestNode;
import com.sun.sgs.tools.test.FilteredJUnit3TestRunner;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import junit.framework.TestSuite;
import org.junit.runner.RunWith;

@RunWith(FilteredJUnit3TestRunner.class)
public class TestChannelServiceImplRelocatingSessions
    extends AbstractChannelServiceTest
{
    
    /** Constructs a test instance. */
    public TestChannelServiceImplRelocatingSessions(String name)
	throws Exception
    {
	super(name);
    }

    // -- Relocation test cases --

    public void testChannelJoinAndRelocate() throws Exception {
	String channelName = "foo";
	DirectiveNodeAssignmentPolicy.instance.setRoundRobin(false);
	createChannel(channelName);
	// All clients will log into server node.
	ClientGroup group = new ClientGroup(someUsers);
	addNodes("host2", "host3");
	
	try {
	    // Join all users to channel and send some messages on channel.
	    joinUsers(channelName, someUsers);
	    checkUsersJoined(channelName, someUsers);
	    int count = getChannelServiceBindingCount();
	    sendMessagesToChannel(channelName, group, 2);
	    
	    // Move clients to new nodes.
	    printServiceBindings("before relocate");
	    moveClient(group.getClient(someUsers.get(0)), serverNode,
		       additionalNodes.get("host2"));
	    moveClient(group.getClient(someUsers.get(1)), serverNode,
		       additionalNodes.get("host3"));	    
	    printServiceBindings("after relocate");
	    
	    // Make sure all members are still joined and can receive messages.
	    checkUsersJoined(channelName, someUsers);
	    sendMessagesToChannel(channelName, group, 2);
	    assertEquals(count, getChannelServiceBindingCount());

	    // Disconnect each client and make sure that memberships/bindings
	    // are cleaned up.
	    group.disconnect(true);
	    Thread.sleep(WAIT_TIME);
	    checkUsersJoined(channelName, new ArrayList<String>());
	    assertEquals(count - someUsers.size(),
			 getChannelServiceBindingCount());
	    
	} finally {
	    group.disconnect(false);
	}
    }

    public void testChannelJoinAndRelocateThrice()
	throws Exception
    {
	String channelName = "foo";
	createChannel(channelName);
	// All clients will log into the server node.
	DirectiveNodeAssignmentPolicy.instance.setRoundRobin(false);
	ClientGroup group = new ClientGroup(someUsers);
	String[] hosts = new String[] {"host2", "host3", "host4"};
	addNodes(hosts);
	
	try {
	    // Join all users to channel and send some messages on channel.
	    joinUsers(channelName, someUsers);
	    checkUsersJoined(channelName, someUsers);
	    int count = getChannelServiceBindingCount();
	    sendMessagesToChannel(channelName, group, 2);
	    
	    // Move clients to new nodes.
	    DummyClient relocatingClient =
		group.getClient(someUsers.get(0));
	    SgsTestNode oldNode = serverNode;
	    for (String host : hosts) {
		SgsTestNode newNode = additionalNodes.get(host);
		printServiceBindings("before relocate");
		moveClient(relocatingClient, oldNode, newNode);
		// Make sure all members are still joined and can receive
		// messages. 
		checkUsersJoined(channelName, someUsers);
		sendMessagesToChannel(channelName, group, 2);
		assertEquals(count, getChannelServiceBindingCount());
		oldNode = newNode;
	    }
	    printServiceBindings("after relocate");
	    // Disconnect each client and make sure that memberships/bindings
	    // are cleaned up.
	    group.disconnect(true);
	    checkUsersJoined(channelName, new ArrayList<String>());
	    printServiceBindings("after disconnect");
	    assertEquals(count - someUsers.size(),
			 getChannelServiceBindingCount());
	    
	} finally {
	    group.disconnect(false);
	}
    }

    public void testChannelJoinAndRelocateWithOldNodeFailure()
	throws Exception
    {
	List<String> users = new ArrayList<String>(someUsers);
	String channelName = "foo";
	createChannel(channelName);
	// All clients will log into the server node.
	DirectiveNodeAssignmentPolicy.instance.setRoundRobin(false);
	ClientGroup group = new ClientGroup(users);
	String[] hosts = new String[] {"host2", "host3", "host4"};
	addNodes(hosts);
	
	try {
	    // Join all users to channel and send some messages on channel.
	    joinUsers(channelName, users);
	    checkUsersJoined(channelName, users);
	    int count = getChannelServiceBindingCount();
	    sendMessagesToChannel(channelName, group, 2);
	    
	    // move client to host2
	    DummyClient relocatingClient =
		group.getClient(users.get(0));
	    SgsTestNode node = additionalNodes.get(hosts[0]);
	    printServiceBindings("before first relocation");
	    moveClient(relocatingClient, serverNode, node);
	    printServiceBindings("after first relocation");
	    // notify client to move to host3, but don't relocate yet.
	    moveIdentityAndWaitForRelocationNotification(
		relocatingClient, node, additionalNodes.get(hosts[1]));
	    printServiceBindings("after second relocate notification");
	    // channel bindings should include new binding for
	    // session, and may or may include old because the old binding
	    // may have been cleaned up already.
	    int countAfterRelocateNotification =
		getChannelServiceBindingCount();
	    assertTrue(countAfterRelocateNotification == count ||
		       countAfterRelocateNotification == count + 1);
	    // crash host2.
	    node.shutdown(false);
	    // give recovery a chance.
	    printServiceBindings("after crash");
	    Thread.sleep(WAIT_TIME*3);
	    printServiceBindings("after crash & timeout");
	    users.remove(relocatingClient.name);
	    group.removeSessionsFromGroup(node.getAppPort());
	    checkUsersJoined(channelName, users);
	    sendMessagesToChannel(channelName, group, 2);
	    assertEquals(count - 1,
			 getChannelServiceBindingCount());
	    // Disconnect each client and make sure that membership/bindings
	    // are cleaned up.
	    group.disconnect(true);
	    checkUsersJoined(channelName, new ArrayList<String>());
	    assertEquals(count - users.size() - 1,
			 getChannelServiceBindingCount());
	    
	} finally {
	    group.disconnect(false);
	}
    }

    public void testChannelJoinDuringRelocatePreparation()
	throws Exception
    {
	String channelName = "foo";
	DirectiveNodeAssignmentPolicy.instance.setRoundRobin(false);
	createChannel(channelName);
	// All clients will log into server node.
	ClientGroup group = new ClientGroup(someUsers);
	addNodes("host2");
	MySessionStatusListener mySessionStatusListener =
	    new MySessionStatusListener();
	serverNode.getClientSessionService().
	    addSessionStatusListener(mySessionStatusListener);
	
	try {
	    // Initiate client relocation to new node.
	    printServiceBindings("before relocate");
	    DummyClient relocatingClient = group.getClient(someUsers.get(0));
	    SgsTestNode newNode = additionalNodes.get("host2");
	    moveIdentity(relocatingClient, serverNode, newNode);
	    SimpleCompletionHandler handler =
		mySessionStatusListener.waitForPrepare();
	    
	    // Join all users (including relocating client) to channel during
	    // prepare phase.
	    joinUsers(channelName, someUsers);
	    printServiceBindings("after join, but before relocation complete");

	    Thread.sleep(200);
	    // Mark preparation completed.
	    handler.completed();
	    
	    // Finish relocation.
	    relocatingClient.relocate(0, true, true);
	    
	    // Make sure all members are joined and can receive messages.
	    checkUsersJoined(channelName, someUsers);
	    printServiceBindings("after all joins");
	    sendMessagesToChannel(channelName, group, 2);

	    // Disconnect each client and make sure that memberships/bindings
	    // are cleaned up.
	    group.disconnect(true);
	    Thread.sleep(WAIT_TIME);
	    checkUsersJoined(channelName, new ArrayList<String>());
	    
	} finally {
	    group.disconnect(false);
	}
	
    }

    public void testChannelJoinDuringRelocate() throws Exception {
	String channelName = "foo";
	DirectiveNodeAssignmentPolicy.instance.setRoundRobin(false);
	createChannel(channelName);
	// All clients will log into server node.
	ClientGroup
	    group = new ClientGroup(someUsers);
	addNodes("host2");
	
	try {
	    // Initiate client relocation to new node.
	    printServiceBindings("before relocate");
	    DummyClient relocatingClient = group.getClient(someUsers.get(0));
	    moveIdentityAndWaitForRelocationNotification(
		relocatingClient, serverNode, additionalNodes.get("host2"));
	    
	    // Join all users (including relocating client) to channel.
	    joinUsers(channelName, someUsers);
	    printServiceBindings("after join, but before relocation complete");

	    // Finish relocation.
	    relocatingClient.relocate(0, true, true);
	    
	    // Make sure all members are joined and can receive messages.
	    checkUsersJoined(channelName, someUsers);
	    printServiceBindings("after all joins");
	    sendMessagesToChannel(channelName, group, 2);

	    // Disconnect each client and make sure that memberships/bindings
	    // are cleaned up.
	    group.disconnect(true);
	    Thread.sleep(WAIT_TIME);
	    checkUsersJoined(channelName, new ArrayList<String>());
	    
	} finally {
	    group.disconnect(false);
	}
    }

    // -- Other methods --

    /**
     * Reassigns the client's identity from {@code oldNode} to {@code newNode}.
     */
    private void moveIdentity(
	DummyClient client, SgsTestNode oldNode, SgsTestNode newNode)
	throws Exception
    {
	DirectiveNodeAssignmentPolicy.instance.
	    moveIdentity(client.name, oldNode.getNodeId(), newNode.getNodeId());
    }
    
    /**
     * Reassigns the client's identity from {@code oldNode} to {@code newNode},
     * and waits for the client to receive the relocation notification.  The
     * client does not relocated unless instructed to do so via a {@code
     * relocate} invocation.
     */
    private void moveIdentityAndWaitForRelocationNotification(
	DummyClient client, SgsTestNode oldNode, SgsTestNode newNode)
	throws Exception
    {
	System.err.println("reassigning identity:" + client.name +
			   " from node: " + oldNode.getNodeId() +
			   " to node: " + newNode.getNodeId());
	moveIdentity(client, oldNode, newNode);
	client.waitForRelocationNotification(0);
    }
    
    /**
     * Moves the client from the server node to a new node.
     */
    private void moveClient(DummyClient client, SgsTestNode oldNode,
			    SgsTestNode newNode)
	throws Exception
    {
	moveIdentityAndWaitForRelocationNotification(
	    client, oldNode, newNode);
	client.relocate(0, true, true);
    }

    private class MySessionStatusListener
	implements ClientSessionStatusListener
    {
	private SimpleCompletionHandler handler = null;
	    
	public void disconnected(final BigInteger sessionRefId) {}

	public void prepareToRelocate(BigInteger sessionRefId, long newNodeId,
				      SimpleCompletionHandler handler)
	{
	    synchronized (this) {
		this.handler = handler;
		notifyAll();
	    }
	}
	
	public void relocated(BigInteger sessionRefId) {
	}

	SimpleCompletionHandler waitForPrepare() {
	    synchronized (this) {
		if (handler == null) {
		    try {
			wait(WAIT_TIME);
		    } catch (InterruptedException e) {
		    }
		}
	    }
	    return handler;
	}
	
	void setPrepared() {
	    handler.completed();
	}
    }
}
