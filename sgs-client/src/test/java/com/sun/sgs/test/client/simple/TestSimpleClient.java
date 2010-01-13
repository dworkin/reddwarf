/*
 * Copyright (c) 2007-2010, Sun Microsystems, Inc.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in
 *       the documentation and/or other materials provided with the
 *       distribution.
 *     * Neither the name of Sun Microsystems, Inc. nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * --
 */

package com.sun.sgs.test.client.simple;

import com.sun.sgs.client.ClientChannel;
import com.sun.sgs.client.ClientChannelListener;
import com.sun.sgs.client.simple.SimpleClient;
import com.sun.sgs.client.simple.SimpleClientListener;
import com.sun.sgs.impl.sharedutil.MessageBuffer;

import java.net.PasswordAuthentication;
import java.nio.ByteBuffer;
import java.util.Properties;
import junit.framework.TestCase;

public class TestSimpleClient extends TestCase {

    private static final char[] password = { 'g', 'u', 'e', 's', 't' };

    private static final char[] redirectPassword =
    	{ 'r', 'e', 'd', 'i', 'r', 'e', 'c', 't' };

    private static final long TIMEOUT = 1000;

    public TestSimpleClient(String name) {
	super(name);
    }

    /** Prints the test case. */
    protected void setUp() throws Exception {
	System.err.println("Testcase: " + getName());
    }
    
    // Tests that a failure to connect to the server causes a
    // disconnected callback (but not a loginFailed callback).
    public void testLoginNoServer() throws Exception {
	DummySimpleClientListener listener =
	    new DummySimpleClientListener();

	SimpleClient client = new SimpleClient(listener);
	Properties props =
	    createProperties(
		"host", "localhost",
		"port", Integer.toString(5382),
		"connectTimeout", Long.toString(TIMEOUT));
	client.login(props);
	synchronized (client) {
	    client.wait(TIMEOUT * 2);
	}
	assertTrue(listener.disconnected);
	assertEquals(0, listener.getPasswordAuthentication);
	if (listener.disconnectReason == null) {
	    fail("Received null disconnect reason");
	}
	System.err.println("reason: " + listener.disconnectReason);
	assertFalse(listener.disconnectGraceful);
	assertFalse(listener.loginFailed);
    }

    // TBD: it would be good to have a test that exercises
    // the timeout expiration.

    // Tests that a login authentication failure at the server causes
    // a loginFailed callback on the client (but not a disconnected
    // callback).
    public void testLoginFailedCallback() throws Exception {
	DummySimpleClientListener listener =
	    new DummySimpleClientListener(
 		new PasswordAuthentication("guest", new char[] {'!'}));

	SimpleClient client = new SimpleClient(listener);
	int port = 5382;
	Properties props =
	    createProperties(
		"host", "localhost",
		"port", Integer.toString(port),
		"connectTimeout", Long.toString(TIMEOUT));
	SimpleServer server = new SimpleServer(port);
	try {
	    server.start();
	    client.login(props);
	    synchronized (client) {
		client.wait(TIMEOUT);
	    }
	    assertTrue(listener.loginFailed);
	    if (listener.loginFailedReason == null) {
		fail("Didn't receive loginFailed callback");
	    }
	    System.err.println("reason: " + listener.loginFailedReason);
	    assertFalse(listener.disconnected);

	} finally {
	    server.shutdown();
	}
    }

    public void testLoggedInCallback() throws Exception {
	DummySimpleClientListener listener =
	    new DummySimpleClientListener(
		new PasswordAuthentication("guest", password));

	SimpleClient client = new SimpleClient(listener);
	int port = 5383;
	Properties props =
	    createProperties(
		"host", "localhost",
		"port", Integer.toString(port),
		"connectTimeout", Long.toString(TIMEOUT));
	SimpleServer server = new SimpleServer(port);
	try {
	    server.start();
	    client.login(props);
	    synchronized (client) {
		client.wait(TIMEOUT);
	    }
	    assertTrue(listener.loggedIn);
	} finally {
	    server.shutdown();
	}
    }

    public void testLoginObtainsPasswordAuthentication() throws Exception {
	DummySimpleClientListener listener =
	    new DummySimpleClientListener(
		new PasswordAuthentication("guest", password));

	SimpleClient client = new SimpleClient(listener);
	int port = 5383;
	Properties props =
	    createProperties(
		"host", "localhost",
		"port", Integer.toString(port),
		"connectTimeout", Long.toString(TIMEOUT));
	SimpleServer server = new SimpleServer(port);
	try {
	    server.start();
	    client.login(props);
	    synchronized (client) {
		client.wait(TIMEOUT);
	    }
	    assertEquals(1, listener.loggedInCount);
	    assertEquals(1, listener.getPasswordAuthentication);
	    
	    client.logout(false);
	    synchronized (client) {
		client.wait(TIMEOUT);
	    }
	    assertTrue(listener.disconnected);
	    assertTrue(listener.disconnectGraceful);
	    
	    client.login(props);
	    synchronized (client) {
		client.wait(TIMEOUT);
	    }
	    assertEquals(2, listener.loggedInCount);
	    assertEquals(2, listener.getPasswordAuthentication);
	    
	} finally {
	    server.shutdown();
	}
    }

    public void testLoginObtainsPasswordAuthenticationAfterFailure()
	throws Exception
    {
	DummySimpleClientListener listener =
	    new DummySimpleClientListener(
 		new PasswordAuthentication("guest", new char[] {'!'}));

	SimpleClient client = new SimpleClient(listener);
	int port = 5383;
	Properties props =
	    createProperties(
		"host", "localhost",
		"port", Integer.toString(port),
		"connectTimeout", Long.toString(TIMEOUT));
	SimpleServer server = new SimpleServer(port);
	try {
	    server.start();
	    client.login(props);
	    synchronized (client) {
		client.wait(TIMEOUT);
	    }
	    assertTrue(listener.loginFailed);
	    assertEquals(1, listener.getPasswordAuthentication);

	    listener.auth = new PasswordAuthentication("guest", password);
	    client.login(props);
	    synchronized (client) {
		client.wait(TIMEOUT);
	    }
	    assertEquals(1, listener.loggedInCount);
	    assertEquals(2, listener.getPasswordAuthentication);
	    
	} finally {
	    server.shutdown();
	}
    }
    
    public void testRedirectDoesNotObtainPasswordAuthentication()
	throws Exception
    {
	DummySimpleClientListener listener =
	    new DummySimpleClientListener(
		new PasswordAuthentication("redirect", redirectPassword));

	SimpleClient client = new SimpleClient(listener);
	int port = 5383;
	Properties props =
	    createProperties(
		"host", "localhost",
		"port", Integer.toString(port),
		"connectTimeout", Long.toString(TIMEOUT));
	SimpleServer server = new SimpleServer(port);
	try {
	    server.start();
	    client.login(props);
	    synchronized (client) {
		client.wait(TIMEOUT);
	    }
	    assertTrue(server.redirect);
	    assertTrue(listener.loggedIn);
	    assertEquals(1, listener.loggedInCount);
	    assertEquals(1, listener.getPasswordAuthentication);
	} finally {
	    server.shutdown();
	}
    }

    public void testDisconnectedCallbackAfterGracefulLogout() throws Exception {
	DummySimpleClientListener listener =
	    new DummySimpleClientListener(
		new PasswordAuthentication("guest", password));

	SimpleClient client = new SimpleClient(listener);
	int port = 5383;
	Properties props =
	    createProperties(
		"host", "localhost",
		"port", Integer.toString(port),
		"connectTimeout", Long.toString(TIMEOUT));
	SimpleServer server = new SimpleServer(port);
	try {
	    server.start();
	    client.login(props);
	    synchronized (client) {
		client.wait(TIMEOUT);
	    }
	    assertTrue(listener.loggedIn);
	    assertFalse(listener.disconnected);
	    // request graceful logout
	    client.logout(false);
	    synchronized (client) {
		client.wait(TIMEOUT);
	    }
	    assertTrue(listener.disconnected);
	    assertTrue(listener.disconnectGraceful);
	} finally {
	    server.shutdown();
	}
    }

    public void testDisconnectedCallbackAfterForcedLogout() throws Exception {
	DummySimpleClientListener listener =
	    new DummySimpleClientListener(
		new PasswordAuthentication("guest", password));

	SimpleClient client = new SimpleClient(listener);
	int port = 5383;
	Properties props =
	    createProperties(
		"host", "localhost",
		"port", Integer.toString(port),
		"connectTimeout", Long.toString(TIMEOUT));
	SimpleServer server = new SimpleServer(port);
	try {
	    server.start();
	    client.login(props);
	    synchronized (client) {
		client.wait(TIMEOUT);
	    }
	    assertTrue(listener.loggedIn);
	    assertFalse(listener.disconnected);
	    // request forced disconnection
	    client.logout(true);
	    synchronized (client) {
		client.wait(TIMEOUT);
	    }
	    assertTrue(listener.disconnected);
	    assertFalse(listener.disconnectGraceful);
	} finally {
	    server.shutdown();
	}
    }

    /**
     * Test that login (with a different authentication
     * credential) can succeed after a login failure
     * @throws Exception
     */
    public void testLoginAfterLoginFailure() throws Exception{
 	DummySimpleClientListener listener =
	    new DummySimpleClientListener(
 		new PasswordAuthentication("guest", new char[] {'!'}));

	SimpleClient client = new SimpleClient(listener);
	int port = 5382;
	Properties props =
	    createProperties(
		"host", "localhost",
		"port", Integer.toString(port),
		"connectTimeout", Long.toString(TIMEOUT));
	SimpleServer server = new SimpleServer(port);
	try {
	    server.start();
	    client.login(props);
	    synchronized (client) {
		client.wait(TIMEOUT);
	    }
	    assertTrue(listener.loginFailed);
            assertFalse(listener.loggedIn);
            listener.loginFailed = false;
            listener.setAuthentication(
                    new PasswordAuthentication("guest", password));
            client.login(props);
            synchronized (client){
                client.wait(TIMEOUT);
            }
            assertTrue(listener.loggedIn);
            assertFalse(listener.loginFailed);
        } finally {
            server.shutdown();
        }
    }

    public void testRelocate() throws Exception {
	DummySimpleClientListener listener =
	    new DummySimpleClientListener(
		new PasswordAuthentication("guest", password));

	SimpleClient client = new SimpleClient(listener);
	int port = 5383;
	int newPort = 5384;
	Properties props =
	    createProperties(
		"host", "localhost",
		"port", Integer.toString(port),
		"connectTimeout", Long.toString(TIMEOUT));
	SimpleServer server = new SimpleServer(port);
	SimpleServer newServer = new SimpleServer(newPort);
	try {
	    server.start();
	    newServer.start();
	    client.login(props);
	    synchronized (client) {
		client.wait(TIMEOUT);
	    }
	    assertTrue(listener.loggedIn);
	    assertFalse(listener.reconnecting);
	    assertFalse(listener.reconnected);
	    MessageBuffer msg =
		new MessageBuffer(MessageBuffer.getSize("Relocate") + 4);
	    msg.putString("Relocate").putInt(newPort);
	    client.send(ByteBuffer.wrap(msg.getBuffer()).asReadOnlyBuffer());
	    synchronized (client) {
		client.wait(TIMEOUT);
	    }
	    assertTrue(listener.reconnecting);
	    assertTrue(listener.reconnected);
		
	} finally {
	    server.shutdown();
	}
    }
    
    private class DummySimpleClientListener implements SimpleClientListener {

	private volatile int getPasswordAuthentication = 0;
	private volatile boolean disconnected = false;
	private volatile boolean disconnectGraceful = false;
	private volatile String disconnectReason = null;
	private volatile boolean loginFailed = false;
	private volatile String loginFailedReason = null;
	private volatile boolean loggedIn = false;
	private volatile int loggedInCount = 0;
	private volatile PasswordAuthentication auth;
	private volatile boolean reconnecting = false;
	private volatile boolean reconnected = false;

	DummySimpleClientListener() {
	    this(null);
	}

	DummySimpleClientListener(PasswordAuthentication auth) {
	    this.auth = auth;
	}
	
	public PasswordAuthentication getPasswordAuthentication() {
	    getPasswordAuthentication++;
	    return auth;
	}

	public void loggedIn() {
	    System.err.println("TestSimpleClient.loggedIn");
	    loggedIn = true;
	    loggedInCount++;
	    synchronized (this) {
		notify();
	    }
	}

	public void loginFailed(String reason) {
	    System.err.println("TestSimpleClient.loginFailed: reason: " +
			       reason);
	    loginFailed = true;
	    loginFailedReason = reason;
	    synchronized (this) {
		notify();
	    }
	}

	public ClientChannelListener joinedChannel(ClientChannel channel) {
	    return null;
	}

	public void receivedMessage(ByteBuffer message) {
	}

	public void reconnecting() {
	    reconnecting = true;
	}
	
	public void reconnected() {
	    reconnected = true;
	}
	
	public void disconnected(boolean graceful, String reason){
	    System.err.println("TestSimpleClient.disconnected: graceful: " +
			       graceful + ", reason: " + reason);
	    disconnected = true;
	    disconnectGraceful = graceful;
	    disconnectReason = reason;
	    synchronized (this) {
		notify();
	    }
	}

        /**
         * Set a new authentication credential for this client listener.
         * This will allow the same listener to attempt to log in using
         * different login name/password combinations.
	 *
         * @param newAuthentication an authentication object that will be used
         * for login
         */
        void setAuthentication(PasswordAuthentication newAuthentication) {
            auth = newAuthentication;
        }
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
    
}
