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

package com.sun.sgs.test.util;

import com.sun.sgs.impl.io.SocketEndpoint;
import com.sun.sgs.impl.io.TransportType;
import com.sun.sgs.impl.sharedutil.HexDumper;
import com.sun.sgs.impl.sharedutil.MessageBuffer;
import com.sun.sgs.io.Connection;
import com.sun.sgs.io.ConnectionListener;
import com.sun.sgs.io.Connector;
import com.sun.sgs.protocol.simple.SimpleSgsProtocol;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.junit.Assert;

/**
 * Abstract dummy client code for testing purposes.
 */
public abstract class AbstractDummyClient extends Assert {
    
    private static final int WAIT_TIME = 5000;

    private static final byte RELOCATE_PROTOCOL_VERSION = 5;

    /* -- client state -- */
    public final String name;
    private final byte protocolVersion;
    protected volatile byte[] reconnectKey = new byte[0];
    private final Object lock = new Object();

    /* -- connection state -- */
    private Connector<SocketAddress> connector;
    private Listener listener;
    private Connection connection;
    private volatile int connectPort = 0;
    private boolean connected = false;
    private boolean waitForSuspendMessages = false;
    private boolean suspendMessages = false;

    /* -- login/logout state -- */
    private boolean loginAck = false;
    private boolean loginSuccess = false;
    private boolean logoutAck = false;

    /* -- redirection state -- */
    private boolean loginRedirect = false;
    private String redirectHost;
    public int redirectPort;

    /* -- relocation state -- */
    private boolean relocateSession;
    private String relocateHost;
    private int relocatePort;
    private byte[] relocateKey = new byte[0];
    private boolean relocateAck;
    private boolean relocateSuccess;
	
	
    /**
     * Constructs an instance with the given {@code name} with the latest
     * protocol version.
     */
    public AbstractDummyClient(String name) {
	this(name, SimpleSgsProtocol.VERSION);
    }

    /**
     * Constructs an instance with the given {@code name} and the specified
     * protocol version.
     */
    public AbstractDummyClient(String name, byte protocolVersion) {
	this.name = name;
	this.protocolVersion = protocolVersion;
    }

    /**
     * Connects this client to the given {@code port} and returns this
     * instance.
     */
    public AbstractDummyClient connect(int port) {
	connectPort = port;
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

    /**
     * Returns {@code true} if this client is connected.
     */
    public boolean isConnected() {
	synchronized (lock) {
	    return connected;
	}
    }

    /**
     * Returns the port last specified for the {@link #connect connect}
     * method.
     */
    public int getConnectPort() {
	return connectPort;
    }

    /**
     * Returns the redirect port.
     */
    public int getRedirectPort() {
	synchronized (lock) {
	    return redirectPort;
	}
    }
    
    /**
     * Throws a {@code RuntimeException} if this session is not
     * logged in.
     */
    protected void checkLoggedIn() {
	synchronized (lock) {
	    if (!(connected && (loginSuccess || relocateSuccess ))) {
		throw new RuntimeException(
		    toString() + " not connected or loggedIn");
	    }
	}
    }

    /**
     * Sends a login request and waits for it to be acknowledged,
     * returning {@code true} if login was successful, and {@code
     * false} if login was redirected.  If the login was not successful
     * or redirected, then a {@code RuntimeException} is thrown because
     * the login operation timed out before being acknowledged.
     */
    public boolean login() {
	return login(true);
    }

    /**
     * Sends a login request and if {@code waitForLogin} is {@code
     * true} waits for the request to be acknowledged, returning {@code
     * true} if login was successful, and {@code false} if login was
     * redirected, otherwise a {@code RuntimeException} is thrown
     * because the login operation timed out before being acknowledged.
     *
     * If {@code waitForLogin} is false, this method returns {@code
     * true} if the login is known to be successful (the outcome may
     * not yet be known because the login operation is asynchronous),
     * otherwise it returns false.  Invoke {@code waitForLogin} to wait
     * for an expected successful login.
     */
    public boolean login(boolean waitForLogin) {
	synchronized (lock) {
	    if (connected == false) {
		throw new RuntimeException(toString() + " not connected");
	    }
	}
	String password = "password";

	MessageBuffer buf =
	    new MessageBuffer(2 + MessageBuffer.getSize(name) +
			      MessageBuffer.getSize(password));
	buf.putByte(SimpleSgsProtocol.LOGIN_REQUEST).
	    putByte(protocolVersion).
	    putString(name).
	    putString(password);
	loginAck = false;
	try {
	    connection.sendBytes(buf.getBuffer());
	} catch (IOException e) {
	    throw new RuntimeException(e);
	}
	if (waitForLogin) {
	    if (waitForLogin()) {
		return true;
	    } else if (isLoginRedirected()) {
		int port = redirectPort;
		disconnect();
		connect(port);
		return login();
	    }
	}
	synchronized (lock) {
	    return loginSuccess;
	}
    }

    /**
     * Waits for a login acknowledgement, and returns {@code true} if
     * login was successful, {@code false} if login was redirected or
     * failed, otherwise a {@code RuntimeException} is thrown because
     * the login operation timed out before being acknowledged.
     */
    public boolean waitForLogin() {
	synchronized (lock) {
	    try {
		if (loginAck == false) {
		    lock.wait(WAIT_TIME);
		}
		if (loginAck != true) {
		    throw new RuntimeException(toString() + " login timed out");
		}
		if (loginRedirect == true) {
		    return false;
		}
		return loginSuccess;
	    } catch (InterruptedException e) {
		throw new RuntimeException(toString() + " login timed out", e);
	    }
	}
    }

    /**
     * Returns {@code true} if the login is redirected, otherwise returns
     * {@code false}.
     */
    public boolean isLoginRedirected() {
	return loginRedirect;
    }

    /**
     * Notifies this client that it is logged in or relocated and a new
     * reconnect key has been granted.
     */
    protected void newReconnectKey(byte[] reconnectKey) {
    }
    
    /**
     * Waits for this client to receive a RELOCATE_NOTIFICATION message.
     * Throws {@code AssertionFailedError} if the notification is not
     * received before the timeout period, or if {@code expectedPort} is
     * non-zero and does not match the relocation port specified in the
     * received RELOCATE_NOTIFICATION.
     */
    public void waitForRelocationNotification(int expectedPort) {
	checkRelocateProtocolVersion();
	System.err.println(toString() +
			   " waiting for relocation notification...");
	synchronized (lock) {
	    try {
		if (relocateSession == false) {
		    lock.wait(WAIT_TIME);
		}
		if (relocateSession != true) {
		    fail(toString() + " relocate notification timed out");
		}
		if (expectedPort != 0) {
		    assertEquals(expectedPort, relocatePort);
		}
	    } catch (InterruptedException e) {
		fail(toString() + " relocated timed out: " + e.toString());
	    }
	}
    }

    /**
     * Relocates this client's connection, if the server has instructed it
     * to do so via a RELOCATE_NOTIFICATION. <p>
     *
     * If this client has not yet received a RELOCATE_NOTIFICATION, it first
     * waits until one is received or the timeout expires, which ever comes
     * first.  If a RELOCATE_NOTIFICATION is not received or if the
     * specified {@code expectedPort} is non-zero and does not match the
     * relocation port, then {@code AssertionFailedError} is thrown. <p>
     *
     * If a RELOCATE_NOTIFICATION is correctly received, then this method
     * sends a RELOCATE_REQUEST message to the local host on the relocation
     * port received by a RELOCATE_NOTIFICATION. If {@code useValidKey} is
     * {@code true}, the current valid relocation key is used in the
     * relocate request, otherwise an invalid relocation key is used. <p>
     *
     * This method waits for an acknowledgment (either RELOCATE_SUCCESS or
     * RELOCATE_FAILURE).  If {@code shouldSucceed} is {@code true} and a
     * RELOCATE_FAILURE is received, this method throws {@code
     * AssertionFailedError}; similarly if {@code shouldSucceed} is {@code
     * false} and a RELOCATE_SUCCESS is received, {@code
     * AssertionFailedError} will be thrown.
     */
    public void relocate(int expectedPort, boolean useValidKey,
			 boolean shouldSucceed)
    {
	checkRelocateProtocolVersion();
	synchronized (lock) {
	    if (!relocateSession) {
		waitForRelocationNotification(expectedPort);
	    } else {
		if (expectedPort != 0) {
		    assertEquals(expectedPort, relocatePort);
		}
	    }
	}
	System.err.println(toString() + " relocating...");
	disconnect();
	relocateAck = false;
	relocateSuccess = false;
	suspendMessages = false;
	connect(relocatePort);
	byte[] key = useValidKey ? relocateKey : new byte[0];
	ByteBuffer buf = ByteBuffer.allocate(2 + key.length);
	buf.put(SimpleSgsProtocol.RELOCATE_REQUEST).
	    put(SimpleSgsProtocol.VERSION).
	    put(key).
	    flip();
	try {
	    connection.sendBytes(buf.array());
	} catch (IOException e) {
	    throw new RuntimeException(e);
	}
	synchronized (lock) {
	    try {
		if (!relocateAck) {
		    lock.wait(WAIT_TIME);
		}
		if (!relocateAck) {
		    throw new RuntimeException(
			toString() + " relocate timed out");
		}
		if (shouldSucceed) {
		    if (!relocateSuccess) {
			fail("relocation failed");
		    }
		} else if (relocateSuccess) {
		    fail("relocation succeeded");
		}
	    } catch (InterruptedException e) {
		throw new RuntimeException(
		    toString() + " relocate timed out", e);
	    }
	    relocateSession = false;
	    relocateAck = false;
	    relocatePort = 0;
	    relocateSuccess = false;
	}
	
    }

    /**
     * Sends a SESSION_MESSAGE to the server with the specified content.
     * If {@code checkSuspend} is {@code true}, then, if the subclass
     * supports suspending messages, it should check to see if messages are
     * suspended before forwarding the message to the server.  The method
     * default implementation of {@link #sendRaw} will check if messages
     * are suspended. 
     */
    public abstract void sendMessage(byte[] message, boolean checkSuspend);

    /**
     * Writes the specified {@code bytes} directly to the underlying
     * connection.  If {@code checkSuspended} is {@code true}, and messages
     * sending is currently suspended, then throw an {@code
     * IllegalStateException}. 
     */
    protected void sendRaw(byte[] bytes, boolean checkSuspended) {
	synchronized (lock) {
	    if (checkSuspended && suspendMessages) {
		throw new IllegalStateException("messages suspended");
	    }
				  
	    try {
		connection.sendBytes(bytes);
	    } catch (IOException e) {
		throw new RuntimeException(e);
	    }
	}
    }

    /**
     * If this session is not connected, this method returns; otherwise
     * this method sends a LOGOUT_REQUEST to the server, and waits for
     * this client to receive a LOGOUT_SUCCESS acknowledgment or the
     * timeout to expire, whichever comes first.  If the LOGOUT_SUCCESS
     * acknowledgment is not received, then {@code AssertionFailedError}
     * is thrown. 
     */
    public void logout() {
	synchronized (lock) {
	    if (connected == false) {
		return;
	    }
	    logoutAck = false;
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
		    fail(toString() + " logout timed out");
		}
	    } catch (InterruptedException e) {
		fail(toString() + " logout timed out: " + e.toString());
	    } finally {
		if (! logoutAck)
		    disconnect();
	    }
	}
    }

    /**
     * Disconnects this client, and returns either when the connection
     * is closed or the timeout expires, which ever comes first.
     */
    public void disconnect() {
	System.err.println(toString() + " disconnecting");

	synchronized (lock) {
	    if (! connected) {
		return;
	    }
	    try {
		connection.close();
		lock.wait(WAIT_TIME);
	    } catch (Exception e) {
		System.err.println(toString() + " disconnect exception:" + e);
		lock.notifyAll();
	    } finally {
		if (connected) {
		    reset();
		}
	    }
	}
    }

    /**
     * Resets the connection state so that the client can connect and
     * login again.
     */
    private void reset() {
	assert Thread.holdsLock(lock);
	connected = false;
	connection = null;
	loginAck = false;
	loginSuccess = false;
	loginRedirect = false;
    }

    /**
     * Waits for the connection to close or the timeout to expire,
     * whichever comes first, and returns {@code true} if the
     * underlying connection disconnected, and {@code false} otherwise.
     */
    public boolean waitForDisconnect() {
	synchronized (lock) {
	    try {
		if (connected) {
		    lock.wait(WAIT_TIME);
		}
	    } catch (InterruptedException ignore) {
	    }
	    return !connected;
	}
    }

    /**
     * Sets a flag that indicates this client should wait for a
     * SUSPEND_MESSAGES notification, instead of replying with a
     * SUSPEND_MESSAGES_COMPLETE ack to the server.  To wait for the
     * SUSPEND_MESSAGES notification, invoke the {@link
     * #waitForSuspendMessages} method.
     */
    public void setWaitForSuspendMessages() {
	checkRelocateProtocolVersion();
	synchronized (lock) {
	    waitForSuspendMessages = true;
	}
    }

    /**
     * Waits for a SUSPEND_MESSAGES notification to be sent to this
     * client.  A previous call to {@link #setWaitForSuspendMessages} must
     * be invoked before the server sends a SUSPEND_MESSAGES notification
     * in order for this wait to be successful.  This method returns when a
     * SUSPEND_MESSAGES notification has been received; it does NOT allow a
     * SUSPEND_MESSAGES_COMPLETE ack to be sent.  To send the
     * SUSPEND_MESSAGES_COMPLETE ack, invoke the {@link
     * #sendSuspendMessagesComplete} method.
     */
    public void waitForSuspendMessages() {
	checkRelocateProtocolVersion();	
	synchronized (lock) {
	    try {
		if (!suspendMessages) {
		    lock.wait(WAIT_TIME);
		}
	    } catch (InterruptedException ignore) {
	    }
	    if (!suspendMessages) {
		fail(toString() +
		     " time out waiting for SUSPEND_MESSAGES");
	    }
	}
    }

    /**
     * Sends a SUSPEND_MESSAGES_COMPLETE ack to the server.
     */
    public void sendSuspendMessagesComplete() {
	checkRelocateProtocolVersion();
	synchronized (lock) {
	    ByteBuffer msg = ByteBuffer.allocate(1);
	    msg.put(SimpleSgsProtocol.SUSPEND_MESSAGES_COMPLETE).
		flip();
	    try {
		connection.sendBytes(msg.array());
	    } catch (IOException e) {
		throw new RuntimeException(e);
	    }
	}
    }

    /**
     * Gives a subclass a chance to handle an {@code opcode}.  This
     * implementation handles login, redirection, relocation, and
     * logout but does not handle session or channel messages.
     */
    protected void handleOpCode(byte opcode, MessageBuffer buf) {

	switch (opcode) {
	    case SimpleSgsProtocol.LOGIN_SUCCESS:
		reconnectKey = buf.getBytes(buf.limit() - buf.position());
		newReconnectKey(reconnectKey);
		synchronized (lock) {
		    loginAck = true;
		    loginSuccess = true;
		    System.err.println("login succeeded: " + name);
		    lock.notifyAll();
		}
		sendMessage(new byte[0], true);
		break;
		    
	    case SimpleSgsProtocol.LOGIN_FAILURE:
		String failureReason = buf.getString();
		synchronized (lock) {
		    loginAck = true;
		    loginSuccess = false;
		    System.err.println("login failed: " + name +
				       ", reason:" + failureReason);
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
		}
		break;

	    case SimpleSgsProtocol.SUSPEND_MESSAGES:
		checkRelocateProtocolVersion();
		synchronized (lock) {
		    if (suspendMessages) {
			break;
		    }
		    suspendMessages = true;
		    if (waitForSuspendMessages) {
			waitForSuspendMessages = false;
			lock.notifyAll();
		    } else {
			sendSuspendMessagesComplete();
		    }
		}
		break;
		
		    
	    case SimpleSgsProtocol.RELOCATE_NOTIFICATION:
		checkRelocateProtocolVersion();
		relocateHost = buf.getString();
		relocatePort = buf.getInt();
		relocateKey = buf.getBytes(buf.limit() - buf.position());
		synchronized (lock) {
		    relocateSession = true;
		    System.err.println(
			"session to relocate: " + name +
			", host:" + relocateHost +
			", port:" + relocatePort +
			", key:" + HexDumper.toHexString(relocateKey));
		    lock.notifyAll();
		}
		break;

	    case SimpleSgsProtocol.RELOCATE_SUCCESS:
		checkRelocateProtocolVersion();
		reconnectKey = buf.getBytes(buf.limit() - buf.position());
		newReconnectKey(reconnectKey);
		synchronized (lock) {
		    relocateAck = true;
		    relocateSuccess = true;
		    System.err.println("relocate succeeded: " + name);
		    lock.notifyAll();
		}
		sendMessage(new byte[0], true);
		break;
		    
	    case SimpleSgsProtocol.RELOCATE_FAILURE:
		checkRelocateProtocolVersion();
		String relocateFailureReason = buf.getString();
		synchronized (lock) {
		    relocateAck = true;
		    relocateSuccess = false;
		    System.err.println("relocate failed: " + name +
				       ", reason:" + relocateFailureReason);
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

	    default:
		System.err.println(
		    "WARNING: [" + name + "] dropping unknown op code: " +
		    String.format("%02x", opcode));
		break;
	}
    }

    /** {@inheritDoc} */
    public String toString() {
	return "[" + name + "]";
    }

    /**
     * Throws an {@code AssertionFailedError} if this client's protocol
     * version does not support suspend or relocate.
     */
    public void checkRelocateProtocolVersion() {
	if (protocolVersion < RELOCATE_PROTOCOL_VERSION) {
	    String badVersion =
		toString() + " Protocol version: " + protocolVersion +
		" does not support suspend or relocate";
	    System.err.println(badVersion);
	    fail(badVersion);
	}
    }

    /**
     * ConnectionListener for connection I/O events.
     */
    private class Listener implements ConnectionListener {

	/** {@inheritDoc} */
	public void bytesReceived(Connection conn, byte[] buffer) {
	    if (connection != conn) {
		System.err.println(
		    toString() + "AbstractDummyClient.Listener.bytesReceived:" +
		    " wrong handle, got:" + conn + ", expected:" + connection);
		return;
	    }

	    MessageBuffer buf = new MessageBuffer(buffer);
	    byte opcode = buf.getByte();
	    
	    handleOpCode(opcode, buf);
	}

	/** {@inheritDoc} */
	public void connected(Connection conn) {
	    System.err.println(
		AbstractDummyClient.this.toString() + " connected to port:" +
			       connectPort);
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
		reset();
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
