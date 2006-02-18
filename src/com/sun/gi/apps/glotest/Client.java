/*
 * Copyright 2006 by Sun Microsystems, Inc.  All rights reserved.
 */

package com.sun.gi.apps.glotest;

import com.sun.gi.comm.discovery.impl.URLDiscoverer;
import com.sun.gi.comm.users.client.ClientAlreadyConnectedException;
import com.sun.gi.comm.users.client.ClientChannel;
import com.sun.gi.comm.users.client.ClientChannelListener;
import com.sun.gi.comm.users.client.ClientConnectionManager;
import com.sun.gi.comm.users.client.ClientConnectionManagerListener;
import com.sun.gi.comm.users.client.impl.ClientConnectionManagerImpl;

import java.io.File;
import java.util.logging.Logger;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;

public class Client implements ClientConnectionManagerListener {

    private static final Logger log =
	Logger.getLogger("com.sun.gi.apps.battleboard.client");

    private ClientConnectionManager  mgr;
    private ClientChannel            channel;

    private String         appName = "GLOTestRace";
    private String         appUser = "foo";
    private String         appPass = "bar";

    public Client() { }

    public Client(String[] args) {
	if (args.length > 0) {
	    appName = args[0];
	}
    }

    public void run() {
	try {
	    mgr = new ClientConnectionManagerImpl(appName,
		      new URLDiscoverer(
			  new File("FakeDiscovery.xml").toURI().toURL()));
	    mgr.setListener(this);

	    String[] classNames = mgr.getUserManagerClassNames();

	    mgr.connect(classNames[0]);

	} catch (Exception e) {
	    e.printStackTrace();
	    return;
	}
    }

    public void visitNameCallback(NameCallback cb) {
	log.finer("visitNameCallback");
	cb.setName(appUser);
    }

    public void visitPasswordCallback(PasswordCallback cb) {
	log.finer("visitPasswordCallback");
	cb.setPassword(appPass.toCharArray());
    }

    // ClientConnectionManagerListener methods

    public void validationRequest(Callback[] callbacks) {
	log.info("validationRequest");

	for (Callback cb : callbacks) {
	    try {
		if (cb instanceof NameCallback) {
		    visitNameCallback((NameCallback) cb);
		} else if (cb instanceof PasswordCallback) {
		    visitPasswordCallback((PasswordCallback) cb);
		}
	    } catch (Exception e) {
		e.printStackTrace();
	    } 
	}

	mgr.sendValidationResponse(callbacks);
    }

    public void connected(byte[] myID) {
	log.info("connected");
    }

    public void connectionRefused(String message) {
	log.info("connectionRefused: " + message);
    }

    public void disconnected() {
	log.info("disconnected");
    }

    public void userJoined(byte[] userID) {
	log.info("userJoined");
    }

    public void userLeft(byte[] userID) {
	log.info("userLeft");
    }

    public void failOverInProgress() {
	log.info("failOverInProgress - client choosing to exit");
	System.exit(1);
    }

    public void reconnected() {
	log.info("reconnected");
    }

    public void channelLocked(String chan, byte[] userID) {
	log.warning("Channel `" + chan + "' is locked");
    }

    public void joinedChannel(final ClientChannel channel) {
	log.info("joinedChannel " + channel.getName());
    }

    // main()

    public static void main(String[] args) {
	new Client(args).run();
    }

}
