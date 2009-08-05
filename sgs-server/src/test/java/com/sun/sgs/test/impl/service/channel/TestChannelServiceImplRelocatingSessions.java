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
import com.sun.sgs.test.util.TestAbstractKernelRunnable;
import com.sun.sgs.tools.test.FilteredNameRunner;
import com.sun.sgs.tools.test.IntegrationTest;
import java.io.Serializable;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(FilteredNameRunner.class)
public class TestChannelServiceImplRelocatingSessions
    extends AbstractChannelServiceTest
{
    private final List<String> oneUser =
	Arrays.asList(new String[] { "rex" });

    private final List<String> noUsers =
	new ArrayList<String>();

    /** Constructs a test instance. */
    public TestChannelServiceImplRelocatingSessions() throws Exception {
    }
    
    // -- Relocation test cases --

    @Test
    @IntegrationTest
    public void testChannelJoinAndRelocate() throws Exception {
	String channelName = "foo";
	DirectiveNodeAssignmentPolicy.instance.setRoundRobin(false);
	createChannel(channelName);
	// All clients will log into server node.
	ClientGroup group = new ClientGroup(someUsers);
	SgsTestNode node1 = addNode();
	SgsTestNode node2 = addNode();
	
	try {
	    // Join all users to channel and send some messages on channel.
	    joinUsers(channelName, someUsers);
	    checkUsersJoined(channelName, someUsers);
	    sendMessagesToChannel(channelName, group, 2);
	    
	    // Move clients to new nodes.
	    moveClient(group.getClient(someUsers.get(0)), serverNode, node1);
	    moveClient(group.getClient(someUsers.get(1)), serverNode, node2);
	    
	    // Make sure all members are still joined and can receive messages.
	    checkUsersJoined(channelName, someUsers);
	    sendMessagesToChannel(channelName, group, 2);

	    // Disconnect each client and make sure that memberships/bindings
	    // are cleaned up.
	    group.disconnect(true);
	    Thread.sleep(WAIT_TIME);
	    checkUsersJoined(channelName, noUsers);
	    
	} finally {
	    group.disconnect(false);
	}
    }

    @Test
    @IntegrationTest
    public void testChannelLeaveAfterRelocate() throws Exception {
	String channelName = "foo";
	DirectiveNodeAssignmentPolicy.instance.setRoundRobin(false);
	createChannel(channelName);
	// All clients will log into server node.
	ClientGroup group = new ClientGroup(oneUser);
	SgsTestNode node1 = addNode();
	
	try {
	    // Join all users to channel and send some messages on channel.
	    joinUsers(channelName, oneUser);
	    checkUsersJoined(channelName, oneUser);
	    
	    sendMessagesToChannel(channelName, group, 2);
	    
	    // Move clients to new nodes.
	    DummyClient client = group.getClient(oneUser.get(0));
	    moveClient(client, serverNode, node1);
	    
	    // Make sure all members are still joined and can receive messages.
	    checkUsersJoined(channelName, oneUser);
	    leaveUsers(channelName, oneUser);
	    client.assertLeftChannel(channelName);
	    checkUsersJoined(channelName, noUsers);
	    
	} finally {
	    group.disconnect(false);
	}
    }

    @Test
    @IntegrationTest
    public void testChannelJoinAndRelocateMultipleTimes()
	throws Exception
    {
	String channelName = "foo";
	createChannel(channelName);
	// All clients will log into the server node.
	DirectiveNodeAssignmentPolicy.instance.setRoundRobin(false);
	ClientGroup group = new ClientGroup(someUsers);
	Set<SgsTestNode> nodes = addNodes(3);
	
	try {
	    // Join all users to channel and send some messages on channel.
	    joinUsers(channelName, someUsers);
	    checkUsersJoined(channelName, someUsers);
	    sendMessagesToChannel(channelName, group, 2);
	    
	    // Move clients to new nodes.
	    DummyClient relocatingClient =
		group.getClient(someUsers.get(0));
	    SgsTestNode oldNode = serverNode;
	    for (SgsTestNode newNode : nodes) {
		moveClient(relocatingClient, oldNode, newNode);
		// Make sure all members are still joined and can receive
		// messages. 
		checkUsersJoined(channelName, someUsers);
		sendMessagesToChannel(channelName, group, 2);
		oldNode = newNode;
	    }
	    // Disconnect each client and make sure that memberships/bindings
	    // are cleaned up.
	    group.disconnect(true);
	    checkUsersJoined(channelName, noUsers);
	    
	} finally {
	    group.disconnect(false);
	}
    }

    @Test
    @IntegrationTest
    public void testChannelJoinAndRelocateWithOldNodeFailure()
	throws Exception
    {
	List<String> users = new ArrayList<String>(someUsers);
	String channelName = "foo";
	createChannel(channelName);
	// All clients will log into the server node.
	DirectiveNodeAssignmentPolicy.instance.setRoundRobin(false);
	ClientGroup group = new ClientGroup(users);
	SgsTestNode node1 = addNode();
	SgsTestNode node2 = addNode();
	
	try {
	    // Join all users to channel and send some messages on channel.
	    joinUsers(channelName, users);
	    checkUsersJoined(channelName, users);
	    sendMessagesToChannel(channelName, group, 2);
	    
	    // move client to node1
	    DummyClient relocatingClient =
		group.getClient(users.get(0));
	    moveClient(relocatingClient, serverNode, node1);
	    // notify client to move to node2, but don't relocate yet.
	    moveIdentityAndWaitForRelocationNotification(
		relocatingClient, node1, node2);
	    // crash node1.
	    node1.shutdown(false);
	    // give recovery a chance.
	    Thread.sleep(WAIT_TIME*3);
	    users.remove(relocatingClient.name);
	    group.removeSessionsFromGroup(node1.getAppPort());
	    checkUsersJoined(channelName, users);
	    sendMessagesToChannel(channelName, group, 2);
	    // Disconnect each client and make sure that membership/bindings
	    // are cleaned up.
	    group.disconnect(true);
	    checkUsersJoined(channelName, noUsers);
	    
	} finally {
	    group.disconnect(false);
	}
    }

    @Test
    @IntegrationTest
    public void testChannelJoinToOldNodeDuringRelocate()
	throws Exception
    {
	String channelName = "foo";
	DirectiveNodeAssignmentPolicy.instance.setRoundRobin(false);
	// channel coordinator is on server node
	createChannel(channelName);
	SgsTestNode oldNode = addNode();
	SgsTestNode newNode = addNode();
	
	// Client will log into "oldNode"
	ClientGroup group = new ClientGroup(oneUser, oldNode.getAppPort());
	
	try {
	    // Initiate client relocation to new node.
	    DummyClient relocatingClient = group.getClient(oneUser.get(0));
	    // Hold up joins send to oldNode
	    holdChannelServerMethodToNode(oldNode, "join");
	    joinUsers(channelName, oneUser); 
	    
	    moveIdentity(relocatingClient, oldNode, newNode);
	    Thread.sleep(200);
	    releaseChannelServerMethodHeld(oldNode);
	    
	    // Finish relocation.
	    relocatingClient.relocate(0, true, true);
	    
	    // Make sure all members are joined and can receive messages.
	    checkUsersJoined(channelName, oneUser);
	    sendMessagesToChannel(channelName, group, 2);

	    // Disconnect each client and make sure that memberships/bindings
	    // are cleaned up.
	    group.disconnect(true);
	    Thread.sleep(WAIT_TIME);
	    checkUsersJoined(channelName, noUsers);
	    
	} finally {
	    group.disconnect(false);
	}
	
    }
    @Test
    @IntegrationTest
    public void testChannelLeaveToOldNodeDuringRelocate()
	throws Exception
    {
	String channelName = "foo";
	DirectiveNodeAssignmentPolicy.instance.setRoundRobin(false);
	// channel coordinator is on server node
	createChannel(channelName);
	SgsTestNode oldNode = addNode();
	SgsTestNode newNode = addNode();
	
	// Client will log into "oldNode"
	ClientGroup group = new ClientGroup(oneUser, oldNode.getAppPort());
	
	try {
	    // Initiate client relocation to new node.
	    DummyClient relocatingClient = group.getClient(oneUser.get(0));
	    // Hold up "leave" to oldNode
	    holdChannelServerMethodToNode(oldNode, "leave");
	    joinUsers(channelName, oneUser);
	    relocatingClient.assertJoinedChannel(channelName);
	    checkUsersJoined(channelName, oneUser);
	    leaveUsers(channelName, oneUser);
	    moveIdentity(relocatingClient, oldNode, newNode);
	    Thread.sleep(200);
	    releaseChannelServerMethodHeld(oldNode);
	    
	    // Finish relocation.
	    relocatingClient.relocate(0, true, true);
	    
	    // Make sure all members are joined and can receive messages.
	    relocatingClient.assertLeftChannel(channelName);
	    checkUsersJoined(channelName, noUsers);
	    
	} finally {
	    group.disconnect(false);
	}
    }
    

    @Test
    @IntegrationTest
    public void testChannelJoinToOldNodeAfterRelocate()
	throws Exception
    {
	String channelName = "foo";
	DirectiveNodeAssignmentPolicy.instance.setRoundRobin(false);
	// channel coordinator is on server node
	createChannel(channelName);
	SgsTestNode oldNode = addNode();
	SgsTestNode newNode = addNode();
	
	// Client will log into "oldNode"
	ClientGroup group = new ClientGroup(oneUser, oldNode.getAppPort());
	
	try {
	    // Initiate client relocation to new node.
	    DummyClient relocatingClient = group.getClient(oneUser.get(0));
	    // Hold up "join" to oldNode
	    holdChannelServerMethodToNode(oldNode, "join");
	    joinUsers(channelName, oneUser);

	    moveClient(relocatingClient, oldNode, newNode);
	    // Release "join" to oldNode.
	    releaseChannelServerMethodHeld(oldNode);
	    
	    // Make sure all members are joined and can receive messages.
	    checkUsersJoined(channelName, oneUser);
	    sendMessagesToChannel(channelName, group, 2);

	    // Disconnect each client and make sure that memberships/bindings
	    // are cleaned up.
	    group.disconnect(true);
	    Thread.sleep(WAIT_TIME);
	    checkUsersJoined(channelName, noUsers);
	    
	} finally {
	    group.disconnect(false);
	}
    }
    
    @Test
    @IntegrationTest
    public void testChannelLeaveToOldNodeAfterRelocate()
	throws Exception
    {
	String channelName = "foo";
	DirectiveNodeAssignmentPolicy.instance.setRoundRobin(false);
	// channel coordinator is on server node
	createChannel(channelName);
	SgsTestNode oldNode = addNode();
	SgsTestNode newNode = addNode();
	
	// Client will log into "oldNode"
	ClientGroup group = new ClientGroup(oneUser, oldNode.getAppPort());
	
	try {
	    // Initiate client relocation to new node.
	    DummyClient relocatingClient = group.getClient(oneUser.get(0));
	    // Hold up "leave" to oldNode
	    holdChannelServerMethodToNode(oldNode, "leave");
	    joinUsers(channelName, oneUser);
	    relocatingClient.assertJoinedChannel(channelName);
	    leaveUsers(channelName, oneUser);
	    Thread.sleep(200);
	    // Relocate client
	    moveClient(relocatingClient, oldNode, newNode);
	    // Make sure client hasn't yet received "leave" notification.
	    relocatingClient.assertJoinedChannel(channelName);
	    // Release "leave" to oldNode.
	    releaseChannelServerMethodHeld(oldNode);
	    
	    // Check that relocating client received leave message via new
	    // node. 
	    relocatingClient.assertLeftChannel(channelName);
	    checkUsersJoined(channelName, noUsers);
	    
	} finally {
	    group.disconnect(false);
	}
    }
    
    @Test
    @IntegrationTest
    public void testChannelJoinToOldNodeAfterRelocateTwice()
	throws Exception
    {
	String channelName = "foo";
	DirectiveNodeAssignmentPolicy.instance.setRoundRobin(false);
	// channel coordinator is on server node
	createChannel(channelName);
	SgsTestNode oldNode = addNode();
	SgsTestNode newNode1 = addNode();
	SgsTestNode newNode2 = addNode();
	
	// Client will log into "oldNode"
	ClientGroup group = new ClientGroup(oneUser, oldNode.getAppPort());
	
	try {
	    // Initiate client relocation to new node.
	    DummyClient relocatingClient = group.getClient(oneUser.get(0));
	    // Hold up joins send to oldNode
	    holdChannelServerMethodToNode(oldNode, "join");
	    joinUsers(channelName, oneUser); 
	    
	    moveClient(relocatingClient, oldNode, newNode1);
	    moveClient(relocatingClient, newNode1, newNode2);
	    // Release "join" to oldNode.
	    releaseChannelServerMethodHeld(oldNode);
	    
	    // Make sure all members are joined and can receive messages.
	    checkUsersJoined(channelName, oneUser);
	    sendMessagesToChannel(channelName, group, 2);

	    // Disconnect each client and make sure that memberships/bindings
	    // are cleaned up.
	    group.disconnect(true);
	    Thread.sleep(WAIT_TIME);
	    checkUsersJoined(channelName, noUsers);
	    
	} finally {
	    group.disconnect(false);
	}
	
    }
    
    @Test
    @IntegrationTest
    public void testChannelJoinDuringRelocatePreparation()
	throws Exception
    {
	String channelName1 = "foo";
	String channelName2 = "bar";
	DirectiveNodeAssignmentPolicy.instance.setRoundRobin(false);
	createChannel(channelName1);
	createChannel(channelName2);
	// All clients will log into server node.
	ClientGroup group = new ClientGroup(someUsers);
	SgsTestNode newNode = addNode();
	MySessionStatusListener mySessionStatusListener =
	    new MySessionStatusListener();
	serverNode.getClientSessionService().
	    addSessionStatusListener(mySessionStatusListener);
	
	try {

	    joinUsers(channelName1, someUsers);
	    checkUsersJoined(channelName1, someUsers);
	    
	    // Initiate client relocation to new node.
	    DummyClient relocatingClient = group.getClient(someUsers.get(0));
	    moveIdentity(relocatingClient, serverNode, newNode);
	    SimpleCompletionHandler handler =
		mySessionStatusListener.waitForPrepare();
	    
	    // Join all users (including relocating client) to channel during
	    // prepare phase.
	    joinUsers(channelName2, someUsers);

	    Thread.sleep(200);
	    // Mark preparation completed.
	    handler.completed();
	    
	    // Finish relocation.
	    relocatingClient.relocate(0, true, true);
	    
	    // Make sure all members are joined and can receive messages.
	    checkUsersJoined(channelName2, someUsers);
	    sendMessagesToChannel(channelName2, group, 2);
	    sendMessagesToChannel(channelName1, group, 2);

	    // Disconnect each client and make sure that memberships/bindings
	    // are cleaned up.
	    group.disconnect(true);
	    Thread.sleep(WAIT_TIME);
	    checkUsersJoined(channelName1, noUsers);
	    checkUsersJoined(channelName2, noUsers);
	    
	} finally {
	    group.disconnect(false);
	}
	
    }

    @Test
    @IntegrationTest
    public void testChannelJoinDuringRelocate() throws Exception {
	String channelName = "foo";
	DirectiveNodeAssignmentPolicy.instance.setRoundRobin(false);
	createChannel(channelName);
	// All clients will log into server node.
	ClientGroup
	    group = new ClientGroup(someUsers);
	SgsTestNode newNode = addNode();
	
	try {
	    // Initiate client relocation to new node.
	    DummyClient relocatingClient = group.getClient(someUsers.get(0));
	    moveIdentityAndWaitForRelocationNotification(
		relocatingClient, serverNode, newNode);
	    
	    // Join all users (including relocating client) to channel.
	    joinUsers(channelName, someUsers);

	    // Finish relocation.
	    relocatingClient.relocate(0, true, true);
	    
	    // Make sure all members are joined and can receive messages.
	    checkUsersJoined(channelName, someUsers);
	    sendMessagesToChannel(channelName, group, 2);

	    // Disconnect each client and make sure that memberships/bindings
	    // are cleaned up.
	    group.disconnect(true);
	    Thread.sleep(WAIT_TIME);
	    checkUsersJoined(channelName, noUsers);
	    
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
