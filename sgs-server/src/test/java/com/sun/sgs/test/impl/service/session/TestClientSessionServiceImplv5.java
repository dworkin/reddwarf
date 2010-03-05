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

package com.sun.sgs.test.impl.service.session;

import com.sun.sgs.impl.sharedutil.MessageBuffer;
import com.sun.sgs.test.impl.service.session.TestClientSessionServiceImplv4.
    DummyClient;
import com.sun.sgs.test.util.IdentityAssigner;
import com.sun.sgs.test.util.SgsTestNode;
import com.sun.sgs.tools.test.FilteredNameRunner;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(FilteredNameRunner.class)
public class TestClientSessionServiceImplv5 extends TestClientSessionServiceImplv4 {

    private static final String APP_NAME = "TestClientSessionServiceImplv5";
    
    private static final byte PROTOCOL_v5 = 0x05;

    private IdentityAssigner identityAssigner;
    
    /** Number of managed objects per client session: 4 (ClientSessionImpl,
     * ClientSessionWrapper, EventQueue, ManagedQueue).
     */
    private static final int MANAGED_OBJECTS_PER_SESSION = 4;

    /** Constructs a test instance. */
    public TestClientSessionServiceImplv5() throws Exception {
    }

    @Before
    public void setUp() throws Exception {
        super.setUp(null, true, APP_NAME, PROTOCOL_v5);
	identityAssigner = new IdentityAssigner(serverNode);
    }

    @Test
    public void testRelocateClientSession() throws Exception {
	String newNodeHost = "newNode";
	DummyClient client = createClientToRelocate(newNodeHost);
	int objectCount = getObjectCount();
	try {
	    SgsTestNode newNode = additionalNodes.get(newNodeHost);
	    client.relocate(newNode.getAppPort(), true, true);
	    // need to wait here to get the true object count to make sure
	    // that objects and bindings aren't cleaned up.
	    Thread.sleep(WAIT_TIME);
	    assertEquals(objectCount, getObjectCount());
	    checkBindings(1);
	    client.assertDisconnectedCallbackNotInvoked();
	} finally {
	    client.disconnect();
	}
    }

    @Test
    public void testRelocateClientSessionSendingAfterRelocate()
	throws Exception
    {
	String newNodeHost = "newNode";
	DummyClient client = createClientToRelocate(newNodeHost);
	try {
	    SgsTestNode newNode = additionalNodes.get(newNodeHost);
	    client.relocate(newNode.getAppPort(), true, true);
	    sendMessagesFromNodeToClient(serverNode, client, 4, 0);
	    sendMessagesFromNodeToClient(newNode, client, 4, 10);
	} finally {
	    client.disconnect();
	}
    }

    @Test
    public void testRelocateClientSessionSendingDuringRelocate()
	throws Exception
    {
	String newNodeHost = "newNode";
	DummyClient client = createClientToRelocate(newNodeHost);
	try {
	    SgsTestNode newNode = additionalNodes.get(newNodeHost);
	    int objectCount = getObjectCount();
	    sendMessagesFromNode(serverNode, client, 4, 0);
	    sendMessagesFromNode(newNode, client, 4, 10);
	    client.relocate(newNode.getAppPort(), true, true);
	    synchronized (client.clientReceivedMessages) {
		client.waitForClientToReceiveExpectedMessages(4);
		client.validateMessageSequence(
		    client.clientReceivedMessages, 4, 0);
		client.clientReceivedMessages.clear();
		client.waitForClientToReceiveExpectedMessages(4);
		client.validateMessageSequence(
		    client.clientReceivedMessages, 4, 10);
	    }
	    waitForExpectedObjectCount(objectCount);
	} finally {
	    client.disconnect();
	}
    }
    
    @Test
    public void testRelocateInvalidRelocationKey()  throws Exception {
	String newNodeHost = "new";
	DummyClient client = createClientToRelocate(newNodeHost);
	try {
	    SgsTestNode newNode = additionalNodes.get(newNodeHost);
	    int objectCount = getObjectCount();
	    client.relocate(newNode.getAppPort(), false, false);
	    waitForExpectedObjectCount(
		objectCount - MANAGED_OBJECTS_PER_SESSION);
	    checkBindings(0);
	    client.assertDisconnectedCallbackInvoked(false);
	} finally {
	    client.disconnect();
	}
    }

    @Test
    public void testRelocateWithInterveningClientLoginRejected()
	throws Exception
    {
	String newNodeHost = "newNode";
	DummyClient client = createClientToRelocate(newNodeHost);
	DummyClient otherClient = createDummyClient("foo");
	SgsTestNode newNode = additionalNodes.get(newNodeHost);
	try {
	    int newPort = newNode.getAppPort();
	    assertFalse(otherClient.connect(newPort).login());
	    client.relocate(newPort, true, true);
	    checkBindings(1);
	    client.assertDisconnectedCallbackNotInvoked();
	} finally {
	    client.disconnect();
	}
    }

    @Test
    public void testRelocateWithLoginPreemptedAfterRelocate()
	throws Exception
    {
	String newNodeHost = "newNode";
	allowNewLogin = true;	// enable login preemption on new node
	DummyClient client = createClientToRelocate(newNodeHost);
	DummyClient otherClient = createDummyClient("foo");
	SgsTestNode newNode = additionalNodes.get(newNodeHost);
	try {
	    int newPort = newNode.getAppPort();
	    client.relocate(newPort, true, true);
	    assertTrue(otherClient.connect(newPort).login());
	    checkBindings(1);
	    client.assertDisconnectedCallbackInvoked(false);
	} finally {
	    client.disconnect();
	}
    }

    @Test
    public void testRelocateWithRedirect()
	throws Exception
    {
	String host2 = "host2";
	String host3 = "host3";
	DummyClient client = createClientToRelocate(host2);
	addNodes(host3);
	SgsTestNode node2 = additionalNodes.get(host2);
	SgsTestNode node3 = additionalNodes.get(host3);
	identityAssigner.moveIdentity("foo", node2.getNodeId(),
			 node3.getNodeId());
	int objectCount = getObjectCount();
	client.relocate(node2.getAppPort(), true, false);
	waitForExpectedObjectCount(
	    objectCount - MANAGED_OBJECTS_PER_SESSION);
	checkBindings(0);
	client.assertDisconnectedCallbackInvoked(false);
    }

    @Test
    public void testOldNodeFailsDuringRelocateToNewNode()
	throws Exception
    {
	String newNodeHost = "newNode";
	DummyClient client = createClientToRelocate(newNodeHost);
	int objectCount = getObjectCount();
	checkBindings(1);
	try {
	    // Simulate oldNode crashing by having client not connect to
	    // newNode by the timeout period.
	    Thread.sleep(WAIT_TIME);
	    assertEquals(objectCount - MANAGED_OBJECTS_PER_SESSION,
			 getObjectCount());
	    checkBindings(0);
	    client.assertDisconnectedCallbackInvoked(false);
	} finally {
	    client.disconnect();
	}
    }

    @Test
    public void testNewNodeFailsDuringRelocateToNewNode()
	throws Exception
    {
	String newNodeHost = "newNode";
	DummyClient client = createClientToRelocate(newNodeHost);
	checkBindings(1);
	try {
	    // Shutdown new node, and wait before checking if
	    // session's persistent data has been cleaned up.
	    SgsTestNode newNode = additionalNodes.get(newNodeHost);
	    newNode.shutdown(false);
	    Thread.sleep(WAIT_TIME);
	    checkBindings(0);
	    client.assertDisconnectedCallbackInvoked(false);
	} finally {
	    client.disconnect();
	}
    }

    @Test
    public void testClientSendDuringSuspendMessages() throws Exception {
	String newNodeHost = "newNode";
	int numMessages = 4;
	DummyClient client = createClientAndReassignIdentity(newNodeHost, true);
	try {
	    client.waitForSuspendMessages();
	    for (int i = 0; i < numMessages; i++) {
		MessageBuffer buf = new MessageBuffer(4);
		buf.putInt(i);
		client.sendMessage(buf.getBuffer(), false);
	    }
	    Thread.sleep(1000);
	    client.sendSuspendMessagesComplete();
	    SgsTestNode newNode = additionalNodes.get(newNodeHost);
	    client.waitForRelocationNotification(newNode.getAppPort());
	    client.validateMessageSequence(
		client.sessionListenerReceivedMessages, numMessages, 0);
	    assertTrue(client.clientReceivedMessages.isEmpty());
	    client.relocate(newNode.getAppPort(), true, true);
	    client.waitForClientToReceiveExpectedMessages(numMessages);
	    client.validateMessageSequence(
		client.clientReceivedMessages, numMessages, 0);
	    assertTrue(client.sessionListenerReceivedMessages.isEmpty());
	} finally {
	    client.disconnect();
	}
    }

    @Test
    public void testClientSendAfterSuspendMessages() throws Exception {
	String newNodeHost = "newNode";
	int numMessages = 4;
	DummyClient client = createClientAndReassignIdentity(newNodeHost, true);
	try {
	    client.waitForSuspendMessages();
	    client.sendSuspendMessagesComplete();
	    for (int i = 0; i < numMessages; i++) {
		MessageBuffer buf = new MessageBuffer(4);
		buf.putInt(i);
		client.sendMessage(buf.getBuffer(), false);
	    }
	    SgsTestNode newNode = additionalNodes.get(newNodeHost);
	    client.waitForRelocationNotification(newNode.getAppPort());
	    // Make sure that all messages received after the suspend was
	    // completed have been dropped.
	    assertTrue(client.sessionListenerReceivedMessages.isEmpty());
	    assertTrue(client.clientReceivedMessages.isEmpty());
	    client.relocate(newNode.getAppPort(), true, true);
	    assertTrue(client.sessionListenerReceivedMessages.isEmpty());
	    assertTrue(client.clientReceivedMessages.isEmpty());
	} finally {
	    client.disconnect();
	}
    }

    /**
     * Performs the following in preparation for a relocation test:
     * <ul>
     * <li>creates a new {@code DummyClient} named "foo"
     * <li>creates a new node named {@code newNodeHost} 
     * <li>logs the client into the server node
     * <li>reassigns the client's identity to the new node
     * <li>returns the constructed {@code DummyClient}
     * </ul>
     */
    private DummyClient createClientAndReassignIdentity(
	String newNodeHost, boolean setWaitForSuspendMessages)
	throws Exception
    {
	final String name = "foo";
	addNodes(newNodeHost);
	DummyClient client = createDummyClient(name);
	if (setWaitForSuspendMessages) {
	    client.setWaitForSuspendMessages();
	}
	assertTrue(client.connect(serverNode.getAppPort()).login());
	SgsTestNode newNode = additionalNodes.get(newNodeHost);
	System.err.println("reassigning identity:" + name +
			   " from server node to host: " +
			   newNodeHost);
	identityAssigner.
	    moveIdentity(name, serverNode.getNodeId(), newNode.getNodeId());
	System.err.println("(done) reassigning identity");
	return client;
    }
    
    /**
     * Performs the following in preparation for a relocation test:
     * <ul>
     * <li>creates a new {@code DummyClient} named "foo"
     * <li>creates a new node named {@code newNodeHost} 
     * <li>logs the client into the server node
     * <li>reassigns the client's identity to the new node
     * <li>waits for the client to receive the relocate notification
     * <li>returns the constructed {@code DummyClient}
     * </ul>
     */
    private DummyClient createClientToRelocate(String newNodeHost)
	throws Exception
    {
	DummyClient client =
	    createClientAndReassignIdentity(newNodeHost, false);
	SgsTestNode newNode = additionalNodes.get(newNodeHost);
	client.waitForRelocationNotification(newNode.getAppPort());
	return client;
    }

    /**
     * Waits for the object count to match the {@code expectedCount}, and
     * throws an AssertionFailedException if the object count does not
     * match the {@code expectedCount} after the wait time has expired.
     */
    private void waitForExpectedObjectCount(int expectedCount)
	throws Exception
    {
	int objectCount = 0;
	for (int i = 0; i < 10; i++) {
	    objectCount = getObjectCount();
	    if (objectCount == expectedCount) {
		return;
	    } else {
		Thread.sleep(WAIT_TIME / 10);
	    }
	}
	assertEquals(expectedCount, objectCount);
    }
}
