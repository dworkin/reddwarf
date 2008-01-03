/*
 * Copyright 2007 Sun Microsystems, Inc.
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
import com.sun.sgs.impl.io.SocketEndpoint;
import com.sun.sgs.impl.io.TransportType;
import com.sun.sgs.impl.kernel.StandardProperties;
import com.sun.sgs.impl.service.data.DataServiceImpl;
import com.sun.sgs.impl.service.session.ClientSessionServiceImpl;
import com.sun.sgs.impl.sharedutil.CompactId;
import com.sun.sgs.impl.sharedutil.MessageBuffer;
import com.sun.sgs.impl.util.AbstractKernelRunnable;
import com.sun.sgs.io.Connector;
import com.sun.sgs.io.Connection;
import com.sun.sgs.io.ConnectionListener;
import com.sun.sgs.kernel.TaskOwner;
import com.sun.sgs.kernel.TaskScheduler;
import com.sun.sgs.protocol.simple.SimpleSgsProtocol;
import com.sun.sgs.test.util.SgsTestNode;
import com.sun.sgs.test.util.SimpleTestIdentityAuthenticator;
import java.io.IOException;
import java.io.Serializable;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Properties;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import junit.framework.TestCase;

public class TestClientSessionServiceImpl extends TestCase {

    private static final String APP_NAME = "TestClientSessionServiceImpl";

    private static final String LOGIN_FAILED_MESSAGE = "login failed";

    private static final int WAIT_TIME = 5000;

    private static final String RETURN_NULL = "return null";

    private static final String NON_SERIALIZABLE = "non-serializable";

    private static final String THROW_RUNTIME_EXCEPTION =
	"throw RuntimeException";

    private static final String SESSION_PREFIX =
	"com.sun.sgs.impl.service.session.proxy";

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

    /** The task scheduler. */
    private TaskScheduler taskScheduler;

    /** The owner for tasks I initiate. */
    private TaskOwner taskOwner;

    /** The shared data service. */
    private DataServiceImpl dataService;

    /** True if test passes. */
    private boolean passed;

    /** The test clients, keyed by user name. */
    private static Map<String, DummyClient> dummyClients;

    /** Constructs a test instance. */
    public TestClientSessionServiceImpl(String name) throws Exception {
	super(name);
    }

    protected void setUp() throws Exception {
        passed = false;
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

        taskScheduler = 
            serverNode.getSystemRegistry().getComponent(TaskScheduler.class);
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
	passed = true;
    }

    protected void tearDown() throws Exception {
        tearDown(true);
    }

    protected void tearDown(boolean clean) throws Exception {
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
		    "com.sun.sgs.app.name", APP_NAME,
		    "com.sun.sgs.app.port", "0");
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
		    "com.sun.sgs.app.name", APP_NAME,
		    "com.sun.sgs.app.port", "0");
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
		    "com.sun.sgs.app.name", APP_NAME);
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
	    client.disconnect(false);
	}
    }

    public void testLoginSuccess() throws Exception {
	DummyClient client = new DummyClient("success");
	try {
	    client.connect(serverNode.getAppPort());
	    client.login("password");
	} finally {
            client.disconnect(false);
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
		if (! client.login("password")) {
		    // login redirected
		    redirectCount++;
		    int redirectPort =
			(additionalNodes.get(client.redirectHost)).getAppPort();
		    client = new DummyClient(user);
		    client.connect(redirectPort);
		    if (!client.login("password")) {
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
		    client.disconnect(false);
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
	    client.login("password");
	    if (SimpleTestIdentityAuthenticator.allIdentities.
                    getNotifyLoggedIn(name)) {
		System.err.println(
		    "notifyLoggedIn invoked for identity: " + name);
	    } else {
		fail("notifyLoggedIn not invoked for identity: " + name);
	    }
	} finally {
            client.disconnect(false);
	}
    }

    public void testLoggedInReturningNonSerializableClientSessionListener()
	throws Exception
    {
	DummyClient client = new DummyClient(NON_SERIALIZABLE);
	try {
	    client.connect(serverNode.getAppPort());
	    client.login("password");
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
	    client.disconnect(false);
	}
    }

    public void testLoggedInReturningNullClientSessionListener()
	throws Exception
    {
	DummyClient client = new DummyClient(RETURN_NULL);
	try {
	    client.connect(serverNode.getAppPort());
	    client.login("bar");
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
	    client.disconnect(false);
	}
    }

    public void testLoggedInThrowingRuntimeException()
	throws Exception
    {
	DummyClient client = new DummyClient(THROW_RUNTIME_EXCEPTION);
	try {
	    client.connect(serverNode.getAppPort());
	    client.login("bar");
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
	    client.disconnect(false);
	}
    }

    public void testLogoutRequestAndDisconnectedCallback() throws Exception {
	DummyClient client = new DummyClient("logout");
	try {
	    client.connect(serverNode.getAppPort());
	    client.login("test");
	    client.logout();
	    client.checkDisconnected(true);
	} catch (InterruptedException e) {
	    e.printStackTrace();
	    fail("testLogout interrupted");
	} finally {
	    client.disconnect(false);
	}
    }

    public void testLogoutAndNotifyLoggedOutCallback() throws Exception {
	String name = "logout";
	DummyClient client = new DummyClient(name);
	try {
	    client.connect(serverNode.getAppPort());
	    client.login("password");
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
            client.disconnect(false);
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
	    client.login("password");

	    List<String> listenerKeys = getServiceBindingKeys(LISTENER_PREFIX);
	    System.err.println("Listener keys: " + listenerKeys);
	    if (listenerKeys.isEmpty()) {
		fail("no listener keys");
	    } else if (listenerKeys.size() > 1) {
		fail("more than one listener key");
	    }
	    
	    List<String> sessionKeys = getServiceBindingKeys(SESSION_PREFIX);
	    System.err.println("Session keys: " + sessionKeys);
	    if (sessionKeys.isEmpty()) {
		fail("no session keys");
	    } else if (sessionKeys.size() > 1) {
		fail("more than one session key");
	    }
	    
	    List<String> sessionNodeKeys =
		getServiceBindingKeys(SESSION_NODE_PREFIX);
	    System.err.println("Session node keys: " + sessionNodeKeys);
	    if (sessionNodeKeys.isEmpty()) {
		fail("no session node keys");
	    } else if (sessionNodeKeys.size() > 1) {
		fail("more than one session node key");
	    }

            // Simulate "crash"
            tearDown(false);
	    String failedNodeKey = nodeKeys.get(0);
            setUp(false);
	    dataService = serverNode.getDataService();
	    if (! getServiceBindingKeys(NODE_PREFIX).contains(failedNodeKey)) {
		fail("Failed node key prematurely removed: " + failedNodeKey);
	    }
            addNodes("one");
	    client.checkDisconnected(false);

	    listenerKeys = getServiceBindingKeys(LISTENER_PREFIX);	    
	    if (! listenerKeys.isEmpty()) {
		System.err.println("Listener key not removed: " + listenerKeys);
		fail("listener key not removed!");
	    }
	    sessionKeys = getServiceBindingKeys(SESSION_PREFIX);
	    if (! sessionKeys.isEmpty()) {
		System.err.println("Session keys not removed: " + sessionKeys);
		fail("session keys not removed!");
	    }
	    
	    sessionNodeKeys = getServiceBindingKeys(SESSION_NODE_PREFIX);
	    if (! sessionNodeKeys.isEmpty()) {
		System.err.println("Session keys not removed: " + sessionNodeKeys);
		fail("session node keys not removed!");
	    }
	    // Wait to make sure that node key is cleaned up.
	    Thread.sleep(WAIT_TIME);
	    nodeKeys = getServiceBindingKeys(NODE_PREFIX);
	    System.err.println("Node keys: " + nodeKeys);
	    if (nodeKeys.contains(failedNodeKey)) {
		fail("failed node key not removed: " + failedNodeKey);
	    }
	    
	} finally {
	    client.disconnect(false);
	}
    }

    private List<String> getServiceBindingKeys(String prefix) throws Exception {
        GetKeysTask task = new GetKeysTask(prefix);
        taskScheduler.runTransactionalTask(task, taskOwner);
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
	    client.login("dummypassword");
            taskScheduler.runTransactionalTask(new AbstractKernelRunnable() {
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
	    client.disconnect(false);
	}
    }

    public void testClientSessionGetName() throws Exception {
	final String name = "clientname";
	DummyClient client = new DummyClient(name);
	try {
	    client.connect(serverNode.getAppPort());
	    client.login("dummypassword");
            taskScheduler.runTransactionalTask(new AbstractKernelRunnable() {
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
	    client.disconnect(false);
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
	    client.login("dummypassword");
	    client.sendMessages(numMessages, expectedMessages, exception);
	} finally {
	    client.disconnect(false);
	}
    }

    /* -- other methods -- */

    /** Creates a property list with the specified keys and values. */
    private static Properties createProperties(String... args) {
	Properties props = new Properties();
	if (args.length % 2 != 0) {
	    throw new RuntimeException("Odd number of arguments");
	}
	for (int i = 0; i < args.length; i += 2) {
	    props.setProperty(args[i], args[i + 1]);
	}
	return props;
    }

    /** Find the app listener */
    private DummyAppListener getAppListener() {
	return (DummyAppListener) dataService.getServiceBinding(
	    StandardProperties.APP_LISTENER, AppListener.class);
    }

    /**
     * Dummy client code for testing purposes.
     */
    private class DummyClient {

	private String name;
	private String password;
	private Connector<SocketAddress> connector;
	private ConnectionListener listener;
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
	private CompactId sessionId;
	private CompactId reconnectionKey;
	private final AtomicLong sequenceNumber = new AtomicLong(0);
	
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
 			    "DummyClient.connect timed out");
		    }
		} catch (InterruptedException e) {
		    throw new RuntimeException(
			"DummyClient.connect timed out", e);
		}
	    }
	    
	}

	void disconnect(boolean graceful) {
            System.err.println("DummyClient.disconnect: " + graceful);

            if (graceful) {
                logout();
                return;
            }

            synchronized (lock) {
                if (connected == false) {
                    return;
                }
                try {
                    connection.close();
                } catch (IOException e) {
                    System.err.println(
                        "DummyClient.disconnect exception:" + e);
                    connected = false;
                    lock.notifyAll();
                }
            }
	}

	/**
	 * Returns {@code true} if login was successful, and returns
	 * {@code false} if login was redirected.
	 */
	boolean login(String password) {
	    synchronized (lock) {
		if (connected == false) {
		    throw new RuntimeException(
			"DummyClient.login not connected");
		}
	    }
	    this.password = password;

	    MessageBuffer buf =
		new MessageBuffer(3 + MessageBuffer.getSize(name) +
				  MessageBuffer.getSize(password));
	    buf.putByte(SimpleSgsProtocol.VERSION).
		putByte(SimpleSgsProtocol.APPLICATION_SERVICE).
		putByte(SimpleSgsProtocol.LOGIN_REQUEST).
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
	 * Returns the next sequence number for this session.
	 */
	private long nextSequenceNumber() {
	    return sequenceNumber.getAndIncrement();
	}

	/**
	 * Sends a SESSION_MESSAGE.
	 */
	void sendMessage(byte[] message) {
	    checkLoggedIn();

	    MessageBuffer buf =
		new MessageBuffer(13 + message.length);
	    buf.putByte(SimpleSgsProtocol.VERSION).
		putByte(SimpleSgsProtocol.APPLICATION_SERVICE).
		putByte(SimpleSgsProtocol.SESSION_MESSAGE).
		putLong(nextSequenceNumber()).
		putByteArray(message);
	    try {
		connection.sendBytes(buf.getBuffer());
	    } catch (IOException e) {
		throw new RuntimeException(e);
	    }
	}

	void sendMessages(int numMessages, int expectedMessages, RuntimeException re) {
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

	void logout() {
            synchronized (lock) {
                if (connected == false) {
                    return;
                }
                MessageBuffer buf = new MessageBuffer(3);
                buf.putByte(SimpleSgsProtocol.VERSION).
		    putByte(SimpleSgsProtocol.APPLICATION_SERVICE).
		    putByte(SimpleSgsProtocol.LOGOUT_REQUEST);
                logoutAck = false;
                awaitGraceful = true;
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
                    }
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

		byte version = buf.getByte();
		if (version != SimpleSgsProtocol.VERSION) {
		    System.err.println(
			"bytesReceived: got version: " +
			version + ", expected: " + SimpleSgsProtocol.VERSION);
		    return;
		}

		byte serviceId = buf.getByte();
		if (serviceId != SimpleSgsProtocol.APPLICATION_SERVICE) {
		    System.err.println(
			"bytesReceived: got service id: " +
                        serviceId + ", expected: " +
                        SimpleSgsProtocol.APPLICATION_SERVICE);
		    return;
		}

		byte opcode = buf.getByte();

		switch (opcode) {

		case SimpleSgsProtocol.LOGIN_SUCCESS:
		    sessionId = CompactId.getCompactId(buf);
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
		    synchronized (lock) {
			loginAck = true;
			loginRedirect = true;
			System.err.println("login redirected: " + name +
					   ", host:" + redirectHost);
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
                    buf.getLong(); // FIXME sequence number
		    byte[] message = buf.getBytes(buf.getUnsignedShort());
		    synchronized (lock) {
			messageList.add(message);
			System.err.println("message received: " + message);
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

	private final Map<ClientSession, ManagedReference> sessions =
	    Collections.synchronizedMap(
		new HashMap<ClientSession, ManagedReference>());

        /** {@inheritDoc} */
	public ClientSessionListener loggedIn(ClientSession session) {
	    
	    if (session.getName().equals(RETURN_NULL)) {
		return null;
	    } else if (session.getName().equals(NON_SERIALIZABLE)) {
		return new NonSerializableClientSessionListener();
	    } else if (session.getName().equals(THROW_RUNTIME_EXCEPTION)) {
		throw new RuntimeException("loggedIn throwing an exception");
	    } else {
		DummyClientSessionListener listener =
		    new DummyClientSessionListener(session);
		DataManager dataManager = AppContext.getDataManager();
		ManagedReference listenerRef =
		    dataManager.createReference(listener);
		dataManager.markForUpdate(this);
		sessions.put(session, listenerRef);
		System.err.println(
		    "DummyAppListener.loggedIn: session:" + session);
		return listener;
	    }
	}

        /** {@inheritDoc} */
	public void initialize(Properties props) {
	}

	private Set<ClientSession> getSessions() {
	    return sessions.keySet();
	}
    }

    private static class NonSerializableClientSessionListener
	implements ClientSessionListener
    {
        /** {@inheritDoc} */
	public void disconnected(boolean graceful) {
	}

        /** {@inheritDoc} */
	public void receivedMessage(byte[] message) {
	}
    }

    private static class DummyClientSessionListener
	implements ClientSessionListener, Serializable, ManagedObject
    {
	private final static long serialVersionUID = 1L;
	private final String name;
	private int seq = -1;
	
	private transient final ClientSession session;
	
	DummyClientSessionListener(ClientSession session) {
	    this.session = session;
	    this.name = session.getName();
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
	}

        /** {@inheritDoc} */
	public void receivedMessage(byte[] message) {
	    MessageBuffer buf = new MessageBuffer(message);
	    int num = buf.getInt();
	    DummyClient client = dummyClients.get(name);
	    System.err.println("receivedMessage: " + num + 
			       "\nthrowException: " + client.throwException);
	    if (num <= seq) {
		throw new RuntimeException(
		    "expected message greater than " + seq + ", got " + num);
	    }
	    AppContext.getDataManager().markForUpdate(this);
	    client.messages.add(message);
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
	
}
