package com.sun.gi.apps.battleboard;

import java.util.logging.Logger;

import com.sun.gi.logic.GLO;
import com.sun.gi.logic.SimChannelListener;
import com.sun.gi.logic.SimTask;
import com.sun.gi.logic.SimUserDataListener;
import com.sun.gi.logic.SimUserListener;
import com.sun.gi.comm.routing.ChannelID;
import com.sun.gi.comm.routing.UserID;
import com.sun.gi.comm.users.server.impl.SGSUserImpl;
import com.sun.gi.logic.GLO;
import com.sun.gi.logic.GLOReference;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.HashMap;
import java.util.Queue;
import java.util.LinkedList;
import java.util.Set;
import java.util.HashSet;

public class Matchmaker implements SimUserDataListener {

    private static final long serialVersionUID = 1L;

    private static final Logger log =
	Logger.getLogger("com.sun.gi.apps.battleboard");

    protected static final String MATCHMAKER_GLO_NAME = "matchmaker";

    protected final ChannelID channel;
    protected ChannelID nextGameChannelID;

    protected final int PLAYERS_PER_GAME = 2;

    public static GLOReference instance(SimTask task) {

	GLOReference ref = task.findGLO(MATCHMAKER_GLO_NAME);

	log.fine("Matchmaker instance ref is `" + ref + "'");

	if (ref != null) {
	    return ref;
	}

	log.fine("Created new Matchmaker instance");

	Matchmaker mm = new Matchmaker(task);
	ref = task.createGLO(mm, MATCHMAKER_GLO_NAME);
	task.addChannelListener(mm.channel, ref);
	return ref;
    }

    public static void addPlayer(SimTask task, GLOReference mmRef,
	    Player player) {

	Matchmaker mm = (Matchmaker) mmRef.peek(task);
	mm.addPlayer(task, player);
    }

    protected Matchmaker(SimTask task) {
	// Create the matchmaker channel so we can talk to unjoined clients
	channel = task.openChannel("matchmaker");
	task.lock(channel, true);

	makeNewGameChannel(task);
    }

    protected void makeNewGameChannel(SimTask task) {
	// Create a new channel for this game

	// XXX store and increment a next-channel-number in the GLO,
	// instead of using the current time(?) -jm
	String gameName = "BB-" + System.currentTimeMillis();

	log.finer("Matchmaker: Next game channel is `" + gameName + "'");

	nextGameChannelID = task.openChannel(gameName);
	task.lock(nextGameChannelID, true);
    }

    protected void addPlayer(SimTask task, Player player) {
	task.join(player.getUID(), channel);
    }

    protected void sendAlreadyJoined(SimTask task, UserID uid) {
	ByteBuffer buf = ByteBuffer.allocate(64);
	buf.put("already-joined".getBytes());
	task.sendData(channel, new UserID[] { uid }, buf, true);
    }

    protected void sendJoinOK(SimTask task, UserID uid) {
	ByteBuffer buf = ByteBuffer.allocate(64);
	buf.put("ok ".getBytes());
	// ... TODO ...
	task.sendData(channel, new UserID[] { uid }, buf, true);
    }

    // SimUserDataListener methods

    public void userJoinedChannel(SimTask task, ChannelID cid, UserID uid) {
	log.info("Matchmaker: User " + uid + " joined channel " + cid);
    }

    public void userLeftChannel(SimTask task, ChannelID cid, UserID uid) {
	log.info("Matchmaker: User " + uid + " left channel " + cid);
    }

    // Handle the "join" command in matchmaker mode
    public void userDataReceived(SimTask task, UserID uid, ByteBuffer data) {
	log.info("Matchmaker: Direct data from user " + uid);

	byte[] bytes = new byte[data.remaining()];
	data.get(bytes);
	String cmd = new String(bytes);

	if (! cmd.startsWith ("join ")) {
	    log.warning("Matchmaker got non-join command: `" + cmd + "'");
	    return;
	}

	final String playerName = cmd.substring(5);
	log.info("Matchmaker: join from `" + playerName + "'");

/*
	if (userIDToPlayer.containsKey(uid)) {
	    log.warning("Matchmaker already has name `" +
		userIDToPlayer.get(uid) + "' for uid " + uid);
	    sendAlreadyJoined(task, uid);
	}

	Player p = userIDToPlayer.get(uid);

	if (p == null)
*/

	Player p = Player.get(task, uid);
	p.setNickname(playerName);

	sendJoinOK(task, uid);

	// if there are now enough players
	//   - create a new object to handle the new game
	//   - have it listen to the game channel
	//     task.addChannelListener(nextGameChannelID, theGameGLORef);
	//     and it can:
	//     - compute and broadcast the turn order
	//     - etc...
    }
}
