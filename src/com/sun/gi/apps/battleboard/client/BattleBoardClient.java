/*
 * Copyright 2006 by Sun Microsystems, Inc.  All rights reserved.
 */

package com.sun.gi.apps.battleboard.client;

import com.sun.gi.comm.discovery.impl.URLDiscoverer;
import com.sun.gi.comm.users.client.ClientChannel;
import com.sun.gi.comm.users.client.ClientChannelListener;
import com.sun.gi.comm.users.client.ClientConnectionManager;
import com.sun.gi.comm.users.client.ClientConnectionManagerListener;
import com.sun.gi.comm.users.client.impl.ClientConnectionManagerImpl;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;

public class BattleBoardClient implements ClientConnectionManagerListener {

    private static Logger log =
	Logger.getLogger("com.sun.gi.apps.battleboard.client");

    private ClientConnectionManager mgr;

    private static Pattern wsRegexp = Pattern.compile("\\s");
    private State state = State.CONNECTING;

    enum State {
	CONNECTING,
	JOINING_GAME
    }

    /*
     * Allow the user to control some aspects of the system from the
     * commandline rather than interactively.
     *
     * The properties battleboard.userName, battleboard.userPassword,
     * and battleboard.playerName can be used to specify the user
     * name, password, and player name, respectively.
     *
     * If the "battleboard.interactive" property is "false", then
     * append a newline to each prompt.  This makes it considerably
     * easier to attach the client to a line-oriented test harness.
     */

    private String userName = System.getProperty(
	    "battleboard.userName", null);
    private String userPassword = System.getProperty(
	    "battleboard.userPassword", null);
    private String playerName = System.getProperty(
	    "battleboard.playerName", null);
    private static boolean nonInteractive = "false".equals(
	    System.getProperty("battleboard.interactive", "true"));

    public BattleBoardClient() {
        // no-op
    }

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
	if (nonInteractive) {
	    System.out.println();
	}
	System.out.flush();
    }

    public void visitNameCallback(NameCallback cb) {
	log.finer("visitNameCallback");
	if (userName != null) {
	    cb.setName(userName);
	} else {
	    showPrompt(cb.getPrompt());
	    String line = getLine();
	    userName = wsRegexp.matcher(line).replaceAll("");
	    cb.setName(line);
	}
    }

    public void visitPasswordCallback(PasswordCallback cb) {
	log.finer("visitPasswordCallback");
	if (userPassword != null) {
	    cb.setPassword(userPassword.toCharArray());
	} else {
	    showPrompt(cb.getPrompt());
	    cb.setPassword(getLine().toCharArray());
	}
    }

    // ClientConnectionManagerListener methods

    public void validationRequest(Callback[] callbacks) {
	log.fine("validationRequest");

	for (Callback cb : callbacks) {
	    try {
		if (cb == null) {
		    // shouldn't happen.
		    log.warning("null callback");
		} else if (cb instanceof NameCallback) {
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
	String cmd = "join " + playerName;
	ByteBuffer buf = ByteBuffer.wrap(cmd.getBytes());
	buf.position(buf.limit());
	mgr.sendToServer(buf, true);
    }

    protected String getLine() {
	try {
	    BufferedReader input = new BufferedReader(
		    new InputStreamReader(System.in));
	    String line =  input.readLine();
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

	    if (playerName == null) {

		/*
		 * If the user hasn't provided a playerName, offer
		 * that they use their userName as their playerName.
		 */

		playerName = userName;
		showPrompt("Enter your handle [" + userName + "]");
		String line = getLine();
		if (line.length() > 0) {
		    // Spaces aren't allowed
		    playerName = wsRegexp.matcher(line).replaceAll("");
		}
	    }

            /*
             * This Matchmaker channel listener, isn't strictly needed.
             * The server will manage the process of joining this client
             * to the correct Game channel when enough players are present.
             *
             * A more sophisticated game might match players to games based
             * on various parameters.
             * 
             * This listener will also get called back if the Matchmaker
             * channel closes for some reason, which a client may wish
             * to handle.
             * 
             * Any non-trivial listener should be implemented as a
             * full-fledged class rather than as an anonynmous class.
             */
	    channel.setListener(new ClientChannelListener() {
		public void playerJoined(byte[] playerID) {
		    //log.fine("playerJoined on " + channel.getName());
		}

		public void playerLeft(byte[] playerID) {
		    //log.fine("playerLeft on " + channel.getName());
		}

		public void dataArrived(byte[] uid, ByteBuffer data,
			boolean reliable) {
		    //log.fine("dataArrived on " + channel.getName());

		    byte[] bytes = new byte[data.remaining()];
		    data.get(bytes);

		    //log.fine("got matchmaker data `" + bytes + "'");
		}

		public void channelClosed() {
		    //log.fine("channel " + channel.getName() + " closed");
		}
	    });

	    state = State.JOINING_GAME;
	    sendJoinReq(channel);
	    return;
	}

	// Ok, must be a new game channel we've joined
	if (state == State.JOINING_GAME) {
	    channel.setListener(
		new BattleBoardPlayer(mgr, channel, playerName));
	}
    }

    // main()

    public static void main(String[] args) {
	new BattleBoardClient().run();
    }
}
