/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.test.client.simple;

import com.sun.sgs.client.ClientChannel;
import com.sun.sgs.client.ClientChannelListener;
import com.sun.sgs.client.simple.SimpleClient;
import com.sun.sgs.client.simple.SimpleClientListener;

import java.net.PasswordAuthentication;
import java.util.Properties;
import junit.framework.TestCase;

public class TestSimpleClient extends TestCase {

    public void testLoginNoServer() throws Exception {
	DummySimpleClientListener listener =
	    new DummySimpleClientListener();

	SimpleClient client = new SimpleClient(listener);
	long timeout = 1000;
	Properties props =
	    createProperties(
		"host", "localhost",
		"port", Integer.toString(5382),
		"connectTimeout", Long.toString(timeout));
	client.login(props);
	try {
	    Thread.sleep(timeout * 2);
	} catch (InterruptedException e) {
	}
	if (listener.disconnectReason == null) {
	    fail("Didn't receive disconnected callback");
	}

	System.err.println("reason: " + listener.disconnectReason);
    }

    // TBD: it would be good to have a test that exercises
    // the timeout expiration.

    private class DummySimpleClientListener implements SimpleClientListener {

	private volatile String disconnectReason = null;
	
	public PasswordAuthentication getPasswordAuthentication() {
	    return null;
	}

	public void loggedIn() {
	}

	public void loginFailed(String reason) {
	}

	public ClientChannelListener joinedChannel(ClientChannel channel) {
	    return null;
	}

	public void receivedMessage(byte[] message) {
	}

	public void reconnecting() {
	}
	
	public void reconnected() {
	}
	
	public void disconnected(boolean graceful, String reason){
	    System.err.println("TestSimpleClient.disconnected: graceful: " +
			       graceful + ", reason: " + reason);
	    disconnectReason = reason;
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
