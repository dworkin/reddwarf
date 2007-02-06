package com.sun.sgs.test.impl.service.session;

import com.sun.sgs.app.AppListener;
import com.sun.sgs.app.ChannelManager;
import com.sun.sgs.app.ClientSession;
import com.sun.sgs.app.ClientSessionListener;
import com.sun.sgs.app.DataManager;
import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.ManagedReference;
import com.sun.sgs.app.TaskManager;
import com.sun.sgs.auth.Identity;
import com.sun.sgs.auth.IdentityCredentials;
import com.sun.sgs.auth.IdentityManager;
import com.sun.sgs.impl.auth.NamePasswordCredentials;
import com.sun.sgs.impl.io.SocketEndpoint;
import com.sun.sgs.impl.io.TransportType;
import com.sun.sgs.impl.kernel.DummyAbstractKernelAppContext;
import com.sun.sgs.impl.kernel.MinimalTestKernel;
import com.sun.sgs.impl.service.channel.ChannelServiceImpl;
import com.sun.sgs.impl.service.data.DataServiceImpl;
import com.sun.sgs.impl.service.data.store.DataStoreImpl;
import com.sun.sgs.impl.service.session.ClientSessionServiceImpl;
import com.sun.sgs.impl.service.task.TaskServiceImpl;
import com.sun.sgs.impl.util.MessageBuffer;
import com.sun.sgs.io.Connector;
import com.sun.sgs.io.Connection;
import com.sun.sgs.io.ConnectionListener;
import com.sun.sgs.protocol.simple.SimpleSgsProtocol;
import com.sun.sgs.service.ClientSessionService;
import com.sun.sgs.service.DataService;
import com.sun.sgs.service.TaskService;
import com.sun.sgs.test.util.DummyComponentRegistry;
import com.sun.sgs.test.util.DummyTaskScheduler;
import com.sun.sgs.test.util.DummyTransaction;
import com.sun.sgs.test.util.DummyTransactionProxy;
import java.io.File;
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
import junit.framework.TestCase;

public class TestClientSessionServiceImpl extends TestCase {
    /** The name of the DataServiceImpl class. */
    private static final String DataStoreImplClassName =
	DataStoreImpl.class.getName();

    /** The name of the DataServiceImpl class. */
    private static final String DataServiceImplClassName =
	DataServiceImpl.class.getName();

    /** Directory used for database shared across multiple tests. */
    private static String dbDirectory =
	System.getProperty("java.io.tmpdir") + File.separator +
	"TestClientSessionServiceImpl.db";

    /** The port for the client session service. */
    private static int PORT = 0;

    /** Properties for the session service. */
    private static Properties serviceProps = createProperties(
	"com.sun.sgs.appName", "TestClientSessionServiceImpl",
	"com.sun.sgs.app.port", Integer.toString(PORT));

    /** Properties for creating the shared database. */
    private static Properties dbProps = createProperties(
	DataStoreImplClassName + ".directory",
	dbDirectory,
	"com.sun.sgs.appName", "TestClientSessionServiceImpl",
	DataServiceImplClassName + ".debugCheckInterval", "1");

    private static final String LOGIN_FAILED_MESSAGE = "login failed";

    private static final int WAIT_TIME = 1000;

    private static final String RETURN_NULL = "return null";

    private static final String NON_SERIALIZABLE = "non-serializable";

    private static final String THROW_RUNTIME_EXCEPTION =
	"throw RuntimeException";
    
    private static final String LISTENER_PREFIX =
	"com.sun.sgs.impl.service.session.ClientSessionImpl";
    
    private static Object disconnectedCallbackLock = new Object();
    
    /**
     * Delete the database directory at the start of the test run, but not for
     * each test.
     */
    static {
	System.err.println("Deleting database directory");
	deleteDirectory(dbDirectory);
    }

    /** A per-test database directory, or null if not created. */
    private String directory;
    
    private static DummyTransactionProxy txnProxy =
	MinimalTestKernel.getTransactionProxy();

    private DummyAbstractKernelAppContext appContext;
    private DummyComponentRegistry systemRegistry;
    private DummyComponentRegistry serviceRegistry;
    private DummyTransaction txn;
    
    private DataServiceImpl dataService;
    private ChannelServiceImpl channelService;
    private ClientSessionServiceImpl sessionService;
    private TaskServiceImpl taskService;
    private DummyTaskScheduler taskScheduler;
    private DummyIdentityManager identityManager;

    /** The listen port for the client session service. */
    private int port;

    /** True if test passes. */
    private boolean passed;

    /** Constructs a test instance. */
    public TestClientSessionServiceImpl(String name) {
	super(name);
    }

    /** Creates and configures the session service. */
    protected void setUp() throws Exception {
	passed = false;
	System.err.println("Testcase: " + getName());
	appContext = MinimalTestKernel.createContext();
	systemRegistry = MinimalTestKernel.getSystemRegistry(appContext);
	serviceRegistry = MinimalTestKernel.getServiceRegistry(appContext);
	    
	// create services
	dataService = createDataService(systemRegistry);
	taskService = new TaskServiceImpl(new Properties(), systemRegistry);
	identityManager = new DummyIdentityManager();
	systemRegistry.setComponent(IdentityManager.class, identityManager);
	sessionService =
	    new ClientSessionServiceImpl(serviceProps, systemRegistry);
	channelService = new ChannelServiceImpl(serviceProps, systemRegistry);

	createTransaction();

	// configure data service
        dataService.configure(serviceRegistry, txnProxy);
        txnProxy.setComponent(DataService.class, dataService);
        txnProxy.setComponent(DataServiceImpl.class, dataService);
        serviceRegistry.setComponent(DataManager.class, dataService);
        serviceRegistry.setComponent(DataService.class, dataService);
        serviceRegistry.setComponent(DataServiceImpl.class, dataService);

	// configure task service
        taskService.configure(serviceRegistry, txnProxy);
        txnProxy.setComponent(TaskService.class, taskService);
        txnProxy.setComponent(TaskServiceImpl.class, taskService);
        serviceRegistry.setComponent(TaskManager.class, taskService);
        serviceRegistry.setComponent(TaskService.class, taskService);
        serviceRegistry.setComponent(TaskServiceImpl.class, taskService);
	//serviceRegistry.registerAppContext();

	// configure client session service
	sessionService.configure(serviceRegistry, txnProxy);
	serviceRegistry.setComponent(
	    ClientSessionService.class, sessionService);
	txnProxy.setComponent(
	    ClientSessionService.class, sessionService);
	port = sessionService.getListenPort();
	
	// configure channel service
	channelService.configure(serviceRegistry, txnProxy);
	serviceRegistry.setComponent(ChannelManager.class, channelService);
	
	txn.commit();
	createTransaction();
    }

    /** Sets passed if the test passes. */
    protected void runTest() throws Throwable {
	super.runTest();
	passed = true;
    }
    
    /** Cleans up the transaction. */
    protected void tearDown() throws Exception {
	sessionService.shutdown();
	if (txn != null) {
	    try {
		txn.abort();
	    } catch (IllegalStateException e) {
	    }
	    txn = null;
	}
        if (dataService != null) {
            dataService.shutdown();
        }
        dataService = null;
        deleteDirectory(dbDirectory);
        MinimalTestKernel.destroyContext(appContext);
    }
 
    /* -- Test constructor -- */

    public void testConstructorNullProperties() {
	try {
	    new ClientSessionServiceImpl(null, new DummyComponentRegistry());
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
    }

    public void testConstructorNullComponentRegistry() {
	try {
	    new ClientSessionServiceImpl(serviceProps, null);
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
    }

    public void testConstructorNoAppName() throws Exception {
	try {
	    new ClientSessionServiceImpl(
		new Properties(), new DummyComponentRegistry());
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
    }

    public void testConstructorNoPort() throws Exception {
	try {
	    Properties props =
		createProperties("com.sun.sgs.appName",
				 "TestClientSessionServiceImpl");
	    new ClientSessionServiceImpl(
		props, new DummyComponentRegistry());

	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
    }

    /* -- Test connecting, logging in, logging out with server -- */

    public void testConnection() throws Exception {
	DummyClient client = new DummyClient();
	try {
	    client.connect(port);
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
	try {
	    client.connect(port);
	    client.login("success", "password");
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
	    client.connect(port);
	    client.login(NON_SERIALIZABLE, "password");
	    fail("expected login failure");
	} catch (RuntimeException e) {
	    if (e.getMessage().equals(LOGIN_FAILED_MESSAGE)) {
		System.err.println("login refused");
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
	    client.connect(port);
	    client.login(RETURN_NULL, "bar");
	    fail("expected login failure");	
	} catch (RuntimeException e) {
	    if (e.getMessage().equals(LOGIN_FAILED_MESSAGE)) {
		System.err.println("login refused");
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
	    client.connect(port);
	    client.login(THROW_RUNTIME_EXCEPTION, "bar");
	    fail("expected login failure");	
	} catch (RuntimeException e) {
	    if (e.getMessage().equals(LOGIN_FAILED_MESSAGE)) {
		System.err.println("login refused");
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
	    client.connect(port);
	    client.login(name, "test");
	    client.logout();
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
		    } else if (!sessionListener.graceful) {
			fail("disconnection was not graceful");
		    }
		    System.err.println("Logout successful");
		}
	    }
	} catch (InterruptedException e) {
	    e.printStackTrace();
	    fail("testLogout interrupted");
	} finally {
	    client.disconnect(false);
	}
    }

    public void testNotifyClientSessionListenerAfterCrash() throws Exception {
	registerAppListener();
	DummyClient client = new DummyClient();
	String name = "testRemoveListener";
	try {
	    client.connect(port);
	    client.login(name, "password");
	    sessionService.shutdown();
	    DummyClientSessionListener sessionListener =
		getClientSessionListener(name);
	    if (sessionListener == null) {
		fail("listener is null");
	    } else {
		synchronized (disconnectedCallbackLock) {
		    if (sessionListener.receivedDisconnectedCallback) {
			fail("shouldn't have received disconnected callback");
		    }
		}
	    }
	    Set<String> listenerKeys = getClientSessionListenerKeys();
	    System.err.println("Listener keys: " + listenerKeys);
	    if (listenerKeys.isEmpty()) {
		fail("no listener keys");
	    } else if (listenerKeys.size() > 1) {
		fail("more than one listener key");
	    }
	    sessionService = 
		new ClientSessionServiceImpl(serviceProps, systemRegistry);
	    createTransaction();
	    sessionService.configure(serviceRegistry, txnProxy);
	    serviceRegistry.setComponent(
		ClientSessionService.class, sessionService);
	    txnProxy.setComponent(
	        ClientSessionService.class, sessionService);
	    port = sessionService.getListenPort();
	    txn.commit();

	    sessionListener = getClientSessionListener(name);
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
	createTransaction();
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
	txn.commit();
	return listenerKeys;
    }

    private DummyClientSessionListener getClientSessionListener(String name)
	throws Exception
    {
	createTransaction();
	DummyClientSessionListener sessionListener =
	    getAppListener().getClientSessionListener(name);
	txn.commit();
	return sessionListener;
    }

    /* -- test ClientSession -- */

    public void testClientSessionIsConnected() throws Exception {
	registerAppListener();
	DummyClient client = new DummyClient();
	String name = "clientname";
	try {
	    client.connect(port);
	    client.login(name, "dummypassword");
	    createTransaction();
	    DummyAppListener appListener = getAppListener();
	    Set<ClientSession> sessions = appListener.getSessions();
	    if (sessions.isEmpty()) {
		fail("appListener contains no client sessions!");
	    }
	    for (ClientSession session : appListener.getSessions()) {
		if (session.isConnected() == true) {
		    System.err.println("session is connected");
		    txn.commit();
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
	    client.connect(port);
	    client.login(name, "dummypassword");
	    createTransaction();
	    DummyAppListener appListener = getAppListener();
	    Set<ClientSession> sessions = appListener.getSessions();
	    if (sessions.isEmpty()) {
		fail("appListener contains no client sessions!");
	    }
	    for (ClientSession session : appListener.getSessions()) {
		if (session.getName().equals(name)) {
		    System.err.println("names match");
		    txn.commit();
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
	    client.connect(port);
	    client.login(name, "dummypassword");
	    createTransaction();
	    DummyAppListener appListener = getAppListener();
	    Set<ClientSession> sessions = appListener.getSessions();
	    if (sessions.isEmpty()) {
		fail("appListener contains no client sessions!");
	    }
	    for (ClientSession session : appListener.getSessions()) {
		if (Arrays.equals(session.getSessionId(), client.getSessionId())) {
		    System.err.println("session IDs match");
		    txn.commit();
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
    
    public void testClientSessionSend() throws Exception {
    }
    /* -- other methods -- */

    /** Deletes the specified directory, if it exists. */
    static void deleteDirectory(String directory) {
	File dir = new File(directory);
	if (dir.exists()) {
	    for (File f : dir.listFiles()) {
		if (!f.delete()) {
		    throw new RuntimeException("Failed to delete file: " + f);
		}
	    }
	    if (!dir.delete()) {
		throw new RuntimeException(
		    "Failed to delete directory: " + dir);
	    }
	}
    }

    /**
     * Creates a new transaction, and sets transaction proxy's
     * current transaction.
     */
    private DummyTransaction createTransaction() {
	txn = new DummyTransaction();
	txnProxy.setCurrentTransaction(txn);
	return txn;
    }
    
    
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
     * Creates a new data service.  If the database directory does
     * not exist, one is created.
     */
    private DataServiceImpl createDataService(
	DummyComponentRegistry registry)
	throws Exception
    {
	File dir = new File(dbDirectory);
	if (!dir.exists()) {
	    if (!dir.mkdir()) {
		throw new RuntimeException(
		    "Problem creating directory: " + dir);
	    }
	}
	return new DataServiceImpl(dbProps, registry);
    }

    private void registerAppListener() throws Exception {
	createTransaction();
	DummyAppListener appListener = new DummyAppListener();
	dataService.setServiceBinding(
	    "com.sun.sgs.app.AppListener", appListener);
	txn.commit();
    }
    
    private DummyAppListener getAppListener() {
	return (DummyAppListener) dataService.getServiceBinding(
	    "com.sun.sgs.app.AppListener", AppListener.class);
    }
    

    /**
     * Dummy identity manager for testing purposes.
     */
    private static class DummyIdentityManager implements IdentityManager {
	public Identity authenticateIdentity(IdentityCredentials credentials) {
	    return new DummyIdentity(credentials);
	}
    }
    
    /**
     * Identity returned by the DummyIdentityManager.
     */
    private static class DummyIdentity implements Identity {

	private final String name;

	DummyIdentity(IdentityCredentials credentials) {
	    this.name = ((NamePasswordCredentials) credentials).getName();
	}
	
	public String getName() {
	    return name;
	}

	public void notifyLoggedIn() {}

	public void notifyLoggedOut() {}
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
	private String reason;
	private byte[] sessionId;
	private byte[] reconnectionKey;
	
	DummyClient() {
	}

	byte[] getSessionId() {
	    return sessionId;
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
	    if (!graceful) {
		synchronized (lock) {
		    if (connected == false) {
			return;
		    }
		    connected = false;
		    try {
			connection.close();
		    } catch (IOException e) {
			System.err.println(
			    "DummyClient.disconnect exception:" + e);
		    }
		    lock.notifyAll();
		}
	    } else {
		synchronized (lock) {
		    if (connected == false) {
			return;
		    }
		    MessageBuffer buf = new MessageBuffer(3);
		    buf.putByte(SimpleSgsProtocol.VERSION).
			putByte(SimpleSgsProtocol.APPLICATION_SERVICE).
			putByte(SimpleSgsProtocol.LOGOUT_REQUEST);
		    logoutAck = false;
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

	void logout() {
	    synchronized (lock) {
		if (connected == false) {
		    throw new RuntimeException(
			"DummyClient.login not connected");
		}
	    }

	    MessageBuffer buf = new MessageBuffer(3);
	    buf.putByte(SimpleSgsProtocol.VERSION).
		putByte(SimpleSgsProtocol.APPLICATION_SERVICE).
		putByte(SimpleSgsProtocol.LOGOUT_REQUEST);
	    logoutAck = false;

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
			    "DummyClient.logout timed out");
		    }
		} catch (InterruptedException e) {
		    throw new RuntimeException(
			"DummyClient.logout timed out", e);
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
		    sessionId = buf.getBytes(buf.getUnsignedShort());
		    reconnectionKey = buf.getBytes(buf.getUnsignedShort());
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

		case SimpleSgsProtocol.LOGOUT_SUCCESS:
		    synchronized (lock) {
			logoutAck = true;
			System.err.println("logout succeeded: " + name);
			lock.notifyAll();
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
		ManagedReference listenerRef =
		    txnProxy.getService(DataService.class).
		    createReference(listener);
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
	
	private transient final ClientSession session;
	
	DummyClientSessionListener(ClientSession session) {
	    this.session = session;
	    this.name = session.getName();
	}

        /** {@inheritDoc} */
	public void disconnected(boolean graceful) {
	    System.err.println("DummyClientSessionListener[" + name +
			       "] disconnected invoked with " + graceful);
	    synchronized (disconnectedCallbackLock) {
		receivedDisconnectedCallback = true;
		this.graceful = graceful;
		disconnectedCallbackLock.notifyAll();
	    }
	}

        /** {@inheritDoc} */
	public void receivedMessage(byte[] message) {
	}
    }
}
