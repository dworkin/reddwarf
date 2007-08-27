/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.test.impl.service.session;

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.AppListener;
import com.sun.sgs.app.ClientSession;
import com.sun.sgs.app.ClientSessionId;
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
import com.sun.sgs.io.Connector;
import com.sun.sgs.io.Connection;
import com.sun.sgs.io.ConnectionListener;
import com.sun.sgs.protocol.simple.SimpleSgsProtocol;
import com.sun.sgs.service.ClientSessionService;
import com.sun.sgs.test.util.DummyComponentRegistry;
import com.sun.sgs.test.util.DummyTransactionProxy;
import com.sun.sgs.test.util.SgsTestStack;
import java.io.IOException;
import java.io.Serializable;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicLong;
import junit.framework.TestCase;

public class TestClientSessionServiceImpl extends TestCase {
    private static final String LOGIN_FAILED_MESSAGE = "login failed";

    private static final int WAIT_TIME = 5000;

    private static final String RETURN_NULL = "return null";

    private static final String NON_SERIALIZABLE = "non-serializable";

    private static final String THROW_RUNTIME_EXCEPTION =
	"throw RuntimeException";
    
    private static final String LISTENER_PREFIX =
	ClientSessionServiceImpl.LISTENER_PREFIX;
    
    private static Object disconnectedCallbackLock = new Object();

    private SgsTestStack stack;
    private DataServiceImpl dataService;

    /** True if test passes. */
    private boolean passed;

    /** Constructs a test instance. */
    public TestClientSessionServiceImpl(String name) throws Exception {
	super(name);
	stack = new SgsTestStack("TestClientSessionServiceImpl", null);
    }

    /** Creates and configures the session service. */
    protected void setUp() throws Exception {
        passed = false;
        System.err.println("Testcase: " + getName());
	stack.setUp(true);
	dataService = stack.getDataService();
	stack.createTransaction();
    }

    /** Sets passed if the test passes. */
    protected void runTest() throws Throwable {
	super.runTest();
        Thread.sleep(100);
	passed = true;
    }
    
    /** Cleans up the transaction. */
    protected void tearDown() throws Exception {
        tearDown(true);
    }

    protected void tearDown(boolean clean) throws Exception {
	stack.tearDown(clean);
    }

    /* -- Test constructor -- */

    public void testConstructorNullProperties() throws Exception {
	try {
	    new ClientSessionServiceImpl(
		null, new DummyComponentRegistry(),
		new DummyTransactionProxy());
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
    }

    public void testConstructorNullComponentRegistry() throws Exception {
	try {
	    Properties props =
		createProperties(
		    "com.sun.sgs.app.name", "TestClientSessionServiceImpl",
		    "com.sun.sgs.app.port", "0");
	    new ClientSessionServiceImpl(props, null,
					 new DummyTransactionProxy());
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
    }

    public void testConstructorNullTransactionProxy() throws Exception {
	try {
	    Properties props =
		createProperties(
		    "com.sun.sgs.app.name", "TestClientSessionServiceImpl",
		    "com.sun.sgs.app.port", "0");
	    new ClientSessionServiceImpl(props,
					 new DummyComponentRegistry(), null);
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
    }

    public void testConstructorNoAppName() throws Exception {
	try {
	    new ClientSessionServiceImpl(
		new Properties(), new DummyComponentRegistry(),
		new DummyTransactionProxy());
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
    }

    public void testConstructorNoPort() throws Exception {
	try {
	    Properties props =
		createProperties(
		    "com.sun.sgs.app.name", "TestClientSessionServiceImpl");
	    new ClientSessionServiceImpl(
		props, new DummyComponentRegistry(),
		new DummyTransactionProxy());

	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
    }

    /* -- Test connecting, logging in, logging out with server -- */

    public void testConnection() throws Exception {
	DummyClient client = new DummyClient();
	try {
	    client.connect(stack.getAppPort());
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
	registerAppListener();
	DummyClient client = new DummyClient();
	String name = "success";
	try {
	    client.connect(stack.getAppPort());
	    client.login(name, "password");
	} finally {
            client.disconnect(false);
	}
    }
    
    public void testLoginSuccessAndNotifyLoggedInCallback() throws Exception {
	registerAppListener();
	DummyClient client = new DummyClient();
	String name = "success";
	try {
	    client.connect(stack.getAppPort());
	    client.login(name, "password");
	    if (stack.getIdentityManager().getNotifyLoggedIn(name)) {
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
	registerAppListener();
	DummyClient client = new DummyClient();
	try {
	    client.connect(stack.getAppPort());
	    client.login(NON_SERIALIZABLE, "password");
	    fail("expected login failure");
	} catch (RuntimeException e) {
	    if (e.getMessage().equals(LOGIN_FAILED_MESSAGE)) {
		System.err.println("login refused");
		if (stack.getIdentityManager().
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
	registerAppListener();
	DummyClient client = new DummyClient();
	try {
	    client.connect(stack.getAppPort());
	    client.login(RETURN_NULL, "bar");
	    fail("expected login failure");	
	} catch (RuntimeException e) {
	    if (e.getMessage().equals(LOGIN_FAILED_MESSAGE)) {
		System.err.println("login refused");
		if (stack.getIdentityManager().
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
	registerAppListener();
	DummyClient client = new DummyClient();
	try {
	    client.connect(stack.getAppPort());
	    client.login(THROW_RUNTIME_EXCEPTION, "bar");
	    fail("expected login failure");	
	} catch (RuntimeException e) {
	    if (e.getMessage().equals(LOGIN_FAILED_MESSAGE)) {
		System.err.println("login refused");
		if (stack.getIdentityManager().
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
	registerAppListener();
	DummyClient client = new DummyClient();
	String name = "logout";
	try {
	    client.connect(stack.getAppPort());
	    client.login(name, "test");
	    client.logout();
	    synchronized (disconnectedCallbackLock) {
		DummyClientSessionListener sessionListener =
		    getClientSessionListener(name);
		if (sessionListener == null ||
		    !sessionListener.receivedDisconnectedCallback)
		{
		    disconnectedCallbackLock.wait(WAIT_TIME);
		    sessionListener = getClientSessionListener(name);
		}
		if (sessionListener == null) {
		    fail ("sessionListener is null!");
		} else if (!sessionListener.receivedDisconnectedCallback) {
		    fail("disconnected callback not invoked");
		} else if (!sessionListener.graceful) {
		    fail("disconnection was not graceful");
		}
		System.err.println("Logout successful");
	    }
	} catch (InterruptedException e) {
	    e.printStackTrace();
	    fail("testLogout interrupted");
	} finally {
	    client.disconnect(false);
	}
    }

    public void testLogoutAndNotifyLoggedOutCallback() throws Exception {
	registerAppListener();
	DummyClient client = new DummyClient();
	String name = "logout";
	try {
	    client.connect(stack.getAppPort());
	    client.login(name, "password");
	    client.logout();
	    if (stack.getIdentityManager().getNotifyLoggedIn(name)) {
		System.err.println(
		    "notifyLoggedIn invoked for identity: " + name);
	    } else {
		fail("notifyLoggedIn not invoked for identity: " + name);
	    }
	    if (stack.getIdentityManager().getNotifyLoggedOut(name)) {
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
	registerAppListener();
	DummyClient client = new DummyClient();
	String name = "testRemoveListener";
	try {
	    client.connect(stack.getAppPort());
	    client.login(name, "password");

	    Set<String> listenerKeys = getClientSessionListenerKeys();
	    System.err.println("Listener keys: " + listenerKeys);
	    if (listenerKeys.isEmpty()) {
		fail("no listener keys");
	    } else if (listenerKeys.size() > 1) {
		fail("more than one listener key");
	    }
	    
            // Simulate "crash"
            tearDown(false);
            stack.setUp(false);
	    dataService = stack.getDataService();
	    
	    DummyClientSessionListener sessionListener =
		getClientSessionListener(name);
	    if (sessionListener == null) {
		fail("listener is null!");
	    } else {
		synchronized (disconnectedCallbackLock) {

		    if (!sessionListener.receivedDisconnectedCallback) {
			disconnectedCallbackLock.wait(WAIT_TIME);
			sessionListener = getClientSessionListener(name);
		    }

		    if (!sessionListener.receivedDisconnectedCallback) {
			fail("disconnected callback not invoked");
		    } else if (sessionListener.graceful) {
			fail("disconnection was graceful!");
		    }
		    System.err.println("disconnect notification successful");
		}
	    }

	    if (!getClientSessionListenerKeys().isEmpty()) {
		fail("listener key not removed!");
	    }
	} finally {
	    client.disconnect(false);
	}
    }

    private Set<String> getClientSessionListenerKeys() throws Exception {
	stack.createTransaction();
	Set<String> listenerKeys = new HashSet<String>();
	String key = LISTENER_PREFIX;
	for (;;) {
	    key = dataService.nextServiceBoundName(key);
	    if (key == null ||
		! key.regionMatches(
		      0, LISTENER_PREFIX, 0, LISTENER_PREFIX.length()))
	    {
		break;
	    }
	    listenerKeys.add(key);
	}
	stack.commitTransaction();
	return listenerKeys;
    }

    private DummyClientSessionListener getClientSessionListener(String name)
	throws Exception
    {
	stack.createTransaction();
	DummyClientSessionListener sessionListener =
	    getAppListener().getClientSessionListener(name);
	stack.commitTransaction();
	return sessionListener;
    }

    /* -- test ClientSession -- */

    public void testClientSessionIsConnected() throws Exception {
	registerAppListener();
	DummyClient client = new DummyClient();
	String name = "clientname";
	try {
	    client.connect(stack.getAppPort());
	    client.login(name, "dummypassword");
	    stack.createTransaction();
	    DummyAppListener appListener = getAppListener();
	    Set<ClientSession> sessions = appListener.getSessions();
	    if (sessions.isEmpty()) {
		fail("appListener contains no client sessions!");
	    }
	    for (ClientSession session : appListener.getSessions()) {
		if (session.isConnected() == true) {
		    System.err.println("session is connected");
		    stack.commitTransaction();
		    return;
		} else {
		    fail("Expected connected session: " + session);
		}
	    }
	    fail("expected a connected session");
	} finally {
	    client.disconnect(false);
	}
    }
    
    public void testClientSessionGetName() throws Exception {
	registerAppListener();
	DummyClient client = new DummyClient();
	String name = "clientname";
	try {
	    client.connect(stack.getAppPort());
	    client.login(name, "dummypassword");
	    stack.createTransaction();
	    DummyAppListener appListener = getAppListener();
	    Set<ClientSession> sessions = appListener.getSessions();
	    if (sessions.isEmpty()) {
		fail("appListener contains no client sessions!");
	    }
	    for (ClientSession session : appListener.getSessions()) {
		if (session.getName().equals(name)) {
		    System.err.println("names match");
		    stack.commitTransaction();
		    return;
		} else {
		    fail("Expected session name: " + name +
			 ", got: " + session.getName());
		}
	    }
	    fail("expected d connected session");
	} finally {
	    client.disconnect(false);
	}
    }

    public void testClientSessionGetSessionId() throws Exception {
	registerAppListener();
	DummyClient client = new DummyClient();
	String name = "clientname";
	try {
	    client.connect(stack.getAppPort());
	    client.login(name, "dummypassword");
	    stack.createTransaction();
	    DummyAppListener appListener = getAppListener();
	    Set<ClientSession> sessions = appListener.getSessions();
	    if (sessions.isEmpty()) {
		fail("appListener contains no client sessions!");
	    }
	    for (ClientSession session : appListener.getSessions()) {
		if (session.getSessionId().equals(client.getSessionId())) {
		    System.err.println("session IDs match");
		    stack.commitTransaction();
		    return;
		} else {
		    fail("Expected session id: " + client.getSessionId() +
			 ", got: " + session.getSessionId());
		}
	    }
	    fail("expected a connected session");
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
    
    private static final Object receivedAllMessagesLock = new Object();
    private static RuntimeException throwException = null;
    private static int totalExpectedMessages;
    
    private void sendMessagesAndCheck(
	int numMessages, int expectedMessages, RuntimeException exception)
	throws Exception
    {
	synchronized (receivedAllMessagesLock) {
	    totalExpectedMessages = expectedMessages;
	    throwException = exception;
	}
	registerAppListener();
	DummyClient client = new DummyClient();
	String name = "client";
	try {
	    client.connect(stack.getAppPort());
	    client.login(name, "dummypassword");
	    for (int i = 0; i < numMessages; i++) {
		MessageBuffer buf = new MessageBuffer(4);
		buf.putInt(i);
		client.sendMessage(buf.getBuffer());
	    }
	    
	    DummyClientSessionListener sessionListener =
		getClientSessionListener(name);
	    synchronized (receivedAllMessagesLock) {
		if (sessionListener.messages.size() != totalExpectedMessages) {
		    try {
			receivedAllMessagesLock.wait(WAIT_TIME);
		    } catch (InterruptedException e) {
		    }
		}
	    }
	    sessionListener = getClientSessionListener(name);
	    synchronized (receivedAllMessagesLock) {
		int receivedMessages = sessionListener.messages.size();
		if (receivedMessages != totalExpectedMessages) {
		    fail("expected " + totalExpectedMessages + ", received " +
			 receivedMessages);
		}
	    }
		
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
 
    /**
     * Registers an AppListener within a transaction.
     */
    private void registerAppListener() throws Exception {
	
	stack.createTransaction();
	DummyAppListener appListener = new DummyAppListener();
	dataService.setServiceBinding(
	    StandardProperties.APP_LISTENER, appListener);
	stack.commitTransaction();
    }
    
    private DummyAppListener getAppListener() {
	return (DummyAppListener) dataService.getServiceBinding(
	    StandardProperties.APP_LISTENER, AppListener.class);
    }
    /**
     * Dummy client code for testing purposes.
     */
    private static class DummyClient {

	private String name;
	private String password;
	private Connector<SocketAddress> connector;
	private ConnectionListener listener;
	private Connection connection;
	private boolean connected = false;
	private final Object lock = new Object();
	private boolean loginAck = false;
	private boolean loginSuccess = false;
	private boolean logoutAck = false;
	private boolean loginRedirect = false;
        private boolean awaitGraceful = false;
        private boolean awaitLoginFailure = false;
	private String reason;
	private String redirectHost;
	private CompactId sessionId;
	private CompactId reconnectionKey;
	private final AtomicLong sequenceNumber = new AtomicLong(0);
	
	DummyClient() {
	}

	ClientSessionId getSessionId() {
	    return new ClientSessionId(sessionId.getId());
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

	void login(String name, String password) {
	    synchronized (lock) {
		if (connected == false) {
		    throw new RuntimeException(
			"DummyClient.login not connected");
		}
	    }
	    this.name = name;
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
		    if (!loginSuccess) {
			throw new RuntimeException(LOGIN_FAILED_MESSAGE);
		    }
		} catch (InterruptedException e) {
		    throw new RuntimeException(
			"DummyClient.login timed out", e);
		}
	    }
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
		    reconnectionKey = CompactId.getCompactId(buf);
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

    private static class DummyAppListener implements AppListener, Serializable {

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

	DummyClientSessionListener getClientSessionListener(String name) {

	    for (Map.Entry<ClientSession,ManagedReference> entry :
		     sessions.entrySet()) {

		ClientSession session = entry.getKey();
		ManagedReference listenerRef = entry.getValue();
		if (session.getName().equals(name)) {
		    return listenerRef.get(DummyClientSessionListener.class);
		}
	    }
	    return null;
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
	boolean receivedDisconnectedCallback = false;
	boolean graceful = false;
	List<byte[]> messages = new ArrayList<byte[]>();
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
	    synchronized (disconnectedCallbackLock) {
		receivedDisconnectedCallback = true;
		this.graceful = graceful;
		disconnectedCallbackLock.notifyAll();
	    }
	}

        /** {@inheritDoc} */
	public void receivedMessage(byte[] message) {
	    MessageBuffer buf = new MessageBuffer(message);
	    int num = buf.getInt();
	    System.err.println("receivedMessage: " + num + 
			       "\nthrowException: " + throwException);
	    if (num <= seq) {
		throw new RuntimeException(
		    "expected message greater than " + seq + ", got " + num);
	    }
	    AppContext.getDataManager().markForUpdate(this);
	    messages.add(message);
	    seq = num;
	    synchronized (receivedAllMessagesLock) {
		if (throwException != null) {
		    RuntimeException e = throwException;
		    throwException = null;
		    throw e;
		}
	    }
	    if (messages.size() == totalExpectedMessages) {
		synchronized (receivedAllMessagesLock) {
		    receivedAllMessagesLock.notifyAll();
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
