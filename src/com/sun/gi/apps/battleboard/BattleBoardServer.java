/*
 * $Id$
 */

package com.sun.gi.apps.battleboard;

import java.nio.ByteBuffer;
import com.sun.gi.logic.SimBoot;
import com.sun.gi.logic.SimChannelListener;
import com.sun.gi.logic.SimTask;
import com.sun.gi.logic.SimUserDataListener;
import com.sun.gi.logic.SimUserListener;
import com.sun.gi.comm.routing.ChannelID;
import com.sun.gi.comm.routing.UserID;
import com.sun.gi.comm.users.server.impl.SGSUserImpl;
import com.sun.gi.logic.GLO;
import com.sun.gi.logic.GLOReference;
import java.util.Map;
import java.util.HashMap;
import java.util.Queue;
import java.util.LinkedList;
import java.util.Set;
import java.util.HashSet;

import java.util.logging.Logger;

import javax.security.auth.Subject;

public class BattleBoardServer
	implements SimBoot, SimUserListener,
		   SimUserDataListener, SimChannelListener {

    private static final long serialVersionUID = 1L;

    private static Logger log = Logger.getLogger("com.sun.gi.apps.battleboard");

    protected UserID serverUserID = null;
    protected Map<String, GLOReference> gameList;
    protected Map<UserID, Player> userIDToPlayer;
    protected Queue<Player> waitingToPlay;

    protected ChannelID matchmakerChannelID;
    protected ChannelID nextGameChannelID;
    protected final int MAX_PER_GAME = 2;

    protected GLOReference bootRef;

    class Player {
	boolean waiting;

	public Player(UserID id, String name) {
	    waiting = true;
	}

	public boolean isWaiting() {
	    return waiting;
	}
    }

    public void boot(SimTask task, boolean firstBoot) {
	log.info("Booting BattleBoard Server as appID " + task.getAppID());

	// Register with the SGS boot manager
	bootRef = task.findSO("BOOT");
	task.addUserListener(bootRef);

	gameList = new HashMap<String, GLOReference>();
	userIDToPlayer = new HashMap<UserID, Player>();
	waitingToPlay = new LinkedList<Player>();

	// Create the matchmaker channel so we can talk to unjoined clients
	matchmakerChannelID = task.openChannel("matchmaker");
	task.lock(matchmakerChannelID, true);

	// Make a channel for the next game we're getting together
	makeNewGameChannel(task);
    }

    protected void makeNewGameChannel(SimTask task) {
	// Create a new channel for this game
	// XXX store and increment a next-channel-number in the GLO
	String gameName = "BB-" + System.currentTimeMillis();
	log.info("Matchmaker: Next game channel is `" + gameName + "'");
	nextGameChannelID = task.openChannel(gameName);
	task.lock(nextGameChannelID, true);
    }

    public void userJoined(SimTask task, UserID uid, Subject subject) {
	log.info("User " + uid + " joined server");

	// We're interested in channel actions for this user
	task.addUserDataListener(uid, bootRef);

	// Put this user on the matchmaker channel initially
	task.join(uid, matchmakerChannelID);
    }

    public void userLeft(SimTask task, UserID uid) {
	log.info("User " + uid + " left server");
    }

    /// Perform matchmaking
    public void userDataReceived(SimTask task, UserID uid, ByteBuffer data) {
	log.warning("unexpected direct data from user " + uid);
    }

    protected void sendAlreadyJoined(SimTask task, UserID uid) {
	ByteBuffer buf = ByteBuffer.allocate(64);
	buf.put("already-joined".getBytes());
	task.sendData(matchmakerChannelID, new UserID[] { uid }, buf, true);
    }

    protected void sendJoinOK(SimTask task, UserID uid) {
	ByteBuffer buf = ByteBuffer.allocate(64);
	buf.put("ok ".getBytes());
	// ... TODO ...
	task.sendData(matchmakerChannelID, new UserID[] { uid }, buf, true);
    }

    public void userJoinedChannel(SimTask task, ChannelID cid, UserID uid) {
	log.info("User " + uid + " joined channel " + cid);
    }

    public void userLeftChannel(SimTask task, ChannelID cid, UserID uid) {
	log.info("User " + uid + " left channel " + cid);
    }

    // Handle the "join" command in matchmaker mode
    public void dataArrived(SimTask task, ChannelID cid,
	    UserID uid, ByteBuffer data) {

	log.info("Data from user " + uid + " on channel " + cid);

	if (! cid.equals(matchmakerChannelID)) {
	    log.warning("Server got message on unexpected channel. " +
		"was `" + cid + "', expecting matchmaker `" +
		matchmakerChannelID + "'");
	    return;
	}

	byte[] bytes = new byte[data.remaining()];
	data.get(bytes);
	String cmd = new String(bytes);

	if (! cmd.startsWith ("join ")) {
	    log.warning("Matchmaker got non-join command: `" + cmd + "'");
	    return;
	}

	final String playerName = cmd.substring(5);
	log.info("Matchmaker: join from `" + playerName + "'");

	if (userIDToPlayer.containsKey(uid)) {
	    log.warning("Matchmaker already has name `" +
		userIDToPlayer.get(uid) + "' for uid " + uid);
	    sendAlreadyJoined(task, uid);
	}

	Player p = userIDToPlayer.get(uid);

	if (p == null) {
	    p = new Player(uid, playerName);
	}

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
