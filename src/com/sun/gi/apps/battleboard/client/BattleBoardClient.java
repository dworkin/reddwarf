/*
 * Copyright 2006 by Sun Microsystems, Inc.  All rights reserved.
 */

package com.sun.gi.apps.battleboard.client;

import com.sun.gi.apps.battleboard.BattleBoard;
import com.sun.gi.comm.discovery.impl.URLDiscoverer;
import com.sun.gi.comm.users.client.ClientAlreadyConnectedException;
import com.sun.gi.comm.users.client.ClientChannel;
import com.sun.gi.comm.users.client.ClientChannelListener;
import com.sun.gi.comm.users.client.ClientConnectionManager;
import com.sun.gi.comm.users.client.ClientConnectionManagerListener;
import com.sun.gi.comm.users.client.impl.ClientConnectionManagerImpl;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;

public class BattleBoardClient implements ClientConnectionManagerListener {

    private static final Logger log =
	Logger.getLogger("com.sun.gi.apps.battleboard.client");

    private BattleBoard board;
    private ClientConnectionManager mgr;
    private ClientChannel gameChannel;

    private static Pattern wsRegexp = Pattern.compile("\\s");
    private State state = State.CONNECTING;

    private String myPlayerName = "player";

    // private Callback[] validationCallbacks = null;
    // private byte[] serverID = null;

    enum State {
	CONNECTING,
	JOINING_GAME
    }

    public BattleBoardClient() { }

    /**
     *
     */
    public void run() {
	try {
	    mgr = new ClientConnectionManagerImpl("BattleBoard",
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

    protected void showPrompt(String prompt) {
	System.out.print(prompt + ": ");
	System.out.flush();
    }

    public void visitNameCallback(NameCallback cb) {
	log.finer("visitNameCallback");
	showPrompt(cb.getPrompt());
	String line = getLine();
        myPlayerName = wsRegexp.matcher(line).replaceAll("");
	cb.setName(line);
    }

    public void visitPasswordCallback(PasswordCallback cb) {
	log.finer("visitPasswordCallback");
	showPrompt(cb.getPrompt());
	cb.setPassword(getLine().toCharArray());
    }

    // ClientConnectionManagerListener methods

    public void validationRequest(Callback[] callbacks) {
	log.fine("validationRequest");

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

    protected void sendJoinReq(ClientChannel chan) {
	String cmd = "join " + myPlayerName;
	ByteBuffer buf = ByteBuffer.wrap(cmd.getBytes());
	buf.position(buf.limit());
	mgr.sendToServer(buf, true);
    }

    protected String getLine() {
	try {
	    String line = BattleBoardUtils.getKeyboardLine();
	    return (line == null) ? "" : line;
	} catch (IOException e) {
	    e.printStackTrace();
	    return "";
	}
    }

    public void connected(byte[] myID) {
	log.fine("connected");

	switch (state) {
	    case CONNECTING:
		break;
	    default:
		log.warning("connected(): unexpected state " + state);
		break;
	}
    }

    /**
     * {@inheritDoc}
     */
    public void connectionRefused(String message) {
	log.info("connectionRefused");
    }

    /**
     * {@inheritDoc}
     */
    public void disconnected() {
	log.fine("disconnected");
    }

    /**
     * {@inheritDoc}
     */
    public void userJoined(byte[] userID) {
	log.fine("userJoined");
    }

    /**
     * {@inheritDoc}
     */
    public void userLeft(byte[] userID) {
	log.fine("userLeft");
    }

    /**
     * {@inheritDoc}
     */
    public void failOverInProgress() {
	log.info("failOverInProgress - client choosing to exit");
	System.exit(1);
    }

    /**
     * {@inheritDoc}
     */
    public void reconnected() {
	log.info("reconnected");
    }

    /**
     * {@inheritDoc}
     */
    public void channelLocked(String chan, byte[] userID) {
	log.warning("Channel `" + chan + "' is locked");
    }

    /**
     * {@inheritDoc}
     */
    public void joinedChannel(final ClientChannel channel) {
	log.fine("joinedChannel " + channel.getName());

	if (channel.getName().equals("matchmaker")) {

	    showPrompt("Enter your handle [" + myPlayerName + "]");
	    String line = getLine();
	    if (line.length() > 0) {
		// Spaces aren't allowed
		myPlayerName = wsRegexp.matcher(line).replaceAll("");
	    }

	    channel.setListener(new ClientChannelListener() {
		public void playerJoined(byte[] playerID) {
		    log.fine("playerJoined on " + channel.getName());
		}

		public void playerLeft(byte[] playerID) {
		    log.fine("playerLeft on " + channel.getName());
		}

		public void dataArrived(byte[] uid, ByteBuffer data,
			boolean reliable) {
		    log.fine("dataArrived on " + channel.getName());

		    byte[] bytes = new byte[data.remaining()];
		    data.get(bytes);

		    log.fine("got matchmaker data `" + bytes + "'");
		}

		public void channelClosed() {
		    log.fine("channel " + channel.getName() + " closed");
		}
	    });

	    state = State.JOINING_GAME;
	    sendJoinReq(channel);
	    return;
	}

	// Ok, must be a new game channel we've joined
	if (state == State.JOINING_GAME) {
	    channel.setListener(
		new BattleBoardPlayer(mgr, channel, myPlayerName));
	}
    }

    // main()

    public static void main(String[] args) {
	new BattleBoardClient().run();
    }
}
