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

import com.sun.sgs.app.util.ManagedSerializable;
import com.sun.sgs.impl.service.channel.ChannelServer;
import com.sun.sgs.impl.service.nodemap.DirectiveNodeAssignmentPolicy;
import com.sun.sgs.service.ClientSessionStatusListener;
import com.sun.sgs.service.SimpleCompletionHandler;
import com.sun.sgs.test.util.SgsTestNode;
import com.sun.sgs.test.util.TestAbstractKernelRunnable;
import com.sun.sgs.tools.test.FilteredJUnit3TestRunner;
import java.io.Serializable;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import junit.framework.TestSuite;
import org.junit.runner.RunWith;

@RunWith(FilteredJUnit3TestRunner.class)
public class TestChannelServiceImplRelocatingSessions
    extends AbstractChannelServiceTest
{
    protected List<String> oneUser =
	Arrays.asList(new String[] { "rex" });

    private static volatile boolean holdMethod = false;
    private static final Object invocationHandlerLock = new Object();
    
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

    public void testChannelJoinAndRelocateMultipleTimes()
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
	    // The bindings should no longer have a membership binding for
	    // the relocating client or the binding for the channel server
	    // for the crashed node.
	    assertEquals(count - 1 - 1,
			 getChannelServiceBindingCount());
	    // Disconnect each client and make sure that membership/bindings
	    // are cleaned up.
	    group.disconnect(true);
	    checkUsersJoined(channelName, new ArrayList<String>());
	    printServiceBindings("after group disconnect");
	    // The bindings should no longer contain any membership
	    // bindings for client sessions or the binding for channel
	    // server for the crashed node.
	    assertEquals(count - someUsers.size() - 1,
			 getChannelServiceBindingCount());
	    
	} finally {
	    group.disconnect(false);
	}
    }

    public void testChannelJoinToOldNodeDuringRelocate()
	throws Exception
    {
	String channelName = "foo";
	DirectiveNodeAssignmentPolicy.instance.setRoundRobin(false);
	// channel coordinator is on server node
	createChannel(channelName); 
	addNodes("oldNode", "newNode");
	
	// Client will log into "oldNode"
	SgsTestNode oldNode = additionalNodes.get("oldNode");
	ClientGroup group = new ClientGroup(oneUser, oldNode.getAppPort());
	
	try {
	    // Initiate client relocation to new node.
	    printServiceBindings("before relocate");
	    DummyClient relocatingClient = group.getClient(oneUser.get(0));
	    SgsTestNode newNode = additionalNodes.get("newNode");
	    // Hold up joins send to oldNode
	    wrapChannelServer(oldNode.getNodeId(), "join");
	    holdMethod = true;
	    joinUsers(channelName, oneUser); 
	    
	    moveIdentity(relocatingClient, oldNode, newNode);
	    Thread.sleep(200);
	    // Release "join" to oldNode.
	    synchronized (invocationHandlerLock) {
		invocationHandlerLock.notifyAll();
	    }
	    holdMethod = false;
	    // Finish relocation.
	    relocatingClient.relocate(0, true, true);
	    
	    // Make sure all members are joined and can receive messages.
	    checkUsersJoined(channelName, oneUser);
	    sendMessagesToChannel(channelName, group, 2);
	    printServiceBindings("after join");

	    // Disconnect each client and make sure that memberships/bindings
	    // are cleaned up.
	    group.disconnect(true);
	    Thread.sleep(WAIT_TIME);
	    checkUsersJoined(channelName, new ArrayList<String>());
	    
	} finally {
	    group.disconnect(false);
	}
	
    }

    public void testChannelJoinToOldNodeAfterRelocate()
	throws Exception
    {
	String channelName = "foo";
	DirectiveNodeAssignmentPolicy.instance.setRoundRobin(false);
	// channel coordinator is on server node
	createChannel(channelName); 
	addNodes("oldNode", "newNode");
	
	// Client will log into "oldNode"
	SgsTestNode oldNode = additionalNodes.get("oldNode");
	ClientGroup group = new ClientGroup(oneUser, oldNode.getAppPort());
	
	try {
	    // Initiate client relocation to new node.
	    printServiceBindings("before relocate");
	    DummyClient relocatingClient = group.getClient(oneUser.get(0));
	    SgsTestNode newNode = additionalNodes.get("newNode");
	    // Hold up joins send to oldNode
	    wrapChannelServer(oldNode.getNodeId(), "join");
	    holdMethod = true;
	    joinUsers(channelName, oneUser); 
	    
	    moveClient(relocatingClient, oldNode, newNode);
	    // Release "join" to oldNode.
	    synchronized (invocationHandlerLock) {
		invocationHandlerLock.notifyAll();
	    }
	    holdMethod = false;
	    
	    // Make sure all members are joined and can receive messages.
	    checkUsersJoined(channelName, oneUser);
	    sendMessagesToChannel(channelName, group, 2);
	    printServiceBindings("after join");

	    // Disconnect each client and make sure that memberships/bindings
	    // are cleaned up.
	    group.disconnect(true);
	    Thread.sleep(WAIT_TIME);
	    checkUsersJoined(channelName, new ArrayList<String>());
	    
	} finally {
	    group.disconnect(false);
	}
	
    }
    
    public void testChannelJoinToOldNodeAfterRelocateTwice()
	throws Exception
    {
	String channelName = "foo";
	DirectiveNodeAssignmentPolicy.instance.setRoundRobin(false);
	// channel coordinator is on server node
	createChannel(channelName); 
	addNodes("oldNode", "newNode1", "newNode2");
	
	// Client will log into "oldNode"
	SgsTestNode oldNode = additionalNodes.get("oldNode");
	ClientGroup group = new ClientGroup(oneUser, oldNode.getAppPort());
	
	try {
	    // Initiate client relocation to new node.
	    printServiceBindings("before relocate");
	    DummyClient relocatingClient = group.getClient(oneUser.get(0));
	    SgsTestNode newNode1 = additionalNodes.get("newNode1");
	    // Hold up joins send to oldNode
	    wrapChannelServer(oldNode.getNodeId(), "join");
	    holdMethod = true;
	    joinUsers(channelName, oneUser); 
	    
	    moveClient(relocatingClient, oldNode, newNode1);
	    moveClient(relocatingClient, newNode1,
		       additionalNodes.get("newNode2"));
	    // Release "join" to oldNode.
	    synchronized (invocationHandlerLock) {
		invocationHandlerLock.notifyAll();
	    }
	    holdMethod = false;
	    
	    // Make sure all members are joined and can receive messages.
	    checkUsersJoined(channelName, oneUser);
	    sendMessagesToChannel(channelName, group, 2);
	    printServiceBindings("after join");

	    // Disconnect each client and make sure that memberships/bindings
	    // are cleaned up.
	    group.disconnect(true);
	    Thread.sleep(WAIT_TIME);
	    checkUsersJoined(channelName, new ArrayList<String>());
	    
	} finally {
	    group.disconnect(false);
	}
	
    }
    
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
	addNodes("host2");
	MySessionStatusListener mySessionStatusListener =
	    new MySessionStatusListener();
	serverNode.getClientSessionService().
	    addSessionStatusListener(mySessionStatusListener);
	
	try {

	    joinUsers(channelName1, someUsers);
	    checkUsersJoined(channelName1, someUsers);
	    
	    // Initiate client relocation to new node.
	    printServiceBindings("before relocate");
	    DummyClient relocatingClient = group.getClient(someUsers.get(0));
	    SgsTestNode newNode = additionalNodes.get("host2");
	    moveIdentity(relocatingClient, serverNode, newNode);
	    SimpleCompletionHandler handler =
		mySessionStatusListener.waitForPrepare();
	    
	    // Join all users (including relocating client) to channel during
	    // prepare phase.
	    joinUsers(channelName2, someUsers);
	    printServiceBindings("after join, but before relocation complete");

	    Thread.sleep(200);
	    // Mark preparation completed.
	    handler.completed();
	    
	    // Finish relocation.
	    relocatingClient.relocate(0, true, true);
	    
	    // Make sure all members are joined and can receive messages.
	    checkUsersJoined(channelName2, someUsers);
	    printServiceBindings("after all joins");
	    sendMessagesToChannel(channelName2, group, 2);
	    sendMessagesToChannel(channelName1, group, 2);

	    // Disconnect each client and make sure that memberships/bindings
	    // are cleaned up.
	    group.disconnect(true);
	    Thread.sleep(WAIT_TIME);
	    checkUsersJoined(channelName1, new ArrayList<String>());	    
	    checkUsersJoined(channelName2, new ArrayList<String>());
	    
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

    private void wrapChannelServer(final long nodeId, final String methodName)
	throws Exception
    {
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
		NotifyingInvocationHandler handler =
		    new NotifyingInvocationHandler(channelServer, methodName);
		ChannelServer newServer = (ChannelServer)
		    Proxy.newProxyInstance(
			ChannelServer.class.getClassLoader(),
			new Class[] { ChannelServer.class },
			handler);
		dataService.setServiceBinding(
 		    key, constr.newInstance(newServer));
		
	    }}, taskOwner);
    }

    /**
     * This invocation handler waits to be notified before invoking a
     * method (on the underlying instance) whose name is specified
     * during construction.
     */
    private static class NotifyingInvocationHandler
	implements InvocationHandler, Serializable
    {
	private final static long serialVersionUID = 1L;
	private final Object obj;
	private final String methodName;
	
	NotifyingInvocationHandler(Object obj, String methodName) {
	    this.obj = obj;
	    this.methodName = methodName;
	}
	
	public Object invoke(Object proxy, Method method, Object[] args)
	    throws Exception
	{
	    if (holdMethod && method.getName().equals(methodName)) {
		synchronized (invocationHandlerLock) {
		    try {
			invocationHandlerLock.wait();
		    } catch (InterruptedException e) {
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
}
