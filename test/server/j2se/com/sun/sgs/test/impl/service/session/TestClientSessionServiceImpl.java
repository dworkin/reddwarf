/*
 * Copyright 2007-2008 Sun Microsystems, Inc.
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
import com.sun.sgs.app.ExceptionRetryStatus;
import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.ManagedReference;
import com.sun.sgs.app.MessageRejectedException;
import com.sun.sgs.app.NameNotBoundException;
import com.sun.sgs.app.ObjectNotFoundException;
import com.sun.sgs.auth.Identity;
import com.sun.sgs.impl.io.SocketEndpoint;
import com.sun.sgs.impl.io.TransportType;
import com.sun.sgs.impl.kernel.StandardProperties;
import com.sun.sgs.impl.service.session.ClientSessionServer;
import com.sun.sgs.impl.service.session.ClientSessionServiceImpl;
import com.sun.sgs.impl.sharedutil.HexDumper;
import com.sun.sgs.impl.sharedutil.MessageBuffer;
import com.sun.sgs.impl.util.AbstractKernelRunnable;
import com.sun.sgs.impl.util.ManagedSerializable;
import com.sun.sgs.io.Connector;
import com.sun.sgs.io.Connection;
import com.sun.sgs.io.ConnectionListener;
import com.sun.sgs.kernel.TransactionScheduler;
import com.sun.sgs.protocol.simple.SimpleSgsProtocol;
import com.sun.sgs.service.DataService;
import com.sun.sgs.test.util.SgsTestNode;
import com.sun.sgs.test.util.SimpleTestIdentityAuthenticator;
import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
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

import junit.framework.TestCase;
import static com.sun.sgs.test.util.UtilProperties.createProperties;
import junit.framework.TestSuite;

public class TestClientSessionServiceImpl extends TestCase {

    /** If this property is set, then only run the single named test method. */
    private static final String testMethod = System.getProperty("test.method");

    /**
     * Specify the test suite to include all tests, or just a single method if
     * specified.
     */
    public static TestSuite suite() throws Exception {
	if (testMethod == null) {
	    return new TestSuite(TestClientSessionServiceImpl.class);
	}
	TestSuite suite = new TestSuite();
	suite.addTest(new TestClientSessionServiceImpl(testMethod));
	return suite;
    }

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

    /** The node that creates the servers. */
    private SgsTestNode serverNode;

    /** Any additional nodes, keyed by node hostname (for tests
     * needing more than one node). */
    private Map<String,SgsTestNode> additionalNodes;

    /** The transaction scheduler. */
    private TransactionScheduler txnScheduler;

    /** The owner for tasks I initiate. */
    private Identity taskOwner;

    /** The shared data service. */
    private DataService dataService;

    /** The test clients, keyed by user name. */
    private static Map<String, DummyClient> dummyClients;

    /** Constructs a test instance. */
    public TestClientSessionServiceImpl(String name) throws Exception {
	super(name);
    }

    protected void setUp() throws Exception {
        dummyClients = new HashMap<String, DummyClient>();
        System.err.println("Testcase: " + getName());
        setUp(true);
    }

    /** Creates and configures the session service. */
    protected void setUp(boolean clean) throws Exception {
        Properties props = 
            SgsTestNode.getDefaultProperties(APP_NAME, null, 
                                             DummyAppListener.class);
        props.setProperty(StandardProperties.AUTHENTICATORS, 
                      "com.sun.sgs.test.util.SimpleTestIdentityAuthenticator");
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

    /** Sets passed if the test passes. */
    protected void runTest() throws Throwable {
	super.runTest();
        Thread.sleep(100);
    }

    protected void tearDown() throws Exception {
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

    /* -- Test constructor -- */

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

    public void testConstructorNullComponentRegistry() throws Exception {
	try {
	    Properties props =
		createProperties(
		    StandardProperties.APP_NAME, APP_NAME,
		    StandardProperties.APP_PORT, "20000");
	    new ClientSessionServiceImpl(props, null,
					 serverNode.getProxy());
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
    }

    public void testConstructorNullTransactionProxy() throws Exception {
	try {
	    Properties props =
		createProperties(
		    StandardProperties.APP_NAME, APP_NAME,
		    StandardProperties.APP_PORT, "20000");
	    new ClientSessionServiceImpl(props,
					 serverNode.getSystemRegistry(), null);
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
    }

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

    public void testConstructorNoPort() throws Exception {
	try {
	    Properties props =
		createProperties(
		    StandardProperties.APP_NAME, APP_NAME);
	    new ClientSessionServiceImpl(
		props, serverNode.getSystemRegistry(),
		serverNode.getProxy());

	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
    }

    /* -- Test connecting, logging in, logging out with server -- */

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

    public void testLoginSuccess() throws Exception {
	DummyClient client = new DummyClient("success");
	try {
	    client.connect(serverNode.getAppPort());
	    client.login();
	} finally {
            client.disconnect();
	}
    }

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

    public void testLogoutRequestAndDisconnectedCallback() throws Exception {
	final String name = "logout";
	DummyClient client = new DummyClient(name);
	try {
	    client.connect(serverNode.getAppPort());
	    client.login();
	    checkBindings(1);
	    client.logout();
	    client.checkDisconnected(true);
	    checkBindings(0);
	    // check that client session was removed after disconnected callback
	    // returned 
            txnScheduler.runTask(new AbstractKernelRunnable() {
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
	    client.checkDisconnected(true);
	    // give scheduled task a chance to clean up...
	    Thread.sleep(250);
	    checkBindings(0);	    
	} finally {
	    client.disconnect();
	}
    }

    public void testLogoutAndNotifyLoggedOutCallback() throws Exception {
	String name = "logout";
	DummyClient client = new DummyClient(name);
	try {
	    client.connect(serverNode.getAppPort());
	    client.login();
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


    public void testNotifyClientSessionListenerAfterCrash() throws Exception {
	String name = "testRemoveListener";
	DummyClient client = new DummyClient(name);
	try {
	    List<String> nodeKeys = getServiceBindingKeys(NODE_PREFIX);
	    System.err.println("Node keys: " + nodeKeys);
	    if (nodeKeys.isEmpty()) {
		fail("no node keys");
	    } else if (nodeKeys.size() > 1) {
		fail("more than one node key");
	    }
	    
	    client.connect(serverNode.getAppPort());
	    client.login();
	    checkBindings(1);

            // Simulate "crash"
            tearDown(false);
	    String failedNodeKey = nodeKeys.get(0);
            setUp(false);
	    client.checkDisconnected(false);
	    System.err.println("check for session bindings being removed.");
	    checkBindings(0);
	    // Wait to make sure that node key is cleaned up.
	    Thread.sleep(WAIT_TIME);
	    nodeKeys = getServiceBindingKeys(NODE_PREFIX);
	    System.err.println("Node keys: " + nodeKeys);
	    if (nodeKeys.contains(failedNodeKey)) {
		fail("failed node key not removed: " + failedNodeKey);
	    }
	    
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

    private class GetKeysTask extends AbstractKernelRunnable {
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

    /* -- test ClientSession -- */

    public void testClientSessionIsConnected() throws Exception {
	DummyClient client = new DummyClient("clientname");
	try {
	    client.connect(serverNode.getAppPort());
	    client.login();
            txnScheduler.runTask(new AbstractKernelRunnable() {
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

    public void testClientSessionGetName() throws Exception {
	final String name = "clientname";
	DummyClient client = new DummyClient(name);
	try {
	    client.connect(serverNode.getAppPort());
	    client.login();
            txnScheduler.runTask(new AbstractKernelRunnable() {
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

    public void testClientSessionSend() throws Exception {
	final String name = "dummy";
	DummyClient client = new DummyClient(name);
	try {
	    final String counterName = "counter";
	    client.connect(serverNode.getAppPort());
	    client.login();
	    addNodes("a", "b", "c", "d");
	    
	    int iterations = 4;
	    final List<SgsTestNode> nodes = new ArrayList<SgsTestNode>();
	    nodes.add(serverNode);
	    nodes.addAll(additionalNodes.values());
	    
	    /*
	     * Replace each node's ClientSessionServer, bound in the data
	     * service, with a wrapped server that delays before sending
	     * the message.
	     */
	    final DataService ds = dataService;
	    TransactionScheduler txnScheduler =
		serverNode.getSystemRegistry().
		    getComponent(TransactionScheduler.class);
	    txnScheduler.runTask(new AbstractKernelRunnable() {
		@SuppressWarnings("unchecked")
		public void run() {
		    for (SgsTestNode node : nodes) {
			String key = "com.sun.sgs.impl.service.session.server." +
			    node.getNodeId();
			ManagedSerializable<ClientSessionServer> managedServer =
			    (ManagedSerializable<ClientSessionServer>)
			    dataService.getServiceBinding(key);
			DelayingInvocationHandler handler =
			    new DelayingInvocationHandler(managedServer.get());
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
		    	  new AbstractKernelRunnable() {
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
				    dataManager.getBinding(name);
				MessageBuffer buf = new MessageBuffer(4);
				buf.putInt(counter.getAndIncrement());
				session.send(ByteBuffer.wrap(buf.getBuffer()));
			    }},
			
			identity);
		}
	    }
	    txnScheduler.runTask(new AbstractKernelRunnable() {
		public void run() {
		    AppContext.getDataManager().
			setBinding(counterName, new Counter());
		}}, taskOwner);

	    client.checkMessagesReceived(nodes.size() * iterations);

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

    /**
     * Test sending from the server to the client session in a transaction that
     * aborts with a retryable exception to make sure that message buffers are
     * reclaimed.  Try sending 4K bytes, and have the task abort 100 times with
     * a retryable exception so the task is retried.  If the buffers are not
     * being reclaimed then the sends will eventually fail because the buffer
     * space is used up.  Note that this test assumes that sending 400 KB of
     * data will surpass the I/O throttling limit.
     */
    public void testClientSessionSendAbortRetryable() throws Exception {
	DummyClient client = new DummyClient("clientname");
	try {
	    client.connect(serverNode.getAppPort());
	    client.login();
	    txnScheduler.runTask(
		new AbstractKernelRunnable() {
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

    public void testClientSend() throws Exception {
	sendMessagesAndCheck(5, 5, null);
    }

    public void testClientSendWithListenerThrowingRetryableException()
	throws Exception
    {
	sendMessagesAndCheck(
	    5, 5, new MaybeRetryException("retryable", true));
    }

    public void testClientSendWithListenerThrowingNonRetryableException()
	throws Exception
    {
	sendMessagesAndCheck(
	    5, 4, new MaybeRetryException("non-retryable", false));
    }

    private void sendMessagesAndCheck(
	int numMessages, int expectedMessages, RuntimeException exception)
	throws Exception
    {
	String name = "clientname";
	DummyClient client = new DummyClient(name);
	try {
	    client.connect(serverNode.getAppPort());
	    client.login();
	    client.sendMessages(numMessages, expectedMessages, exception);
	} finally {
	    client.disconnect();
	}
    }

    /* -- other methods -- */

    /** Find the app listener */
    private DummyAppListener getAppListener() {
	return (DummyAppListener) dataService.getServiceBinding(
	    StandardProperties.APP_LISTENER);
    }

    /**
     * Dummy client code for testing purposes.
     */
    private class DummyClient {

	private String name;
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
	private byte[] reconnectKey;
	
	volatile boolean receivedDisconnectedCallback = false;
	volatile boolean graceful = false;
	
	volatile RuntimeException throwException;
	volatile int expectedMessages;
	Queue<byte[]> messages = new ConcurrentLinkedQueue<byte[]>();
	

	DummyClient(String name) {
	    this.name = name;
	    dummyClients.put(name, this);
	}

	void connect(int port) {
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
		System.err.println("DummyClient.connect throws: " + e);
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
 			    "DummyClient.connect timed out to " + port);
		    }
		} catch (InterruptedException e) {
		    throw new RuntimeException(
			"DummyClient.connect timed out to " + port, e);
		}
	    }
	    
	}

	void disconnect() {
            System.err.println("DummyClient.disconnect");

            synchronized (lock) {
                if (connected == false) {
                    return;
                }
                connected = false;
            }

            try {
                connection.close();
            } catch (IOException e) {
                System.err.println(
                    "DummyClient.disconnect exception:" + e);
            }

            synchronized (lock) {
                lock.notifyAll();
            }
	}

	/**
	 * Returns {@code true} if login was successful, and returns
	 * {@code false} if login was redirected.
	 */
	boolean login() {
	    synchronized (lock) {
		if (connected == false) {
		    throw new RuntimeException(
			"DummyClient.login not connected");
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
	    synchronized (lock) {
		try {
		    if (loginAck == false) {
			lock.wait(WAIT_TIME);
		    }
		    if (loginAck != true) {
			throw new RuntimeException(
			    "DummyClient.login timed out");
		    }
		    if (loginRedirect == true) {
			return false;
		    }
		    if (!loginSuccess) {
			throw new RuntimeException(LOGIN_FAILED_MESSAGE);
		    }
		} catch (InterruptedException e) {
		    throw new RuntimeException(
			"DummyClient.login timed out", e);
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
			"DummyClient.login not connected or loggedIn");
		}
	    }
	}

	/**
	 * Sends a SESSION_MESSAGE.
	 */
	void sendMessage(byte[] message) {
	    checkLoggedIn();

	    MessageBuffer buf =
		new MessageBuffer(1+ message.length);
	    buf.putByte(SimpleSgsProtocol.SESSION_MESSAGE).
		putBytes(message);
	    try {
		connection.sendBytes(buf.getBuffer());
	    } catch (IOException e) {
		throw new RuntimeException(e);
	    }
	}

	void sendMessages(
	    int numMessages, int expectedMessages, RuntimeException re)
	{
	    this.expectedMessages = expectedMessages;
	    this.throwException = re;
	    
	    for (int i = 0; i < numMessages; i++) {
		MessageBuffer buf = new MessageBuffer(4);
		buf.putInt(i);
		sendMessage(buf.getBuffer());
	    }
	    
	    synchronized (receivedAllMessagesLock) {
		if (messages.size() != expectedMessages) {
		    try {
			receivedAllMessagesLock.wait(WAIT_TIME);
		    } catch (InterruptedException e) {
		    }
		    int receivedMessages = messages.size();
		    if (receivedMessages != expectedMessages) {
			fail("expected " + expectedMessages + ", received " +
			     receivedMessages);
		    }
		}
	    }
	}

	void checkMessagesReceived(int expectedMessages) {
	    this.expectedMessages = expectedMessages;

	    synchronized (receivedAllMessagesLock) {
		if (listener.messageList.size() != expectedMessages) {
		    try {
			receivedAllMessagesLock.wait(WAIT_TIME);
		    } catch (InterruptedException e) {
		    }

		    int receivedMessages = listener.messageList.size();
		    if (receivedMessages != expectedMessages) {
			fail("expected " + expectedMessages + ", received " +
			     receivedMessages);
		    }
		}
	    }

	    int i = 0;
	    for (byte[] message : listener.messageList) {
		MessageBuffer buf = new MessageBuffer(message);
		int value = buf.getInt();
		System.err.println("[" + name + "] received message " + value);
		if (value != i) {
		    fail("expected message " + i + ", got " + value);
		}
		i++;
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
                            "DummyClient.disconnect timed out");
                    }
                } catch (InterruptedException e) {
                    throw new RuntimeException(
                        "DummyClient.disconnect timed out", e);
                } finally {
                    if (! logoutAck)
                        disconnect();
                }
            }
	}

	void checkDisconnected(boolean graceful) throws Exception {
	    synchronized (disconnectedCallbackLock) {
		if (!receivedDisconnectedCallback) {
		    disconnectedCallbackLock.wait(WAIT_TIME);
		}
	    }
	    if (!receivedDisconnectedCallback) {
		fail("disconnected callback not invoked");
	    } else if (this.graceful != graceful) {
		fail("graceful was: " + this.graceful +
		     ", expected: " + graceful);
	    }
	    System.err.println("disconnect successful");
	}
	
	private class Listener implements ConnectionListener {

	    List<byte[]> messageList = new ArrayList<byte[]>();
	    
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
		    synchronized (lock) {
			loginAck = true;
			loginSuccess = true;
			System.err.println("login succeeded: " + name);
			lock.notifyAll();
		    }
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
                        // let disconnect do the lock notification
                    }
		    break;

		case SimpleSgsProtocol.SESSION_MESSAGE:
		    byte[] message = buf.getBytes(buf.limit() - buf.position());
		    synchronized (lock) {
			messageList.add(message);
			System.err.println("[" + name +
					   "] received SESSION_MESSAGE: " +
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
                    // Hack since client might not get last msg
                    if (awaitGraceful) {
                        // Pretend they logged out gracefully
                        logoutAck = true;
                    } else if (! loginAck) {
                        // Pretend they got a login failure message
                        loginAck = true;
                        loginSuccess = false;
                        reason = "disconnected before login ack";
                    }
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

    public static class DummyAppListener implements AppListener, Serializable {

	private final static long serialVersionUID = 1L;

	private final Map<ManagedReference<ClientSession>,
			  ManagedReference<DummyClientSessionListener>>
	    sessions = Collections.synchronizedMap(
		new HashMap<ManagedReference<ClientSession>,
			    ManagedReference<DummyClientSessionListener>>());

        /** {@inheritDoc} */
	public ClientSessionListener loggedIn(ClientSession session) {

	    String name = session.getName();
	    DummyClientSessionListener listener;
	    
	    if (name.equals(RETURN_NULL)) {
		return null;
	    } else if (name.equals(NON_SERIALIZABLE)) {
		return new NonSerializableClientSessionListener();
	    } else if (name.equals(THROW_RUNTIME_EXCEPTION)) {
		throw new RuntimeException("loggedIn throwing an exception");
	    } else if (name.equals(DISCONNECT_THROWS_NONRETRYABLE_EXCEPTION)) {
		listener = new DummyClientSessionListener(name, true);
	    } else {
		listener = new DummyClientSessionListener(name, false);
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
	private final boolean disconnectedThrowsException;
	private int seq = -1;


	DummyClientSessionListener(
	    String name, boolean disconnectedThrowsException)
	{
	    this.name = name;
	    this.disconnectedThrowsException = disconnectedThrowsException;
	}

        /** {@inheritDoc} */
	public void disconnected(boolean graceful) {
	    System.err.println("DummyClientSessionListener[" + name +
			       "] disconnected invoked with " + graceful);
	    AppContext.getDataManager().markForUpdate(this);
	    DummyClient client = dummyClients.get(name);
	    client.receivedDisconnectedCallback = true;
	    client.graceful = graceful;
	    synchronized (client.disconnectedCallbackLock) {
		client.disconnectedCallbackLock.notifyAll();
	    }
	    if (disconnectedThrowsException) {
		throw new RuntimeException(
		    "disconnected throws non-retryable exception");
	    }
	}

        /** {@inheritDoc} */
	public void receivedMessage(ByteBuffer message) {
            byte[] bytes = new byte[message.remaining()];
            message.asReadOnlyBuffer().get(bytes);
	    int num = message.getInt();
	    DummyClient client = dummyClients.get(name);
	    System.err.println("receivedMessage: " + num + 
			       "\nthrowException: " + client.throwException);
	    if (num <= seq) {
		throw new RuntimeException(
		    "expected message greater than " + seq + ", got " + num);
	    }
	    AppContext.getDataManager().markForUpdate(this);
	    client.messages.add(bytes);
	    seq = num;
	    if (client.throwException != null) {
		RuntimeException re = client.throwException;
		client.throwException = null;
		throw re;
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
}
