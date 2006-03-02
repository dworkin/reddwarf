/*
 * Copyright 2006 by Sun Microsystems, Inc.  All rights reserved.
 */

package com.sun.gi.client.dirc;

import com.sun.gi.comm.discovery.impl.URLDiscoverer;
import com.sun.gi.comm.users.client.ClientAlreadyConnectedException;
import com.sun.gi.comm.users.client.ClientChannel;
import com.sun.gi.comm.users.client.ClientChannelListener;
import com.sun.gi.comm.users.client.ClientConnectionManager;
import com.sun.gi.comm.users.client.ClientConnectionManagerListener;
import com.sun.gi.comm.users.client.impl.ClientConnectionManagerImpl;
import com.sun.gi.utils.types.StringUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.swing.JFrame;

public class DIRC implements ClientConnectionManagerListener, ChatManager {

    private static Logger log = Logger.getLogger("com.sun.gi.client.dirc");
    private static final Pattern wsRegexp = Pattern.compile("\\s");

    private String appName;
    private URL    discoveryURL;
    private ClientConnectionManager clientManager;
    private Map<String, ClientChannel> channels;
    private Callback[] validationCallbacks = null;
    private boolean autologin = true;

    private ChatPanel chatPanel;

    public DIRC(String app, URL discovery) {
	appName = app;
	discoveryURL = discovery;
	channels = new HashMap<String, ClientChannel>();

	chatPanel = new ChatPanel(this);

	JFrame frame = new JFrame("DIRC");
	frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE); // XXX
	frame.getContentPane().add(chatPanel);
	frame.pack();
	frame.setVisible(true);
    }

    public void run() {
	try {
	    clientManager =
		new ClientConnectionManagerImpl(appName,
		      new URLDiscoverer(discoveryURL));
	    clientManager.setListener(this);
	    String[] classNames = clientManager.getUserManagerClassNames();
	    clientManager.connect(classNames[0]);
	} catch (Exception e) {
	    e.printStackTrace();
	    return;
	}
    }

    // Input-related methods

    protected void showPrompt(String prompt) {
	chatPanel.info("[Validator] " + prompt);
    }
 
    // Validation-related methods

    public void visitNameCallback(NameCallback cb) {
	log.finest("visitNameCallback");
	String line = "foo";
	/*
	if (! autologin) {
	    showPrompt(cb.getPrompt());
	    line = getLine();
	}
	*/
	cb.setName(line);
    }

    public void visitPasswordCallback(PasswordCallback cb) {
	log.finest("visitPasswordCallback");
	String line = "bar";
	/*
	if (! autologin) {
	    showPrompt(cb.getPrompt());
	    line = getLine();
	}
	*/
	cb.setPassword(line.toCharArray());
    }

    // Chat input
    public void handleChatInput(String text) {
	log.info("handleChatInput: `" + text + "'");
    }

    // ClientConnectionManagerListener methods

    public void validationRequest(Callback[] callbacks) {
	log.finer("validationRequest");

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

	clientManager.sendValidationResponse(callbacks);
    }

    public void connected(byte[] myID) {
	chatPanel.info("connected");
    }

    public void connectionRefused(String message) {
	chatPanel.info("connectionRefused");
    }

    public void disconnected() {
	chatPanel.info("disconnected");
    }

    public void userJoined(byte[] userID) {
	chatPanel.info("userJoined");
    }

    public void userLeft(byte[] userID) {
	chatPanel.info("userLeft");
    }

    public void failOverInProgress() {
	log.finer("failOverInProgress - client choosing to exit");
	System.exit(1);
    }

    public void reconnected() {
	chatPanel.info("reconnected");
    }

    public void channelLocked(String chan, byte[] userID) {
	chatPanel.info("Channel `" + chan + "' is locked");
    }

    public void joinedChannel(final ClientChannel channel) {
	chatPanel.info("joinedChannel " + channel.getName());

	channel.setListener(new ClientChannelListener() {
	    public void playerJoined(byte[] playerID) {
		chatPanel.info("playerJoined on " + channel.getName());
	    }

	    public void playerLeft(byte[] playerID) {
		chatPanel.info("playerLeft on " + channel.getName());
	    }

	    public void dataArrived(byte[] uid, ByteBuffer data,
		    boolean reliable) {

		byte[] bytes = new byte[data.remaining()];
		data.get(bytes);

		chatPanel.messageArrived(StringUtils.bytesToHex(uid),
					 channel.getName(),
					 new String(bytes));
	    }

	    public void channelClosed() {
		chatPanel.info("channel " + channel.getName() + " closed");
	    }
	});
    }

    // main()

    public static void main(String[] args) {
	try {
	    new DIRC(
		"JNWN",
		new File("FakeDiscovery.xml").toURI().toURL()
	    ).run();
	} catch (Exception e) {
	    e.printStackTrace();
	    System.exit(1);
	}
    }

}
