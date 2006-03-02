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
    private boolean autologin = true;

    private Map<String, ClientChannel> channels;
    private ClientChannel currentChannel = null;
    private ChatPanel chatPanel;

    private ClientConnectionManager clientManager;
    private NameCallback     loginNameCB = null;
    private PasswordCallback loginPassCB = null;

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

    // Chat input

    public void handleChatInput(String text) {
	boolean handled = false;
	if (text.startsWith("/")) {
	    handled = handleChatCommand(text);
	}
	if (handled) {
	    return;
	}

	ByteBuffer buf = ByteBuffer.wrap(text.getBytes());
	buf.position(buf.limit());

	if (currentChannel == null) {
	    clientManager.sendToServer(buf, true);
	} else {
	    currentChannel.sendBroadcastData(buf, true);
	}
    }

    public boolean handleChatCommand(String text) {
	if (text.startsWith("/user ")) {
	    if (loginNameCB != null) {
		loginNameCB.setName(text.substring(6));
	    }
	    chatPanel.info("[Validator]: " + loginPassCB.getPrompt());
	    return true;
	}

	if (text.startsWith("/pass ")) {
	    if (loginPassCB != null) { // XXX check that name went first
		loginPassCB.setPassword(text.substring(6).toCharArray());
	    }
	    sendValidationResponse();
	    return true;
	}

	if (text.startsWith("/chan")) {
	    String newChanName = text.substring(6);
	    if (newChanName.length() == 0) {
		currentChannel = null;
		chatPanel.info("Current channel is direct-to-server");
		return true;
	    }

	    ClientChannel newChan = channels.get(newChanName);
	    if (newChan == null) {
		chatPanel.info("No such channel `" + newChanName + "'");
		return true;
	    }

	    if (!(newChan.equals(currentChannel))) {
		currentChannel = newChan;
	    }
	    chatPanel.info("Current channel is `" + newChanName + "'");
	    return true;
	}

	if (text.startsWith("/quit")) {
	    clientManager.disconnect();
	    return true;
	}

	return false;
    }

    // ClientConnectionManagerListener methods

    public void validationRequest(Callback[] callbacks) {
	log.finer("validationRequest");

	for (Callback cb : callbacks) {
	    try {
		if (cb instanceof NameCallback) {
		    loginNameCB = (NameCallback) cb;
		} else if (cb instanceof PasswordCallback) {
		    loginPassCB = (PasswordCallback) cb;
		}
	    } catch (Exception e) {
		e.printStackTrace();
	    } 
	}
	chatPanel.info("[Validator]: " + loginNameCB.getPrompt());
    }

    protected void sendValidationResponse() {
	Callback[] callbacks = new Callback[2];
	callbacks[0] = loginNameCB;
	callbacks[1] = loginPassCB;
	loginNameCB = null;
	loginPassCB = null;
	clientManager.sendValidationResponse(callbacks);
    }

    public void connected(byte[] myID) {
	chatPanel.info("connected as " + StringUtils.bytesToHex(myID));
    }

    public void connectionRefused(String message) {
	chatPanel.info("connection refused: `" + message + "'");
    }

    public void disconnected() {
	chatPanel.info("disconnected");
	System.exit(0);
    }

    public void userJoined(byte[] userID) {
	chatPanel.info(StringUtils.bytesToHex(userID) + " connected");
    }

    public void userLeft(byte[] userID) {
	chatPanel.info(StringUtils.bytesToHex(userID) + " disconnected");
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
	chatPanel.info("Joined `" + channel.getName() + "'");

	channels.put(channel.getName(), channel);

	channel.setListener(new ClientChannelListener() {
	    public void playerJoined(byte[] userID) {
		chatPanel.info(StringUtils.bytesToHex(userID) +
		    " joined `" + channel.getName() + "'");
	    }

	    public void playerLeft(byte[] userID) {
		chatPanel.info(StringUtils.bytesToHex(userID) +
		    " left `" + channel.getName() + "'");
	    }

	    public void dataArrived(byte[] userID, ByteBuffer data,
		    boolean reliable) {

		byte[] bytes = new byte[data.remaining()];
		data.get(bytes);

		chatPanel.messageArrived(StringUtils.bytesToHex(userID),
					 channel.getName(),
					 new String(bytes));
	    }

	    public void channelClosed() {
		chatPanel.info("Channel " + channel.getName() + " closed");
		if (currentChannel == channels.remove(channel.getName())) {
		    currentChannel = null;
		}
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
