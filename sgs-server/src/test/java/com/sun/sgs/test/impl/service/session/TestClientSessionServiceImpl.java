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
import com.sun.sgs.impl.io.SocketEndpoint;
import com.sun.sgs.impl.io.TransportType;
import com.sun.sgs.impl.kernel.StandardProperties;
import com.sun.sgs.impl.protocol.simple.SimpleSgsProtocolAcceptor;
import com.sun.sgs.impl.service.session.ClientSessionServer;
import com.sun.sgs.impl.service.session.ClientSessionServiceImpl;
import com.sun.sgs.impl.service.session.ClientSessionWrapper;
import com.sun.sgs.impl.sharedutil.HexDumper;
import com.sun.sgs.impl.sharedutil.MessageBuffer;
import com.sun.sgs.impl.util.AbstractService.Version;
import com.sun.sgs.io.Connector;
import com.sun.sgs.io.Connection;
import com.sun.sgs.io.ConnectionListener;
import com.sun.sgs.kernel.TransactionScheduler;
import com.sun.sgs.protocol.simple.SimpleSgsProtocol;
import com.sun.sgs.service.ClientSessionDisconnectListener;
import com.sun.sgs.service.ClientSessionService;
import com.sun.sgs.service.DataService;
import com.sun.sgs.test.util.SgsTestNode;
import com.sun.sgs.test.util.SimpleTestIdentityAuthenticator;
import com.sun.sgs.test.util.TestAbstractKernelRunnable;
import com.sun.sgs.tools.test.FilteredNameRunner;
import com.sun.sgs.tools.test.IntegrationTest;
import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
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
public class TestClientSessionServiceImpl extends Assert {

    private static final String APP_NAME = "TestClientSessionServiceImpl";
    
    private static final String LOGIN_FAILED_MESSAGE = "login failed";

    private static final int WAIT_TIME = 5000;

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

    /** The ClientSession service properties. */
    private static final Properties serviceProps =
	createProperties(
	    StandardProperties.APP_NAME, APP_NAME,
            com.sun.sgs.impl.transport.tcp.TcpTransport.LISTEN_PORT_PROPERTY, "20000");

    /** The node that creates the servers. */
    private SgsTestNode serverNode;

    /** Any additional nodes, keyed by node host name (for tests
     * needing more than one node). */
    private Map<String,SgsTestNode> additionalNodes;

    /** Version information from ClientSessionServiceImpl class. */
    private final String VERSION_KEY;
    private final int MAJOR_VERSION;
    private final int MINOR_VERSION;
    
    /** If {@code true}, shuts off some printing during performance tests. */
    private boolean isPerformanceTest = false;
    
    /** The transaction scheduler. */
    private TransactionScheduler txnScheduler;

    /** The owner for tasks I initiate. */
    private Identity taskOwner;

    /** The shared data service. */
    private DataService dataService;

    /** The test clients, keyed by client session ID. */
    private static Map<BigInteger, DummyClient> dummyClients;
    
    private static Field getField(Class cl, String name) throws Exception {
	Field field = cl.getDeclaredField(name);
	field.setAccessible(true);
	return field;
    }

    /** Constructs a test instance. */
    public TestClientSessionServiceImpl() throws Exception {
	Class cl = ClientSessionServiceImpl.class;
	VERSION_KEY = (String) getField(cl, "VERSION_KEY").get(null);
	MAJOR_VERSION = getField(cl, "MAJOR_VERSION").getInt(null);
	MINOR_VERSION = getField(cl, "MINOR_VERSION").getInt(null);
    }

    @Before
    public void setUp() throws Exception {
        dummyClients = new HashMap<BigInteger, DummyClient>();
        setUp(null, true);
    }

    /** Creates and configures the session service. */
    protected void setUp(Properties props, boolean clean) throws Exception {
	if (props == null) {
	    props = 
                SgsTestNode.getDefaultProperties(APP_NAME, null, 
						 DummyAppListener.class);
	}
        props.setProperty(StandardProperties.AUTHENTICATORS, 
                      "com.sun.sgs.test.util.SimpleTestIdentityAuthenticator");
	props.setProperty("com.sun.sgs.impl.service.watchdog.server.renew.interval",
			  "100000");
	serverNode = 
                new SgsTestNode(APP_NAME, DummyAppListener.class, props, clean);

        txnScheduler = 
            serverNode.getSystemRegistry().
            getComponent(TransactionScheduler.class);
        taskOwner = serverNode.getProxy().getCurrentOwner();

        dataService = serverNode.getDataService();
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

    // -- Test constructor --

    @Test
    public void testConstructorNullProperties() throws Exception {
	try {
	    new ClientSessionServiceImpl(
		null, serverNode.getSystemRegistry(),
		serverNode.getProxy());
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
    }

    @Test
    public void testConstructorNullComponentRegistry() throws Exception {
	try {
	    new ClientSessionServiceImpl(serviceProps, null,
					 serverNode.getProxy());
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
    }

    @Test
    public void testConstructorNullTransactionProxy() throws Exception {
	try {
	    new ClientSessionServiceImpl(serviceProps,
					 serverNode.getSystemRegistry(), null);
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
    }

    @Test
    public void testConstructorNoAppName() throws Exception {
	try {
	    new ClientSessionServiceImpl(
		new Properties(), serverNode.getSystemRegistry(),
		serverNode.getProxy());
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
    }

    @Test
    public void testConstructorNoPort() throws Exception {
        Properties props =
            createProperties(StandardProperties.APP_NAME, APP_NAME);
        new ClientSessionServiceImpl(
            props, serverNode.getSystemRegistry(),
            serverNode.getProxy());
    }

    @Test
    public void testConstructorDisconnectDelayTooSmall() throws Exception {
	try {
	    Properties props =
		createProperties(
		    StandardProperties.APP_NAME, APP_NAME,
                    SimpleSgsProtocolAcceptor.DISCONNECT_DELAY_PROPERTY, "199");
	    new ClientSessionServiceImpl(
		props, serverNode.getSystemRegistry(),
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

	new ClientSessionServiceImpl(
	    serviceProps, serverNode.getSystemRegistry(),
	    serverNode.getProxy());
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
	    new ClientSessionServiceImpl(
		serviceProps, serverNode.getSystemRegistry(),
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
	    new ClientSessionServiceImpl(
		serviceProps, serverNode.getSystemRegistry(),
		serverNode.getProxy());
	    fail("Expected IllegalStateException");
	} catch (IllegalStateException e) {
	    System.err.println(e);
	}
    }

    // -- Test registerSessionDisconnectListener --

    @Test
    public void testRegisterSessionDisconnectListenerNullArg() {
	try {
	    serverNode.getClientSessionService().
		registerSessionDisconnectListener(null);
	    fail("expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
    }

    @Test
    public void testRegisterSessionDisconnectListenerInTxn()
	throws Exception
    {
	try {
	    txnScheduler.runTask(new TestAbstractKernelRunnable() {
		public void run() {
		    serverNode.getClientSessionService().
			registerSessionDisconnectListener(
			    new DummyDisconnectListener());
		}}, taskOwner);
	    fail("expected IllegalStateException");
	} catch (IllegalStateException e) {
	    System.err.println(e);
	}
    }

    @Test
    public void testRegisterSessionDisconnectListenerNoTxn() {
	serverNode.getClientSessionService().
	    registerSessionDisconnectListener(new DummyDisconnectListener());
    }

    // -- Test getSessionProtocol --

    @Test
    public void testGetSessionProtocolNullArg() {
	try {
	    serverNode.getClientSessionService(). getSessionProtocol(null);
	    fail("expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
    }

    @Test
    public void testGetSessionProtocolInTxn()
	throws Exception
    {
	try {
	    txnScheduler.runTask(new TestAbstractKernelRunnable() {
		public void run() {
		    serverNode.getClientSessionService().
			getSessionProtocol(new BigInteger(1, new byte[] {0}));
		}}, taskOwner);
	    fail("expected IllegalStateException");
	} catch (IllegalStateException e) {
	    System.err.println(e);
	}
    }

    @Test
    public void testGetSessionProtocolNoTxn() {
	assertNull(serverNode.getClientSessionService().
		   getSessionProtocol(new BigInteger(1, new byte[] {0})));
    }
    
    // -- Test connecting, logging in, logging out with server -- 

    @Test
    public void testConnection() throws Exception {
	DummyClient client = new DummyClient("foo");
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
	DummyClient client = new DummyClient("success");
	try {
	    client.connect(serverNode.getAppPort());
	    client.login();
	} finally {
            client.disconnect();
	}
    }

    @Test
    @IntegrationTest
    public void testLoginRedirect() throws Exception {
	int serverAppPort = serverNode.getAppPort();
	String[] hosts = new String[] { "one", "two", "three", "four"};
	String[] users = new String[] { "sleepy", "bashful", "dopey", "doc" };
	Set<DummyClient> clients = new HashSet<DummyClient>();
	addNodes(hosts);
	boolean failed = false;
	int redirectCount = 0;
	try {
	    for (String user : users) {
		DummyClient client = new DummyClient(user);
		client.connect(serverAppPort);
		if (! client.login()) {
		    // login redirected
		    redirectCount++;
                    int port = client.redirectPort;
		    client = new DummyClient(user);
		    client.connect(port);
		    if (!client.login()) {
			failed = true;
			System.err.println("login for user: " + user +
					   " redirected twice");
		    }
		}
		clients.add(client);
	    }
	    
	    int expectedRedirects = users.length;
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
	DummyClient client = new DummyClient("dummy");
	try {
	    client.connect(serverNode.getAppPort());
	    client.login(false);
	    client.sendMessagesInSequence(1, 0, null);
	} finally {
            client.disconnect();
	}
    }

    @Test
    @IntegrationTest
    public void testSendAfterLoginComplete() throws Exception {
	DummyClient client = new DummyClient("dummy");
	try {
	    client.connect(serverNode.getAppPort());
	    client.login(true);
	    client.sendMessagesInSequence(1, 1, null);
	} finally {
            client.disconnect();
	}
    }

    @Test
    @IntegrationTest
    public void testLoginSuccessAndNotifyLoggedInCallback() throws Exception {
	String name = "success";
	DummyClient client = new DummyClient(name);
	try {
	    client.connect(serverNode.getAppPort());
	    client.login();
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
	DummyClient client = new DummyClient(NON_SERIALIZABLE);
	try {
	    client.connect(serverNode.getAppPort());
	    client.login();
	    fail("expected login failure");
	} catch (RuntimeException e) {
	    if (e.getMessage().equals(LOGIN_FAILED_MESSAGE)) {
		System.err.println("login refused");
		if (SimpleTestIdentityAuthenticator.allIdentities.
		        getNotifyLoggedIn(NON_SERIALIZABLE))
		{
		    fail("unexpected notifyLoggedIn invoked on identity: " +
			 NON_SERIALIZABLE);
		}
		return;
	    } else {
		fail("unexpected login failure: " + e);
	    }
	} finally {
	    client.disconnect();
	}
    }

    @Test
    @IntegrationTest
    public void testLoggedInReturningNullClientSessionListener()
	throws Exception
    {
	DummyClient client = new DummyClient(RETURN_NULL);
	try {
	    client.connect(serverNode.getAppPort());
	    client.login();
	    fail("expected login failure");	
	} catch (RuntimeException e) {
	    if (e.getMessage().equals(LOGIN_FAILED_MESSAGE)) {
		System.err.println("login refused");
		if (SimpleTestIdentityAuthenticator.allIdentities.
		        getNotifyLoggedIn(NON_SERIALIZABLE))
		{
		    fail("unexpected notifyLoggedIn invoked on identity: " +
			 NON_SERIALIZABLE);
		}
		return;
	    } else {
		fail("unexpected login failure: " + e);
	    }
	} finally {
	    client.disconnect();
	}
    }

    @Test
    @IntegrationTest
    public void testLoggedInThrowingRuntimeException()
	throws Exception
    {
	DummyClient client = new DummyClient(THROW_RUNTIME_EXCEPTION);
	try {
	    client.connect(serverNode.getAppPort());
	    client.login();
	    fail("expected login failure");	
	} catch (RuntimeException e) {
	    if (e.getMessage().equals(LOGIN_FAILED_MESSAGE)) {
		System.err.println("login refused");
		if (SimpleTestIdentityAuthenticator.allIdentities.
		        getNotifyLoggedIn(NON_SERIALIZABLE))
		{
		    fail("unexpected notifyLoggedIn invoked on identity: " +
			 NON_SERIALIZABLE);
		}
		return;
	    } else {
		fail("unexpected login failure: " + e);
	    }
	} finally {
	    client.disconnect();
	}
    }

    @Test
    @IntegrationTest
    public void testLoginTwiceBlockUser() throws Exception {
	String name = "dummy";
	DummyClient client1 = new DummyClient(name);
	DummyClient client2 = new DummyClient(name);
	int port = serverNode.getAppPort();
	client1.connect(port).login();
	try {
	    client2.connect(port).login();
	    fail("expected client2 login failure");
	} catch (RuntimeException e) {
	    if (e.getMessage().equals(LOGIN_FAILED_MESSAGE)) {
		System.err.println("login refused");
	    } else {
		fail("unexpected login failure: " + e);
	    }
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
	    SgsTestNode.getDefaultProperties(APP_NAME, null,
					     DummyAppListener.class);
	props.setProperty(
 	    "com.sun.sgs.impl.service.session.allow.new.login", "true");
	setUp(props, false);
	String name = "dummy";
	
	DummyClient client1 = new DummyClient(name);
	DummyClient client2 = new DummyClient(name);
	int port = serverNode.getAppPort();
	try {
	    client1.connect(port).login();
	    Thread.sleep(100);
	    client2.connect(port).login();
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
	DummyClient client = new DummyClient(name);
	try {
	    client.connect(serverNode.getAppPort());
	    client.login();
	    client.logout();
	    assertTrue(client.isConnected());
	    assertTrue(client.waitForDisconnect());
	} finally {
	    client.disconnect();
	}
    }

    @Test
    @IntegrationTest
    public void testLogoutRequestAndDisconnectedCallback() throws Exception {
	final String name = "logout";
	DummyClient client = new DummyClient(name);
	try {
	    client.connect(serverNode.getAppPort());
	    client.login();
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
	    new DummyClient(DISCONNECT_THROWS_NONRETRYABLE_EXCEPTION);
	try {
	    client.connect(serverNode.getAppPort());
	    client.login();
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
	DummyClient client = new DummyClient(name);
	try {
	    client.connect(serverNode.getAppPort()).login();
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
		DummyClient client = new DummyClient(name + String.valueOf(i));
		client.connect(appPort).login();
	    }
	    checkBindings(numClients);

            // Simulate "crash"
            tearDown(false);
	    String failedNodeKey = nodeKeys.get(0);
            setUp(null, false);

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
	DummyClient client = new DummyClient("clientname");
	try {
	    client.connect(serverNode.getAppPort());
	    client.login();
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
	DummyClient client = new DummyClient(name);
	try {
	    client.connect(serverNode.getAppPort());
	    client.login();
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
	DummyClient client = new DummyClient(name);
	try {
	    client.connect(serverNode.getAppPort()).login();
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
	DummyClient client = new DummyClient(name);
	try {
	    client.connect(serverNode.getAppPort()).login();
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
	DummyClient client = new DummyClient("dummy");
	int iterations = 3;
	int numAdditionalNodes = 2;
	Queue<byte[]> messages =
	    sendMessagesFromNodesToClient(
 		client, numAdditionalNodes, iterations, Delivery.UNRELIABLE,
		false);
	int expectedMessages = (1 + numAdditionalNodes) * iterations;
	assertEquals(expectedMessages, messages.size());
    }

    @Test
    public void testClientSessionSendUnreliableMessagesWithFailure()
	throws Exception
    {
	DummyClient client = new DummyClient("dummy");
	int iterations = 3;
	int numAdditionalNodes = 2;
	Queue<byte[]> messages =
	    sendMessagesFromNodesToClient(
		client, numAdditionalNodes, iterations, Delivery.UNRELIABLE,
		true);
	int expectedMessages = iterations;
	assertEquals(expectedMessages, messages.size());
    }

    @Test
    public void testClientSessionSendSequence() throws Exception {
	DummyClient client = new DummyClient("dummy");
	int iterations = 3;
	int numAdditionalNodes = 2;
	Queue<byte[]> messages =
	    sendMessagesFromNodesToClient(
		client, numAdditionalNodes, iterations, Delivery.RELIABLE,
		false);
	int expectedMessages = (1 + numAdditionalNodes) * iterations;
	client.validateMessageSequence(messages, expectedMessages);
    }
    
    private Queue<byte[]> sendMessagesFromNodesToClient(
	    final DummyClient client, int numAdditionalNodes, int iterations,
	    final Delivery delivery, final boolean oneUnreliableServer)
	throws Exception
    {
	try {
	    final String counterName = "counter";
	    client.connect(serverNode.getAppPort());
	    client.login();
	    for (int i = 0; i < numAdditionalNodes; i++) {
		addNodes(Integer.toString(i));
	    }
	    
	    final List<SgsTestNode> nodes = new ArrayList<SgsTestNode>();
	    nodes.add(serverNode);
	    nodes.addAll(additionalNodes.values());
	    int expectedMessages = 
		oneUnreliableServer ?
		iterations :
		nodes.size() * iterations;
	    
	    // Replace each node's ClientSessionServer, bound in the data
	    // service, with a wrapped server that delays before sending
	    // the message.
	    final DataService ds = dataService;
	    TransactionScheduler txnScheduler =
		serverNode.getSystemRegistry().
		    getComponent(TransactionScheduler.class);
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

	    return client.waitForClientToRecieveExpectedMessages(
		expectedMessages);

	} finally {
	    client.disconnect();
	}
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
	DummyClient client = new DummyClient(name);
	try {
	    client.connect(serverNode.getAppPort());
	    client.login();
	    final int numMessages = 3;
	    txnScheduler.runTask(new TestAbstractKernelRunnable() {
		public void run() {
		    ClientSession session = (ClientSession)
			AppContext.getDataManager().getBinding(name);
		    System.err.println("Sending messages");
		    for (int i = 0; i < numMessages; i++) {
			session.send(buf, delivery);
		    }
		}}, taskOwner);
	
	    System.err.println("waiting for client to receive messages");
	    Queue<byte[]> messages =
		client.waitForClientToRecieveExpectedMessages(numMessages);
	    for (byte[] message : messages) {
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
	DummyClient client = new DummyClient("clientname");
	try {
	    client.connect(serverNode.getAppPort());
	    client.login();
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
	sendMessagesAndCheck(5, 5, null);
    }

    @Test
    public void testClientSendWithListenerThrowingRetryableException()
	throws Exception
    {
	sendMessagesAndCheck(
	    5, 5, new MaybeRetryException("retryable", true));
    }

    @Test
    public void testClientSendWithListenerThrowingNonRetryableException()
	throws Exception
    {
	sendMessagesAndCheck(
	    5, 4, new MaybeRetryException("non-retryable", false));
    }


    @Test
    public void testLocalSendPerformance() throws Exception {
	final String user = "dummy";
	DummyClient client = (new DummyClient(user)).connect(serverNode.getAppPort());
	client.login();

	isPerformanceTest = true;
	int numIterations = 1000;
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
	DummyClient client = new DummyClient(user);
	client.connect(serverNode.getAppPort()).login();
	client.sendMessage(new byte[0]);
	client.logout();
	client.checkDisconnectedCallback(true);
    }
    
    /* -- other methods -- */

    private void sendMessagesAndCheck(
	int numMessages, int expectedMessages, RuntimeException exception)
	throws Exception
    {
	String name = "clientname";
	DummyClient client = new DummyClient(name);
	try {
	    client.connect(serverNode.getAppPort());
	    client.login();
	    client.sendMessagesInSequence(
		numMessages, expectedMessages, exception);
	} finally {
	    client.disconnect();
	}
    }

    /**
     * Check that the session bindings are the expected number and throw an
     * exception if they aren't.
     */
    private void checkBindings(int numExpected) throws Exception {
	
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
    
    private List<String> getServiceBindingKeys(String prefix) throws Exception {
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

    private void printIt(String line) {
	if (! isPerformanceTest) {
	    System.err.println(line);
	}
    }
    
    /** Find the app listener */
    private DummyAppListener getAppListener() {
	return (DummyAppListener) dataService.getServiceBinding(
	    StandardProperties.APP_LISTENER);
    }

    /**
     * Dummy client code for testing purposes.
     */
    private class DummyClient {
	
	final String name;
	private String password;
	private Connector<SocketAddress> connector;
	private Listener listener;
	private Connection connection;
	private boolean connected = false;
	private final Object lock = new Object();
	private final Object disconnectedCallbackLock = new Object();
	private final Object receivedAllMessagesLock = new Object();
	private boolean loginAck = false;
	private boolean loginSuccess = false;
	private boolean loginRedirect = false;
	private boolean logoutAck = false;
        private boolean awaitGraceful = false;
        private boolean awaitLoginFailure = false;
	private String reason;
	private String redirectHost;
        private int redirectPort;
	private byte[] reconnectKey = new byte[0];
	
	volatile boolean receivedDisconnectedCallback = false;
	volatile boolean graceful = false;
	
	volatile RuntimeException throwException;
	volatile int expectedMessages;
	// Messages received by this client's associated ClientSessionListener
	Queue<byte[]> messages = new ConcurrentLinkedQueue<byte[]>();
	

	DummyClient(String name) {
	    this.name = name;
	}

	DummyClient connect(int port) {
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
		System.err.println(toString() + " connect throws: " + e);
		e.printStackTrace();
		throw new RuntimeException("DummyClient.connect failed", e);
	    }
	    synchronized (lock) {
		try {
		    if (connected == false) {
			lock.wait(WAIT_TIME);
		    }
		    if (connected != true) {
			throw new RuntimeException(
			    toString() + " connect timed out to " + port);
		    }
		} catch (InterruptedException e) {
		    throw new RuntimeException(
			toString() + " connect timed out to " + port, e);
		}
	    }
	    return this;
	}

	void disconnect() {
            System.err.println(toString() + " disconnecting");

            synchronized (lock) {
                if (connected == false) {
                    return;
                }
                connected = false;
            }

            try {
                connection.close();
            } catch (IOException e) {
                System.err.println(toString() + " disconnect exception:" + e);
            }

            synchronized (lock) {
                lock.notifyAll();
            }
	}

	/**
	 * Sends a login request and waits for it to be acknowledged,
	 * returning {@code true} if login was successful, and {@code
	 * false} if login was redirected.  If the login was not successful
	 * or redirected, then a {@code RuntimeException} is thrown because
	 * the login operation timed out before being acknowledged.
	 */
	boolean login() {
	    return login(true);
	}

	/**
	 * Sends a login request and if {@code waitForLogin} is {@code
	 * true} waits for the request to be acknowledged, returning {@code
	 * true} if login was successful, and {@code false} if login was
	 * redirected, otherwise a {@code RuntimeException} is thrown
	 * because the login operation timed out before being acknowldeged.
	 *
	 * If {@code waitForLogin} is false, this method returns {@code
	 * true} if the login is known to be successful (the outcome may
	 * not yet be known because the login operation is asynchronous),
	 * otherwise it returns false.  Invoke {@code waitForLogin} to wait
	 * for an expected successful login.
	 */
	boolean login(boolean waitForLogin) {
	    synchronized (lock) {
		if (connected == false) {
		    throw new RuntimeException(toString() + " not connected");
		}
	    }
	    this.password = "password";

	    MessageBuffer buf =
		new MessageBuffer(2 + MessageBuffer.getSize(name) +
				  MessageBuffer.getSize(password));
	    buf.putByte(SimpleSgsProtocol.LOGIN_REQUEST).
                putByte(SimpleSgsProtocol.VERSION).
		putString(name).
		putString(password);
	    loginAck = false;
	    try {
		connection.sendBytes(buf.getBuffer());
	    } catch (IOException e) {
		throw new RuntimeException(e);
	    }
	    if (waitForLogin) {
		return waitForLogin();
	    } else {
		synchronized (lock) {
		    return loginSuccess;
		}
	    }
	}

	/**
	 * Waits for a login acknowledgement, and returns {@code true} if
	 * login was successful, {@code false} if login was redirected,
	 * otherwise a {@code RuntimeException} is thrown because the login
	 * operation timed out before being acknowledged.
	 */
	boolean waitForLogin() {
	    synchronized (lock) {
		try {
		    if (loginAck == false) {
			lock.wait(WAIT_TIME);
		    }
		    if (loginAck != true) {
			throw new RuntimeException(
			    toString() + " login timed out");
		    }
		    if (loginRedirect == true) {
			return false;
		    }
		    if (!loginSuccess) {
			throw new RuntimeException(LOGIN_FAILED_MESSAGE);
		    }
		} catch (InterruptedException e) {
		    throw new RuntimeException(
			toString() + " login timed out", e);
		}
	    }
	    return true;
	}

	/**
	 * Throws a {@code RuntimeException} if this session is not
	 * logged in.
	 */
	private void checkLoggedIn() {
	    synchronized (lock) {
		if (!connected || !loginSuccess) {
		    throw new RuntimeException(
			toString() + " not connected or loggedIn");
		}
	    }
	}

	/**
	 * Sends a SESSION_MESSAGE with the specified content.
	 */
	void sendMessage(byte[] message) {
	    MessageBuffer buf =
		new MessageBuffer(5 + reconnectKey.length + message.length);
	    buf.putByte(SimpleSgsProtocol.SESSION_MESSAGE).
		putByteArray(reconnectKey).
		putByteArray(message);
	    try {
		connection.sendBytes(buf.getBuffer());
	    } catch (IOException e) {
		throw new RuntimeException(e);
	    }
	}

	/**
	 * From this client, sends the number of messages (each containing a
	 * monotonically increasing sequence number), then waits for all the
	 * messages to be received by this client's associated {@code
	 * ClientSessionListener}, and validates the sequence of messages
	 * received by the listener.  If {@code throwException} is non-null,
	 * the {@code ClientSessionListener} will throw the specified
	 * exception in its {@code receivedMessage} method for only the first
	 * message it receives.
	 */
	void sendMessagesInSequence(
	    int numMessages, int expectedMessages, RuntimeException re)
	{
	    this.expectedMessages = expectedMessages;
	    this.throwException = re;
	    
	    for (int i = 0; i < numMessages; i++) {
		MessageBuffer buf = new MessageBuffer(4);
		buf.putInt(i);
		sendMessage(buf.getBuffer());
	    }
	    
	    validateMessageSequence(messages, expectedMessages);
	}

	/**
	 * Waits until all messages have been received by this client, and
	 * validates that the expected number of messages were received by the
	 * client in the correct sequence.
	 */
	void checkMessagesReceived(int expectedMessages) {
	    validateMessageSequence(listener.messageList, expectedMessages);
	}

	/**
	 * Waits for this client to receive the number of messages sent from
	 * the application.
	 */
	Queue<byte[]> waitForClientToRecieveExpectedMessages(
	    int expectedMessages)
	{
	    waitForExpectedMessages(listener.messageList, expectedMessages);
	    return listener.messageList;
	}

	/**
	 * Waits for the number of expected messages to be deposited in the
	 * specified message queue.
	 */
	private void waitForExpectedMessages(
	    Queue<byte[]> messageQueue, int expectedMessages)
	{
	    this.expectedMessages = expectedMessages;
	    synchronized (receivedAllMessagesLock) {
		if (messageQueue.size() != expectedMessages) {
		    try {
			receivedAllMessagesLock.wait(WAIT_TIME);
		    } catch (InterruptedException e) {
		    }
		}
	    }
	    int receivedMessages = messageQueue.size();
	    if (receivedMessages != expectedMessages) {
		fail(toString() + " expected " + expectedMessages +
		     ", received " + receivedMessages);
	    }
	}

	/**
	 * Waits for the number of expected messages to be recorded in the
	 * specified 'list', and validates that the expected number of messages
	 * were received by the ClientSessionListener in the correct sequence.
	 */
	void validateMessageSequence(
	    Queue<byte[]> messageQueue, int expectedMessages)
	{
	    waitForExpectedMessages(messageQueue, expectedMessages);
	    if (expectedMessages != 0) {
		int i = (new MessageBuffer(messageQueue.peek())).getInt();
		for (byte[] message : messageQueue) {
		    MessageBuffer buf = new MessageBuffer(message);
		    int value = buf.getInt();
		    System.err.println(toString() + " received message " + value);
		    if (value != i) {
			fail("expected message " + i + ", got " + value);
		    }
		    i++;
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
 			    toString() + " disconnect timed out");
                    }
                } catch (InterruptedException e) {
                    throw new RuntimeException(
                        toString() + " disconnect timed out", e);
                } finally {
                    if (! logoutAck)
                        disconnect();
                }
            }
	}

	void checkDisconnectedCallback(boolean graceful) throws Exception {
	    synchronized (disconnectedCallbackLock) {
		if (!receivedDisconnectedCallback) {
		    disconnectedCallbackLock.wait(WAIT_TIME);
		}
	    }
	    if (!receivedDisconnectedCallback) {
		fail(toString() + " disconnected callback not invoked");
	    } else if (this.graceful != graceful) {
		fail(toString() + " graceful was: " + this.graceful +
		     ", expected: " + graceful);
	    }
	    System.err.println(toString() + " disconnect successful");
	}

	boolean isConnected() {
	    synchronized (lock) {
		return connected;
	    }
	}

	// Returns true if disconnect occurred.
	boolean waitForDisconnect() {
	    synchronized(lock) {
		try {
		    if (connected == true) {
			lock.wait(WAIT_TIME);
		    }
		} catch (InterruptedException ignore) {
		}
		return !connected;
	    }
	}

	public String toString() {
	    return "[" + name + "]";
	}
	
	private class Listener implements ConnectionListener {

	    Queue<byte[]> messageList = new LinkedList<byte[]>();
	    
            /** {@inheritDoc} */
	    public void bytesReceived(Connection conn, byte[] buffer) {
		if (connection != conn) {
		    System.err.println(
			"DummyClient.Listener connected wrong handle, got:" +
			conn + ", expected:" + connection);
		    return;
		}

		MessageBuffer buf = new MessageBuffer(buffer);

		byte opcode = buf.getByte();

		switch (opcode) {

		case SimpleSgsProtocol.LOGIN_SUCCESS:
		    reconnectKey = buf.getBytes(buf.limit() - buf.position());
		    dummyClients.put(
			new BigInteger(1, reconnectKey), DummyClient.this);
		    synchronized (lock) {
			loginAck = true;
			loginSuccess = true;
			System.err.println("login succeeded: " + name);
			lock.notifyAll();
		    }
		    sendMessage(new byte[0]);
		    break;
		    
		case SimpleSgsProtocol.LOGIN_FAILURE:
		    reason = buf.getString();
		    synchronized (lock) {
			loginAck = true;
			loginSuccess = false;
			System.err.println("login failed: " + name +
					   ", reason:" + reason);
			lock.notifyAll();
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

		case SimpleSgsProtocol.LOGOUT_SUCCESS:
                    synchronized (lock) {
                        logoutAck = true;
                        System.err.println("logout succeeded: " + name);
			lock.notifyAll();
                    }
		    break;

		case SimpleSgsProtocol.SESSION_MESSAGE:
		    byte[] message = buf.getBytes(buf.limit() - buf.position());
		    synchronized (lock) {
			messageList.add(message);
			printIt("[" + name + "] received SESSION_MESSAGE: " +
				HexDumper.toHexString(message));
			lock.notifyAll();
		    }
		    break;

		default:
		    System.err.println(	
		"bytesReceived: unknown op code: " + opcode);
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
                    connected = false;
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
		client.receivedDisconnectedCallback = true;
		client.graceful = graceful;
		synchronized (client.disconnectedCallbackLock) {
		    client.disconnectedCallbackLock.notifyAll();
		}
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
		"receivedMessage: " + HexDumper.toHexString(bytes) + 
		"\nthrowException: " + client.throwException);
	    if (client.throwException != null) {
		RuntimeException re = client.throwException;
		client.throwException = null;
		throw re;
	    } else {
		AppContext.getDataManager().markForUpdate(this);
		client.messages.add(bytes);
	    }
	    if (client.messages.size() == client.expectedMessages) {
		synchronized (client.receivedAllMessagesLock) {
		    client.receivedAllMessagesLock.notifyAll();
		}
	    }
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

    private static class DummyDisconnectListener
	implements ClientSessionDisconnectListener
    {
	public void disconnected(BigInteger sessionRefId) { }
    }
}
