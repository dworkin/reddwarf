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

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.AppListener;
import com.sun.sgs.app.ClientSession;
import com.sun.sgs.app.ClientSessionListener;
import com.sun.sgs.app.DataManager;
import com.sun.sgs.app.Delivery;
import com.sun.sgs.app.ExceptionRetryStatus;
import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.ManagedReference;
import com.sun.sgs.app.MessageRejectedException;
import com.sun.sgs.app.NameNotBoundException;
import com.sun.sgs.app.ObjectNotFoundException;
import com.sun.sgs.app.util.ManagedSerializable;
import com.sun.sgs.auth.Identity;
import com.sun.sgs.impl.kernel.StandardProperties;
import com.sun.sgs.impl.service.session.ClientSessionServer;
import com.sun.sgs.impl.service.session.ClientSessionWrapper;
import com.sun.sgs.impl.sharedutil.HexDumper;
import com.sun.sgs.impl.sharedutil.MessageBuffer;
import com.sun.sgs.kernel.TransactionScheduler;
import com.sun.sgs.protocol.simple.SimpleSgsProtocol;
import com.sun.sgs.service.DataService;
import com.sun.sgs.test.util.AbstractDummyClient;
import com.sun.sgs.test.util.ConfigurableNodePolicy;
import com.sun.sgs.test.util.SgsTestNode;
import com.sun.sgs.test.util.SimpleTestIdentityAuthenticator;
import com.sun.sgs.test.util.TestAbstractKernelRunnable;
import com.sun.sgs.tools.test.FilteredNameRunner;
import com.sun.sgs.tools.test.IntegrationTest;
import java.io.Serializable;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Properties;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static com.sun.sgs.test.util.UtilProperties.createProperties;

@RunWith(FilteredNameRunner.class)
public class TestClientSessionServiceImplv4 extends Assert {


    private static final String APP_NAME = "TestClientSessionServiceImplv4";
    
    private static final byte PROTOCOL_v4 = 0x04;

    protected static final int WAIT_TIME = 5000;

    private static final String RETURN_NULL = "return null";

    private static final String NON_SERIALIZABLE = "non-serializable";

    private static final String THROW_RUNTIME_EXCEPTION =
	"throw RuntimeException";

    private static final String DISCONNECT_THROWS_NONRETRYABLE_EXCEPTION =
	"disconnect throws non-retryable exception";

    private static final String SESSION_PREFIX =
	"com.sun.sgs.impl.service.session.impl";

    private static final String SESSION_NODE_PREFIX =
	"com.sun.sgs.impl.service.session.node";

    private static final String LISTENER_PREFIX =
	"com.sun.sgs.impl.service.session.listener";

    private static final String NODE_PREFIX =
	"com.sun.sgs.impl.service.watchdog.node";

    private String appName;

    /** The node that creates the servers. */
    protected SgsTestNode serverNode;

    /** The protocol version. */
    private byte protocolVersion;

    /** Any additional nodes, keyed by node host name (for tests
     * needing more than one node). */
    protected Map<String,SgsTestNode> additionalNodes;

    protected boolean allowNewLogin = false;

    /** The transaction scheduler. */
    private TransactionScheduler txnScheduler;

    /** The owner for tasks I initiate. */
    private Identity taskOwner;

    /** The shared data service. */
    private DataService dataService;

    /** The test clients, keyed by client session ID. */
    private static Map<BigInteger, DummyClient> dummyClients;

    private static volatile RuntimeException receivedMessageException = null;
    
    /** Constructs a test instance. */
    public TestClientSessionServiceImplv4() throws Exception {
    }

    @Before
    public void setUp() throws Exception {
        setUp(null, true, APP_NAME, PROTOCOL_v4);
    }

    /** Creates and configures the session service. */
    protected void setUp(Properties props, boolean clean, String appName,
			 byte protocolVersion)
	throws Exception
    {
	if (props == null) {
	    props = 
                SgsTestNode.getDefaultProperties(appName, null, 
						 DummyAppListener.class);
	}
	this.protocolVersion = protocolVersion;
	this.appName = appName;
        props.setProperty(StandardProperties.AUTHENTICATORS, 
                      "com.sun.sgs.test.util.SimpleTestIdentityAuthenticator");
	props.setProperty("com.sun.sgs.impl.service.nodemap.policy.class",
			  ConfigurableNodePolicy.class.getName());
	props.setProperty(
	    "com.sun.sgs.impl.service.watchdog.server.renew.interval", "1000");
	props.setProperty(
            "com.sun.sgs.impl.service.nodemap.remove.expire.time", "5000");
	props.setProperty(
 	   "com.sun.sgs.impl.protocol.simple.protocol.version",
	   Byte.toString(protocolVersion));
	props.setProperty(
	    StandardProperties.SESSION_RELOCATION_TIMEOUT_PROPERTY,
	    "5000");
	
	serverNode = 
	    new SgsTestNode(appName, DummyAppListener.class, props, clean);

        txnScheduler = 
            serverNode.getSystemRegistry().
            getComponent(TransactionScheduler.class);
        taskOwner = serverNode.getProxy().getCurrentOwner();

        dataService = serverNode.getDataService();
        dummyClients = new HashMap<BigInteger, DummyClient>();
	receivedMessageException = null;
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
                appName, serverNode, DummyAppListener.class);
	    props.setProperty(StandardProperties.AUTHENTICATORS, 
                "com.sun.sgs.test.util.SimpleTestIdentityAuthenticator");
            props.put("com.sun.sgs.impl.service.watchdog.client.host", host);
	    if (allowNewLogin) {
		props.setProperty(
		    "com.sun.sgs.impl.service.session.allow.new.login", "true");
	    }
	    props.setProperty(
		"com.sun.sgs.impl.protocol.simple.protocol.version",
		Byte.toString(protocolVersion));
	    props.setProperty(
	        StandardProperties.SESSION_RELOCATION_TIMEOUT_PROPERTY,
		"5000");
            SgsTestNode node =
                    new SgsTestNode(serverNode, DummyAppListener.class, props);
            additionalNodes.put(host, node);
        }
    }

    @After
    public void tearDown() throws Exception {
        tearDown(true);
    }

    protected void tearDown(boolean clean) throws Exception {
        Thread.sleep(100);
	if (additionalNodes != null) {
            for (SgsTestNode node : additionalNodes.values()) {
                node.shutdown(false);
            }
            additionalNodes = null;
        }
        serverNode.shutdown(clean);
        serverNode = null;
    }

    // -- Test connecting, logging in, logging out with server -- 

    @Test
    public void testConnection() throws Exception {
	DummyClient client = createDummyClient("foo");
	try {
	    client.connect(serverNode.getAppPort());
	} catch (Exception e) {
	    System.err.println("Exception: " + e);
	    Throwable t = e.getCause();
	    System.err.println("caused by: " + t);
	    System.err.println("detail message: " + t.getMessage());
	    throw e;
	    
	} finally {
	    client.disconnect();
	}
    }

    @Test
    public void testLoginSuccess() throws Exception {
	DummyClient client = createDummyClient("success");
	try {
	    client.connect(serverNode.getAppPort());
	    assertTrue(client.login());
	} finally {
            client.disconnect();
	}
    }

    @Test
    @IntegrationTest
    public void testLoginRedirect() throws Exception {
	int serverAppPort = serverNode.getAppPort();
	String[] users = new String[] { "sleepy", "bashful", "dopey", "doc" };
	Set<DummyClient> clients = new HashSet<DummyClient>();
	addNodes(users);
	boolean failed = false;
	int redirectCount = 0;
	ConfigurableNodePolicy.setRoundRobinPolicy();
	try {
	    for (String user : users) {
		DummyClient client = createDummyClient(user);
		client.connect(serverAppPort);
		if (client.login()) {
		    if (client.getConnectPort() != serverAppPort) {
			// login redirected
			redirectCount++;
		    }
		} else {
		    failed = true;
		    System.err.println("login for user: " + user + " failed");
		}
		clients.add(client);
	    }
	    
	    int expectedRedirects = users.length - 1;
	    if (redirectCount != expectedRedirects) {
		failed = true;
		System.err.println("Expected " + expectedRedirects +
				   " redirects, got " + redirectCount);
	    } else {
		System.err.println(
		    "Number of redirected users: " + redirectCount);
	    }
	    
	    if (failed) {
		fail("test failed (see output)");
	    }
	    
	} finally {
	    for (DummyClient client : clients) {
		try {
		    client.disconnect();
		} catch (Exception e) {
		    System.err.println(
			"Exception disconnecting client: " + client);
		}
	    }
	}
	
    }

    @Test
    @IntegrationTest
    public void testSendBeforeLoginComplete() throws Exception {
	DummyClient client = createDummyClient("dummy");
	try {
	    client.connect(serverNode.getAppPort());
	    client.login(false);
	    client.sendMessagesFromClientInSequence(1, 0);
	} finally {
            client.disconnect();
	}
    }

    @Test
    @IntegrationTest
    public void testSendAfterLoginComplete() throws Exception {
	DummyClient client = createDummyClient("dummy");
	try {
	    client.connect(serverNode.getAppPort());
	    client.login(true);
	    client.sendMessagesFromClientInSequence(1, 1);
	} finally {
            client.disconnect();
	}
    }

    @Test
    @IntegrationTest
    public void testLoginSuccessAndNotifyLoggedInCallback() throws Exception {
	String name = "success";
	DummyClient client = createDummyClient(name);
	try {
	    client.connect(serverNode.getAppPort());
	    assertTrue(client.login());
	    if (SimpleTestIdentityAuthenticator.allIdentities.
                    getNotifyLoggedIn(name)) {
		System.err.println(
		    "notifyLoggedIn invoked for identity: " + name);
	    } else {
		fail("notifyLoggedIn not invoked for identity: " + name);
	    }
	} finally {
            client.disconnect();
	}
    }

    @Test
    @IntegrationTest
    public void testLoggedInReturningNonSerializableClientSessionListener()
	throws Exception
    {
	DummyClient client = createDummyClient(NON_SERIALIZABLE);
	try {
	    client.connect(serverNode.getAppPort());
	    assertFalse(client.login());
	    assertFalse(SimpleTestIdentityAuthenticator.allIdentities.
			getNotifyLoggedIn(NON_SERIALIZABLE));
	} finally {
	    client.disconnect();
	}
    }

    @Test
    @IntegrationTest
    public void testLoggedInReturningNullClientSessionListener()
	throws Exception
    {
	DummyClient client = createDummyClient(RETURN_NULL);
	try {
	    client.connect(serverNode.getAppPort());
	    assertFalse(client.login());
	    assertFalse(SimpleTestIdentityAuthenticator.allIdentities.
		        getNotifyLoggedIn(RETURN_NULL));
	} finally {
	    client.disconnect();
	}
    }

    @Test
    @IntegrationTest
    public void testLoggedInThrowingRuntimeException()
	throws Exception
    {
	DummyClient client = createDummyClient(THROW_RUNTIME_EXCEPTION);
	try {
	    client.connect(serverNode.getAppPort());
	    assertFalse(client.login());
	    assertFalse(SimpleTestIdentityAuthenticator.allIdentities.
			getNotifyLoggedIn(THROW_RUNTIME_EXCEPTION));
	} finally {
	    client.disconnect();
	}
    }

    @Test
    @IntegrationTest
    public void testLoginTwiceBlockUser() throws Exception {
	String name = "dummy";
	DummyClient client1 = createDummyClient(name);
	DummyClient client2 = createDummyClient(name);
	int port = serverNode.getAppPort();
	try {
	    assertTrue(client1.connect(port).login());
	    assertFalse(client2.connect(port).login());
	} finally {
	    client1.disconnect();
	    client2.disconnect();
	}
    }

    @Test
    @IntegrationTest
    public void testLoginTwicePreemptUser() throws Exception {
	// Set up ClientSessionService to preempt user if same user logs in
	tearDown(false);
	Properties props =
	    SgsTestNode.getDefaultProperties(appName, null,
					     DummyAppListener.class);
	props.setProperty(
 	    "com.sun.sgs.impl.service.session.allow.new.login", "true");
	setUp(props, false, appName, protocolVersion);
	String name = "dummy";
	
	DummyClient client1 = createDummyClient(name);
	DummyClient client2 = createDummyClient(name);
	int port = serverNode.getAppPort();
	try {
	    assertTrue(client1.connect(port).login());
	    Thread.sleep(100);
	    assertTrue(client2.connect(port).login());
	    client1.checkDisconnectedCallback(false);
	    assertTrue(client2.isConnected());
	    
	} finally {
	    client1.disconnect();
	    client2.disconnect();
	}
    }

    @Test
    @IntegrationTest
    public void testDisconnectFromServerAfterLogout() throws Exception {
	final String name = "logout";
	DummyClient client = createDummyClient(name);
	try {
	    client.connect(serverNode.getAppPort());
	    assertTrue(client.login());
	    client.logout();
	    assertTrue(client.isConnected());
	    assertTrue(client.waitForDisconnect());
	} finally {
	    client.disconnect();
	}
    }

    @Test
    @IntegrationTest
    public void testDisconnectedCallbackAfterClientDropsConnection()
	throws Exception
    {
	final String name = "dummy";
	DummyClient client = createDummyClient(name);
	try {
	    client.connect(serverNode.getAppPort());
	    assertTrue(client.login());
	    checkBindings(1);
	    client.disconnect();
	    client.checkDisconnectedCallback(false);
	    checkBindings(0);
	    // check that client session was removed after disconnected callback
	    // returned 
            txnScheduler.runTask(new TestAbstractKernelRunnable() {
                public void run() {
		    try {
			dataService.getBinding(name);
			fail("expected ObjectNotFoundException: " +
			     "object not removed");
		    } catch (ObjectNotFoundException e) {
		    }
                }
             }, taskOwner);
	} catch (InterruptedException e) {
	    e.printStackTrace();
	    fail("testLogout interrupted");
	} finally {
	    client.disconnect();
	}
    }

    @Test
    @IntegrationTest
    public void testLogoutRequestAndDisconnectedCallback() throws Exception {
	final String name = "logout";
	DummyClient client = createDummyClient(name);
	try {
	    client.connect(serverNode.getAppPort());
	    assertTrue(client.login());
	    checkBindings(1);
	    client.logout();
	    client.checkDisconnectedCallback(true);
	    checkBindings(0);
	    // check that client session was removed after disconnected callback
	    // returned 
            txnScheduler.runTask(new TestAbstractKernelRunnable() {
                public void run() {
		    try {
			dataService.getBinding(name);
			fail("expected ObjectNotFoundException: " +
			     "object not removed");
		    } catch (ObjectNotFoundException e) {
		    }
                }
             }, taskOwner);
	} catch (InterruptedException e) {
	    e.printStackTrace();
	    fail("testLogout interrupted");
	} finally {
	    client.disconnect();
	}
    }

    @Test
    @IntegrationTest
    public void testDisconnectedCallbackThrowingNonRetryableException()
	throws Exception
    {
	DummyClient client =
	    createDummyClient(DISCONNECT_THROWS_NONRETRYABLE_EXCEPTION);
	try {
	    client.connect(serverNode.getAppPort());
	    assertTrue(client.login());
	    checkBindings(1);
	    client.logout();
	    client.checkDisconnectedCallback(true);
	    // give scheduled task a chance to clean up...
	    Thread.sleep(250);
	    checkBindings(0);	    
	} finally {
	    client.disconnect();
	}
    }

    @Test
    @IntegrationTest
    public void testLogoutAndNotifyLoggedOutCallback() throws Exception {
	String name = "logout";
	DummyClient client = createDummyClient(name);
	try {
	    assertTrue(client.connect(serverNode.getAppPort()).login());
	    client.logout();
	    if (SimpleTestIdentityAuthenticator.allIdentities.
                    getNotifyLoggedIn(name)) {
		System.err.println(
		    "notifyLoggedIn invoked for identity: " + name);
	    } else {
		fail("notifyLoggedIn not invoked for identity: " + name);
	    }
	    if (SimpleTestIdentityAuthenticator.allIdentities.
                    getNotifyLoggedOut(name)) {
		System.err.println(
		    "notifyLoggedOut invoked for identity: " + name);
	    } else {
		fail("notifyLoggedOut not invoked for identity: " + name);
	    }
	} finally {
            client.disconnect();
	}
    }

    @Test
    @IntegrationTest
    public void testNotifyClientSessionListenerAfterCrash() throws Exception {
	int numClients = 4;
	try {
	    List<String> nodeKeys = getServiceBindingKeys(NODE_PREFIX);
	    System.err.println("Node keys: " + nodeKeys);
	    if (nodeKeys.isEmpty()) {
		fail("no node keys");
	    } else if (nodeKeys.size() > 1) {
		fail("more than one node key");
	    }

	    int appPort = serverNode.getAppPort();
	    for (int i = 0; i < numClients; i++) {
		 // Create half of the clients with a name that starts with
		 // "badClient" which will cause the associated session's
		 // ClientSessionListener's 'disconnected' method to throw a
		 // non-retryable exception.  We want to make sure that all the
		 // client sessions are cleaned up after a crash, even if
		 // invoking a session's listener's 'disconnected' callback
		 // throws a non-retryable exception.
		String name = (i % 2 == 0) ? "client" : "badClient";
		DummyClient client = createDummyClient(name + String.valueOf(i));
		assertTrue(client.connect(appPort).login());
	    }
	    checkBindings(numClients);

            // Simulate "crash"
            tearDown(false);
	    String failedNodeKey = nodeKeys.get(0);
            setUp(null, false, appName, protocolVersion);

	    for (DummyClient client : dummyClients.values()) {
		client.checkDisconnectedCallback(false);
	    }
	    
	    // Wait to make sure that bindings and node key are cleaned up.
	    // Some extra time is needed when a ClientSessionListener throws a
	    // non-retryable exception because a separate task is scheduled to
	    // clean up the client session and bindings.
	    Thread.sleep(WAIT_TIME);
	    
	    System.err.println("check for session bindings being removed.");
	    checkBindings(0);
	    nodeKeys = getServiceBindingKeys(NODE_PREFIX);
	    System.err.println("Node keys: " + nodeKeys);
	    if (nodeKeys.contains(failedNodeKey)) {
		fail("failed node key not removed: " + failedNodeKey);
	    }
	    
	} finally {
	    for (DummyClient client : dummyClients.values()) {
		try {
		    client.disconnect();
		} catch (Exception e) {
		    // ignore
		}
	    }
	}
    }

    // -- test ClientSession --

    @Test
    public void testClientSessionIsConnected() throws Exception {
	DummyClient client = createDummyClient("clientname");
	try {
	    client.connect(serverNode.getAppPort());
	    assertTrue(client.login());
            txnScheduler.runTask(new TestAbstractKernelRunnable() {
                public void run() {
                    DummyAppListener appListener = getAppListener();
                    Set<ClientSession> sessions = appListener.getSessions();
                    if (sessions.isEmpty()) {
                        fail("appListener contains no client sessions!");
                    }
                    for (ClientSession session : appListener.getSessions()) {
                        if (session.isConnected() == true) {
                            System.err.println("session is connected");
                            return;
                        } else {
                            fail("Expected connected session: " + session);
                        }
                    }
                    fail("expected a connected session");
                }
            }, taskOwner);
	} finally {
	    client.disconnect();
	}
    }

    @Test
    public void testClientSessionGetName() throws Exception {
	final String name = "clientname";
	DummyClient client = createDummyClient(name);
	try {
	    client.connect(serverNode.getAppPort());
	    assertTrue(client.login());
            txnScheduler.runTask(new TestAbstractKernelRunnable() {
                public void run() {
                    DummyAppListener appListener = getAppListener();
                    Set<ClientSession> sessions = appListener.getSessions();
                    if (sessions.isEmpty()) {
                        fail("appListener contains no client sessions!");
                    }
                    for (ClientSession session : appListener.getSessions()) {
                        if (session.getName().equals(name)) {
                            System.err.println("names match");
                            return;
                        } else {
                            fail("Expected session name: " + name +
                                 ", got: " + session.getName());
                        }
                    }
                    fail("expected disconnected session");
                }
             }, taskOwner);
	} finally {
	    client.disconnect();
	}
    }

    @Test
    public void testClientSessionToString() throws Exception {
	final String name = "testClient";
	DummyClient client = createDummyClient(name);
	try {
	    assertTrue(client.connect(serverNode.getAppPort()).login());
	    txnScheduler.runTask(new TestAbstractKernelRunnable() {
		    public void run() {
			ClientSession session = (ClientSession)
			    dataService.getBinding(name);
			if (!(session instanceof ClientSessionWrapper)) {
			    fail("session not instance of " +
				 "ClientSessionWrapper");
			}
			System.err.println("session: " + session);
		    }
		}, taskOwner);
	} finally {
	    client.disconnect();
	}
    }

    @Test
    public void testClientSessionToStringNoTransaction() throws Exception {
	final String name = "testClient";
	DummyClient client = createDummyClient(name);
	try {
	    assertTrue(client.connect(serverNode.getAppPort()).login());
	    GetClientSessionTask task = new GetClientSessionTask(name);
	    txnScheduler.runTask(task, taskOwner);
	    try {
		System.err.println("session: " + task.session.toString());
		return;
	    } catch (Exception e) {
		e.printStackTrace();
		fail("unexpected exception in ClientSessionWrapper.toString");
	    }
	} finally {
	    client.disconnect();
	}
    }

    private class GetClientSessionTask extends TestAbstractKernelRunnable {
	private final String name;
	volatile ClientSession session;

	GetClientSessionTask(String name) {
	    this.name = name;
	}

	public void run() {
	    session = (ClientSession) dataService.getBinding(name);
	    if (!(session instanceof ClientSessionWrapper)) {
		fail("session not instance of ClientSessionWrapper");
	    }
	}
    }

    @Test
    public void testClientSessionSendUnreliableMessages() throws Exception {
	DummyClient client = createDummyClient("dummy");
	try {
	    int iterations = 3;
	    int numAdditionalNodes = 2;
	    sendMessagesFromNodesToClient(
		client, numAdditionalNodes, iterations, Delivery.UNRELIABLE,
		false);
	} finally {
	    client.disconnect();
	}
    }

    @Test
    public void testClientSessionSendUnreliableMessagesWithFailure()
	throws Exception
    {
	DummyClient client = createDummyClient("dummy");
	try {
	    int iterations = 3;
	    int numAdditionalNodes = 2;
	    sendMessagesFromNodesToClient(
		client, numAdditionalNodes, iterations, Delivery.UNRELIABLE,
		true);
	} finally {
	    client.disconnect();
	}
	
    }

    @Test
    public void testClientSessionSendSequence() throws Exception {
	DummyClient client = createDummyClient("dummy");
	try {
	    int iterations = 3;
	    int numAdditionalNodes = 2;
	    sendMessagesFromNodesToClient(
		client, numAdditionalNodes, iterations, Delivery.RELIABLE,
		false);
	    int numExpectedMessages = (1 + numAdditionalNodes) * iterations;
	    client.validateMessageSequence(
		client.clientReceivedMessages, numExpectedMessages, 0);
	} finally {
	    client.disconnect();
	}
    }
    
    protected void sendMessagesFromNodesToClient(
	    final DummyClient client, int numAdditionalNodes, int iterations,
	    final Delivery delivery, final boolean oneUnreliableServer)
	throws Exception
    {
	    final String counterName = "counter";
	    client.connect(serverNode.getAppPort());
	    assertTrue(client.login());
	    for (int i = 0; i < numAdditionalNodes; i++) {
		addNodes(Integer.toString(i));
	    }
	    
	    final List<SgsTestNode> nodes = new ArrayList<SgsTestNode>();
	    nodes.add(serverNode);
	    nodes.addAll(additionalNodes.values());
	    int numExpectedMessages = 
		oneUnreliableServer ?
		iterations :
		nodes.size() * iterations;
	    
	    // Replace each node's ClientSessionServer, bound in the data
	    // service, with a wrapped server that delays before sending
	    // the message.
	    txnScheduler.runTask(new TestAbstractKernelRunnable() {
		@SuppressWarnings("unchecked")
		public void run() {
		    boolean setUnreliableServer = oneUnreliableServer;
		    for (SgsTestNode node : nodes) {
			String key = "com.sun.sgs.impl.service.session.server." +
			    node.getNodeId();
			ClientSessionServer sessionServer =
			    ((ManagedSerializable<ClientSessionServer>)
			     dataService.getServiceBinding(key)).get();
			InvocationHandler handler;
			if (setUnreliableServer) {
			    handler = new HungryInvocationHandler(sessionServer);
			    setUnreliableServer = false;
			} else {
			    handler =
				new DelayingInvocationHandler(sessionServer);
			}
			ClientSessionServer delayingServer =
			    (ClientSessionServer)
			    Proxy.newProxyInstance(
				ClientSessionServer.class.getClassLoader(),
				new Class[] { ClientSessionServer.class },
				handler);
			dataService.setServiceBinding(
			    key, new ManagedSerializable(delayingServer));
		    }
		}}, taskOwner);

	    for (int i = 0; i < iterations; i++) {
		for (SgsTestNode node : nodes) {
		    TransactionScheduler localTxnScheduler = 
			node.getSystemRegistry().
			    getComponent(TransactionScheduler.class);
		    Identity identity = node.getProxy().getCurrentOwner();
		    localTxnScheduler.scheduleTask(
		    	  new TestAbstractKernelRunnable() {
			    public void run() {
				DataManager dataManager =
				    AppContext.getDataManager();
				Counter counter;
				try {
				    counter = (Counter)
					dataManager.getBinding(counterName);
				} catch (NameNotBoundException e) {
				    throw new MaybeRetryException("retry", true);
				}
				ClientSession session = (ClientSession)
				    dataManager.getBinding(client.name);
				MessageBuffer buf = new MessageBuffer(4);
				buf.putInt(counter.getAndIncrement());
				session.send(ByteBuffer.wrap(buf.getBuffer()),
					     delivery);
			    }},
			
			identity);
		}
	    }
	    txnScheduler.runTask(new TestAbstractKernelRunnable() {
		public void run() {
		    AppContext.getDataManager().
			setBinding(counterName, new Counter());
		}}, taskOwner);
	    
	    client.waitForClientToReceiveExpectedMessages(numExpectedMessages);

    }

    @Test
    public void testClientSessionSendNullMessage() throws Exception {
	try {
	    sendBufferToClient(null, null);
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
    }

    @Test
    public void testClientSessionSendNullDelivery() throws Exception {
	try {
	    sendBufferToClient(ByteBuffer.wrap(new byte[0]), "", null);
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
    }

    @Test
    public void testClientSessionSendSameBuffer() throws Exception {
	String msgString = "buffer";
	MessageBuffer msg =
	    new MessageBuffer(MessageBuffer.getSize(msgString));
	msg.putString(msgString);
	ByteBuffer buf = ByteBuffer.wrap(msg.getBuffer());
	sendBufferToClient(buf, msgString);
    }

    @Test
    public void testClientSessionSendSameBufferWithOffset()
	throws Exception
    {
	String msgString = "offset buffer";
	MessageBuffer msg =
	    new MessageBuffer(MessageBuffer.getSize(msgString) + 1);
	msg.putByte(0);
	msg.putString(msgString);
	ByteBuffer buf = ByteBuffer.wrap(msg.getBuffer());
	buf.position(1);
	sendBufferToClient(buf, msgString);
    }

    private void sendBufferToClient(final ByteBuffer buf,
				    final String expectedMsgString)
	throws Exception
    {
	sendBufferToClient(buf, expectedMsgString, Delivery.RELIABLE);
    }
	
    private void sendBufferToClient(final ByteBuffer buf,
				    final String expectedMsgString,
				    final Delivery delivery)
	throws Exception
    {	
	final String name = "dummy";
	DummyClient client = createDummyClient(name);
	try {
	    client.connect(serverNode.getAppPort());
	    assertTrue(client.login());
	    final int numMessages = 3;
	    for (int i = 0; i < numMessages; i++) {
		txnScheduler.runTask(new TestAbstractKernelRunnable() {
			public void run() {
			    ClientSession session = (ClientSession)
				AppContext.getDataManager().getBinding(name);
			    System.err.println("Sending messages");
			    session.send(buf, delivery);
			} }, taskOwner);
	    }
	
	    System.err.println("waiting for client to receive messages");
	    client.waitForClientToReceiveExpectedMessages(numMessages);
	    for (byte[] message : client.clientReceivedMessages) {
		if (message.length == 0) {
		    fail("message buffer emtpy");
		}
		String msgString = (new MessageBuffer(message)).getString();
		if (!msgString.equals(expectedMsgString)) {
		    fail("expected: " + expectedMsgString + ", received: " +
			 msgString);
		} else {
		    System.err.println("received expected message: " +
				       msgString);
		}
	    }
	} finally {
	    client.disconnect();
	}
    }
    
    private static class Counter implements ManagedObject, Serializable {
	private static final long serialVersionUID = 1L;

	private int value = 0;

	int getAndIncrement() {
	    AppContext.getDataManager().markForUpdate(this);
	    return value++;
	}
    }

    // Test sending from the server to the client session in a transaction that
    // aborts with a retryable exception to make sure that message buffers are
    // reclaimed.  Try sending 4K bytes, and have the task abort 100 times with
    // a retryable exception so the task is retried.  If the buffers are not
    // being reclaimed then the sends will eventually fail because the buffer
    // space is used up.  Note that this test assumes that sending 400 KB of
    // data will surpass the I/O throttling limit.
    @Test
    public void testClientSessionSendAbortRetryable() throws Exception {
	DummyClient client = createDummyClient("clientname");
	try {
	    client.connect(serverNode.getAppPort());
	    assertTrue(client.login());
	    txnScheduler.runTask(
		new TestAbstractKernelRunnable() {
		    int tryCount = 0;
		    public void run() {
			Set<ClientSession> sessions =
			    getAppListener().getSessions();
			ClientSession session = sessions.iterator().next();
			try {
			    session.send(ByteBuffer.wrap(new byte[4096]));
			} catch (MessageRejectedException e) {
			    fail("Should not run out of buffer space: " + e);
			}
			if (++tryCount < 100) {
			    throw new MaybeRetryException("Retryable",  true);
			}
		    }
		}, taskOwner);
	} finally {
	    client.disconnect();
	}
    }

    @Test
    public void testClientSend() throws Exception {
	String name = "clientname";
	DummyClient client = createDummyClient(name);
	try {
	    client.connect(serverNode.getAppPort());
	    assertTrue(client.login());
	    client.sendMessagesFromClientInSequence(5, 5);
	} finally {
	    client.disconnect();
	}
    }

    @Test
    public void testClientSendWithListenerThrowingRetryableException()
	throws Exception
    {
	String name = "clientname";
	DummyClient client = createDummyClient(name);
	try {
	    client.connect(serverNode.getAppPort());
	    assertTrue(client.login());
	    receivedMessageException =
		new MaybeRetryException("retryable", true);
	    client.sendMessagesFromClientInSequence(5, 5);
	} finally {
	    client.disconnect();
	}
    }

    @Test
    public void testClientSendWithListenerThrowingNonRetryableException()
	throws Exception
    {
	String name = "clientname";
	DummyClient client = createDummyClient(name);
	try {
	    client.connect(serverNode.getAppPort());
	    assertTrue(client.login());
	    receivedMessageException =
		new MaybeRetryException("non-retryable", false);
	    int numMessages = 5;
	    for (int i = 0; i < numMessages; i++) {
		ByteBuffer buf = ByteBuffer.allocate(4);
		buf.putInt(i).flip();
		client.sendMessage(buf.array(), true);
	    }
	    client.waitForSessionListenerToReceiveExpectedMessages(
		numMessages - 1);
	    client.validateMessageSequence(
		client.sessionListenerReceivedMessages, numMessages - 1,
		1);

	} finally {
	    client.disconnect();
	}
    }

    @Test
    @IntegrationTest
    public void testLocalSendPerformance() throws Exception {
	final String user = "dummy";
	DummyClient client = createDummyClient(user);
	assertTrue(client.connect(serverNode.getAppPort()).login());

	int numIterations = 10;
	final ByteBuffer msg = ByteBuffer.allocate(0);
	long startTime = System.currentTimeMillis();
	for (int i = 0; i < numIterations; i++) {
	    txnScheduler.runTask(new TestAbstractKernelRunnable() {
		public void run() {
		    DataManager dataManager = AppContext.getDataManager();
		    ClientSession session = (ClientSession)
			dataManager.getBinding(user);
		    session.send(msg);
		}}, taskOwner);
	}
	long endTime = System.currentTimeMillis();
	System.err.println("send, iterations: " + numIterations +
			   ", elapsed time: " + (endTime - startTime) +
			   " ms.");
    }

    @Test
    public void testRemoveSessionWhileSessionDisconnects() throws Exception {
	final String user = "foo";
	DummyClient client = createDummyClient(user);
	assertTrue(client.connect(serverNode.getAppPort()).login());
	client.sendMessage(new byte[0], true);
	client.logout();
	client.checkDisconnectedCallback(true);
    }

    /* -- other methods -- */

    /**
     * Creates a new {@code DummyClient} with the specified {@code name}
     * and this test instance's protocol version.
     */
    protected DummyClient createDummyClient(String name) {
	return new DummyClient(name, protocolVersion);
    }

    /**
     * Sends the number of specified messages from the specified node to
     * the specified client.  The content of each message is a consecutive
     * integer starting at the specified offset.
     */
    protected  void sendMessagesFromNode(
	SgsTestNode node, final DummyClient client, final int numMessages,
	final int offset)
	throws Exception
    {
	System.err.println("sending messages to client [" + client.name + "]");
        TransactionScheduler transactionScheduler = 
            node.getSystemRegistry(). getComponent(TransactionScheduler.class);
	for (int i = 0; i < numMessages; i++) {
	    final int x = i + offset;
	    transactionScheduler.runTask(new TestAbstractKernelRunnable() {
		public void run() {
		    ClientSession session = (ClientSession)
			AppContext.getDataManager().getBinding(client.name);
			ByteBuffer buf = ByteBuffer.allocate(4);
			buf.putInt(x).flip();
			session.send(buf, Delivery.RELIABLE);
		    } }, taskOwner);
	}
    }

    /**
     * Sends the number of specified messages from the specified node to
     * the specified client and waits for the client to receive the
     * expected messages.  The content of each message is a consecutive
     * integer starting at the specified offset.
     */
    protected void sendMessagesFromNodeToClient(
	SgsTestNode node,  DummyClient client, int numMessages, int offset)
	throws Exception
    {
        sendMessagesFromNode(node, client, numMessages, offset);
	synchronized (client.clientReceivedMessages) {
	    client.waitForClientToReceiveExpectedMessages(numMessages);
	    client.validateMessageSequence(
		client.clientReceivedMessages, numMessages, offset);
	    client.clientReceivedMessages.clear();
	}
    }
    
    /**
     * Check that the session bindings are the expected number and throw an
     * exception if they aren't.
     */
    protected void checkBindings(int numExpected) throws Exception {
	
	List<String> listenerKeys = getServiceBindingKeys(LISTENER_PREFIX);
	System.err.println("Listener keys: " + listenerKeys);
	if (listenerKeys.size() != numExpected) {
	    fail("expected " + numExpected + " listener keys, got " +
		 listenerKeys.size());
	}
	    
	List<String> sessionKeys = getServiceBindingKeys(SESSION_PREFIX);
	System.err.println("Session keys: " + sessionKeys);
	if (sessionKeys.size() != numExpected) {
	    fail("expected " + numExpected + " session keys, got " +
		 sessionKeys.size());
	}
	    
	List<String> sessionNodeKeys =
	    getServiceBindingKeys(SESSION_NODE_PREFIX);
	System.err.println("Session node keys: " + sessionNodeKeys);
	if (sessionNodeKeys.size() != numExpected) {
	    fail("expected " + numExpected + " session node keys, got " +
		 sessionNodeKeys.size());
	}
    }
    
    private List<String> getServiceBindingKeys(String prefix)
	throws Exception
    {
        GetKeysTask task = new GetKeysTask(prefix);
        txnScheduler.runTask(task, taskOwner);
        return task.getKeys();
    }

    private class GetKeysTask extends TestAbstractKernelRunnable {
        private List<String> keys = new ArrayList<String>();
        private final String prefix;
        GetKeysTask(String prefix) {
            this.prefix = prefix;
        }
        public void run() throws Exception {
            String key = prefix;
            for (;;) {
                key = dataService.nextServiceBoundName(key);
                if (key == null ||
                    ! key.regionMatches(
                          0, prefix, 0, prefix.length()))
                {
                    break;
                }
                keys.add(key);
            }
        }
        public List<String> getKeys() { return keys;}
    }

    /** Find the app listener */
    private DummyAppListener getAppListener() {
	return (DummyAppListener) dataService.getServiceBinding(
	    StandardProperties.APP_LISTENER);
    }

    public static class DummyAppListener implements AppListener, 
                                                    ManagedObject,
                                                    Serializable {

	private final static long serialVersionUID = 1L;

	private final Map<ManagedReference<ClientSession>,
			  ManagedReference<DummyClientSessionListener>>
	    sessions = Collections.synchronizedMap(
		new HashMap<ManagedReference<ClientSession>,
			    ManagedReference<DummyClientSessionListener>>());

        /** {@inheritDoc} */
	public ClientSessionListener loggedIn(ClientSession session) {

	    if (!(session instanceof ClientSessionWrapper)) {
		throw new IllegalArgumentException(
		    "session not instance of ClientSessionWrapper:" +
		    session);
	    }

	    String name = session.getName();
	    DummyClientSessionListener listener;
	    
	    if (name.equals(RETURN_NULL)) {
		return null;
	    } else if (name.equals(NON_SERIALIZABLE)) {
		return new NonSerializableClientSessionListener();
	    } else if (name.equals(THROW_RUNTIME_EXCEPTION)) {
		throw new RuntimeException("loggedIn throwing an exception");
	    } else if (name.equals(DISCONNECT_THROWS_NONRETRYABLE_EXCEPTION) ||
		       name.startsWith("badClient")) {
		listener = new DummyClientSessionListener(name, session, true);
	    } else {
		listener = new DummyClientSessionListener(name, session, false);
	    }
	    DataManager dataManager = AppContext.getDataManager();
	    ManagedReference<ClientSession> sessionRef =
		dataManager.createReference(session);
	    ManagedReference<DummyClientSessionListener> listenerRef =
		dataManager.createReference(listener);
	    dataManager.markForUpdate(this);
	    sessions.put(sessionRef, listenerRef);
	    dataManager.setBinding(session.getName(), session);
	    System.err.println("DummyAppListener.loggedIn: session:" + session);
	    return listener;
	}

        /** {@inheritDoc} */
	public void initialize(Properties props) {
	}

	private Set<ClientSession> getSessions() {
	    Set<ClientSession> sessionSet =
		new HashSet<ClientSession>();
	    for (ManagedReference<ClientSession> sessionRef
		     : sessions.keySet())
	    {
		sessionSet.add(sessionRef.get());
	    }
	    return sessionSet;
	}
    }

    private static class NonSerializableClientSessionListener
	implements ClientSessionListener
    {
        /** {@inheritDoc} */
	public void disconnected(boolean graceful) {
	}

        /** {@inheritDoc} */
	public void receivedMessage(ByteBuffer message) {
	}
    }

    private static class DummyClientSessionListener
	implements ClientSessionListener, Serializable, ManagedObject
    {
	private final static long serialVersionUID = 1L;
	private final String name;
	private final ManagedReference<ClientSession> sessionRef;
	private BigInteger reconnectKey = null;
	private final boolean disconnectedThrowsException;


	DummyClientSessionListener(
	    String name, ClientSession session,
	    boolean disconnectedThrowsException)
	{
	    this.name = name;
	    this.sessionRef =
		AppContext.getDataManager().createReference(session);
	    this.disconnectedThrowsException = disconnectedThrowsException;
	}

        /** {@inheritDoc} */
	public void disconnected(boolean graceful) {
	    System.err.println("DummyClientSessionListener[" + name +
			       "] disconnected invoked with " + graceful);
	    AppContext.getDataManager().removeObject(sessionRef.get());
	    DummyClient client =
                    reconnectKey == null ? null :
                                           dummyClients.get(reconnectKey);
	    if (client != null) {
		client.setDisconnectedCallbackInvoked(graceful);
	    }
	    if (disconnectedThrowsException) {
		throw new RuntimeException(
		    "disconnected throws non-retryable exception");
	    }
	}

        /** {@inheritDoc} */
	public void receivedMessage(ByteBuffer message) {
            byte[] messageBytes = new byte[message.remaining()];
	    message.get(messageBytes);
	    MessageBuffer buf = new MessageBuffer(messageBytes);
	    AppContext.getDataManager().markForUpdate(this);
	    reconnectKey = new BigInteger(1, buf.getByteArray());
	    byte[] bytes = buf.getByteArray();
	    if (bytes.length == 0) {
		return;
	    }
	    DummyClient client = dummyClients.get(reconnectKey);
	    System.err.println(
		"DummyClientSessionListener[" + name + "] " +
		"receivedMessage: " + HexDumper.toHexString(bytes) + 
		"\nthrow exception: " + receivedMessageException);
	    if (receivedMessageException != null) {
		RuntimeException re = receivedMessageException;
		receivedMessageException = null;
		throw re;
	    }
	    synchronized (client.sessionListenerReceivedMessages) {
		client.sessionListenerReceivedMessages.add(bytes);
		if (client.sessionListenerReceivedMessages.size() ==
		    client.numSessionListenerExpectedMessages)
		{
		    client.sessionListenerReceivedMessages.notifyAll();
		}
	    }
	    // Echo the received message back to the client
	    ByteBuffer bbuf = ByteBuffer.allocate(bytes.length);
	    bbuf.put(bytes).flip();
	    sessionRef.get().send(bbuf);
	}
    }

    private static class MaybeRetryException
	extends RuntimeException implements ExceptionRetryStatus
    {
	private static final long serialVersionUID = 1L;
	private boolean retry;

	public MaybeRetryException(String s, boolean retry) {
	    super(s);
	    this.retry = retry;
	}

	public boolean shouldRetry() {
	    return retry;
	}
    }

    /**
     * This invocation handler adds a 100 ms delay before invoking any
     * method on the underlying instance.
     */
    private static class DelayingInvocationHandler
	implements InvocationHandler, Serializable
    {
	private final static long serialVersionUID = 1L;
	private Object obj;
	
	DelayingInvocationHandler(Object obj) {
	    this.obj = obj;
	}
	
	public Object invoke(Object proxy, Method method, Object[] args)
	    throws Exception
	{
	    Thread.sleep(100);
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

    /**
     * This invocation handler prevents forwarding the {@code send} and
     * {@code serviceEventQueue} methods to the underlying instance.
     */
    private static class HungryInvocationHandler
	implements InvocationHandler, Serializable
    {
	private final static long serialVersionUID = 1L;
	private Object obj;
	
	HungryInvocationHandler(Object obj) {
	    this.obj = obj;
	}
	
	public Object invoke(Object proxy, Method method, Object[] args)
	    throws Exception
	{
	    String name = method.getName();
	    if (name.equals("send") || name.equals("serviceEventQueue")) {
		return null;
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
                if (!name.equals("com.sun.sgs.impl.service.task.PendingTask") &&
		    !name.equals("com.sun.sgs.impl.service.nodemap.IdentityMO"))
		{
		    /*
		    System.err.print(
		        count + "[" + obj.getClass().getName() + "]:");
		    try {
			System.err.println(obj.toString());
		    } catch (ObjectNotFoundException e) {
			System.err.println(
			    "<< caught ObjectNotFoundException >>");
		    }
		    */
                    count++;
		}
                last = next;
	    }
	}
    }

    static class DummyClient extends AbstractDummyClient {

	private final Object disconnectedCallbackLock = new Object();
	private boolean receivedDisconnectedCallback = false;
	private boolean graceful = false;

	private volatile int numClientExpectedMessages;
	private volatile int numSessionListenerExpectedMessages;
	// Messages received by this client's associated ClientSessionListener
	public Queue<byte[]> sessionListenerReceivedMessages =
	    new ConcurrentLinkedQueue<byte[]>();
	public final Queue<byte[]> clientReceivedMessages =
	    new ConcurrentLinkedQueue<byte[]>();

	

	/** Constructs an instance with the given {@code name}. */
	DummyClient(String name) {
	    super(name);
	}

	DummyClient(String name, byte protocolVersion) {
	    super(name, protocolVersion);
	}

	/**
	 * Records this client in the global map, keyed by client
	 * reconnectKey.
	 */
	protected void newReconnectKey(byte[] reconnectKey) {
	    dummyClients.put(new BigInteger(1, reconnectKey), this);
	}

	/**
	 * Handles an {@code opcode}.
	 */
	@Override
	protected void handleOpCode(byte opcode, MessageBuffer buf) {
	    switch (opcode) {
		
	    case SimpleSgsProtocol.SESSION_MESSAGE:
		byte[] message = buf.getBytes(buf.limit() - buf.position());
		synchronized (clientReceivedMessages) {
		    clientReceivedMessages.add(message);
		    System.err.println(
		    	toString() + " received SESSION_MESSAGE: " +
			HexDumper.toHexString(message));
		    if (clientReceivedMessages.size() ==
			numClientExpectedMessages)
		    {
			clientReceivedMessages.notifyAll();
		    }
		}
		break;
	    
	    default:
		super.handleOpCode(opcode, buf);
		break;
	    }
	}

	/**
	 * Sends a SESSION_MESSAGE with the specified content, prefixing
	 * the message with the reconnect key.  The {@code receivedMessage}
	 * method of this client's associated ClientSessionListener must
	 * expect that each message is prefixed with the reconnect key.
	 */
	@Override
        public void sendMessage(byte[] message, boolean checkSuspend) {
	    MessageBuffer buf =
		new MessageBuffer(5 + reconnectKey.length + message.length);
	    buf.putByte(SimpleSgsProtocol.SESSION_MESSAGE).
		putByteArray(reconnectKey).
		putByteArray(message);
	    sendRaw(buf.getBuffer(), checkSuspend);
	}

	/**
	 * Records that this client's associated ClientSessionListener's
	 * {@code disconnected} method was invoked with the specified value
	 * for {@code graceful}.
	 */
	public void setDisconnectedCallbackInvoked(boolean graceful) {
	    synchronized (disconnectedCallbackLock) {
		receivedDisconnectedCallback = true;
		this.graceful = graceful;
		disconnectedCallbackLock.notifyAll();
	    }
	}

	/**
	 * Verifies that the {@code disconnected} method of this client's
	 * associated ClientSessionListener was invoked with the specified
	 * value for {@code graceful}, and if not, this method throws
	 * {@code AssertionError}.
	 */
	public void assertDisconnectedCallbackInvoked(boolean graceful) {
	    synchronized (disconnectedCallbackLock) {
		assertTrue(receivedDisconnectedCallback);
		assertEquals(this.graceful, graceful);
	    }
	}

	/**
	 * Verifies that the {@code disconnected} method of this client's
	 * associated ClientSessionListener was NOT invoked, otherwise this
	 * method throws {@code AssertionError}.
	 */
	public void assertDisconnectedCallbackNotInvoked() {
	    synchronized (disconnectedCallbackLock) {
		assertFalse(receivedDisconnectedCallback);
	    }
	}

	/**
	 * Waits for the {@code disconnected} method of this client's
	 * associated ClientSessionListener to be invoked with the
	 * specified value for {@code graceful} (within a timeout
	 * period), and if not, this method throws {@code AssertionError}.
	 */
	public void checkDisconnectedCallback(boolean graceful)
	    throws Exception
	{
	    synchronized (disconnectedCallbackLock) {
		if (!receivedDisconnectedCallback) {
		    disconnectedCallbackLock.wait(WAIT_TIME);
		}
		assertDisconnectedCallbackInvoked(graceful);
		System.err.println(toString() + " disconnect successful");
	    }
	}

	/**
	 * From this client, sends the number of messages (each containing a
	 * monotonically increasing sequence number), then waits for all the
	 * messages to be received by this client's associated {@code
	 * ClientSessionListener}, and validates the sequence of messages
	 * received by the listener.  
	 */
	public void sendMessagesFromClientInSequence(
	    int numMessages, int numExpectedMessages)
	{
	    for (int i = 0; i < numMessages; i++) {
		MessageBuffer buf = new MessageBuffer(4);
		buf.putInt(i);
		sendMessage(buf.getBuffer(), true);
	    }
	    waitForSessionListenerToReceiveExpectedMessages(
		numExpectedMessages);
	    validateMessageSequence(
		sessionListenerReceivedMessages, numExpectedMessages, 0);
	}
	
	/**
	 * Waits for this client to receive the number of session messages
	 * sent from the application.
	 */
	public void waitForClientToReceiveExpectedMessages(
	    int numExpectedMessages)
	{
	    numClientExpectedMessages = numExpectedMessages;
	    synchronized (clientReceivedMessages) {
		if (clientReceivedMessages.size() != numExpectedMessages)
		{
		    try {
			clientReceivedMessages.wait(WAIT_TIME);
		    } catch (InterruptedException e) {
		    }
		}
	    }
	    assertEquals(
		"Client " + toString() +
		" did not receive expected number of messages",
		numExpectedMessages, clientReceivedMessages.size());
	    System.err.println(
		toString() + " received expected number of messages: " +
		numExpectedMessages);
	}
	
	/**
	 * Waits for this client's ClientSessionListener to receive the
	 * specified number of messages.
	 */
	public void waitForSessionListenerToReceiveExpectedMessages(
	    int numExpectedMessages)
	{
	    numSessionListenerExpectedMessages = numExpectedMessages;
	    synchronized (sessionListenerReceivedMessages) {
		if (sessionListenerReceivedMessages.size() !=
		    numExpectedMessages)
		{
		    try {
			sessionListenerReceivedMessages.wait(WAIT_TIME);
		    } catch (InterruptedException e) {
		    }
		}
	    }
	    assertEquals(
		"ClientSessionListener for " + toString() +
		" did not receive expected number of messages",
		numExpectedMessages, sessionListenerReceivedMessages.size());
	    System.err.println(
		"ClientSessionListener for " + toString() +
		" received expected number of messages: " +
		numExpectedMessages);
	}

	/**
	 * Waits for the number of expected messages to be recorded in the
	 * specified 'list', and validates that the expected number of messages
	 * were received by the ClientSessionListener in the correct sequence.
	 */
	public void validateMessageSequence(
	    Queue<byte[]> messageQueue, int numExpectedMessages, int offset)
	{
	    assertEquals(
		"validateMessageSequence: unexpected number of messages",
		numExpectedMessages, messageQueue.size());
	    if (numExpectedMessages != 0) {
		int expectedValue = offset;
		for (byte[] message : messageQueue) {
		    MessageBuffer buf = new MessageBuffer(message);
		    int value = buf.getInt();
		    System.err.println(
			toString() + " validating message sequence: " + value);
		    assertEquals("unexpected value", expectedValue, value);
		    expectedValue++;
		}
	    }
	}
	
	/**
	 * {@inheritDoc}
	 *
	 * <p>This implementation verifies that no messages were received
	 * by this client's associated ClientSessionListener during relocation.
	 */
	public void relocate(int newPort, boolean useValidKey,
			     boolean shouldSucceed)
	{
	    super.relocate(newPort, useValidKey, shouldSucceed);
	    // Verify that no messages were received by this client's
	    // associated ClientSessionListener during relocation.
	    waitForSessionListenerToReceiveExpectedMessages(0);
	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>This implementation clears the clientReceivedMessages and
	 * sessionListenerReceivedMessages queue.
	 */
	public void disconnect() {
	    super.disconnect();
	    clientReceivedMessages.clear();
	    sessionListenerReceivedMessages.clear();
	}
    }
}
