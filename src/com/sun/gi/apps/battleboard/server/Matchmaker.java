package com.sun.gi.apps.battleboard.server;

import java.util.logging.Logger;

import com.sun.gi.logic.GLO;
import com.sun.gi.logic.SimChannelListener;
import com.sun.gi.logic.SimTask;
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

public class Matchmaker implements SimChannelListener {

    private static final long serialVersionUID = 1L;

    private static final Logger log =
	Logger.getLogger("com.sun.gi.apps.battleboard.server");

    protected static final String MATCHMAKER_GLO_NAME = "matchmaker";

    protected final ChannelID channel;

    protected int PLAYERS_PER_GAME = 2;

    static final int DEFAULT_BOARD_WIDTH  = 8;
    static final int DEFAULT_BOARD_HEIGHT = 8;
    static final int DEFAULT_CITY_COUNT   = 8;

    protected Set<GLOReference> waitingPlayers =
	new HashSet<GLOReference>();

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
    }

    protected void addPlayer(SimTask task, Player player) {
	task.join(player.getUID(), channel);
    }

    protected void sendAlreadyJoined(SimTask task, UserID uid) {
	ByteBuffer buf = ByteBuffer.allocate(64);
	buf.put("already-joined".getBytes());
	task.sendData(channel, new UserID[] { uid }, buf, true);
    }

    // Handle the "join" command in matchmaker mode
    public void userDataReceived(SimTask task, UserID uid, ByteBuffer data) {
	log.info("Matchmaker: data from user " + uid);

	byte[] bytes = new byte[data.remaining()];
	data.get(bytes);
	String cmd = new String(bytes);

	if (! cmd.startsWith ("join ")) {
	    log.warning("Matchmaker got non-join command: `" + cmd + "'");
	    return;
	}

	final String playerName = cmd.substring(5);
	log.info("Matchmaker: join from `" + playerName + "'");

	for (GLOReference ref : waitingPlayers) {
	    Player p = (Player) ref.peek(task);
	    if (playerName.equals(p.getNickname())) {
		log.warning("Matchmaker already has `" + playerName);
		sendAlreadyJoined(task, uid);
		return;
	    }

	}

	GLOReference playerRef = Player.getRef(task, uid);
	Player player = (Player) playerRef.get(task);
	player.setNickname(playerName);

	waitingPlayers.add(playerRef);

	// XXX the right thing to do here is probably to queue
	// a task to check whether we have a game togther, since
	// we hold the lock on this player but they might not be
	// involved in the next game we can spawn with current waiters.
	// Instead we'll just keep the lock for now.

	checkForEnoughPlayers(task);
    }

    protected void checkForEnoughPlayers(SimTask task) {

	if (waitingPlayers.size() < PLAYERS_PER_GAME) {
	    return;
	}

	if (waitingPlayers.size() > PLAYERS_PER_GAME) {
	    log.warning("Too many waiting players!  How'd that happen? "
		+ "expected " + PLAYERS_PER_GAME + ", got "
		+ waitingPlayers.size());
	}

	Game.create(task, waitingPlayers);
	waitingPlayers.clear();
    }

    // SimChannelListener methods

    public void joinedChannel(SimTask task, ChannelID cid, UserID uid) {
	log.info("Matchmaker: User " + uid + " joined channel " + cid);
    }

    public void leftChannel(SimTask task, ChannelID cid, UserID uid) {
	log.info("Matchmaker: User " + uid + " left channel " + cid);

	GLOReference playerRef = Player.getRef(task, uid);
	waitingPlayers.remove(playerRef);
    }
}
