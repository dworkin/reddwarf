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
    private final static String REX = "rex";
    
    private final String[] oneUser = new String[] { REX };

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
	    sendMessagesToChannel(channelName, 2);
	    checkChannelMessagesReceived(group, channelName, 2);
	    
	    // Move clients to new nodes.
	    moveClient(group.getClient(MOE), serverNode, node1);
	    moveClient(group.getClient(LARRY), serverNode, node2);
	    
	    // Make sure all members are still joined and can receive messages.
	    checkUsersJoined(channelName, someUsers);
	    sendMessagesToChannel(channelName, 2);
	    checkChannelMessagesReceived(group, channelName, 2);

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
	// Client will log into server node.
	DummyClient client = new DummyClient(REX);
	client.connect(port).login();
	SgsTestNode node1 = addNode();
	
	try {
	    // Join all users to channel and send some messages on channel.
	    joinUsers(channelName, oneUser);
	    checkUsersJoined(channelName, oneUser);
	    
	    sendMessagesToChannel(channelName, 2);
	    checkChannelMessagesReceived(client, channelName, 2);
	    
	    // Move clients to new nodes.
	    moveClient(client, serverNode, node1);
	    
	    // Make sure all members are still joined and leave after
	    // relocation works correctly.
	    checkUsersJoined(channelName, oneUser);
	    leaveUsers(channelName, oneUser);
	    client.assertLeftChannel(channelName);
	    checkUsersJoined(channelName, noUsers);
	    
	} finally {
	    client.disconnect();
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
	    sendMessagesToChannel(channelName, 2);
	    checkChannelMessagesReceived(group, channelName, 2);
	    
	    // Move clients to new nodes.
	    DummyClient relocatingClient = group.getClient(MOE);
	    SgsTestNode oldNode = serverNode;
	    for (SgsTestNode newNode : nodes) {
		moveClient(relocatingClient, oldNode, newNode);
		// Make sure all members are still joined and can receive
		// messages. 
		checkUsersJoined(channelName, someUsers);
		sendMessagesToChannel(channelName, 2);
		checkChannelMessagesReceived(group, channelName, 2);
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
	String channelName = "foo";
	
	createChannel(channelName);
	// All clients will log into the server node.
	DirectiveNodeAssignmentPolicy.instance.setRoundRobin(false);
	ClientGroup group = new ClientGroup(someUsers);
	SgsTestNode node1 = addNode();
	SgsTestNode node2 = addNode();
	
	try {
	    // Join all users to channel and send some messages on channel.
	    joinUsers(channelName, someUsers);
	    checkUsersJoined(channelName, someUsers);
	    sendMessagesToChannel(channelName, 2);
	    checkChannelMessagesReceived(group, channelName, 2);
	    
	    // move client to node1
	    DummyClient relocatingClient =
		group.getClient(MOE);
	    moveClient(relocatingClient, serverNode, node1);
	    // notify client to move to node2, but don't relocate yet.
	    moveIdentityAndWaitForRelocationNotification(
		relocatingClient, node1, node2);
	    // crash node1.
	    node1.shutdown(false);
	    // give recovery a chance.
	    Thread.sleep(WAIT_TIME*3);
	    group.removeSessionsFromGroup(node1.getAppPort());
	    checkUsersJoined(channelName, LARRY, CURLY);
	    sendMessagesToChannel(channelName, 2);
	    checkChannelMessagesReceived(group, channelName, 2);
	    // Disconnect each client and make sure that membership
	    // is cleaned up.
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
	ClientGroup group = new ClientGroup(oldNode.getAppPort(), oneUser);
	
	try {
	    // Initiate client relocation to new node.
	    DummyClient relocatingClient = group.getClient(REX);
	    // Hold up joins send to oldNode
	    holdChannelServerMethodToNode(oldNode, "join");
	    joinUsers(channelName, oneUser); 
	    waitForHeldChannelServerMethodToNode(oldNode);
	    moveIdentity(relocatingClient, oldNode, newNode);
	    Thread.sleep(200);
	    releaseChannelServerMethodHeld(oldNode);
	    
	    // Finish relocation.
	    relocatingClient.relocate(0, true, true);
	    
	    // Make sure all members are joined and can receive messages.
	    checkUsersJoined(channelName, oneUser);
	    sendMessagesToChannel(channelName, 2);
	    checkChannelMessagesReceived(group, channelName, 2);

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
	ClientGroup group = new ClientGroup(oldNode.getAppPort(), oneUser);
	
	try {
	    // Initiate client relocation to new node.
	    DummyClient relocatingClient = group.getClient(REX);
	    // Hold up "leave" to oldNode
	    holdChannelServerMethodToNode(oldNode, "leave");
	    joinUsers(channelName, oneUser);
	    relocatingClient.assertJoinedChannel(channelName);
	    checkUsersJoined(channelName, oneUser);
	    leaveUsers(channelName, oneUser);
	    waitForHeldChannelServerMethodToNode(oldNode);
	    moveIdentity(relocatingClient, oldNode, newNode);
	    Thread.sleep(200);
	    releaseChannelServerMethodHeld(oldNode);
	    
	    // Finish relocation.
	    relocatingClient.relocate(0, true, true);
	    
	    // Make sure that the session got the leave message and that
	    // there are no channel members.
	    relocatingClient.assertLeftChannel(channelName);
	    Thread.sleep(2000);
	    checkUsersJoined(channelName, noUsers);
	    
	} finally {
	    group.disconnect(false);
	}
    }
    

    @Test
    @IntegrationTest
    public void testChannelSendToOldNodeDuringRelocate()
	throws Exception
    {
	String channelName = "foo";
	DirectiveNodeAssignmentPolicy.instance.setRoundRobin(false);
	// channel coordinator is on server node
	createChannel(channelName);
	SgsTestNode oldNode = addNode();
	SgsTestNode newNode = addNode();
	
	// Client will log into "oldNode"
	ClientGroup group = new ClientGroup(oldNode.getAppPort(), oneUser);
	
	try {
	    // Initiate client relocation to new node.
	    DummyClient relocatingClient = group.getClient(REX);
	    joinUsers(channelName, oneUser);
	    relocatingClient.assertJoinedChannel(channelName);
	    // Hold up "send" to old node.
	    holdChannelServerMethodToNode(oldNode, "send");
	    sendMessagesToChannel(channelName, 3);
	    waitForHeldChannelServerMethodToNode(oldNode);
	    moveIdentityAndWaitForRelocationNotification(
		relocatingClient, oldNode, newNode);
	    releaseChannelServerMethodHeld(oldNode);
	    
	    // Finish relocation.
	    relocatingClient.relocate(0, true, true);
	    
	    // Make sure all members are joined and can receive messages.
	    checkUsersJoined(channelName, oneUser);
	    checkChannelMessagesReceived(group, channelName, 3);
	    
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
	ClientGroup group = new ClientGroup(oldNode.getAppPort(), oneUser);
	
	try {
	    // Initiate client relocation to new node.
	    DummyClient relocatingClient = group.getClient(REX);
	    // Hold up "join" to oldNode
	    holdChannelServerMethodToNode(oldNode, "join");
	    joinUsers(channelName, oneUser);
	    waitForHeldChannelServerMethodToNode(oldNode);

	    moveClient(relocatingClient, oldNode, newNode);
	    // Release "join" to oldNode.
	    releaseChannelServerMethodHeld(oldNode);
	    
	    // Make sure all members are joined and can receive messages.
	    checkUsersJoined(channelName, oneUser);
	    sendMessagesToChannel(channelName, 2);
	    checkChannelMessagesReceived(group, channelName, 2);

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
	ClientGroup group = new ClientGroup(oldNode.getAppPort(), oneUser);
	
	try {
	    // Initiate client relocation to new node.
	    DummyClient relocatingClient = group.getClient(REX);
	    // Hold up "leave" to oldNode
	    holdChannelServerMethodToNode(oldNode, "leave");
	    joinUsers(channelName, oneUser);
	    relocatingClient.assertJoinedChannel(channelName);
	    leaveUsers(channelName, oneUser);
	    waitForHeldChannelServerMethodToNode(oldNode);

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
	ClientGroup group = new ClientGroup(oldNode.getAppPort(), oneUser);
	
	try {
	    // Initiate client relocation to new node.
	    DummyClient relocatingClient = group.getClient(REX);
	    // Hold up joins send to oldNode
	    holdChannelServerMethodToNode(oldNode, "join");
	    joinUsers(channelName, oneUser); 
	    waitForHeldChannelServerMethodToNode(oldNode);
	    
	    moveClient(relocatingClient, oldNode, newNode1);
	    moveClient(relocatingClient, newNode1, newNode2);
	    // Release "join" to oldNode.
	    releaseChannelServerMethodHeld(oldNode);
	    
	    // Make sure all members are joined and can receive messages.
	    checkUsersJoined(channelName, oneUser);
	    sendMessagesToChannel(channelName, 2);
	    checkChannelMessagesReceived(group, channelName, 2);

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
	    DummyClient relocatingClient = group.getClient(MOE);
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
	    sendMessagesToChannel(channelName2, 2);
	    checkChannelMessagesReceived(group, channelName2, 2);
	    sendMessagesToChannel(channelName1, 2);
	    checkChannelMessagesReceived(group, channelName1, 2);

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
	    DummyClient relocatingClient = group.getClient(MOE);
	    moveIdentityAndWaitForRelocationNotification(
		relocatingClient, serverNode, newNode);
	    
	    // Join all users (including relocating client) to channel.
	    joinUsers(channelName, someUsers);

	    // Finish relocation.
	    relocatingClient.relocate(0, true, true);

	    // Make sure all members are joined and can receive messages.
	    checkUsersJoined(channelName, someUsers);
	    sendMessagesToChannel(channelName, 2);
	    checkChannelMessagesReceived(group, channelName, 2);

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
    public void testChannelLeaveAllAfterRelocate() throws Exception {
	String channelName = "foo";
	DirectiveNodeAssignmentPolicy.instance.setRoundRobin(false);
	// channel coordinator is on server node
	createChannel(channelName);
	SgsTestNode oldNode = addNode();
	SgsTestNode newNode = addNode();
	
	// Client will log into "oldNode"
	ClientGroup group = new ClientGroup(oldNode.getAppPort(), oneUser);
	
	try {
	    // Initiate client relocation to new node.
	    DummyClient relocatingClient = group.getClient(REX);
	    joinUsers(channelName, oneUser);
	    relocatingClient.assertJoinedChannel(channelName);
	    holdChannelServerMethodToNode(oldNode, "close");
	    // send leaveAll...
	    leaveAll(channelName);
	    // Hold up close sent to oldNode
	    waitForHeldChannelServerMethodToNode(oldNode);
	    
	    moveClient(relocatingClient, oldNode, newNode);
	    Thread.sleep(200);
	    releaseChannelServerMethodHeld(oldNode);
	    
	    // Make sure that relocating session got "leave" message.
	    checkUsersJoined(channelName, noUsers);
	    relocatingClient.assertLeftChannel(channelName);
	    
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
